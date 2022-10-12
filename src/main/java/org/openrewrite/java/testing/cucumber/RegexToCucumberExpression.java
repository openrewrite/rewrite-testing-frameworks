/*
 * Copyright 2022 the original author or authors.
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
package org.openrewrite.java.testing.cucumber;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

public class RegexToCucumberExpression extends Recipe {

    private static final String IO_CUCUMBER_JAVA_STEP_DEFINITION = "io.cucumber.java.*.*";

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>(IO_CUCUMBER_JAVA_STEP_DEFINITION);
    }

    @Override
    public String getDisplayName() {
        return "Replace Cucumber-Java step definition regexes with Cucumber expressions.";
    }

    @Override
    public String getDescription() {
        return "Strip regex prefix and suffix from step annotation expressions arguments where possible.";
    }

    @Override
    public @Nullable Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new CucumberStepDefinitionBodyVisitor();
    }

    static final class CucumberStepDefinitionBodyVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration m, ExecutionContext p) {
            J.MethodDeclaration methodDeclaration = super.visitMethodDeclaration(m, p);
            return methodDeclaration.withLeadingAnnotations(ListUtils.map(methodDeclaration.getLeadingAnnotations(),
                    ann -> replaceRegexWithCucumberExpression(methodDeclaration, ann, p)));
        }

        private static J.Annotation replaceRegexWithCucumberExpression(
                // For when we want to match regexes with method arguments for replacement cucumber expressions
                // https://github.com/cucumber/cucumber-expressions#parameter-types
                J.MethodDeclaration methodDeclaration,
                J.Annotation annotation,
                ExecutionContext p) {
            List<Expression> arguments = annotation.getArguments();
            Optional<String> possibleExpression = Stream.of(arguments)
                    .filter(Objects::nonNull)
                    .filter(list -> list.size() == 1)
                    .flatMap(Collection::stream)
                    .filter(J.Literal.class::isInstance)
                    .map(e -> (J.Literal) e)
                    .map(l -> (String) l.getValue())
                    // https://github.com/cucumber/cucumber-expressions/blob/main/java/heuristics.adoc
                    .filter(s -> s.startsWith("^") || s.endsWith("$") || leadingAndTrailingSlash(s))
                    .findFirst();
            if (!possibleExpression.isPresent()) {
                return annotation;
            }

            // Strip leading/trailing regex anchors
            String replacement = stripAnchors(possibleExpression.get());

            // Back off when special characters are encountered in regex
            if (Stream.of("(", ")", "{", "}", "[", "]", "?", "*", "+").anyMatch(replacement::contains)) {
                return annotation;
            }

            // Replace regular expression with cucumber expression
            final String finalReplacement = String.format("\"%s\"", replacement);
            return annotation.withArguments(ListUtils.map(annotation.getArguments(), arg -> ((J.Literal) arg)
                    .withValue(finalReplacement)
                    .withValueSource(finalReplacement)));
        }

        private static String stripAnchors(final String initialExpression) {
            if (leadingAndTrailingSlash(initialExpression)) {
                return initialExpression.substring(1, initialExpression.length() - 1);
            }

            // The presence of anchors assumes a Regular Expression, even if only one of the anchors are present 
            String replacement = initialExpression;
            if (replacement.startsWith("^")) {
                replacement = replacement.substring(1);
            }
            if (replacement.endsWith("$")) {
                replacement = replacement.substring(0, replacement.length() - 1);
            }
            return replacement;
        }

        private static boolean leadingAndTrailingSlash(final String initialExpression) {
            return initialExpression.startsWith("/") && initialExpression.endsWith("/");
        }
    }

}
