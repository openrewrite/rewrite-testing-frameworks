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
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
public class SimplifyChainedAssertJAssertion extends Recipe {
    @Option(displayName = "AssertJ Assertion",
            description = "The chained AssertJ assertion to move to dedicated assertion.",
            example = "equals",
            required = false)
    @Nullable
    String chainedAssertion;

    @Option(displayName = "AssertJ Assertion",
            description = "The AssertJ assert that should be replaced.",
            example = "isTrue",
            required = false)
    @Nullable
    String assertToReplace;

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
        return "Simplify AssertJ chained assertions";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-5838");
    }

    @Override
    public String getDescription() {
        return "Many AssertJ chained assertions have dedicated assertions that function the same. " +
               "It is best to use the dedicated assertions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new SimplifyChainedAssertJAssertionsVisitor();
    }

    private class SimplifyChainedAssertJAssertionsVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");
        private final MethodMatcher CHAINED_ASSERT_MATCHER = new MethodMatcher("java..* " + chainedAssertion + "(..)");
        private final MethodMatcher ASSERT_TO_REPLACE = new MethodMatcher("org.assertj.core.api.* " + assertToReplace + "(..)");

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInvocation, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(methodInvocation, ctx);

            // assert has correct assertion
            if (!ASSERT_TO_REPLACE.matches(mi)) {
                return mi;
            }

            // assertThat has method call
            J.MethodInvocation assertThat = (J.MethodInvocation) mi.getSelect();
            if (!ASSERT_THAT_MATCHER.matches(assertThat) || !(assertThat.getArguments().get(0) instanceof J.MethodInvocation)) {
                return mi;
            }

            J.MethodInvocation assertThatArg = (J.MethodInvocation) assertThat.getArguments().get(0);
            if (!CHAINED_ASSERT_MATCHER.matches(assertThatArg)) {
                return mi;
            }

            if ("size".equals(chainedAssertion) && "isEqualTo".equals(assertToReplace)) {
                if (hasZeroArgument(mi)) {
                    dedicatedAssertion = "isEmpty";
                }
            }

            // method call has select
            Expression select = assertThatArg.getSelect() != null ? assertThatArg.getSelect() : assertThatArg;
            if (!TypeUtils.isAssignableTo(requiredType, select.getType())) {
                return mi;
            }

            List<Expression> arguments = new ArrayList<>(Collections.singletonList(select));
            String template = getTemplate(arguments, assertThatArg, mi);
            String formattedTemplate = String.format(template, dedicatedAssertion);
            return JavaTemplate.builder(formattedTemplate)
                    .contextSensitive()
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5.9", "assertj-core-3.24"))
                    .build()
                    .apply(getCursor(), mi.getCoordinates().replace(), arguments.toArray());
        }
    }

    private String getTemplate(List<Expression> arguments, J.MethodInvocation assertThatArg, J.MethodInvocation methodToReplace) {
        if (hasZeroArgument(methodToReplace)) {
            return "assertThat(#{any()}).%s()";
        }

        if (!(assertThatArg.getArguments().get(0) instanceof J.Empty)
            && !(methodToReplace.getArguments().get(0) instanceof J.Empty)) {
            // Note: this should be the only case when more than one argument needs to be handled. When the assertions involve the map functions
            arguments.add(assertThatArg.getArguments().get(0));
            arguments.add(methodToReplace.getArguments().get(0));
            return "assertThat(#{any()}).%s(#{any()}, #{any()})";
        }

        if (!(assertThatArg.getArguments().get(0) instanceof J.Empty)
            || !(methodToReplace.getArguments().get(0) instanceof J.Empty)) {
            Expression argumentToAdd = assertThatArg.getArguments().get(0) instanceof J.Empty ? methodToReplace.getArguments().get(0) : assertThatArg.getArguments().get(0);
            argumentToAdd = argumentToAdd instanceof J.MethodInvocation ? ((J.MethodInvocation) argumentToAdd).getSelect() : argumentToAdd;
            arguments.add(argumentToAdd);

            if (requiredType.equals("java.nio.file.Path") && dedicatedAssertion.contains("Raw")
                && TypeUtils.isAssignableTo("java.lang.String", assertThatArg.getArguments().get(0).getType())) {
                return "assertThat(#{any()}).%s(Path.of(#{any()}))";
            }
            return "assertThat(#{any()}).%s(#{any()})";
        }

        return "assertThat(#{any()}).%s()";
    }

    private boolean hasZeroArgument(J.MethodInvocation method) {
        List<Expression> arguments = method.getArguments();
        if (arguments.size() == 1 && arguments.get(0) instanceof J.Literal) {
            J.Literal literalArg = (J.Literal) arguments.get(0);
            return literalArg.getValue() != null && literalArg.getValue().equals(0);
        }
        return false;
    }
}
