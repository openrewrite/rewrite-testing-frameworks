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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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

public class CucumberJava8StepDefinitionToCucumberJava extends Recipe {

    private static final String IO_CUCUMBER_JAVA8_STEP_DEFINITION = "io.cucumber.java8.* *(String, ..)";
    private static final String IO_CUCUMBER_JAVA8_STEP_DEFINITION_BODY = "io.cucumber.java8.StepDefinitionBody";
    private static final MethodMatcher STEP_DEFINITION_METHOD_MATCHER = new MethodMatcher(
            IO_CUCUMBER_JAVA8_STEP_DEFINITION);

    public CucumberJava8StepDefinitionToCucumberJava() {
        doNext(new org.openrewrite.java.cleanup.UnnecessaryThrows());
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesMethod<>(IO_CUCUMBER_JAVA8_STEP_DEFINITION, true);
    }

    @Override
    public String getDisplayName() {
        return "Replace Cucumber-Java8 step definitions with Cucumber-Java.";
    }

    @Override
    public String getDescription() {
        return "Replace StepDefinitionBody methods with StepDefinitionAnnotations on new methods with the same body";
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
        public J visitMethodInvocation(J.MethodInvocation mi, ExecutionContext p) {
            J.MethodInvocation methodInvocation = (J.MethodInvocation) super.visitMethodInvocation(mi, p);
            if (!STEP_DEFINITION_METHOD_MATCHER.matches(methodInvocation)) {
                return methodInvocation;
            }

            // Skip any methods not containing a second argument, such as Scenario.log(String)
            List<Expression> arguments = methodInvocation.getArguments();
            if (arguments.size() < 2) {
                return methodInvocation;
            }

            // Annotations require a String literal
            Expression stringExpression = arguments.get(0);
            if (!(stringExpression instanceof J.Literal)) {
                return methodInvocation
                        .withMarkers(methodInvocation.getMarkers().searchResult("TODO Migrate manually"));
            }
            J.Literal literal = (J.Literal) stringExpression;

            // Extract step definition body, when applicable
            Expression possibleStepDefinitionBody = arguments.get(1); // Always available after a first String argument
            if (!(possibleStepDefinitionBody instanceof J.Lambda)
                    || !TypeUtils.isAssignableTo(IO_CUCUMBER_JAVA8_STEP_DEFINITION_BODY,
                            possibleStepDefinitionBody.getType())) {
                return methodInvocation
                        .withMarkers(methodInvocation.getMarkers().searchResult("TODO Migrate manually"));
            }
            J.Lambda lambda = (J.Lambda) possibleStepDefinitionBody;

            StepDefinitionArguments stepArguments = new StepDefinitionArguments(
                    methodInvocation.getSimpleName(), literal, lambda);

            // Determine step definitions class name
            J.ClassDeclaration parentClass = getCursor()
                    .dropParentUntil(J.ClassDeclaration.class::isInstance)
                    .getValue();
            String replacementImport = String.format("%s.%s",
                    methodInvocation.getMethodType().getDeclaringType().getFullyQualifiedName()
                            .replace("java8", "java").toLowerCase(),
                    methodInvocation.getSimpleName());
            doAfterVisit(new CucumberJava8ClassVisitor(
                    parentClass.getType(),
                    replacementImport,
                    stepArguments.template(),
                    stepArguments.parameters().toArray()));

            // Remove original method invocation; it's replaced in the above visitor
            return null;
        }
    }

}

@Value
class StepDefinitionArguments {

    String methodName;
    J.Literal cucumberExpression;
    J.Lambda lambda;

    String template() {
        return String.format("@%s(#{any()})\npublic void %s(%s) throws Exception {\n\t%s\n}",
                methodName,
                formatMethodName(),
                formatMethodArguments(),
                formatMethodBody());
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

    private String formatMethodBody() {
        int copies = lambda.getBody() instanceof J.Block ? ((J.Block) lambda.getBody()).getStatements().size() : 1;
        return Collections.nCopies(copies, "#{any()}").stream().collect(Collectors.joining());
    }

    List<J> parameters() {
        List<J> parameters = new ArrayList<>();
        parameters.add(cucumberExpression);
        if (lambda.getBody() instanceof J.Block) {
            // TODO Lambda block statement unpacking loses any comments / whitespace
            parameters.addAll(((J.Block) lambda.getBody()).getStatements());
        } else {
            parameters.add(lambda.getBody());
        }
        return parameters;
    }

}
