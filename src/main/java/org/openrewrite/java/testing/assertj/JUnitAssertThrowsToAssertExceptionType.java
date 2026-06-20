/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.assertj;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;

public class JUnitAssertThrowsToAssertExceptionType extends Recipe {

    private static final String JUNIT_ASSERTIONS = "org.junit.jupiter.api.Assertions";
    private static final String ASSERTJ_ASSERTIONS = "org.assertj.core.api.Assertions";
    private static final String JUNIT_EXECUTABLE = "org.junit.jupiter.api.function.Executable";
    private static final String THROWING_CALLABLE = "org.assertj.core.api.ThrowableAssert$ThrowingCallable";
    private static final MethodMatcher ASSERT_THROWS_MATCHER = new MethodMatcher(JUNIT_ASSERTIONS + " assertThrows(..)");

    @Getter
    final String displayName = "JUnit AssertThrows to AssertJ exceptionType";

    @Getter
    final String description = "Convert `JUnit#AssertThrows` to `AssertJ#assertThatExceptionOfType` to allow for chained assertions on the thrown exception.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_THROWS_MATCHER), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!ASSERT_THROWS_MATCHER.matches(mi)) {
                    return mi;
                }

                Optional<Boolean> hasReturnType = hasReturnType();

                if (!hasReturnType.isPresent()) {
                    return mi;
                }

                List<Expression> args = mi.getArguments();

                // When the executable is a variable typed as JUnit's `Executable`, AssertJ's `isThrownBy`
                // expects a `ThrowingCallable` instead, so the variable declaration has to be retyped to keep
                // it compiling. That's only safe when every usage of the variable is an `assertThrows`
                // executable argument; otherwise leave the call untouched as the types would conflict.
                Expression executable = args.get(1);
                JavaType.Variable executableVariable = null;
                if (executable instanceof J.Identifier) {
                    JavaType.Variable fieldType = ((J.Identifier) executable).getFieldType();
                    if (fieldType != null && TypeUtils.isOfClassType(fieldType.getType(), JUNIT_EXECUTABLE)) {
                        if (!allUsagesAreAssertThrowsExecutable(fieldType)) {
                            return mi;
                        }
                        executableVariable = fieldType;
                    }
                }

                boolean returnActual = hasReturnType.get();

                maybeRemoveImport(JUNIT_ASSERTIONS);
                maybeRemoveImport(JUNIT_ASSERTIONS + ".assertThrows");
                maybeAddImport(ASSERTJ_ASSERTIONS, "assertThatExceptionOfType");

                if (executableVariable != null) {
                    doAfterVisit(new RetypeExecutableVariable(executableVariable));
                }

                if (args.size() == 2) {
                    String code = "assertThatExceptionOfType(#{any(java.lang.Class)}).isThrownBy(#{any(org.assertj.core.api.ThrowableAssert.ThrowingCallable)})";
                    if (returnActual) {
                        code += ".actual()";
                    }
                    return JavaTemplate.builder(code)
                            .staticImports(ASSERTJ_ASSERTIONS + ".assertThatExceptionOfType")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), args.get(0), args.get(1));
                }

                String code = "assertThatExceptionOfType(#{any()}).as(#{any()}).isThrownBy(#{any(org.assertj.core.api.ThrowableAssert.ThrowingCallable)})";
                if (returnActual) {
                    code += ".actual()";
                }
                return JavaTemplate.builder(code)
                        .staticImports(ASSERTJ_ASSERTIONS + ".assertThatExceptionOfType")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(), args.get(0), args.get(2), args.get(1));
            }

            /**
             * Check if there is a return type which would indicate the need for using
             * {@code .actual()} in the AssertJ call.
             * <p>
             * If the presence of a return type could not be determined then {@code Optional.empty()} is returned
             * and the current {@code J.MethodInvocation} should be used without further changes.
             *
             * @return {@code Optional.of(true)} if there is a return type otherwise {@code Optional.of(false)}.
             * If it could not be determined then {@code Optional.empty()}.
             */
            private Optional<Boolean> hasReturnType() {
                Object parent = getCursor().getParentTreeCursor().getValue();

                // These all have the method invocation return something
                if (parent instanceof J.Assignment ||
                        parent instanceof J.VariableDeclarations ||
                        parent instanceof J.VariableDeclarations.NamedVariable ||
                        parent instanceof J.Return ||
                        parent instanceof J.Ternary) {
                    return Optional.of(true);
                }

                if (parent instanceof J.Block) {
                    return Optional.of(false);
                }

                // Unknown parent type so not supported
                return Optional.empty();
            }

            /**
             * Determine whether every reference to the given variable can safely be retyped from JUnit's
             * {@code Executable} to AssertJ's {@code ThrowingCallable}. That is the case when each reference is
             * either the variable's own declaration/assignment target or the executable argument of an
             * {@code assertThrows} call (all of which are converted by this recipe). Any other usage would still
             * require an {@code Executable} and conflict with the retyped variable.
             */
            private boolean allUsagesAreAssertThrowsExecutable(JavaType.Variable variable) {
                J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
                if (cu == null) {
                    return false;
                }
                AtomicBoolean safe = new AtomicBoolean(true);
                new JavaIsoVisitor<Integer>() {
                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, Integer integer) {
                        if (safe.get() && Objects.equals(identifier.getFieldType(), variable)) {
                            Object parent = getCursor().getParentTreeCursor().getValue();
                            boolean ok = false;
                            if (parent instanceof J.VariableDeclarations.NamedVariable) {
                                ok = ((J.VariableDeclarations.NamedVariable) parent).getName() == identifier;
                            } else if (parent instanceof J.Assignment) {
                                ok = ((J.Assignment) parent).getVariable() == identifier;
                            } else if (parent instanceof J.MethodInvocation) {
                                J.MethodInvocation enclosing = (J.MethodInvocation) parent;
                                ok = ASSERT_THROWS_MATCHER.matches(enclosing) &&
                                        enclosing.getArguments().size() >= 2 &&
                                        enclosing.getArguments().get(1) == identifier;
                            }
                            if (!ok) {
                                safe.set(false);
                            }
                        }
                        return super.visitIdentifier(identifier, integer);
                    }
                }.visit(cu, 0);
                return safe.get();
            }
        });
    }

    /**
     * Retypes the declaration of an {@code org.junit.jupiter.api.function.Executable} variable to
     * {@code org.assertj.core.api.ThrowableAssert.ThrowingCallable}, so that a variable previously passed to
     * {@code assertThrows} keeps compiling when passed to AssertJ's {@code isThrownBy}.
     */
    @RequiredArgsConstructor
    private static class RetypeExecutableVariable extends JavaIsoVisitor<ExecutionContext> {

        private final JavaType.Variable variable;
        private final JavaType.ShallowClass throwingCallable = JavaType.ShallowClass.build(THROWING_CALLABLE);

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
            J.VariableDeclarations mv = super.visitVariableDeclarations(multiVariable, ctx);
            if (!TypeUtils.isOfClassType(mv.getTypeAsFullyQualified(), JUNIT_EXECUTABLE) ||
                    mv.getVariables().stream().noneMatch(v -> Objects.equals(v.getVariableType(), variable))) {
                return mv;
            }

            maybeRemoveImport(JUNIT_EXECUTABLE);
            maybeAddImport(THROWING_CALLABLE);

            TypeTree typeExpression = mv.getTypeExpression();
            J.Identifier newTypeExpression = new J.Identifier(Tree.randomId(),
                    typeExpression == null ? Space.EMPTY : typeExpression.getPrefix(),
                    Markers.EMPTY, emptyList(), "ThrowingCallable", throwingCallable, null);
            return mv.withTypeExpression(newTypeExpression);
        }

        @Override
        public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
            J.Identifier id = super.visitIdentifier(identifier, ctx);
            JavaType.Variable fieldType = id.getFieldType();
            if (Objects.equals(fieldType, variable)) {
                return id.withType(throwingCallable).withFieldType(fieldType.withType(throwingCallable));
            }
            return id;
        }
    }
}
