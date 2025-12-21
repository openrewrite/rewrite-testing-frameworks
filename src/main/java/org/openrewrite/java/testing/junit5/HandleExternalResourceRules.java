package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.trait.Annotated;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.openrewrite.java.testing.junit5.Junit4Utils.EXTERNAL_RESOURCE_RULE;

/**
 * A recipe that handles the usage of the junit-4 ExternalResourceRule's by adding
 * the @ExtendWith(ExternalResourceSupport.class) annotation to the test class.
 * In declarative recipe list, this should be the last recipe, to handle any junit4 ExternalResourceRules, which
 * are not explicitly handled.
 */
public class HandleExternalResourceRules extends Recipe {

    private static final String EXTERNAL_RESOURCE_SUPPORT =
            "org.junit.jupiter.migrationsupport.rules.ExternalResourceSupport";
    private static final String EXTEND_WITH = "org.junit.jupiter.api.extension.ExtendWith";

    @Override
    public String getDisplayName() {
        return "Handle the usage of ExternalResourceRule fields using @ExtendWith(ExternalResourceSupport.class)";
    }

    @Override
    public String getDescription() {
        return "Handles the usage of the ExternalResourceRule fields by adding the @ExtendWith(ExternalResourceSupport.class) annotation to the test class.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new HandleExternalResourceRulesVisitor();
    }

    private static class HandleExternalResourceRulesVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {

            boolean hasExternalResourceRule =
                    new ExternalResourceRuleScanner().reduce(classDecl, new AtomicBoolean()).get();

            boolean hasExtendWithAnnotation = new Annotated.Matcher(String.format("@%s(%s.class)", EXTEND_WITH, EXTERNAL_RESOURCE_SUPPORT))
                    .<AtomicBoolean>asVisitor((a, flag) -> {
                        flag.set(true);
                        return a.getTree();
                    }).reduce(classDecl, new AtomicBoolean(false)).get();

            // If the class has a ExternalResourceRule and no @ExtendWith(ExternalResourceSupport.class)
            // annotation, add one
            if (hasExternalResourceRule && !hasExtendWithAnnotation) {
                maybeAddImport(EXTERNAL_RESOURCE_SUPPORT);
                maybeAddImport(EXTEND_WITH);
                JavaTemplate externalResourceSupportTemplate =
                        JavaTemplate.builder("@ExtendWith(ExternalResourceSupport.class)")
                                .imports(EXTERNAL_RESOURCE_SUPPORT, EXTEND_WITH)
                                .javaParser(
                                        JavaParser.fromJavaVersion()
                                                .classpathFromResources(ctx,"junit-jupiter-migrationsupport-5", "junit-jupiter-api-5"))
                                .build();
                classDecl =
                        externalResourceSupportTemplate.apply(
                                updateCursor(classDecl),
                                classDecl
                                        .getCoordinates()
                                        .addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
            }
            return super.visitClassDeclaration(classDecl, ctx);
        }
    }

    // Checks for existence of ExternalResourceRule
    private static class ExternalResourceRuleScanner extends JavaIsoVisitor<AtomicBoolean> {

        @Override
        public J.VariableDeclarations visitVariableDeclarations(
                J.VariableDeclarations variableDeclarations, AtomicBoolean hasExternalResourceRule) {
            if (!Junit4Utils.hasJunit4Rules(variableDeclarations)) {
                return variableDeclarations;
            }
            return super.visitVariableDeclarations(variableDeclarations, hasExternalResourceRule);
        }

        @Override
        public J.VariableDeclarations.NamedVariable visitVariable(
                J.VariableDeclarations.NamedVariable variable, AtomicBoolean hasExternalResourceRule) {
            if (variable.getInitializer() != null) {
                if (TypeUtils.isAssignableTo(EXTERNAL_RESOURCE_RULE, variable.getInitializer().getType())) {
                    hasExternalResourceRule.set(true);
                }
            }
            return variable;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(
                J.MethodDeclaration methodDeclaration, AtomicBoolean hasExternalResourceRule) {
            if (Junit4Utils.hasJunit4Rules(methodDeclaration)) {
                if (methodDeclaration.getMethodType() != null) {
                    if (TypeUtils.isAssignableTo(
                            EXTERNAL_RESOURCE_RULE, methodDeclaration.getMethodType().getReturnType())) {
                        hasExternalResourceRule.set(true);
                    }
                }
            }
            return methodDeclaration;
        }
    }
}
