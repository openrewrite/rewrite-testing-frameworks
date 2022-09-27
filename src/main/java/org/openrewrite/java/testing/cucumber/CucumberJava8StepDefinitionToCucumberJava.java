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
import java.util.Optional;
import java.util.stream.Collectors;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.java.tree.J.*;
import org.openrewrite.java.tree.JavaType.Class;
import org.openrewrite.java.tree.JavaType.FullyQualified;
import org.openrewrite.maven.ChangeDependencyGroupIdAndArtifactId;

public class CucumberJava8StepDefinitionToCucumberJava extends Recipe {

    private static final String IO_CUCUMBER_JAVA8 = "io.cucumber.java8";
    private static final String IO_CUCUMBER_JAVA8_STEP_DEFINITION = IO_CUCUMBER_JAVA8 + ".* *(String, ..)";
    private static final String IO_CUCUMBER_JAVA8_STEP_DEFINITION_BODY = "io.cucumber.java8.StepDefinitionBody";
    private static final MethodMatcher STEP_DEFINITION_METHOD_MATCHER = new MethodMatcher(
            IO_CUCUMBER_JAVA8_STEP_DEFINITION);

    public CucumberJava8StepDefinitionToCucumberJava() {
        doNext(new ChangeDependencyGroupIdAndArtifactId(
                "io.cucumber", "cucumber-java8",
                "io.cucumber", "cucumber-java",
                null, null));
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

            // Annotations require a String literal
            List<Expression> arguments = mi.getArguments();
            Expression stringExpression = arguments.get(0);
            if (!(stringExpression instanceof Literal)) {
                return methodInvocation;
            }
            Literal literal = (Literal) stringExpression;

            // Extract step definition body, when applicable
            Expression possibleStepDefinitionBody = arguments.get(1); // Always available after a first String argument
            if (!(possibleStepDefinitionBody instanceof Lambda)
                    || !TypeUtils.isAssignableTo(IO_CUCUMBER_JAVA8_STEP_DEFINITION_BODY,
                            possibleStepDefinitionBody.getType())) {
                return methodInvocation;
            }
            Lambda lambda = (Lambda) possibleStepDefinitionBody;

            // Convert cucumber expression into a generated method name
            String literalValue = (String) literal.getValue();
            String literalMethodName = literalValue
                    .replaceAll("\\s+", "_")
                    .replaceAll("[^A-Za-z0-9_]", "")
                    .toLowerCase();
            // TODO Type loss here, but my attempts to pass these as J failed
            String lambdaParameters = lambda.getParameters().getParameters().stream()
                    .filter(j -> j instanceof J.VariableDeclarations)
                    .map(j -> (J.VariableDeclarations) j)
                    .map(VariableDeclarations::toString)
                    .collect(Collectors.joining(", "));
            String stepDefinitionMethodName = mi.getSimpleName();
            // TODO J.Block lambda bodies are needlessly wrapped here, but leaving out the {} breaks the generated code
            J lambdaBody = lambda.getBody();
            final String template;
            List<J> templateParameters = new ArrayList<>();
            templateParameters.add(literal);
            if (lambdaBody instanceof J.Block) {
                J.Block block = (J.Block) lambdaBody;
                List<Statement> statements = block.getStatements();
                // TODO Lambda statement unpacking loses any comments/whitespace
                String lambdaStatements = Collections.nCopies(statements.size(), "#{any()}").stream()
                        .collect(Collectors.joining());
                template = String.format("@%s(#{any()})\npublic void %s(%s) {\n\t%s\n}",
                        stepDefinitionMethodName,
                        literalMethodName,
                        lambdaParameters,
                        lambdaStatements);
                templateParameters.addAll(statements);
            } else {
                template = String.format("@%s(#{any()})\npublic void %s(%s) {\n\t#{any()\n}",
                        stepDefinitionMethodName,
                        literalMethodName,
                        lambdaParameters);
                templateParameters.add(lambdaBody);
            }
            // Determine step definitions class name
            J.ClassDeclaration parentClass = getCursor()
                    .dropParentUntil(J.ClassDeclaration.class::isInstance)
                    .getValue();
            String replacementImport = String.format("%s.%s",
                    methodInvocation.getMethodType().getDeclaringType().getFullyQualifiedName()
                            .replace("java8", "java").toLowerCase(),
                    stepDefinitionMethodName);
            doAfterVisit(new CucumberStepDefinitionClassVisitor(
                    parentClass.getType(),
                    replacementImport,
                    template,
                    templateParameters.toArray()));

            // Remove original method invocation; it's replaced in the above visitor
            return null;
        }
    }

    static final class CucumberStepDefinitionClassVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final FullyQualified stepDefinitionsClass;
        private final String replacementImport;
        private final String template;
        private final Object[] templateParameters;

        private CucumberStepDefinitionClassVisitor(
                FullyQualified stepDefinitionsClassName,
                String replacementImport,
                String template,
                Object[] templateParameters) {
            this.stepDefinitionsClass = stepDefinitionsClassName;
            this.replacementImport = replacementImport;
            this.template = template;
            this.templateParameters = templateParameters;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext p) {
            J.ClassDeclaration classDeclaration = super.visitClassDeclaration(classDecl, p);
            if (!TypeUtils.isOfType(classDeclaration.getType(), stepDefinitionsClass)) {
                // We aren't looking at the specified class so return without making any modifications
                return classDeclaration;
            }

            // Remove implement of Java8 interfaces & imports; return retained
            List<TypeTree> retained = filterImplementingInterfaces(classDeclaration);

            // Import Given/When/Then as applicable
            maybeAddImport(replacementImport);

            // Update implements & add new methods last
            return maybeAutoFormat(
                    classDeclaration,
                    classDeclaration
                            .withImplements(retained)
                            .withTemplate(JavaTemplate.builder(this::getCursor, template)
                                    .javaParser(() -> JavaParser.fromJavaVersion().classpath("junit", "cucumber-java")
                                            .build())
                                    .imports(replacementImport)
                                    .build(),
                                    classDeclaration.getBody().getCoordinates().lastStatement(),
                                    templateParameters),
                    p);
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext p) {
            // Remove empty constructor which might be left over after removing method invocations
            J.MethodDeclaration methodDeclaration = (J.MethodDeclaration) super.visitMethodDeclaration(md, p);
            // TODO Should we also remove now empty methods? And how to remove callers?
            if (methodDeclaration.isConstructor() && methodDeclaration.getBody().getStatements().isEmpty()) {
                return null;
            }
            return methodDeclaration;
        }

        private List<TypeTree> filterImplementingInterfaces(J.ClassDeclaration classDeclaration) {
            List<TypeTree> retained = new ArrayList<>();
            for (TypeTree typeTree : Optional.ofNullable(classDeclaration.getImplements()).orElse(List.of())) {
                if (typeTree.getType() instanceof JavaType.Class) {
                    JavaType.Class clazz = (Class) typeTree.getType();
                    if (IO_CUCUMBER_JAVA8.equals(clazz.getPackageName())) {
                        maybeRemoveImport(clazz.getFullyQualifiedName());
                        continue;
                    }
                }
                retained.add(typeTree);
            }
            return retained;
        }
    }
}
