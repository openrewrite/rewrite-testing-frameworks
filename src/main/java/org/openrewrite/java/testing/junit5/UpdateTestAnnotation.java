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
package org.openrewrite.java.testing.junit5;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.FindImports;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markup;
import org.openrewrite.staticanalysis.LambdaBlockToExpression;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class UpdateTestAnnotation extends Recipe {

    @Override
    public String getDisplayName() {
        return "Migrate JUnit 4 `@Test` annotations to JUnit 5";
    }

    @Override
    public String getDescription() {
        return "Update usages of JUnit 4's `@org.junit.Test` annotation to JUnit 5's `org.junit.jupiter.api.Test` annotation.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                new UsesType<>("org.junit.Test", false),
                new FindImports("org.junit.Test", null).getVisitor()
        ), new UpdateTestAnnotationVisitor());
    }

    private static class UpdateTestAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final AnnotationMatcher JUNIT4_TEST = new AnnotationMatcher("@org.junit.Test");

        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            J.CompilationUnit c = super.visitCompilationUnit(cu, ctx);
            Set<NameTree> nameTreeSet = c.findType("org.junit.Test");
            if (!nameTreeSet.isEmpty()) {
                // Update other references like `Test.class`.
                c = (J.CompilationUnit) new ChangeType("org.junit.Test", "org.junit.jupiter.api.Test", true)
                        .getVisitor().visitNonNull(c, ctx);
            }

            maybeRemoveImport("org.junit.Test");
            doAfterVisit(new JavaIsoVisitor<ExecutionContext>() {
                @Override
                public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                    J.CompilationUnit c = cu;
                    c = c.withClasses(ListUtils.map(c.getClasses(), clazz -> (J.ClassDeclaration) visit(clazz, ctx)));
                    // take one more pass over the imports now that we've had a chance to add warnings to all
                    // uses of @Test through the rest of the source file
                    return c.withImports(ListUtils.map(c.getImports(), anImport -> (J.Import) visit(anImport, ctx)));
                }

                @Override
                public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                    if ("org.junit.Test".equals(anImport.getTypeName())) {
                        return Markup.error(anImport, new IllegalStateException("This import should have been removed by this recipe."));
                    }
                    return anImport;
                }

                @Override
                public JavaType visitType(@Nullable JavaType javaType, ExecutionContext ctx) {
                    if (TypeUtils.isOfClassType(javaType, "org.junit.Test")) {
                        getCursor().putMessageOnFirstEnclosing(J.class, "danglingTestRef", true);
                    }
                    return javaType;
                }

                @Override
                public J postVisit(J tree, ExecutionContext ctx) {
                    if (getCursor().getMessage("danglingTestRef", false)) {
                        return Markup.warn(tree, new IllegalStateException("This still has a type of `org.junit.Test`"));
                    }
                    return tree;
                }
            });
            return c;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            ChangeTestAnnotation cta = new ChangeTestAnnotation();
            J.MethodDeclaration m = (J.MethodDeclaration) cta.visitNonNull(method, ctx, getCursor().getParentOrThrow());
            if (m != method) {
                JavaParser.Builder<?, ?> javaParser = JavaParser.fromJavaVersion()
                        .classpathFromResources(ctx, "junit-jupiter-api-5", "apiguardian-api-1.1");
                if (cta.expectedException != null) {
                    m = JavaTemplate.builder("org.junit.jupiter.api.function.Executable o = () -> #{};")
                            .javaParser(javaParser)
                            .build()
                            .apply(
                                    updateCursor(m),
                                    m.getCoordinates().replaceBody(),
                                    m.getBody()
                            );

                    assert m.getBody() != null;
                    J.Lambda lambda = (J.Lambda) ((J.VariableDeclarations) m.getBody().getStatements().get(0))
                            .getVariables().get(0).getInitializer();

                    assert lambda != null;

                    if (cta.expectedException instanceof J.FieldAccess &&
                        TypeUtils.isAssignableTo("org.junit.Test$None", ((J.FieldAccess) cta.expectedException).getTarget().getType())) {
                        m = JavaTemplate.builder("assertDoesNotThrow(#{any(org.junit.jupiter.api.function.Executable)});")
                                .javaParser(javaParser)
                                .staticImports("org.junit.jupiter.api.Assertions.assertDoesNotThrow")
                                .build()
                                .apply(updateCursor(m), m.getCoordinates().replaceBody(), lambda);
                        maybeAddImport("org.junit.jupiter.api.Assertions", "assertDoesNotThrow");
                    } else {
                        m = JavaTemplate.builder("assertThrows(#{any(java.lang.Class)}, #{any(org.junit.jupiter.api.function.Executable)});")
                                .javaParser(javaParser)
                                .staticImports("org.junit.jupiter.api.Assertions.assertThrows")
                                .build()
                                .apply(updateCursor(m), m.getCoordinates().replaceBody(), cta.expectedException, lambda);
                        // Only remove the throws clause for the specific exception being tested
                        m = removeSpecificThrowsClause(m, cta.expectedException);
                        // Unconditionally add the import for assertThrows, got a report where the above template adds the method successfully
                        // but with missing type attribution for assertThrows so the import was missing
                        maybeAddImport("org.junit.jupiter.api.Assertions", "assertThrows", false);
                    }
                }
                if (cta.timeout != null) {
                    m = JavaTemplate.builder("@Timeout(value = #{any(long)}, unit = TimeUnit.MILLISECONDS)")
                            .javaParser(javaParser)
                            .imports("org.junit.jupiter.api.Timeout", "java.util.concurrent.TimeUnit")
                            .build()
                            .apply(
                                    updateCursor(m),
                                    m.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)),
                                    cta.timeout
                            );
                    maybeAddImport("org.junit.jupiter.api.Timeout");
                    maybeAddImport("java.util.concurrent.TimeUnit");
                }
                maybeAddImport("org.junit.jupiter.api.Test");

                doAfterVisit(new LambdaBlockToExpression().getVisitor());
            }

            return super.visitMethodDeclaration(m, ctx);
        }

        private J.MethodDeclaration removeSpecificThrowsClause(J.MethodDeclaration method, Expression expectedException) {
            List<NameTree> thrown = method.getThrows();
            if (thrown == null || thrown.isEmpty()) {
                return method;
            }

            // Extract the exception type from the expected exception expression
            // This should always be a `J.FieldAccess` of the form MyException.class
            JavaType expectedType = null;
            if (expectedException instanceof J.FieldAccess) {
                J.FieldAccess fa = (J.FieldAccess) expectedException;
                expectedType = fa.getTarget().getType();
            }

            if (expectedType == null) {
                return method;
            }

            // Filter out only the exception that matches the expected exception
            final JavaType finalExpectedType = expectedType;
            List<NameTree> filteredThrows = ListUtils.map(thrown, throwClause -> {
                JavaType throwType = throwClause.getType();
                if (throwType != null && TypeUtils.isAssignableTo(finalExpectedType, throwType)) {
                    return null;
                }
                return throwClause;
            });

            // If all throws were removed, return empty list, otherwise keep the remaining ones
            if (filteredThrows.isEmpty() || filteredThrows.stream().allMatch(t -> t == null)) {
                return method.withThrows(Collections.emptyList());
            }

            return method.withThrows(filteredThrows);
        }

        private static class ChangeTestAnnotation extends JavaIsoVisitor<ExecutionContext> {
            @Nullable
            Expression expectedException;

            @Nullable
            Expression timeout;

            boolean found;

            private JavaParser.Builder<?, ?> javaParser(ExecutionContext ctx) {
                   return JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "junit-jupiter-api-5", "apiguardian-api-1.1");
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation a, ExecutionContext ctx) {
                if (!found && JUNIT4_TEST.matches(a)) {
                    // While unlikely, it's possible that a method has an inner class/lambda/etc. with methods that have test annotations
                    // Avoid considering any but the first test annotation found
                    found = true;
                    if (a.getArguments() != null) {
                        for (Expression arg : a.getArguments()) {
                            if (!(arg instanceof J.Assignment)) {
                                continue;
                            }
                            J.Assignment assign = (J.Assignment) arg;
                            String assignParamName = ((J.Identifier) assign.getVariable()).getSimpleName();
                            Expression e = assign.getAssignment();
                            if ("expected".equals(assignParamName)) {
                                expectedException = e;
                            } else if ("timeout".equals(assignParamName)) {
                                timeout = e;
                            }
                        }
                    }

                    if (a.getAnnotationType() instanceof J.FieldAccess) {
                        a = JavaTemplate.builder("@org.junit.jupiter.api.Test")
                                .javaParser(javaParser(ctx))
                                .build()
                                .apply(getCursor(), a.getCoordinates().replace());
                    } else {
                        a = a.withArguments(null)
                                .withType(JavaType.ShallowClass.build("org.junit.jupiter.api.Test"));
                    }
                }
                return a;
            }
        }
    }
}
