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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import static java.util.Comparator.comparing;

/**
 * An OpenRewrite recipe that migrates JUnit 4 Testcontainers {@code @Rule} or {@code @ClassRule}
 * fields to JUnit 5's {@code @Container} and adds the {@code @Testcontainers} annotation to the
 * class if necessary.
 */
public class AddTestcontainersAnnotations extends Recipe {
    private static final String CLASS_RULE_FQN = "org.junit.ClassRule";
    private static final String RULE_FQN = "org.junit.Rule";
    private static final String GENERIC_CONTAINER_FQN = "org.testcontainers.containers.GenericContainer";
    private static final String TESTCONTAINERS_FQN = "org.testcontainers.junit.jupiter.Testcontainers";
    private static final String CONTAINER_FQN = "org.testcontainers.junit.jupiter.Container";

    @Override
    public String getDisplayName() {
        return "Adopt `@Container` and add `@Testcontainers`";
    }

    @Override
    public String getDescription() {
        return "Convert Testcontainers `@Rule`/`@ClassRule` to JUnit 5 `@Container` and add `@Testcontainers`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> usesRule = Preconditions.or(
                new UsesType<>(RULE_FQN, true),
                new UsesType<>(CLASS_RULE_FQN, true)
        );
        return Preconditions.check(usesRule, new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDeclaration, ctx);
                if (classDeclaration == cd) {
                    return cd;
                }

                maybeRemoveImport(RULE_FQN);
                maybeRemoveImport(CLASS_RULE_FQN);

                if (service(AnnotationService.class).isAnnotatedWith(cd, TESTCONTAINERS_FQN)) {
                    return cd;
                }

                // Add class level annotation
                maybeAddImport(TESTCONTAINERS_FQN);
                return JavaTemplate.builder("@Testcontainers")
                        .imports(TESTCONTAINERS_FQN)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "testcontainers-1", "junit-jupiter-1"))
                        .build()
                        .apply(updateCursor(cd), cd.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)));
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations varDecls, ExecutionContext ctx) {
                if (!TypeUtils.isAssignableTo(GENERIC_CONTAINER_FQN, varDecls.getType())) {
                    return varDecls;
                }
                if (!service(AnnotationService.class).isAnnotatedWith(varDecls, RULE_FQN) &&
                        !service(AnnotationService.class).isAnnotatedWith(varDecls, CLASS_RULE_FQN)) {
                    return varDecls;
                }

                // Remove ClassRule/Rule annotations
                J.VariableDeclarations vd = varDecls.withLeadingAnnotations(ListUtils.filter(varDecls.getLeadingAnnotations(),
                        ann -> !TypeUtils.isAssignableTo(RULE_FQN, ann.getType()) &&
                                !TypeUtils.isAssignableTo(CLASS_RULE_FQN, ann.getType())));
                if (vd == varDecls || service(AnnotationService.class).isAnnotatedWith(varDecls, CONTAINER_FQN)) {
                    return vd;
                }

                // Add field level annotation
                maybeAddImport(CONTAINER_FQN);
                return JavaTemplate.builder("@Container")
                        .imports(CONTAINER_FQN)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "testcontainers-1", "junit-jupiter-1"))
                        .build()
                        .apply(updateCursor(vd), vd.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)));
            }
        });
    }

}
