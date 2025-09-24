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
package org.openrewrite.java.testing.truth;

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

import java.util.List;

public class TruthAssertWithMessageToAssertJ extends Recipe {

    private static final MethodMatcher ASSERT_WITH_MESSAGE = new MethodMatcher("com.google.common.truth.Truth assertWithMessage(..)");
    private static final MethodMatcher ASSERT_THAT = new MethodMatcher("com.google.common.truth.StandardSubjectBuilder that(..)");

    @Override
    public String getDisplayName() {
        return "Convert Truth `assertWithMessage` to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Converts Google Truth's `assertWithMessage().that()` pattern to AssertJ's `assertThat().as()` pattern.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_WITH_MESSAGE), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                // Look for the pattern: assertWithMessage(...).that(...)
                if (mi.getSelect() instanceof J.MethodInvocation) {
                    J.MethodInvocation select = (J.MethodInvocation) mi.getSelect();
                    if (ASSERT_WITH_MESSAGE.matches(select) && ASSERT_THAT.matches(mi)) {
                        // We have the pattern assertWithMessage(message).that(actual)
                        List<Expression> messageArgs = select.getArguments();
                        List<Expression> actualArgs = mi.getArguments();

                        if (!actualArgs.isEmpty()) {
                            Expression actual = actualArgs.get(0);

                            maybeAddImport("org.assertj.core.api.Assertions", "assertThat", false);
                            maybeRemoveImport("com.google.common.truth.Truth");

                            if (messageArgs.size() == 1) {
                                // Simple message
                                Expression message = messageArgs.get(0);
                                return JavaTemplate.builder("assertThat(#{any()}).as(#{any()})")
                                        .staticImports("org.assertj.core.api.Assertions.assertThat")
                                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                                        .build()
                                        .apply(getCursor(), mi.getCoordinates().replace(), actual, message);
                            }
                            if (messageArgs.size() > 1) {
                                // Formatted message - needs to be combined
                                Object[] formatArgs = new Object[messageArgs.size() + 1];
                                formatArgs[0] = actual;
                                formatArgs[1] = messageArgs.get(0);
                                for (int i = 1; i < messageArgs.size(); i++) {
                                    formatArgs[i + 1] = messageArgs.get(i);
                                }
                                String template = "assertThat(#{any()}).as(String.format(#{any()}";
                                for (int i = 1; i < messageArgs.size(); i++) {
                                    template += ", #{any()}";
                                }
                                template += "))";

                                return JavaTemplate.builder(template)
                                        .staticImports("org.assertj.core.api.Assertions.assertThat")
                                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                                        .build()
                                        .apply(getCursor(), mi.getCoordinates().replace(), formatArgs);
                            }
                        }
                    }
                }

                return mi;
            }
        });
    }
}
