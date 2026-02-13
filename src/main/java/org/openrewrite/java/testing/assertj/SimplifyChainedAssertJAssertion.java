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
package org.openrewrite.java.testing.assertj;

import lombok.AllArgsConstructor;
import lombok.Getter;
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
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static java.util.Collections.singleton;

@AllArgsConstructor
@NoArgsConstructor
public class SimplifyChainedAssertJAssertion extends Recipe {

    @Option(displayName = "AssertJ chained assertion",
            description = "The chained AssertJ assertion to move to dedicated assertion.",
            example = "equals",
            required = false)
    @Nullable
    String chainedAssertion;

    @Option(displayName = "AssertJ replaced assertion",
            description = "The AssertJ assert that should be replaced.",
            example = "isTrue",
            required = false)
    @Nullable
    String assertToReplace;

    @Option(displayName = "AssertJ replacement assertion",
            description = "The AssertJ method to migrate to.",
            example = "isEqualTo",
            required = false)
    @Nullable
    String dedicatedAssertion;

    @Option(displayName = "Required type",
            description = "The type of the actual assertion argument.",
            example = "java.lang.String",
            required = false)
    @Nullable
    String requiredType;

    @Getter
    final String displayName = "Simplify AssertJ chained assertions";

    @Getter
    final Set<String> tags = singleton("RSPEC-S5838");

    @Getter
    final String description = "Many AssertJ chained assertions have dedicated assertions that function the same. " +
            "It is best to use the dedicated assertions.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        MethodMatcher assertThatMatcher = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");
        MethodMatcher chainedAssertMatcher = new MethodMatcher("java..* " + chainedAssertion + "(..)");
        MethodMatcher assertToReplace = new MethodMatcher("org.assertj.core.api.* " + this.assertToReplace + "(..)");

        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInvocation, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(methodInvocation, ctx);

                // assert has correct assertion
                if (!assertToReplace.matches(mi) || mi.getArguments().size() != 1 || !(mi.getSelect() instanceof J.MethodInvocation)) {
                    return mi;
                }

                // assertThat has method call
                J.MethodInvocation assertThat = (J.MethodInvocation) mi.getSelect();
                if (!assertThatMatcher.matches(assertThat) || !(assertThat.getArguments().get(0) instanceof J.MethodInvocation)) {
                    return mi;
                }

                J.MethodInvocation assertThatArg = (J.MethodInvocation) assertThat.getArguments().get(0);
                if (!chainedAssertMatcher.matches(assertThatArg)) {
                    return mi;
                }

                // Extract the actual argument for the new assertThat call
                Expression actual = assertThatArg.getSelect() != null ? assertThatArg.getSelect() : assertThatArg;
                if (!TypeUtils.isAssignableTo(requiredType, actual.getType())) {
                    return mi;
                }

                // Skip transformation when actual type has wildcard type parameters and the
                // dedicated assertion takes arguments (e.g. containsEntry on Map<?, ?>, contains on Optional<?>).
                // When both arguments are empty the dedicated assertion has no parameters, so wildcards are not an issue.
                boolean assertThatArgumentIsEmpty = assertThatArg.getArguments().get(0) instanceof J.Empty;
                boolean methodToReplaceArgumentIsEmpty = mi.getArguments().get(0) instanceof J.Empty;
                if (!(assertThatArgumentIsEmpty && methodToReplaceArgumentIsEmpty)) {
                    JavaType.Parameterized parameterized = TypeUtils.asParameterized(actual.getType());
                    if (parameterized != null) {
                        for (JavaType typeParam : parameterized.getTypeParameters()) {
                            if (typeParam instanceof JavaType.GenericTypeVariable &&
                                "?".equals(((JavaType.GenericTypeVariable) typeParam).getName())) {
                                return mi;
                            }
                        }
                    }
                }

                // Skip transformation when the assertion argument type is wider than the chained method's
                // return type (e.g. isEqualTo(1L) with size() returning int -> hasSize(1L) won't compile)
                if (!methodToReplaceArgumentIsEmpty) {
                    Expression replaceArg = mi.getArguments().get(0);
                    JavaType.Primitive argPrimitive = TypeUtils.asPrimitive(replaceArg.getType());
                    JavaType chainedReturnType = assertThatArg.getType();
                    JavaType.Primitive chainedPrimitive = TypeUtils.asPrimitive(chainedReturnType);
                    if (argPrimitive == JavaType.Primitive.Long && chainedPrimitive == JavaType.Primitive.Int) {
                        return mi;
                    }
                }

                List<Expression> arguments = new ArrayList<>();
                arguments.add(actual);

                String template = getStringTemplateAndAppendArguments(assertThatArg, mi, arguments);
                return JavaTemplate.builder(String.format(template, dedicatedAssertion))
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5", "assertj-core-3"))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(), arguments.toArray());
            }

            private String getStringTemplateAndAppendArguments(J.MethodInvocation assertThatArg, J.MethodInvocation methodToReplace, List<Expression> arguments) {
                Expression assertThatArgument = assertThatArg.getArguments().get(0);
                Expression methodToReplaceArgument = methodToReplace.getArguments().get(0);
                boolean assertThatArgumentIsEmpty = assertThatArgument instanceof J.Empty;
                boolean methodToReplaceArgumentIsEmpty = methodToReplaceArgument instanceof J.Empty;

                // If both arguments are empty, then the select is already added to the arguments list, and we use a minimal template
                if (assertThatArgumentIsEmpty && methodToReplaceArgumentIsEmpty) {
                    return "assertThat(#{any()}).%s()";
                }

                // If both arguments are not empty, then we add both to the arguments to the arguments list, and return a template with two arguments
                if (!assertThatArgumentIsEmpty && !methodToReplaceArgumentIsEmpty) {
                    // This should only happen for map assertions using a key and value
                    arguments.add(assertThatArgument);
                    arguments.add(methodToReplaceArgument);
                    return "assertThat(#{any()}).%s(#{any()}, #{any()})";
                }

                // If either argument is empty, we choose which one to add to the arguments list, and optionally extract the select
                arguments.add(extractEitherArgument(assertThatArgumentIsEmpty, assertThatArgument, methodToReplaceArgument));

                // Special case for Path.of() assertions
                if ("java.nio.file.Path".equals(requiredType) && dedicatedAssertion.contains("Raw") &&
                    TypeUtils.isAssignableTo("java.lang.String", assertThatArgument.getType())) {
                    maybeAddImport("java.nio.file.Path");
                    return "assertThat(#{any()}).%s(Path.of(#{any()}))";
                }

                return "assertThat(#{any()}).%s(#{any()})";
            }

            private Expression extractEitherArgument(boolean assertThatArgumentIsEmpty, Expression assertThatArgument, Expression methodToReplaceArgument) {
                if (assertThatArgumentIsEmpty) {
                    return methodToReplaceArgument;
                }
                // Only on the assertThat argument do we possibly replace the argument with the select; such as list.size() -> list
                if (chainedAssertMatcher.matches(assertThatArgument)) {
                    return Objects.requireNonNull(((J.MethodInvocation) assertThatArgument).getSelect());
                }
                return assertThatArgument;
            }
        };
    }
}
