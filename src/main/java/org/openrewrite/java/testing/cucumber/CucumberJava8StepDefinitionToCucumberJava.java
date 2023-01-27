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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Value
@EqualsAndHashCode(callSuper = true)
public class CucumberJava8StepDefinitionToCucumberJava extends Recipe {

    private static final String IO_CUCUMBER_JAVA8_STEP_DEFINITION = "io.cucumber.java8.* *(String, ..)";
    private static final String IO_CUCUMBER_JAVA8_STEP_DEFINITION_BODY = "io.cucumber.java8.StepDefinitionBody";
    private static final MethodMatcher STEP_DEFINITION_METHOD_MATCHER = new MethodMatcher(
            IO_CUCUMBER_JAVA8_STEP_DEFINITION);

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesMethod<>(IO_CUCUMBER_JAVA8_STEP_DEFINITION, true);
    }

    @Override
    public String getDisplayName() {
        return "Replace Cucumber-Java8 step definitions with Cucumber-Java";
    }

    @Override
    public String getDescription() {
        return "Replace StepDefinitionBody methods with StepDefinitionAnnotations on new methods with the same body.";
    }

    @Override
    public @Nullable Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(10);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new CucumberStepDefinitionBodyVisitor();
    }

    static final class CucumberStepDefinitionBodyVisitor extends JavaVisitor<ExecutionContext> {
        @Override
        public J visitMethodInvocation(J.MethodInvocation methodInvocation, ExecutionContext p) {
            J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(methodInvocation, p);
            if (!STEP_DEFINITION_METHOD_MATCHER.matches(m)) {
                return m;
            }

            // Skip any methods not containing a second argument, such as Scenario.log(String)
            List<Expression> arguments = m.getArguments();
            if (arguments.size() < 2) {
                return m;
            }

            // Annotations require a String literal
            Expression stringExpression = arguments.get(0);
            if (!(stringExpression instanceof J.Literal)) {
                return SearchResult.found(m, "TODO Migrate manually");
            }
            J.Literal literal = (J.Literal) stringExpression;

            // Extract step definition body, when applicable
            Expression possibleStepDefinitionBody = arguments.get(1); // Always available after a first String argument
            if (!(possibleStepDefinitionBody instanceof J.Lambda)
                    || !TypeUtils.isAssignableTo(IO_CUCUMBER_JAVA8_STEP_DEFINITION_BODY,
                            possibleStepDefinitionBody.getType())) {
                return SearchResult.found(m, "TODO Migrate manually");
            }
            J.Lambda lambda = (J.Lambda) possibleStepDefinitionBody;

            StepDefinitionArguments stepArguments = new StepDefinitionArguments(
                    m.getSimpleName(), literal, lambda);

            // Determine step definitions class name
            J.ClassDeclaration parentClass = getCursor()
                    .dropParentUntil(J.ClassDeclaration.class::isInstance)
                    .getValue();
            if(m.getMethodType() == null) {
                return m;
            }
            String replacementImport = String.format("%s.%s",
                    m.getMethodType().getDeclaringType().getFullyQualifiedName()
                            .replace("java8", "java").toLowerCase(),
                    m.getSimpleName());
            doAfterVisit(new CucumberJava8ClassVisitor(
                    parentClass.getType(),
                    replacementImport,
                    stepArguments.template(),
                    stepArguments.parameters()));

            // Remove original method invocation; it's replaced in the above visitor
            //noinspection DataFlowIssue
            return null;
        }
    }

}

@Value
class StepDefinitionArguments {

    String annotationName;
    J.Literal cucumberExpression;
    J.Lambda lambda;

    String template() {
        return "@#{}(#{any()})\npublic void #{}(#{}) throws Exception {\n\t#{any()}\n}";
    }

    private String formatMethodName() {
        return ((String) cucumberExpression.getValue())
                .replaceAll("\\s+", "_")
                .replaceAll("[^A-Za-z0-9_]", "")
                .toLowerCase();
    }

    private String formatMethodArguments() {
        // TODO Type loss here, but my attempts to pass these as J failed: __P__.<java.lang.Object>/*__p0__*/p <error>()
        return lambda.getParameters().getParameters().stream()
                .filter(j -> j instanceof J.VariableDeclarations)
                .map(j -> (J.VariableDeclarations) j)
                .map(J.VariableDeclarations::toString)
                .collect(Collectors.joining(", "));
    }

    Object[] parameters() {
        return new Object[] {
                annotationName,
                cucumberExpression,
                formatMethodName(),
                formatMethodArguments(),
                lambda.getBody() };
    }

}
