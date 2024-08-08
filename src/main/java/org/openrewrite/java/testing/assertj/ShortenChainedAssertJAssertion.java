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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.java.tree.J.Literal;
import org.openrewrite.java.tree.J.MethodInvocation;
import org.openrewrite.java.tree.JavaType;

@AllArgsConstructor
@NoArgsConstructor
public class ShortenChainedAssertJAssertion extends Recipe {

    @Option(displayName = "AssertJ Assertion",
            description = "The AssertJ assert that should be replaced.",
            example = "isTrue",
            required = false)
    @Nullable
    String assertToReplace;
    
    @Option(displayName = "AssertJ Assertion",
            description = "The chained AssertJ assertion to move to dedicated assertion.",
            example = "equals",
            required = false)
    @Nullable
    String assertArgument;


    @Option(displayName = "AssertJ Assertion",
            description = "The AssertJ method to migrate to.",
            example = "isEqualTo",
            required = false)
    @Nullable
    String dedicatedAssertion;

    @Option(displayName = "Required Type",
            description = "Specifies the type the recipe should run on.",
            example = "java.lang.String",
            required = false)
    @Nullable
    String requiredType;


    @Override
    public String getDisplayName() {
        return "Convert `assertThat(Object).isEqualTo(null)` to `isNull()`";
    }

    @Override
    public String getDescription() {
        return "Adopt idiomatic AssertJ assertion for null check.";
    }
    
    private static final MethodMatcher IS_EQUAL_TO = new MethodMatcher("org.assertj.core.api.Assert isEqualTo(..)", true);


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

            // assert has correct assertion
            if (!ASSERT_TO_REPLACE.matches(mi)) {
                return mi;
            }
            
            if(!(mi.getArguments().get(0) instanceof J.Literal)) {
                return mi;
            }
            
            Literal literal = (J.Literal)mi.getArguments().get(0);
            if(!literal.getValueSource().equals(String.valueOf(assertArgument))) {
                return mi;
            }

            // assertThat has method call
            J.MethodInvocation assertThat = (J.MethodInvocation) mi.getSelect();
            if (!ASSERT_THAT_MATCHER.matches(assertThat)) {
                return mi;
            }
            
            JavaType assertThatArgType = assertThat.getArguments().get(0).getType();
            if (!TypeUtils.isAssignableTo(requiredType, assertThatArgType)) {
                return mi;
            }

            String template = mi.getSelect() + "." + dedicatedAssertion + "()";
            return applyTemplate(template, mi, ctx);
        }

        private J.MethodInvocation applyTemplate(String formattedTemplate,   J.MethodInvocation mi, ExecutionContext ctx) {
            return JavaTemplate.builder(formattedTemplate)
                    .contextSensitive()
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5.9", "assertj-core-3.24"))
                    .build()
                    .apply(getCursor(), mi.getCoordinates().replace() );
        }
    }
 
}
