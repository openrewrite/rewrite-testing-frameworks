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
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
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
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Objects.requireNonNull;
import static org.openrewrite.Tree.randomId;

public class AddMissingSuperCallInTestCase extends Recipe {

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
            "`TestCase`'s own implementations are empty. An override declaring no `throws` gains the clause the " +
            "inherited method declares, which an override is always allowed to repeat. One whose `throws` is " +
            "narrower than that is left alone, since widening it could break a subclass that narrows it again " +
            "and calls `super`.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S2188");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(TEST_CASE, true), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                JavaType.Method methodType = md.getMethodType();
                J.Block body = md.getBody();
                if (methodType == null || body == null) {
                    return md;
                }
                boolean setUp = SET_UP.matches(methodType);
                if (!setUp && !TEAR_DOWN.matches(methodType) ||
                        callsSuper(body, methodType.getName()) ||
                        !inheritsAnImplementation(methodType.getDeclaringType(), methodType.getName())) {
                    return md;
                }
                JavaType.Method inherited = TypeUtils.findOverriddenMethod(methodType).orElse(null);
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
                if (throwz == null) {
                    md = declareThrows(md, methodType, thrown);
                } else if (thrown.stream().anyMatch(t -> throwz.stream().noneMatch(d -> TypeUtils.isAssignableTo(d.getType(), t)))) {
                    // An override may only declare what it overrides does, so a narrower clause cannot take the call
                    return md;
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
                if (exceptions.isEmpty()) {
                    return md;
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
     * Whether an ancestor between the type and {@code TestCase} declares the method with a body, so that
     * overriding it without a {@code super} call skips work. {@code TestCase}'s own are empty.
     */
    private static boolean inheritsAnImplementation(JavaType.@Nullable FullyQualified type, String name) {
        if (type == null || !TypeUtils.isAssignableTo(TEST_CASE, type)) {
            return false;
        }
        for (JavaType.FullyQualified s = type.getSupertype(); s != null && !TypeUtils.isOfClassType(s, TEST_CASE); s = s.getSupertype()) {
            for (JavaType.Method m : s.getMethods()) {
                if (name.equals(m.getName()) && m.getParameterTypes().isEmpty()) {
                    return !m.hasFlags(org.openrewrite.java.tree.Flag.Abstract);
                }
            }
        }
        return false;
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

    /** Whether the select is {@code super}, either bare or qualified as in {@code MyTest.super.setUp()}. */
    private static boolean isSuperSelect(@Nullable Expression select) {
        return select instanceof J.Identifier && "super".equals(((J.Identifier) select).getSimpleName()) ||
                select instanceof J.FieldAccess && "super".equals(((J.FieldAccess) select).getSimpleName());
    }
}
