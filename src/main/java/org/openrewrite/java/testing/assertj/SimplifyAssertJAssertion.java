/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.assertj;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

@AllArgsConstructor
@NoArgsConstructor
public class SimplifyAssertJAssertion extends Recipe {

    @Option(displayName = "AssertJ assertion",
            description = "The assertion method that should be replaced.",
            example = "hasSize",
            required = false)
    @Nullable
    String assertToReplace;

    @Option(displayName = "Assertion argument literal",
            description = "The literal argument passed into the assertion to replace; use \"null\" for `null`.",
            example = "0")
    String literalArgument;

    @Option(displayName = "Dedicated assertion",
            description = "The zero argument assertion to adopt instead.",
            example = "isEmpty")
    String dedicatedAssertion;

    @Option(displayName = "Required type",
            description = "The type of the actual assertion argument.",
            example = "java.lang.String")
    String requiredType;

    @Override
    public String getDisplayName() {
        return "Simplify AssertJ assertions with literal arguments";
    }

    @Override
    public String getDescription() {
        return "Simplify AssertJ assertions by replacing them with more expressiove dedicated assertions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new ShorthenChainedAssertJAssertionsVisitor();
    }

    private class ShorthenChainedAssertJAssertionsVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");
        private final MethodMatcher ASSERT_TO_REPLACE = new MethodMatcher("org.assertj.core.api.* " + assertToReplace + "(..)");

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInvocation, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(methodInvocation, ctx);

            // Match the end of the chain first, then the select to avoid matching the wrong method chain
            if (!ASSERT_TO_REPLACE.matches(mi) || !ASSERT_THAT_MATCHER.matches(mi.getSelect())) {
                return mi;
            }

            // Compare argument with passed in literal
            if (!(mi.getArguments().get(0) instanceof J.Literal) ||
                !literalArgument.equals(((J.Literal) mi.getArguments().get(0)).getValueSource())) { // Implies "null" is `null`
                return mi;
            }

            // Check argument type of assertThat
            if (!TypeUtils.isAssignableTo(requiredType, ((J.MethodInvocation) mi.getSelect()).getArguments().get(0).getType())) {
                return mi;
            }

            // Assume zero argument replacement method
            return JavaTemplate.builder("#{any()}." + dedicatedAssertion + "()")
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3.24"))
                    .build()
                    .apply(getCursor(), mi.getCoordinates().replace(), mi.getSelect());
        }
    }
}
