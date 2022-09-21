package org.openrewrite.java.testing.cucumber;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
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
                    String simpleName = mi.getSimpleName();
                    List<Expression> arguments = mi.getArguments();
                    Expression stringExpression = arguments.get(0);
                    if (stringExpression instanceof Literal literal) {
                        String literalValue = (String) literal.getValue();
                        System.out.println(literalValue);
                    } else
                        continue;

                    Expression possibleStepDefinitionBody = arguments.get(1);
                    if (possibleStepDefinitionBody instanceof Lambda lambda
                            && TypeUtils.isAssignableTo("io.cucumber.java8.StepDefinitionBody",
                                    possibleStepDefinitionBody.getType())) {
                        Parameters parameters = lambda.getParameters();
                        J lambdaBody = lambda.getBody();
                        System.out.println(parameters);
                        System.out.println(lambdaBody);
                    } else
                        continue;

                }
            }

            // TODO Only remove empty constructor
//            return method.withTemplate(JavaTemplate.builder(this::getCursor, ";").build(),
//                    method.getCoordinates().replace());
            return method;
        }

        @Override
        public J visitMethodInvocation(MethodInvocation method, ExecutionContext p) {
            System.out.println("Method invocation:" + method);
            // TODO Auto-generated method stub
            return super.visitMethodInvocation(method, p);
        }
    }
}
