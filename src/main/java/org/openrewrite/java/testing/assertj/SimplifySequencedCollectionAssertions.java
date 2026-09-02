/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SimplifySequencedCollectionAssertions extends Recipe {

    // Object-equality assertions come from AbstractAssert, so they do not depend on CharSequence behavior
    private static final Set<String> OBJECT_EQUALITY_ASSERTIONS = new HashSet<>(Arrays.asList("isEqualTo", "isNotEqualTo"));

    private static final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");
    private static final MethodMatcher GET_FIRST_MATCHER = new MethodMatcher("java.util.* getFirst()");
    private static final MethodMatcher GET_LAST_MATCHER = new MethodMatcher("java.util.* getLast()");

    @Getter
    final String displayName = "Simplify AssertJ assertions on SequencedCollection";

    @Getter
    final String description = "Simplify AssertJ assertions on SequencedCollection by using dedicated assertion methods. " +
            "For example, `assertThat(sequencedCollection.getLast())` can be simplified to `assertThat(sequencedCollection).last()`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(GET_FIRST_MATCHER),
                        new UsesMethod<>(GET_LAST_MATCHER)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                        // Check if this is an assertThat call
                        if (!ASSERT_THAT_MATCHER.matches(mi) || mi.getArguments().size() != 1) {
                            return mi;
                        }

                        // Check if the argument is a method invocation
                        Expression arg = mi.getArguments().get(0);
                        if (arg instanceof J.MethodInvocation) {
                            // Check if the method is getFirst() or getLast() on a SequencedCollection
                            if (GET_FIRST_MATCHER.matches(arg)) {
                                if (usesCharSequenceSpecificAssertion((J.MethodInvocation) arg)) {
                                    return mi;
                                }
                                return assertThat(mi, (J.MethodInvocation) arg, "first", ctx);
                            }
                            if (GET_LAST_MATCHER.matches(arg)) {
                                if (usesCharSequenceSpecificAssertion((J.MethodInvocation) arg)) {
                                    return mi;
                                }
                                return assertThat(mi, (J.MethodInvocation) arg, "last", ctx);
                            }
                        }

                        return mi;
                    }

                    private boolean usesCharSequenceSpecificAssertion(J.MethodInvocation elementAccess) {
                        if (!TypeUtils.isAssignableTo("java.lang.CharSequence", elementAccess.getType())) {
                            return false;
                        }
                        J.MethodInvocation selected = getCursor().getValue();
                        Cursor assertionCursor = getCursor().getParentTreeCursor();
                        while (assertionCursor != null && assertionCursor.getValue() instanceof J.MethodInvocation) {
                            J.MethodInvocation assertion = assertionCursor.getValue();
                            if (!(assertion.getSelect() instanceof J.MethodInvocation) ||
                                    !selected.getId().equals(((J.MethodInvocation) assertion.getSelect()).getId())) {
                                break;
                            }
                            if (isCharSequenceSpecificAssertion(assertion)) {
                                return true;
                            }
                            selected = assertion;
                            assertionCursor = assertionCursor.getParentTreeCursor();
                        }
                        return false;
                    }

                    private boolean isCharSequenceSpecificAssertion(J.MethodInvocation assertion) {
                        if (OBJECT_EQUALITY_ASSERTIONS.contains(assertion.getSimpleName())) {
                            return false;
                        }
                        return assertion.getMethodType() != null && TypeUtils.isAssignableTo(
                                "org.assertj.core.api.AbstractCharSequenceAssert", assertion.getMethodType().getDeclaringType());
                    }

                    private J.MethodInvocation assertThat(J.MethodInvocation mi, J.MethodInvocation argMethod, String dedicatedAssertion, ExecutionContext ctx) {
                        return JavaTemplate.builder("assertThat(#{any(java.lang.Iterable)})." + dedicatedAssertion + "()")
                                .staticImports("org.assertj.core.api.Assertions.assertThat")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                                .build()
                                .apply(getCursor(), mi.getCoordinates().replace(), argMethod.getSelect());
                    }
                }
        );
    }
}
