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

public class UseExplicitSize extends Recipe {
    @Override
    public String getDisplayName() {
        return "Replace AssertJ `assertTrue` to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Convert assertJ's `assertThat(collection.size()).isEqualTo(Y)` with AssertJ's `assertThat(collection).hasSize()`.";
    }

  @Override
  public Duration getEstimatedEffortPerOccurrence() {
    return Duration.ofMinutes(5);
  }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.assertj.core.api.Assertions");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new UseExplicitSizeVisitor();
    }

    public static class UseExplicitSizeVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final Supplier<JavaParser> ASSERTJ_JAVA_PARSER = () -> JavaParser.fromJavaVersion().classpath("assertj-core").build();

        private static final MethodMatcher ASSERT_THAT = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");
        private static final MethodMatcher IS_EQUAL_TO = new MethodMatcher("org.assertj.core.api.* isEqualTo(..)");
        private static final MethodMatcher SIZE = new MethodMatcher("java.util.Collection size(..)", true);

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation m, ExecutionContext ctx) {
        	J.MethodInvocation method = super.visitMethodInvocation(m, ctx);
        	if (!IS_EQUAL_TO.matches(method)) {
                return method;
            }
            
            if (!ASSERT_THAT.matches((J.MethodInvocation)method.getSelect())) {
                return method;
            }

            J.MethodInvocation assertThat = (MethodInvocation) method.getSelect();

            if (!(assertThat.getArguments().get(0) instanceof J.MethodInvocation)) {
            	return method;
            }
            
            J.MethodInvocation size = (J.MethodInvocation) assertThat.getArguments().get(0);
            
            if (!SIZE.matches(size)) {
            	return method;
            }
            
            Expression list =  size.getSelect();
            Expression expectedSize = method.getArguments().get(0);

            String template = "assertThat(#{any(java.util.List)}).hasSize(#{any()});";
            return method.withTemplate(
            		JavaTemplate.builder(this::getCursor, template)
                            .javaParser(ASSERTJ_JAVA_PARSER)
                            .build(),
                    method.getCoordinates().replace(),
                    list,
                    expectedSize);
        }
    }
}
