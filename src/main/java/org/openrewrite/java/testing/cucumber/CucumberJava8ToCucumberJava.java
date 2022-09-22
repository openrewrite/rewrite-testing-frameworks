package org.openrewrite.java.testing.cucumber;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.vavr.Predicates;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.java.tree.J.*;
import org.openrewrite.java.tree.J.Lambda.Parameters;
import org.openrewrite.maven.ChangeDependencyGroupIdAndArtifactId;

public class CucumberJava8ToCucumberJava extends Recipe {

    private static final String IO_CUCUMBER_JAVA8 = "io.cucumber.java8";
    private static final String IO_CUCUMBER_JAVA8_STEP_DEFINITION = IO_CUCUMBER_JAVA8 + ".* *(String, ..)";

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
        @Override
        public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext p) {
            List<TypeTree> interfaces = classDecl.getImplements();
            if (interfaces == null || interfaces.isEmpty()) {
                return super.visitClassDeclaration(classDecl, p);
            }

            List<TypeTree> retained = new ArrayList<>();
            for (TypeTree typeTree : interfaces) {
                if (typeTree.getType() instanceof JavaType.Class clazz
                        && IO_CUCUMBER_JAVA8.equals(clazz.getPackageName())) {
                    maybeRemoveImport(clazz.getFullyQualifiedName());
                    maybeAddImport("io.cucumber.java.%s.*".formatted(clazz.getClassName().toLowerCase()), false);
                } else {
                    retained.add(typeTree);
                }
            }
            return classDecl.withImplements(retained);
        }

        @Override
        public J visitMethodDeclaration(MethodDeclaration method, ExecutionContext p) {
            if (!method.isConstructor()) {
                return super.visitMethodDeclaration(method, p);
            }

            Block body = method.getBody();
            List<Statement> statements = body.getStatements();
            var methodMatcher = new MethodMatcher(IO_CUCUMBER_JAVA8_STEP_DEFINITION);
            List<Statement> replaced = new ArrayList<>();
            for (Statement statement : statements) {
                if (statement instanceof MethodInvocation mi
                        && methodMatcher.matches(mi)) {
                    final String stepDefinitionMethodName = mi.getSimpleName();
                    List<Expression> arguments = mi.getArguments();
                    Expression stringExpression = arguments.get(0);
                    final String literalValue;
                    if (stringExpression instanceof Literal literal) {
                        literalValue = (String) literal.getValue();
                    } else
                        continue;

                    Expression possibleStepDefinitionBody = arguments.get(1);
                    final Parameters parameters;
                    final J lambdaBody;
                    if (possibleStepDefinitionBody instanceof Lambda lambda
                            && TypeUtils.isAssignableTo("io.cucumber.java8.StepDefinitionBody",
                                    possibleStepDefinitionBody.getType())) {
                        parameters = lambda.getParameters();
                        lambdaBody = lambda.getBody();
                    } else
                        continue;

                    // TODO Create new method using
                    System.out.println(stepDefinitionMethodName);
                    System.out.println(literalValue);
                    String literalMethodName = literalValue.replaceAll("\s+", "_").replaceAll("[^A-Za-z0-9_]", "");
                    System.out.println(literalMethodName);
                    System.out.println(parameters);
                    System.out.println(lambdaBody);

                    doAfterVisit(new JavaIsoVisitor<ExecutionContext>() {
                        @Override
                        public ClassDeclaration visitClassDeclaration(ClassDeclaration classDecl, ExecutionContext p) {
                            ClassDeclaration classDeclaration = super.visitClassDeclaration(classDecl, p);
                            return classDeclaration.withTemplate(
                                    JavaTemplate.builder(this::getCursor, """
                                            @%s
                                            public void %s(%s) {
                                                //TODO any
                                            }
                                            """.formatted(
                                                    stepDefinitionMethodName,
                                                    literalMethodName, 
                                                    String.join(", ", Collections.nCopies(parameters.getParameters().size(), "#{any()}"))
                                                    ))
                                            .javaParser(() -> JavaParser.fromJavaVersion()
                                                    .classpath("junit", "cucumber-java").build())
                                            .build(),
                                    classDeclaration.getBody().getCoordinates().lastStatement(),
                                    parameters.getParameters().toArray());
                                    //,lambdaBody);
                        }
                    });

                    replaced.add(statement);
                }
            }
            return method.withBody(body.withStatements(statements.stream()
                    .filter(Predicates.not(replaced::contains)).toList()));
        }

        @Override
        public J visitMethodInvocation(MethodInvocation method, ExecutionContext p) {
            System.out.println("Method invocation:" + method);
            // TODO Auto-generated method stub
            return super.visitMethodInvocation(method, p);
        }
    }
}
