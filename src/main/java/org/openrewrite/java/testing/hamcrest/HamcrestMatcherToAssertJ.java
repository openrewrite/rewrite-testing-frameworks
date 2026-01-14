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

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;

import static java.util.stream.Collectors.joining;

@NoArgsConstructor
@AllArgsConstructor
public class HamcrestMatcherToAssertJ extends Recipe {

    @Option(displayName = "Hamcrest matcher",
            description = "The Hamcrest `Matcher` to migrate to JUnit5.",
            example = "equalTo",
            required = false)
    @Nullable
    String matcher;

    @Option(displayName = "AssertJ assertion",
            description = "The AssertJ method to migrate to.",
            example = "isEqualTo",
            required = false)
    @Nullable
    String assertion;

    @Option(displayName = "Argument type",
            description = "The type of the argument to the Hamcrest `Matcher`.",
            example = "java.math.BigDecimal",
            required = false)
    @Nullable
    String argumentType;

    @Getter
    final String displayName = "Migrate from Hamcrest `Matcher` to AssertJ";

    @Getter
    final String description = "Migrate from Hamcrest `Matcher` to AssertJ assertions.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>("org.hamcrest.*Matchers " + matcher + "(..)"), new MigrateToAssertJVisitor());
    }

    private class MigrateToAssertJVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final MethodMatcher assertThatMatcher = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(..)");
        private final MethodMatcher matchersMatcher = new MethodMatcher("org.hamcrest.*Matchers " + matcher + "(..)");
        private final MethodMatcher subMatcher = new MethodMatcher("org.hamcrest.*Matchers *(org.hamcrest.Matcher)");

        // AssertJ assertions that don't take any arguments - matcher arguments should be ignored
        private final Set<String> noArgAssertions = new HashSet<>(Arrays.asList("isNotNull", "isNull"));

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
            if (assertThatMatcher.matches(mi)) {
                return replace(mi, ctx);
            }
            return mi;
        }

        private J.MethodInvocation replace(J.MethodInvocation mi, ExecutionContext ctx) {
            List<Expression> mia = mi.getArguments();
            Expression reasonArgument = mia.size() == 3 ? mia.get(0) : null;
            Expression actualArgument = mia.get(mia.size() - 2);
            Expression matcherArgument = mia.get(mia.size() - 1);
            if (!matchersMatcher.matches(matcherArgument) || subMatcher.matches(matcherArgument)) {
                return mi;
            }
            if (argumentType != null && !TypeUtils.isAssignableTo(argumentType, actualArgument.getType())) {
                return mi;
            }

            String actual = typeToIndicator(actualArgument.getType());
            J.MethodInvocation matcherArgumentMethod = (J.MethodInvocation) matcherArgument;
            boolean isNoArgAssertion = noArgAssertions.contains(assertion);
            String argsTemplate = isNoArgAssertion ? "" : getArgumentsTemplate(matcherArgumentMethod);
            JavaTemplate template = JavaTemplate.builder(String.format(
                            "assertThat(%s)" +
                            (reasonArgument != null ? ".as(#{any(String)})" : "") +
                            ".%s(%s)",
                            actual, assertion, argsTemplate))
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                    .staticImports(
                            "org.assertj.core.api.Assertions.assertThat",
                            "org.assertj.core.api.Assertions.within")
                    .build();
            maybeRemoveImport("org.hamcrest.Matchers." + matcher);
            maybeRemoveImport("org.hamcrest.CoreMatchers." + matcher);
            maybeRemoveImport("org.hamcrest.MatcherAssert");
            maybeRemoveImport("org.hamcrest.MatcherAssert.assertThat");
            maybeAddImport("org.assertj.core.api.Assertions", "assertThat");
            maybeAddImport("org.assertj.core.api.Assertions", "within");

            List<Expression> templateArguments = new ArrayList<>();
            templateArguments.add(actualArgument);
            if (reasonArgument != null) {
                templateArguments.add(reasonArgument);
            }
            // Skip adding matcher arguments for assertions that don't take any arguments
            if (!isNoArgAssertion) {
                boolean isCloseTo = CLOSE_TO_MATCHER.matches(matcherArgumentMethod);
                List<Expression> matcherArgs = matcherArgumentMethod.getArguments();
                for (int i = 0; i < matcherArgs.size(); i++) {
                    Expression originalArgument = matcherArgs.get(i);
                    if (!(originalArgument instanceof J.Empty)) {
                        // For closeTo, the tolerance (second argument) type must match the actual value type for within()
                        if (isCloseTo && i == 1) {
                            templateArguments.add(ensureMatchingNumericType(originalArgument, actualArgument.getType()));
                        } else {
                            templateArguments.add(originalArgument);
                        }
                    }
                }
            }
            return template.apply(getCursor(), mi.getCoordinates().replace(), templateArguments.toArray());
        }

        private final MethodMatcher CLOSE_TO_MATCHER = new MethodMatcher("org.hamcrest.Matchers closeTo(..)");

        private Expression ensureMatchingNumericType(Expression toleranceExpr, @Nullable JavaType actualType) {
            JavaType toleranceType = toleranceExpr.getType();
            // Only cast if the actual value is a double/float and tolerance is an integer type
            if ((actualType == JavaType.Primitive.Double || TypeUtils.isOfClassType(actualType, "java.lang.Double")) &&
                    (toleranceType == JavaType.Primitive.Int || toleranceType == JavaType.Primitive.Long)) {
                // Wrap with (double) cast to ensure correct within() overload is called
                return new J.TypeCast(
                        Tree.randomId(),
                        toleranceExpr.getPrefix(),
                        Markers.EMPTY,
                        new J.ControlParentheses<>(
                                Tree.randomId(),
                                Space.EMPTY,
                                Markers.EMPTY,
                                JRightPadded.build(new J.Primitive(Tree.randomId(), Space.EMPTY, Markers.EMPTY, JavaType.Primitive.Double))
                        ),
                        toleranceExpr.withPrefix(Space.SINGLE_SPACE)
                );
            }
            return toleranceExpr;
        }

        private String getArgumentsTemplate(J.MethodInvocation matcherArgument) {
            List<Expression> methodArguments = matcherArgument.getArguments();
            if (CLOSE_TO_MATCHER.matches(matcherArgument)) {
                return String.format("%s, within(%s)",
                        typeToIndicator(methodArguments.get(0).getType()),
                        typeToIndicator(methodArguments.get(1).getType()));
            }
            return methodArguments.stream()
                    .filter(a -> !(a instanceof J.Empty))
                    .map(a -> typeToIndicator(a.getType()))
                    .collect(joining(", "));
        }

        private String typeToIndicator(@Nullable JavaType type) {
            if (type instanceof JavaType.Array) {
                type = ((JavaType.Array) type).getElemType();
                String str = type instanceof JavaType.Primitive || type.toString().startsWith("java.") ?
                        type.toString().replaceAll("<.*>", "") : "java.lang.Object";
                return String.format("#{anyArray(%s)}", str);
            }
            if (type instanceof JavaType.Primitive || type != null && type.toString().startsWith("java.")) {
                return "#{any()}";
            }
            return "#{any(java.lang.Object)}";
        }
    }
}
