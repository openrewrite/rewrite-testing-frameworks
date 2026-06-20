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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypedTree;
import org.openrewrite.marker.Markers;

import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

public class SimplifyAssertJInstanceOfAssertion extends Recipe {

    private static final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");
    private static final MethodMatcher IS_TRUE_MATCHER = new MethodMatcher("org.assertj.core.api.* isTrue()");
    private static final MethodMatcher IS_FALSE_MATCHER = new MethodMatcher("org.assertj.core.api.* isFalse()");

    @Getter
    final String displayName = "Simplify AssertJ assertions on `instanceof` expressions";

    @Getter
    final Set<String> tags = singleton("RSPEC-S5838");

    @Getter
    final String description = "Replace `assertThat(x instanceof Type).isTrue()` with the dedicated " +
            "`assertThat(x).isInstanceOf(Type.class)`, and the negated and `isFalse()` variants with `isNotInstanceOf`, " +
            "so failures describe the actual type rather than just `expected true but was false`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_THAT_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                boolean isTrue = IS_TRUE_MATCHER.matches(mi);
                if (!isTrue && !IS_FALSE_MATCHER.matches(mi) || !ASSERT_THAT_MATCHER.matches(mi.getSelect())) {
                    return mi;
                }

                // Unwrap parentheses and logical negations around the assertThat argument
                Expression argument = ((J.MethodInvocation) mi.getSelect()).getArguments().get(0);
                boolean negated = false;
                while (true) {
                    if (argument instanceof J.Parentheses) {
                        argument = (Expression) ((J.Parentheses<?>) argument).getTree();
                    } else if (argument instanceof J.Unary && ((J.Unary) argument).getOperator() == J.Unary.Type.Not) {
                        negated = !negated;
                        argument = ((J.Unary) argument).getExpression();
                    } else {
                        break;
                    }
                }
                if (!(argument instanceof J.InstanceOf)) {
                    return mi;
                }

                J.InstanceOf instanceOf = (J.InstanceOf) argument;
                // Skip pattern matching `instanceof` (e.g. `x instanceof String s`), as the binding may be used later
                if (instanceOf.getPattern() != null) {
                    return mi;
                }
                // isTrue + instanceof and isFalse + !instanceof both assert the type holds
                String dedicatedAssertion = isTrue != negated ? "isInstanceOf" : "isNotInstanceOf";

                // Preserve the original `assertThat` select (static import or qualified), only swapping its argument
                J.MethodInvocation assertThat = (J.MethodInvocation) mi.getSelect();
                J.MethodInvocation newAssertThat = assertThat.withArguments(
                        singletonList(instanceOf.getExpression().withPrefix(Space.EMPTY)));

                TypedTree clazz = (TypedTree) instanceOf.getClazz();
                TypedTree rawClazz = clazz instanceof J.ParameterizedType ? ((J.ParameterizedType) clazz).getClazz() : clazz;
                return JavaTemplate.builder("#{any()}." + dedicatedAssertion + "(#{any(java.lang.Class)})")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(), newAssertThat, toClassLiteral(rawClazz));
            }

            private Expression toClassLiteral(TypedTree clazz) {
                JavaType clazzType = clazz.getType();
                JavaType.Parameterized classType = new JavaType.Parameterized(null,
                        JavaType.ShallowClass.build("java.lang.Class"),
                        singletonList(clazzType != null ? clazzType : JavaType.Unknown.getInstance()));
                J.Identifier classKeyword = new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY,
                        emptyList(), "class", classType, null);
                Expression target = ((Expression) clazz).withPrefix(Space.EMPTY);
                return new J.FieldAccess(Tree.randomId(), Space.EMPTY, Markers.EMPTY,
                        target, JLeftPadded.build(classKeyword), classType);
            }
        });
    }
}
