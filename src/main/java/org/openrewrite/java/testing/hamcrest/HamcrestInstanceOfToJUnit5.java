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
package org.openrewrite.java.testing.hamcrest;

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

import java.util.ArrayList;
import java.util.List;

public class HamcrestInstanceOfToJUnit5 extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate from Hamcrest `instanceOf` matcher to JUnit 5";
    }

    @Override
    public String getDescription() {
        return "Migrate from Hamcrest `instanceOf` and `isA` matcher to JUnit5 `assertInstanceOf` assertion.";
    }

    private static final MethodMatcher INSTANCE_OF_MATCHER = new MethodMatcher("org.hamcrest.Matchers instanceOf(..)");
    private static final MethodMatcher IS_A_MATCHER = new MethodMatcher("org.hamcrest.Matchers isA(..)");
    private static final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(.., org.hamcrest.Matcher)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> preconditions = Preconditions.and(
                new UsesMethod<>(ASSERT_THAT_MATCHER),
                Preconditions.or(
                        new UsesMethod<>(INSTANCE_OF_MATCHER),
                        new UsesMethod<>(IS_A_MATCHER)));
        return Preconditions.check(preconditions, new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
                if (ASSERT_THAT_MATCHER.matches(mi)) {
                    Expression reason;
                    Expression examinedObject;
                    Expression hamcrestMatcher;

                    if (mi.getArguments().size() == 2) {
                        reason = null;
                        examinedObject = mi.getArguments().get(0);
                        hamcrestMatcher = mi.getArguments().get(1);
                    } else if (mi.getArguments().size() == 3) {
                        reason = mi.getArguments().get(0);
                        examinedObject = mi.getArguments().get(1);
                        hamcrestMatcher = mi.getArguments().get(2);
                    } else {
                        return mi;
                    }

                    J.MethodInvocation matcherInvocation = (J.MethodInvocation) hamcrestMatcher;
                    while ("not".equals(matcherInvocation.getSimpleName())) {
                        maybeRemoveImport("org.hamcrest.Matchers.not");
                        maybeRemoveImport("org.hamcrest.CoreMatchers.not");
                        matcherInvocation = (J.MethodInvocation) new RemoveNotMatcherVisitor().visit(matcherInvocation, ctx);
                    }

                    if (INSTANCE_OF_MATCHER.matches(matcherInvocation) || IS_A_MATCHER.matches(matcherInvocation)) {
                        boolean logicalContext = RemoveNotMatcherVisitor.getLogicalContext(matcherInvocation, ctx);

                        String templateString = (logicalContext ?
                                "assertInstanceOf(#{any(java.lang.Class)}, #{any(java.lang.Object)}" :
                                "assertFalse(#{any(java.lang.Class)}.isAssignableFrom(#{any(java.lang.Object)}.getClass())") +
                                (reason == null ? ")" : ", #{any(java.lang.String)})");

                        JavaTemplate template = JavaTemplate.builder(templateString)
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5"))
                                .staticImports("org.junit.jupiter.api.Assertions." + (logicalContext ? "assertInstanceOf" : "assertFalse"))
                                .build();

                        maybeRemoveImport("org.hamcrest.MatcherAssert.assertThat");
                        maybeRemoveImport("org.hamcrest.Matchers.instanceOf");
                        maybeRemoveImport("org.hamcrest.CoreMatchers.instanceOf");
                        maybeRemoveImport("org.hamcrest.Matchers.isA");
                        maybeRemoveImport("org.hamcrest.CoreMatchers.isA");
                        maybeAddImport("org.junit.jupiter.api.Assertions", logicalContext ? "assertInstanceOf" : "assertFalse");

                        List<Expression> arguments = new ArrayList<>();
                        arguments.add(matcherInvocation.getArguments().get(0));
                        arguments.add(examinedObject);
                        if (reason != null) {
                            arguments.add(reason);
                        }

                        return template.apply(getCursor(), mi.getCoordinates().replace(), arguments.toArray());
                    }
                }
                return super.visitMethodInvocation(mi, ctx);
            }
        });
    }
}
