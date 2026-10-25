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

import lombok.Getter;
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
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;

import static java.util.Objects.requireNonNull;
import static org.openrewrite.Preconditions.or;

public class AdoptAssertJDurationAssertions extends Recipe {

    private static final String INTEGER_ASSERT_IS_EQUAL_TO = "org.assertj.core.api.AbstractIntegerAssert isEqualTo(..)";
    private static final String INTEGER_ASSERT_IS_GREATER_THAN = "org.assertj.core.api.AbstractIntegerAssert isGreaterThan(..)";
    private static final String INTEGER_ASSERT_IS_LESS_THAN = "org.assertj.core.api.AbstractIntegerAssert isLessThan(..)";
    private static final String LONG_ASSERT_IS_LESS_THAN = "org.assertj.core.api.AbstractLongAssert isLessThan(..)";
    private static final String LONG_ASSERT_IS_GREATER_THAN = "org.assertj.core.api.AbstractLongAssert isGreaterThan(..)";
    private static final String LONG_ASSERT_IS_EQUAL_TO = "org.assertj.core.api.AbstractLongAssert isEqualTo(..)";

    private static final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");
    private static final MethodMatcher GET_SECONDS_MATCHER = new MethodMatcher("java.time.Duration getSeconds()");
    private static final MethodMatcher AS_MATCHER = new MethodMatcher("org.assertj.core.api.AbstractObjectAssert as(..)");
    private static final MethodMatcher LONG_IS_EQUAL_TO_MATCHER = new MethodMatcher(LONG_ASSERT_IS_EQUAL_TO, true);

    private static final List<MethodMatcher> IS_MATCHERS = Arrays.asList(
            new MethodMatcher(INTEGER_ASSERT_IS_EQUAL_TO, true),
            new MethodMatcher(INTEGER_ASSERT_IS_GREATER_THAN, true),
            new MethodMatcher(INTEGER_ASSERT_IS_LESS_THAN, true),

            new MethodMatcher(LONG_ASSERT_IS_EQUAL_TO, true),
            new MethodMatcher(LONG_ASSERT_IS_GREATER_THAN, true),
            new MethodMatcher(LONG_ASSERT_IS_LESS_THAN, true)
    );

    private static final Map<String, String> METHOD_MAP = new HashMap<String, String>() {{
        put("getSeconds", "hasSeconds");
        put("isGreaterThan", "isPositive");
        put("isLessThan", "isNegative");
        put("isEqualTo", "isZero");
    }};

    @Getter
    final String displayName = "Adopt AssertJ Duration assertions";

    @Getter
    final String description = "Adopt AssertJ `DurationAssert` assertions for more expressive messages.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                or(
                        new UsesMethod<>(INTEGER_ASSERT_IS_EQUAL_TO, true),
                        new UsesMethod<>(INTEGER_ASSERT_IS_GREATER_THAN, true),
                        new UsesMethod<>(INTEGER_ASSERT_IS_LESS_THAN, true),
                        new UsesMethod<>(LONG_ASSERT_IS_EQUAL_TO, true),
                        new UsesMethod<>(LONG_ASSERT_IS_GREATER_THAN, true),
                        new UsesMethod<>(LONG_ASSERT_IS_LESS_THAN, true)
                ), new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (IS_MATCHERS.stream().anyMatch(matcher -> matcher.matches(mi))) {
                            return simplifyMultipleAssertions(mi, ctx);
                        }
                        return mi;
                    }

                    private J.MethodInvocation simplifyMultipleAssertions(J.MethodInvocation m, ExecutionContext ctx) {
                        Expression isEqualToArg = m.getArguments().get(0);
                        Expression select = m.getSelect();
                        List<Object> templateParameters = new ArrayList<>();
                        templateParameters.add(null);
                        Expression asDescription = null;

                        if (AS_MATCHER.matches(select)) {
                            asDescription = ((J.MethodInvocation) select).getArguments().get(0);
                            select = ((J.MethodInvocation) select).getSelect();
                            templateParameters.add(asDescription);
                        }

                        if (!ASSERT_THAT_MATCHER.matches(select)) {
                            return m;
                        }
                        // The templates call the same assertThat as the replaced code
                        String assertions = requireNonNull(((J.MethodInvocation) select).getMethodType()).getDeclaringType().getFullyQualifiedName();

                        Expression assertThatArgumentExpr = ((J.MethodInvocation) select).getArguments().get(0);
                        if (!(assertThatArgumentExpr instanceof J.MethodInvocation)) {
                            return m;
                        }
                        J.MethodInvocation assertThatArg = (J.MethodInvocation) assertThatArgumentExpr;

                        if (isZero(isEqualToArg) && checkIfRelatedToDuration(assertThatArg)) {
                            String formatted_template = formatTemplate("assertThat(#{any()}).%s();", m.getSimpleName(), asDescription);
                            templateParameters.set(0, assertThatArg);
                            return applyTemplate(ctx, m, formatted_template, assertions, templateParameters.toArray());
                        }

                        if (LONG_IS_EQUAL_TO_MATCHER.matches(m) && GET_SECONDS_MATCHER.matches(assertThatArg)) {
                            Expression assertThatArgSelect = assertThatArg.getSelect();
                            String methodName = assertThatArg.getSimpleName();
                            String formatted_template = formatTemplate("assertThat(#{any()}).%s(#{any()});", methodName, asDescription);
                            templateParameters.set(0, assertThatArgSelect);
                            templateParameters.add(isEqualToArg);

                            return applyTemplate(ctx, m, formatted_template, assertions, templateParameters.toArray());
                        }

                        return m;
                    }

                    private boolean isZero(Expression isEqualToArg) {
                        if (isEqualToArg instanceof J.Literal) {
                            J.Literal literal = (J.Literal) isEqualToArg;
                            return literal.getValue() instanceof Number && ((Number) literal.getValue()).longValue() == 0;
                        }
                        return false;
                    }

                    private J.MethodInvocation applyTemplate(ExecutionContext ctx, J.MethodInvocation m, String template, String assertions, Object... parameters) {
                        maybeAddImport(assertions, "assertThat");
                        J.MethodInvocation invocation = JavaTemplate.builder(template)
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                                .staticImports(assertions + ".assertThat")
                                .build()
                                .apply(getCursor(), m.getCoordinates().replace(), parameters);

                        // retain whitespace formatting
                        if (invocation.getPadding().getSelect() != null && m.getPadding().getSelect() != null) {
                            return invocation.getPadding()
                                    .withSelect(
                                            invocation.getPadding().getSelect()
                                                    .withAfter(m.getPadding().getSelect().getAfter())
                                    );
                        }
                        return invocation;
                    }

                    private boolean checkIfRelatedToDuration(J.MethodInvocation argument) {
                        return argument.getSelect() != null && TypeUtils.isOfClassType(argument.getSelect().getType(), "java.time.Duration");
                    }

                    @SuppressWarnings("ConstantValue")
                    private String formatTemplate(String template, String methodName, Object asDescriptionArg) {
                        String replacementMethod = METHOD_MAP.get(methodName);
                        if (asDescriptionArg == null) {
                            return String.format(template, replacementMethod);
                        }
                        StringBuilder newTemplate = new StringBuilder(template);
                        newTemplate.insert(newTemplate.indexOf(").") + 1, ".as(#{any()})");
                        return String.format(newTemplate.toString(), replacementMethod);
                    }
                }
        );
    }
}
