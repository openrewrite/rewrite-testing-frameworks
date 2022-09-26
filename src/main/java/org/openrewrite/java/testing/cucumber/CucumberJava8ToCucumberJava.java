package org.openrewrite.java.testing.cucumber;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.java.tree.J.*;
import org.openrewrite.maven.ChangeDependencyGroupIdAndArtifactId;

public class CucumberJava8ToCucumberJava extends Recipe {

    private static final String IO_CUCUMBER_JAVA8 = "io.cucumber.java8";
    private static final String IO_CUCUMBER_JAVA8_STEP_DEFINITION = IO_CUCUMBER_JAVA8 + ".* *(String, ..)";
    private static final MethodMatcher STEP_DEFINITION_METHOD_MATCHER = new MethodMatcher(
            IO_CUCUMBER_JAVA8_STEP_DEFINITION);

    public CucumberJava8ToCucumberJava() {
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
        return "Replace Cucumber-Java8 with Cucumber-Java.";
    }

    @Override
    public String getDescription() {
        return "Replace LambdaGlue method invocations with StepDefinitionAnnotations on new methods with the same body";
    }

    @Override
    public @Nullable Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(10);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new CucumberJava8Visitor();
    }

    static class CucumberJava8Visitor extends JavaVisitor<ExecutionContext> {
        /**
         * Remove `implements io.cucumber.java8.En` and add `import static io.cucumber.java.en.*;`.
         */
        @Override
        public J visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext p) {
            J.ClassDeclaration classDecl = (ClassDeclaration) super.visitClassDeclaration(cd, p);
            List<TypeTree> interfaces = classDecl.getImplements();
            if (interfaces == null || interfaces.isEmpty()) {
                return classDecl;
            }

            List<TypeTree> retained = new ArrayList<>();
            for (TypeTree typeTree : interfaces) {
                if (typeTree.getType() instanceof JavaType.Class clazz
                        && IO_CUCUMBER_JAVA8.equals(clazz.getPackageName())) {
                    maybeRemoveImport(clazz.getFullyQualifiedName());
                } else {
                    retained.add(typeTree);
                }
            }
            return classDecl.withImplements(retained);
        }

        @Override
        public J visitMethodDeclaration(MethodDeclaration md, ExecutionContext p) {
            // Remove empty constructor which might be left over after removing method invocations
            J.MethodDeclaration methodDeclaration = (J.MethodDeclaration) super.visitMethodDeclaration(md, p);
            if (methodDeclaration.isConstructor() && methodDeclaration.getBody().getStatements().isEmpty()) {
                return null;
            }
            return methodDeclaration;
        }

        @Override
        public J visitMethodInvocation(MethodInvocation mi, ExecutionContext p) {
            J.MethodInvocation methodInvocation = (MethodInvocation) super.visitMethodInvocation(mi, p);
            if (!STEP_DEFINITION_METHOD_MATCHER.matches(methodInvocation)) {
                return methodInvocation;
            }

            String stepDefinitionMethodName = mi.getSimpleName();
            String replacementImport = "%s.%s".formatted(
                    methodInvocation.getMethodType().getDeclaringType().getFullyQualifiedName()
                            .replace("java8", "java").toLowerCase(),
                    stepDefinitionMethodName);

            // Annotations require a String literal
            List<Expression> arguments = mi.getArguments();
            Expression stringExpression = arguments.get(0);
            if (!(stringExpression instanceof Literal literal)) {
                return methodInvocation;
            }
            String literalValue = (String) literal.getValue();

            // Extract step definition body
            // TODO Prevent index out of bounds for non StepDefinitionBody
            Expression possibleStepDefinitionBody = arguments.get(1);
            if (!(possibleStepDefinitionBody instanceof Lambda lambda)
                    || !TypeUtils.isAssignableTo("io.cucumber.java8.StepDefinitionBody",
                            possibleStepDefinitionBody.getType())) {
                return methodInvocation;
            }
            J lambdaBody = lambda.getBody();

            // Convert cucumber expression into a generated method name
            String literalMethodName = literalValue
                    .replaceAll("\s+", "_")
                    .replaceAll("[^A-Za-z0-9_]", "")
                    .toLowerCase();
            List<String> lambdaParameters = lambda.getParameters().getParameters().stream()
                    .filter(j -> j instanceof J.VariableDeclarations)
                    .map(j -> (J.VariableDeclarations) j)
                    .map(VariableDeclarations::toString)
                    .toList(); // TODO Type loss here, but my attempts to pass these as J failed
            String nCopiesOfAnyArgument = String.join(", ", lambdaParameters);
            String template = """
                    @%s(#{any()})
                    public void %s(%s) {
                        #{any()}
                    }
                    """.formatted(
                    stepDefinitionMethodName,
                    literalMethodName,
                    nCopiesOfAnyArgument);
            List<J> templateParameters = new ArrayList<>();
            templateParameters.add(literal);
            templateParameters.add(lambdaBody);

            // TODO Determine step definitions class name
            String stepDefinitionsClassName = "com.example.app.CalculatorStepDefinitions";

            doAfterVisit(new JavaIsoVisitor<ExecutionContext>() {
                @Override
                public ClassDeclaration visitClassDeclaration(ClassDeclaration classDecl, ExecutionContext p) {
                    ClassDeclaration classDeclaration = super.visitClassDeclaration(classDecl, p);

                    if (classDecl.getType() == null
                            || !stepDefinitionsClassName.equals(classDecl.getType().getFullyQualifiedName())) {
                        // We aren't looking at the specified class so return without making any modifications
                        return classDeclaration;
                    }
                    maybeAddImport(replacementImport);
                    return classDeclaration.withTemplate(
                            JavaTemplate.builder(this::getCursor, template)
                                    .javaParser(() -> JavaParser.fromJavaVersion()
                                            .classpath("junit", "cucumber-java").build())
                                    .imports(replacementImport)
                                    .build(),
                            classDeclaration.getBody().getCoordinates().lastStatement(),
                            templateParameters.toArray());
                }
            });

            return null;
        }
    }
}
