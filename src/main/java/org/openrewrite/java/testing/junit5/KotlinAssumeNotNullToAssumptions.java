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
package org.openrewrite.java.testing.junit5;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.kotlin.KotlinTemplate;
import org.openrewrite.kotlin.KotlinVisitor;
import org.openrewrite.kotlin.tree.K;

import java.util.ArrayList;
import java.util.List;

public class KotlinAssumeNotNullToAssumptions extends Recipe {
    @Getter
    final String displayName = "Migrate Kotlin `assumeNotNull` to Jupiter assumptions";

    @Getter
    final String description = "Replace JUnit 4 null assumptions with equivalent Kotlin Jupiter assumptions.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        if (!KotlinJUnit5Support.available()) {
            return TreeVisitor.noop();
        }
        return KotlinVisitors.create();
    }

    private static final class KotlinVisitors {
        private static TreeVisitor<?, ExecutionContext> create() {
            return new Visitor();
        }

        private static final class Visitor extends KotlinVisitor<ExecutionContext> {
            final MethodMatcher assumeNotNull = new MethodMatcher("org.junit.Assume assumeNotNull(..)");

            @Override
            public J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (!assumeNotNull.matches(m)) {
                    return m;
                }
                List<Expression> arguments = new ArrayList<>();
                List<String> substitutions = new ArrayList<>();
                for (Expression argument : m.getArguments()) {
                    if (argument instanceof J.Empty) {
                        continue;
                    }
                    boolean spread = argument instanceof K.SpreadArgument;
                    arguments.add(spread ? ((K.SpreadArgument) argument).getExpression() : argument);
                    substitutions.add(spread ? "*#{any()}" : "#{any()}");
                }
                if (arguments.isEmpty()) {
                    maybeRemoveImport("org.junit.Assume.assumeNotNull");
                    return null;
                }
                String template;
                if (arguments.size() == 1 && m.getArguments().get(0) instanceof K.SpreadArgument) {
                    template = "#{any()}.forEach { assumeFalse(it == null) }";
                } else if (arguments.size() == 1) {
                    template = "assumeFalse(#{any()} == null)";
                } else {
                    template = "arrayOf(" + String.join(", ", substitutions) + ").forEach { assumeFalse(it == null) }";
                }
                maybeRemoveImport("org.junit.Assume.assumeNotNull");
                maybeAddImport("org.junit.jupiter.api.Assumptions", "assumeFalse", false);
                J.MethodInvocation replacement = KotlinTemplate.builder(template)
                        .parser(KotlinParser.builder().classpathFromResources(ctx, "junit-jupiter-api-5"))
                        .imports("org.junit.jupiter.api.Assumptions.assumeFalse").build()
                        .apply(updateCursor(m), m.getCoordinates().replace(), arguments.toArray());
                return replacement.withPrefix(m.getPrefix());
            }
        }
    }
}
