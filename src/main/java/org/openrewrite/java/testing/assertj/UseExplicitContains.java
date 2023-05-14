/*
 * Copyright 2021 the original author or authors.
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
import java.util.function.Supplier;

public class UseExplicitContains extends Recipe {
    @Override
    public String getDisplayName() {
        return "Use explicit contains in Assertj";
    }

    @Override
    public String getDescription() {
        return "Convert AssertJ `assertThat(collection.contains(element)).isTrue()` with assertThat(collection).contains(element) "
                + "and `assertThat(collection.contains(element)).isFalse()` with assertThat(collection).doesNotContain(element).";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.assertj.core.api.Assertions", false);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new UseExplicitContainsVisitor();
    }

    public static class UseExplicitContainsVisitor extends JavaIsoVisitor<ExecutionContext> {
        private Supplier<JavaParser> assertionsParser;
        private Supplier<JavaParser> assertionsParser(ExecutionContext ctx) {
            if(assertionsParser == null) {
                assertionsParser = () -> JavaParser.fromJavaVersion()
                        .classpathFromResources(ctx, "assertj-core-3.24.+")
                        .build();
            }
            return assertionsParser;
        }

        private static final MethodMatcher ASSERT_THAT = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");
        private static final MethodMatcher IS_TRUE = new MethodMatcher("org.assertj.core.api.AbstractBooleanAssert isTrue()");
        private static final MethodMatcher IS_FALSE = new MethodMatcher("org.assertj.core.api.AbstractBooleanAssert isFalse()");
        private static final MethodMatcher CONTAINS = new MethodMatcher("java.util.Collection contains(..)", true);

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

            if (!ASSERT_THAT.matches((J.MethodInvocation)method.getSelect())) {
                return method;
            }

            J.MethodInvocation assertThat = (MethodInvocation) method.getSelect();

            if (!(assertThat.getArguments().get(0) instanceof J.MethodInvocation)) {
                return method;
            }

            J.MethodInvocation contains = (J.MethodInvocation) assertThat.getArguments().get(0);
            if (!CONTAINS.matches(contains)) {
                return method;
            }

            Expression list =  contains.getSelect();
            Expression element = contains.getArguments().get(0);

            String template = isTrue ? "assertThat(#{any()}).contains(#{any()});" :
                "assertThat(#{any()}).doesNotContain(#{any()});";
            JavaTemplate builtTemplate = JavaTemplate.builder(this::getCursor, template)
                    .javaParser(assertionsParser(ctx))
                    .build();
            return method.withTemplate(
                    builtTemplate,
                    method.getCoordinates().replace(),
                    list,
                    element);
        }
    }
}
