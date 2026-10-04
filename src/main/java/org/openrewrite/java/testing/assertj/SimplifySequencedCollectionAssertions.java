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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SimplifySequencedCollectionAssertions extends Recipe {

    // `first()` and `last()` return an `ObjectAssert`, so only assertions inherited from these types remain available
    private static final Set<String> OBJECT_ASSERT_TYPES = new HashSet<>(Arrays.asList(
            "org.assertj.core.api.ObjectAssert",
            "org.assertj.core.api.AbstractObjectAssert",
            "org.assertj.core.api.AbstractAssert",
            "org.assertj.core.api.Assert",
            "org.assertj.core.api.Descriptable",
            "org.assertj.core.api.ExtensionPoints"));

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

                        if (!ASSERT_THAT_MATCHER.matches(mi) || mi.getArguments().size() != 1) {
                            return mi;
                        }

                        Expression arg = mi.getArguments().get(0);
                        if (!(arg instanceof J.MethodInvocation)) {
                            return mi;
                        }
                        String dedicatedAssertion = GET_FIRST_MATCHER.matches(arg) ? "first" :
                                GET_LAST_MATCHER.matches(arg) ? "last" : null;
                        if (dedicatedAssertion == null || !chainedAssertionsAvailableOnObjectAssert()) {
                            return mi;
                        }
                        return assertThat(mi, (J.MethodInvocation) arg, dedicatedAssertion, ctx);
                    }

                    private boolean chainedAssertionsAvailableOnObjectAssert() {
                        J.MethodInvocation selected = getCursor().getValue();
                        for (Cursor parent = getCursor().getParentTreeCursor();
                             parent.getValue() instanceof J.MethodInvocation;
                             parent = parent.getParentTreeCursor()) {
                            J.MethodInvocation chained = parent.getValue();
                            if (chained.getSelect() != selected) {
                                break;
                            }
                            if (!isAvailableOnObjectAssert(chained)) {
                                return false;
                            }
                            selected = chained;
                        }
                        return true;
                    }

                    private boolean isAvailableOnObjectAssert(J.MethodInvocation assertion) {
                        JavaType.Method methodType = assertion.getMethodType();
                        if (methodType == null) {
                            return false;
                        }
                        JavaType.FullyQualified declaringType = methodType.getDeclaringType();
                        if (OBJECT_ASSERT_TYPES.contains(declaringType.getFullyQualifiedName())) {
                            return true;
                        }
                        List<Expression> arguments = ListUtils.filter(assertion.getArguments(), arg -> !(arg instanceof J.Empty));
                        for (JavaType.FullyQualified type = declaringType.getSupertype(); type != null; type = type.getSupertype()) {
                            if (OBJECT_ASSERT_TYPES.contains(type.getFullyQualifiedName()) &&
                                    type.getMethods().stream().anyMatch(inherited ->
                                            inherited.getName().equals(methodType.getName()) && acceptsArguments(inherited, arguments))) {
                                return true;
                            }
                        }
                        return false;
                    }

                    private boolean acceptsArguments(JavaType.Method method, List<Expression> arguments) {
                        List<JavaType> parameterTypes = method.getParameterTypes();
                        if (parameterTypes.size() != arguments.size()) {
                            return false;
                        }
                        for (int i = 0; i < parameterTypes.size(); i++) {
                            JavaType parameterType = parameterTypes.get(i);
                            if (parameterType instanceof JavaType.GenericTypeVariable) {
                                continue;
                            }
                            JavaType.FullyQualified erasedParameterType = TypeUtils.asFullyQualified(parameterType);
                            if (erasedParameterType == null ||
                                    !TypeUtils.isAssignableTo(erasedParameterType.getFullyQualifiedName(), arguments.get(i).getType())) {
                                return false;
                            }
                        }
                        return true;
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
