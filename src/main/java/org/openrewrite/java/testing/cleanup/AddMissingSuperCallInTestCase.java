/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.testing.cleanup;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.NameTree;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Objects.requireNonNull;
import static org.openrewrite.Tree.randomId;

public class AddMissingSuperCallInTestCase extends ScanningRecipe<Set<String>> {

    private static final String TEST_CASE = "junit.framework.TestCase";

    private static final MethodMatcher SET_UP = new MethodMatcher(TEST_CASE + " setUp()", true);
    private static final MethodMatcher TEAR_DOWN = new MethodMatcher(TEST_CASE + " tearDown()", true);

    @Getter
    final String displayName = "Call `super` from JUnit 3 `setUp` and `tearDown` overrides";

    @Getter
    final String description = "Adds the missing `super.setUp()` or `super.tearDown()` call to an override in a " +
            "`junit.framework.TestCase` subclass, so the shared setup and cleanup an ancestor performs still runs. " +
            "`super.setUp()` goes first and `super.tearDown()` last, the order that lets an ancestor initialize " +
            "before the test and clean up after it. A class extending `TestCase` directly is left alone, because " +
            "`TestCase`'s own implementations are empty, as is an empty override, which turns the ancestor's off " +
            "on purpose. An override whose `throws` is missing or narrower than the inherited one gains the " +
            "inherited clause, so the call compiles, unless code in the repository extends the class, calls the " +
            "method, or has it implement an interface method, which the wider clause could stop compiling. A " +
            "`tearDown` whose last statement may not complete normally is left alone.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S2188");

    @Override
    public Set<String> getInitialValue(ExecutionContext ctx) {
        return new HashSet<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<String> pinned) {
        // Types whose `setUp` and `tearDown` cannot take a wider `throws` clause without breaking other code
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                JavaType.FullyQualified type = classDecl.getType();
                if (type != null) {
                    if (type.getSupertype() != null) {
                        pinned.add(type.getSupertype().getFullyQualifiedName());
                    }
                    for (JavaType.FullyQualified i : type.getInterfaces()) {
                        if (TypeUtils.findDeclaredMethod(i, "setUp", emptyList()).isPresent() ||
                                TypeUtils.findDeclaredMethod(i, "tearDown", emptyList()).isPresent()) {
                            pinned.add(type.getFullyQualifiedName());
                        }
                    }
                }
                return super.visitClassDeclaration(classDecl, ctx);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                JavaType.FullyQualified type = newClass.getClazz() == null ? null : TypeUtils.asFullyQualified(newClass.getClazz().getType());
                if (newClass.getBody() != null && type != null) {
                    pinned.add(type.getFullyQualifiedName());
                }
                return super.visitNewClass(newClass, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                pinCalled(method.getMethodType());
                return super.visitMethodInvocation(method, ctx);
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                pinCalled(memberRef.getMethodType());
                return super.visitMemberReference(memberRef, ctx);
            }

            private void pinCalled(JavaType.@Nullable Method called) {
                if (called != null && called.getParameterTypes().isEmpty() &&
                        ("setUp".equals(called.getName()) || "tearDown".equals(called.getName()))) {
                    pinned.add(called.getDeclaringType().getFullyQualifiedName());
                }
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<String> pinned) {
        return Preconditions.check(new UsesType<>(TEST_CASE, true), new JavaIsoVisitor<ExecutionContext>() {
            // The inserted call and `throws` clause are Java syntax
            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return sourceFile instanceof J.CompilationUnit;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                JavaType.Method methodType = md.getMethodType();
                J.Block body = md.getBody();
                // An empty override is the usual way to switch off an ancestor's setup or cleanup on purpose
                if (methodType == null || body == null || body.getStatements().isEmpty()) {
                    return md;
                }
                boolean setUp = SET_UP.matches(methodType);
                if ((!setUp && !TEAR_DOWN.matches(methodType)) ||
                        callsSuper(body, methodType.getName()) ||
                        (!setUp && !reachableAfter(body))) {
                    return md;
                }
                JavaType.Method inherited = inheritedImplementation(methodType.getDeclaringType(), methodType.getName());
                if (inherited == null) {
                    return md;
                }
                List<JavaType> thrown = inherited.getThrownExceptions();
                for (JavaType t : thrown) {
                    if (TypeUtils.asFullyQualified(t) == null) {
                        return md;
                    }
                }
                List<NameTree> throwz = md.getThrows();
                if (throwz == null ? !thrown.isEmpty() :
                        thrown.stream().anyMatch(t -> throwz.stream().noneMatch(d -> TypeUtils.isAssignableTo(d.getType(), t)))) {
                    JavaType.FullyQualified declaring = requireNonNull(methodType.getDeclaringType());
                    if (pinned.contains(declaring.getFullyQualifiedName()) ||
                            !thrown.stream().allMatch(t -> accessibleFrom(requireNonNull(TypeUtils.asFullyQualified(t)), declaring.getPackageName()))) {
                        return md;
                    }
                    md = declareThrows(md, methodType, thrown);
                }
                md = JavaTemplate.builder("super." + methodType.getName() + "()")
                        .build()
                        .apply(updateCursor(md), setUp ?
                                requireNonNull(md.getBody()).getCoordinates().firstStatement() :
                                requireNonNull(md.getBody()).getCoordinates().lastStatement());
                // The template cannot attribute `super` against a supertype it only knows from the type table
                J.Block withCall = requireNonNull(md.getBody());
                JavaType.Method superMethod = inherited;
                JavaType.Method owner = methodType;
                return md.withBody(withCall.withStatements(setUp ?
                        ListUtils.mapFirst(withCall.getStatements(), s -> attribute(s, superMethod, owner)) :
                        ListUtils.mapLast(withCall.getStatements(), s -> attribute(s, superMethod, owner))));
            }

            // Built by hand, as a template could not attribute exception types declared in the project
            private J.MethodDeclaration declareThrows(J.MethodDeclaration md, JavaType.Method methodType, List<JavaType> exceptions) {
                if (md.getThrows() != null) {
                    for (NameTree replaced : md.getThrows()) {
                        maybeRemoveImport(TypeUtils.asFullyQualified(replaced.getType()));
                    }
                }
                List<JRightPadded<NameTree>> declared = new ArrayList<>(exceptions.size());
                for (JavaType thrown : exceptions) {
                    JavaType.FullyQualified exception = requireNonNull(TypeUtils.asFullyQualified(thrown));
                    String className = exception.getClassName();
                    declared.add(JRightPadded.build(new J.Identifier(randomId(), Space.SINGLE_SPACE, Markers.EMPTY,
                            emptyList(), className.substring(className.lastIndexOf('.') + 1), exception, null)));
                    maybeAddImport(exception);
                }
                return md.getPadding().withThrows(JContainer.build(Space.SINGLE_SPACE, declared, Markers.EMPTY))
                        .withMethodType(methodType.withThrownExceptions(exceptions));
            }
        });
    }

    private static Statement attribute(Statement statement, JavaType.Method superMethod, JavaType.Method owner) {
        J.MethodInvocation call = (J.MethodInvocation) statement;
        return call.withMethodType(superMethod)
                .withName(call.getName().withType(superMethod))
                .withSelect(((J.Identifier) requireNonNull(call.getSelect()))
                        .withType(owner.getDeclaringType().getSupertype()));
    }

    /**
     * The nearest declaration between the type and {@code TestCase} if it has a body, so that overriding it
     * without a {@code super} call skips work. {@code TestCase}'s own are empty.
     */
    private static JavaType.@Nullable Method inheritedImplementation(JavaType.@Nullable FullyQualified type, String name) {
        for (JavaType.FullyQualified s = type == null ? null : type.getSupertype(); s != null && !TypeUtils.isOfClassType(s, TEST_CASE); s = s.getSupertype()) {
            for (JavaType.Method m : s.getMethods()) {
                if (name.equals(m.getName()) && m.getParameterTypes().isEmpty()) {
                    return m.hasFlags(Flag.Abstract) ? null : m;
                }
            }
        }
        return null;
    }

    /** Whether the type and each class enclosing it is public or in the package, so a `throws` clause there can name it. */
    private static boolean accessibleFrom(JavaType.FullyQualified type, String packageName) {
        for (JavaType.FullyQualified t = type; t != null; t = t.getOwningClass()) {
            if (!t.hasFlags(Flag.Public) && !packageName.equals(t.getPackageName())) {
                return false;
            }
        }
        return true;
    }

    /** Whether a call appended to the body is reachable, judged conservatively from its last statement (JLS 14.22). */
    private static boolean reachableAfter(J.Block body) {
        List<Statement> statements = body.getStatements();
        Statement last = statements.get(statements.size() - 1);
        return last instanceof J.MethodInvocation || last instanceof J.Assignment ||
                last instanceof J.AssignmentOperation || last instanceof J.Unary ||
                last instanceof J.VariableDeclarations || last instanceof J.NewClass ||
                last instanceof J.If && ((J.If) last).getElsePart() == null;
    }

    /**
     * Matched by name rather than with a `MethodMatcher`, because the call this recipe inserts has no method type
     * yet, and a typed matcher would miss it and add a second one on the next cycle.
     */
    private static boolean callsSuper(J.Block body, String name) {
        return new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean found) {
                if (name.equals(method.getSimpleName()) && isSuperSelect(method.getSelect())) {
                    found.set(true);
                }
                return super.visitMethodInvocation(method, found);
            }

            // A nested class has its own `super`, so a call there does not stand in for the enclosing method's
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, AtomicBoolean found) {
                return classDecl;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, AtomicBoolean found) {
                return super.visitNewClass(newClass.withBody(null), found);
            }
        }.reduce(body, new AtomicBoolean()).get();
    }

    /**
     * Whether the select is {@code super}, either bare or qualified as in {@code MyTest.super.setUp()}, but not
     * {@code Iface.super}, which calls an interface default method.
     */
    private static boolean isSuperSelect(@Nullable Expression select) {
        if (select instanceof J.FieldAccess && "super".equals(((J.FieldAccess) select).getSimpleName())) {
            JavaType.FullyQualified qualifier = TypeUtils.asFullyQualified(((J.FieldAccess) select).getTarget().getType());
            return qualifier == null || qualifier.getKind() != JavaType.FullyQualified.Kind.Interface;
        }
        return select instanceof J.Identifier && "super".equals(((J.Identifier) select).getSimpleName());
    }
}
