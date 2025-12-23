/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.format;
import static java.util.Comparator.comparing;

/**
 * A recipe that handles the usage of the junit-4 ExternalResourceRule's by adding
 * the @ExtendWith(ExternalResourceSupport.class) annotation to the test class.
 * In declarative recipe list, this should be the last recipe, to handle any junit4 ExternalResourceRules, which
 * are not explicitly handled.
 */
public class HandleExternalResourceRules extends Recipe {

    private static final AnnotationMatcher ANY_RULE_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.*Rule", true);
    private static final String CLASS_RULE = "org.junit.ClassRule";
    private static final String EXTERNAL_RESOURCE_RULE = "org.junit.rules.ExternalResource";
    private static final String EXTERNAL_RESOURCE_SUPPORT = "org.junit.jupiter.migrationsupport.rules.ExternalResourceSupport";
    private static final String EXTEND_WITH = "org.junit.jupiter.api.extension.ExtendWith";
    private static final AnnotationMatcher EXTEND_WITH_ANNOTATION_MATCHER = new AnnotationMatcher(format("@%s(%s.class)", EXTEND_WITH, EXTERNAL_RESOURCE_SUPPORT), true);
    private static final String RULE = "org.junit.Rule";

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
        return Preconditions.check(
                Preconditions.or(new UsesType<>(CLASS_RULE, true), new UsesType<>(RULE, true)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                        if (!new ExternalResourceRuleScanner()
                                .reduce(cd, new AtomicBoolean(), getCursor().getParentOrThrow())
                                .get()) {
                            return cd;
                        }

                        // If the class has no @ExtendWith(ExternalResourceSupport.class) annotation, add one
                        if (!service(AnnotationService.class).matches(updateCursor(cd), EXTEND_WITH_ANNOTATION_MATCHER)) {
                            maybeAddImport(EXTERNAL_RESOURCE_SUPPORT);
                            maybeAddImport(EXTEND_WITH);
                            return JavaTemplate.builder("@ExtendWith(ExternalResourceSupport.class)")
                                    .imports(EXTERNAL_RESOURCE_SUPPORT, EXTEND_WITH)
                                    .javaParser(JavaParser.fromJavaVersion()
                                            .classpathFromResources(ctx, "junit-jupiter-migrationsupport-5", "junit-jupiter-api-5"))
                                    .build()
                                    .apply(updateCursor(cd), cd.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)));
                        }

                        return cd;
                    }
                });
    }

    // Checks for existence of ExternalResourceRule
    private static class ExternalResourceRuleScanner extends JavaIsoVisitor<AtomicBoolean> {

        @Override
        public J.VariableDeclarations visitVariableDeclarations(
                J.VariableDeclarations variableDeclarations, AtomicBoolean hasExternalResourceRule) {
            if (!service(AnnotationService.class).matches(getCursor(), ANY_RULE_ANNOTATION_MATCHER)) {
                return variableDeclarations;
            }
            return super.visitVariableDeclarations(variableDeclarations, hasExternalResourceRule);
        }

        @Override
        public J.VariableDeclarations.NamedVariable visitVariable(
                J.VariableDeclarations.NamedVariable variable, AtomicBoolean hasExternalResourceRule) {
            if (variable.getInitializer() != null &&
                    TypeUtils.isAssignableTo(EXTERNAL_RESOURCE_RULE, variable.getInitializer().getType())) {
                hasExternalResourceRule.set(true);
            }
            return variable;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(
                J.MethodDeclaration methodDeclaration, AtomicBoolean hasExternalResourceRule) {
            if (service(AnnotationService.class).matches(getCursor(), ANY_RULE_ANNOTATION_MATCHER) &&
                    methodDeclaration.getMethodType() != null &&
                    TypeUtils.isAssignableTo(EXTERNAL_RESOURCE_RULE, methodDeclaration.getMethodType().getReturnType())) {
                hasExternalResourceRule.set(true);
            }
            return methodDeclaration;
        }
    }
}
