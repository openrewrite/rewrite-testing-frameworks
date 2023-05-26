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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.MethodInvocation;

import java.time.Duration;

public class UseExplicitIsEmpty extends Recipe {
    @Override
    public String getDisplayName() {
        return "Use AssertJ `isEmpty()` on collections";
    }

    @Override
    public String getDescription() {
        return "Convert AssertJ `assertThat(collection.isEmpty()).isTrue()` to `assertThat(collection).isEmpty()` "
               + "and `assertThat(collection.isEmpty()).isFalse()` to `assertThat(collection).isNotEmpty()`.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.assertj.core.api.Assertions", false), new UseExplicitContainsIsEmpty());
    }

    public static class UseExplicitContainsIsEmpty extends JavaIsoVisitor<ExecutionContext> {
        private JavaParser.Builder<?, ?> assertionsParser;

        private JavaParser.Builder<?, ?> assertionsParser(ExecutionContext ctx) {
            if (assertionsParser == null) {
                assertionsParser = JavaParser.fromJavaVersion()
                        .classpathFromResources(ctx, "assertj-core-3.24");
            }
            return assertionsParser;
        }

        private static final MethodMatcher ASSERT_THAT = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");
        private static final MethodMatcher IS_TRUE = new MethodMatcher("org.assertj.core.api.AbstractBooleanAssert isTrue()");
        private static final MethodMatcher IS_FALSE = new MethodMatcher("org.assertj.core.api.AbstractBooleanAssert isFalse()");
        private static final MethodMatcher IS_EMPTY = new MethodMatcher("java.util.Collection isEmpty()");

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation m, ExecutionContext ctx) {
            J.MethodInvocation method = super.visitMethodInvocation(m, ctx);
            boolean isTrue = IS_TRUE.matches(method);
            if (!isTrue && !IS_FALSE.matches(method)) {
                return method;
            }

            if (!(method.getSelect() instanceof J.MethodInvocation)) {
                return method;
            }

            if (!ASSERT_THAT.matches((J.MethodInvocation) method.getSelect())) {
                return method;
            }

            J.MethodInvocation assertThat = (MethodInvocation) method.getSelect();

            if (!(assertThat.getArguments().get(0) instanceof J.MethodInvocation)) {
                return method;
            }

            J.MethodInvocation isEmpty = (J.MethodInvocation) assertThat.getArguments().get(0);
            if (!IS_EMPTY.matches(isEmpty)) {
                return method;
            }

            Expression collection = isEmpty.getSelect();

            String template = isTrue ? "assertThat(#{any()}).isEmpty();" :
                    "assertThat(#{any()}).isNotEmpty();";
            JavaTemplate builtTemplate = JavaTemplate.builder(template)
                    .context(this::getCursor)
                    .javaParser(assertionsParser(ctx))
                    .build();
            return method.withTemplate(
                    builtTemplate,
                    getCursor(),
                    method.getCoordinates().replace(),
                    collection);
        }
    }
}
