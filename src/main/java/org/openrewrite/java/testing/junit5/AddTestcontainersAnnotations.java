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

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

/**
 * An OpenRewrite recipe that migrates JUnit 4 Testcontainers {@code @Rule} or {@code @ClassRule}
 * fields to JUnit 5's {@code @Container} and adds the {@code @Testcontainers} annotation to the
 * class if necessary.
 */
public class AddTestcontainersAnnotations extends Recipe {
    @Override
    public String getDisplayName() {
        return "Handle the usage of GenericContainer rules";
    }

    @Override
    public String getDescription() {
        return "Handles the usage of GenericContainer rules by adding the @Container and @Testcontainers annotations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TestcontainersAnnotationsVisitor();
    }

    private static class TestcontainersAnnotationsVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final String CLASS_RULE_FQN = "org.junit.ClassRule";
        private static final String RULE_FQN = "org.junit.Rule";
        private static final String GENERIC_CONTAINER_FQN = "org.testcontainers.containers.GenericContainer";
        private static final String TESTCONTAINERS_FQN = "org.testcontainers.junit.jupiter.Testcontainers";
        private static final String CONTAINER_FQN = "org.testcontainers.junit.jupiter.Container";
        private static final String[] CLASSPATH = {"testcontainers", "junit-jupiter"};

        private static final JavaTemplate CONTAINER_ANNOTATION_TEMPLATE = JavaTemplate.builder("@Container")
                .imports(CONTAINER_FQN)
                .javaParser(JavaParser.fromJavaVersion().classpath(CLASSPATH))
                .build();

        private static final JavaTemplate TESTCONTAINERS_ANNOTATION_TEMPLATE = JavaTemplate.builder("@Testcontainers")
                .imports(TESTCONTAINERS_FQN)
                .javaParser(JavaParser.fromJavaVersion().classpath(CLASSPATH))
                .build();

        private static boolean isRule(J.VariableDeclarations varDecls) {
            return varDecls.getLeadingAnnotations().stream()
                    .map(J.Annotation::getType)
                    .anyMatch(t -> TypeUtils.isAssignableTo(RULE_FQN, t) || TypeUtils.isAssignableTo(CLASS_RULE_FQN, t));
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            classDecl = super.visitClassDeclaration(classDecl, ctx);

            if (!getCursor().getMessage("hasContainerRule", false)) {
                return classDecl;
            }

            maybeRemoveImport(RULE_FQN);
            maybeRemoveImport(CLASS_RULE_FQN);

            boolean alreadyHasTestcontainersAnnotation = classDecl.getLeadingAnnotations().stream()
                    .anyMatch(ann -> TypeUtils.isAssignableTo(TESTCONTAINERS_FQN, ann.getType()));

            if (alreadyHasTestcontainersAnnotation) {
                return classDecl;
            }

            maybeAddImport(TESTCONTAINERS_FQN);

            return TESTCONTAINERS_ANNOTATION_TEMPLATE.apply(
                    updateCursor(classDecl),
                    classDecl.getCoordinates()
                            .addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations varDecls, ExecutionContext ctx) {
            if (!isRule(varDecls)) {
                return varDecls;
            }

            if (!TypeUtils.isAssignableTo(GENERIC_CONTAINER_FQN, varDecls.getType())) {
                return varDecls;
            }

            // Tell first enclosing ClassDeclaration it needs to add @Testcontainers annotation
            getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, "hasContainerRule", true);

            List<J.Annotation> annotationsToKeep = varDecls.getLeadingAnnotations().stream()
                    .filter(ann -> !TypeUtils.isAssignableTo(RULE_FQN, ann.getType()))
                    .filter(ann -> !TypeUtils.isAssignableTo(CLASS_RULE_FQN, ann.getType()))
                    .collect(Collectors.toList());

            varDecls = varDecls.withLeadingAnnotations(annotationsToKeep);

            boolean alreadyHasContainerAnnotation = annotationsToKeep.stream()
                    .anyMatch(ann -> TypeUtils.isAssignableTo(CONTAINER_FQN, ann.getType()));

            if (alreadyHasContainerAnnotation) {
                return varDecls;
            }

            maybeAddImport(CONTAINER_FQN);

            varDecls = CONTAINER_ANNOTATION_TEMPLATE.apply(
                    updateCursor(varDecls),
                    varDecls.getCoordinates()
                            .addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));

            return varDecls;
        }
    }
}
