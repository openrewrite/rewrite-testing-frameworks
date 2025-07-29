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
package org.openrewrite.java.testing.mockito;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

public class RemoveTimesZeroAndOne extends Recipe {
    @Override
    public String getDisplayName() {
        return "Remove `Mockito.times(0)` and `Mockito.times(1)`";
    }

    @Override
    public String getDescription() {
        return "Remove `Mockito.times(0)` and `Mockito.times(1)` from `Mockito.verify()` calls.";
    }

    private static final MethodMatcher verifyMatcher = new MethodMatcher("org.mockito.Mockito verify(..)", false);
    private static final MethodMatcher timesMatcher = new MethodMatcher("org.mockito.Mockito times(int)", false);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new UsesMethod<>(verifyMatcher),
                        new UsesMethod<>(timesMatcher)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (timesMatcher.matches(mi) && J.Literal.isLiteralValue(mi.getArguments().get(0), 0)) {
                            maybeAddImport("org.mockito.Mockito", "never");
                            maybeRemoveImport("org.mockito.Mockito.times");
                            return JavaTemplate.builder("never()")
                                    .staticImports("org.mockito.Mockito.never")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core"))
                                    .build()
                                    .apply(getCursor(), mi.getCoordinates().replace());
                        }
                        if (verifyMatcher.matches(mi) && mi.getArguments().size() == 2) {
                            J.MethodInvocation times = (J.MethodInvocation) mi.getArguments().get(1);
                            if (timesMatcher.matches(times) && J.Literal.isLiteralValue(times.getArguments().get(0), 1)) {
                                maybeRemoveImport("org.mockito.Mockito.times");
                                JavaType.Method methodType = mi.getMethodType()
                                        .withParameterNames(mi.getMethodType().getParameterNames().subList(0, 1))
                                        .withParameterTypes(mi.getMethodType().getParameterTypes().subList(0, 1));
                                return mi
                                        .withArguments(mi.getArguments().subList(0, 1))
                                        .withMethodType(methodType)
                                        .withName(mi.getName().withType(methodType));
                            }
                        }
                        return mi;
                    }
                }
        );
    }
}
