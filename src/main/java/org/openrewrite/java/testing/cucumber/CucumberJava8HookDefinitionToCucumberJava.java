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
import java.util.List;
import java.util.Optional;

import lombok.Value;
import lombok.With;
import org.openrewrite.Applicability;
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
import org.openrewrite.java.tree.J.Lambda;
import org.openrewrite.java.tree.J.Literal;
import org.openrewrite.java.tree.J.MethodInvocation;
import org.openrewrite.java.tree.JavaType.Primitive;
import org.openrewrite.maven.ChangeDependencyGroupIdAndArtifactId;

public class CucumberJava8HookDefinitionToCucumberJava extends Recipe {

    private static final String IO_CUCUMBER_JAVA8 = "io.cucumber.java8";
    private static final String IO_CUCUMBER_JAVA8_HOOK_BODY = "io.cucumber.java8.HookBody";
    private static final String IO_CUCUMBER_JAVA8_HOOK_NO_ARGS_BODY = "io.cucumber.java8.HookNoArgsBody";

    private static final String HOOK_BODY_DEFINITION = IO_CUCUMBER_JAVA8
            + ".LambdaGlue *(.., " + IO_CUCUMBER_JAVA8_HOOK_BODY + ")";
    private static final String HOOK_NO_ARGS_BODY_DEFINITION = IO_CUCUMBER_JAVA8
            + ".LambdaGlue *(.., " + IO_CUCUMBER_JAVA8_HOOK_NO_ARGS_BODY + ")";

    private static final MethodMatcher HOOK_BODY_DEFINITION_METHOD_MATCHER = new MethodMatcher(
            HOOK_BODY_DEFINITION);
    private static final MethodMatcher HOOK_NO_ARGS_BODY_DEFINITION_METHOD_MATCHER = new MethodMatcher(
            HOOK_NO_ARGS_BODY_DEFINITION);

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return Applicability.or(
                new UsesMethod<>(HOOK_BODY_DEFINITION, true),
                new UsesMethod<>(HOOK_NO_ARGS_BODY_DEFINITION, true));
    }

    @Override
    public String getDisplayName() {
        return "Replace Cucumber-Java8 hook definition with Cucumber-Java.";
    }

    @Override
    public String getDescription() {
        return "Replace LamdbaGlue hook definitions with new annotated methods with the same body";
    }

    @Override
    public @Nullable Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(10);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new CucumberJava8HooksVisitor();
    }

    static final class CucumberJava8HooksVisitor extends JavaVisitor<ExecutionContext> {
        @Override
        public J visitMethodInvocation(J.MethodInvocation mi, ExecutionContext p) {
            J.MethodInvocation methodInvocation = (MethodInvocation) super.visitMethodInvocation(mi, p);
            if (!HOOK_BODY_DEFINITION_METHOD_MATCHER.matches(methodInvocation)
                    && !HOOK_NO_ARGS_BODY_DEFINITION_METHOD_MATCHER.matches(methodInvocation)) {
                return methodInvocation;
            }

            // Replacement annotations can only handle literals or constants
            if (methodInvocation.getArguments().stream()
                    .anyMatch(arg -> !(arg instanceof J.Literal) && !(arg instanceof J.Lambda))) {
                // TODO Consider adding a marker to indicate method invocation was not migrated because of argument type
                return methodInvocation;
            }

            // Convert invoked method into new annotation
            String stepDefinitionMethodName = methodInvocation.getSimpleName();
            String replacementImport = String.format("%s.%s",
                    methodInvocation.getMethodType().getDeclaringType().getFullyQualifiedName()
                            .replace("java8", "java").toLowerCase(),
                    stepDefinitionMethodName);

            // Extract arguments passed to method
            HookArguments hookArguments = parseHookArguments(methodInvocation.getArguments());
            System.out.println(hookArguments);

            // Generate new method name based on arguments

            // Generate template & template parameters based on arguments

            // Add new template method at end of class declaration

            return null;
        }

        /**
         * Parse up to three arguments:
         * - last one is always a Lambda;
         * - first can also be a String or int.
         * - second can be an int;
         * 
         * @param arguments
         * @return
         */
        HookArguments parseHookArguments(List<Expression> arguments) {
            // Lambda is always last, and can either contain a body with Scenario argument, or without
            int argumentsSize = arguments.size();
            Expression lambdaArgument = arguments.get(argumentsSize - 1);
            HookArguments hookArguments = new HookArguments(
                    null,
                    null,
                    TypeUtils.isAssignableTo(IO_CUCUMBER_JAVA8_HOOK_BODY, lambdaArgument.getType()),
                    ((J.Lambda) lambdaArgument).getBody());
            if (argumentsSize == 1) {
                return hookArguments;
            }

            Literal firstArgument = (Literal) arguments.get(0);
            if (argumentsSize == 2) {
                // First argument is either a String or an int
                if (firstArgument.getType() == Primitive.String) {
                    return hookArguments.withTagExpression((String) firstArgument.getValue());
                }
                return hookArguments.withOrder((Integer) firstArgument.getValue());
            }
            // First argument is always a String, second argument always an int
            return hookArguments
                    .withTagExpression((String) firstArgument.getValue())
                    .withOrder((Integer) ((Literal) arguments.get(1)).getValue());
        }
    }
}

@Value
@With
class HookArguments {
    @Nullable
    String tagExpression;
    @Nullable
    Integer order;
    boolean scenario;
    J lambda;
}
