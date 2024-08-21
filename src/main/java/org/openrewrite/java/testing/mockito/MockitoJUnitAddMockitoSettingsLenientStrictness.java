/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.testing.mockito;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.Comparator;
import java.util.Set;

public class MockitoJUnitAddMockitoSettingsLenientStrictness extends Recipe {

    private static String EXTEND_WITH_FQ = "org.junit.jupiter.api.extension.ExtendWith";
    private static String MOCKITO_EXTENSION_FQ = "org.mockito.junit.jupiter.MockitoExtension";
    private static String MOCKITO_SETTINGS_FQ = "org.mockito.junit.jupiter.MockitoSettings";
    private static String MOCKITO_STRICTNESS_FQ = "org.mockito.quality.Strictness";

    private static final String EXTEND_WITH_MOCKITO_EXTENSION = "@" + EXTEND_WITH_FQ + "(" + MOCKITO_EXTENSION_FQ + ".class)";

    @Override
    public String getDisplayName() {
        return "Add `@MockitoSettings(strictness = Strictness.LENIENT)` when migration to JUnit 5";
    }

    @Override
    public String getDescription() {
        return "Add `@MockitoSettings(strictness = Strictness.LENIENT)` when migration to JUnit 5.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new UsesType<>(MOCKITO_EXTENSION_FQ, true),
                        Preconditions.not(new UsesType<>(MOCKITO_SETTINGS_FQ, true))
                ), new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        Set<J.Annotation> annotations = FindAnnotations.find(classDecl, EXTEND_WITH_MOCKITO_EXTENSION);
                        if (!annotations.isEmpty()) {
                            maybeAddImport(MOCKITO_SETTINGS_FQ);
                            maybeAddImport(MOCKITO_STRICTNESS_FQ);
                            classDecl = JavaTemplate.builder("@MockitoSettings(strictness = Strictness.LENIENT)")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-junit-jupiter", "mockito-core"))
                                    .imports(MOCKITO_SETTINGS_FQ, MOCKITO_STRICTNESS_FQ)
                                    .build()
                                    .apply(getCursor(), classDecl.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                            doAfterVisit(new RemoveAnnotation(EXTEND_WITH_MOCKITO_EXTENSION).getVisitor());
                            maybeRemoveImport(EXTEND_WITH_FQ);
                            maybeRemoveImport(MOCKITO_EXTENSION_FQ);
                        }
                        return super.visitClassDeclaration(classDecl, ctx);
                    }
                });
    }
}
