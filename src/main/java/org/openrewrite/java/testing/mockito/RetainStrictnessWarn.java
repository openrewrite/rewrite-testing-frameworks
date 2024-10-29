/*
 * Copyright 2024 the original author or authors.
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

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.RemoveAnnotation;
import org.openrewrite.java.dependencies.DependencyInsight;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class RetainStrictnessWarn extends ScanningRecipe<AtomicBoolean> {

    private static final String EXTEND_WITH_FQ = "org.junit.jupiter.api.extension.ExtendWith";
    private static final String MOCKITO_EXTENSION_FQ = "org.mockito.junit.jupiter.MockitoExtension";
    private static final String MOCKITO_SETTINGS_FQ = "org.mockito.junit.jupiter.MockitoSettings";
    private static final String MOCKITO_STRICTNESS_FQ = "org.mockito.quality.Strictness";

    private static final String EXTEND_WITH_MOCKITO_EXTENSION = "@" + EXTEND_WITH_FQ + "(" + MOCKITO_EXTENSION_FQ + ".class)";

    @Override
    public String getDisplayName() {
        return "Retain Mockito strictness `WARN` when switching to JUnit 5";
    }

    @Override
    public String getDescription() {
        return "Migrating from JUnit 4 to 5 [changes the default strictness](https://stackoverflow.com/a/53234137/53444) of the mocks from `WARN` to `STRICT_STUBS`. " +
               "To prevent tests from failing we restore the original behavior by adding `@MockitoSettings(strictness = Strictness.WARN)`.";
    }

    @Override
    public AtomicBoolean getInitialValue(ExecutionContext ctx) {
        return new AtomicBoolean(false);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AtomicBoolean usingOlderMockito) {
        TreeVisitor<?, ExecutionContext> div = new DependencyInsight("org.mockito", "mockito-*", "[1.1,2.17)", null).getVisitor();
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!usingOlderMockito.get() && div.visit(tree, ctx) != tree) {
                    usingOlderMockito.set(true);
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AtomicBoolean usingOlderMockito) {
        return Preconditions.check(usingOlderMockito.get(),
                Preconditions.check(
                        Preconditions.and(
                                new UsesType<>(MOCKITO_EXTENSION_FQ, true),
                                Preconditions.not(new UsesType<>(MOCKITO_SETTINGS_FQ, false))
                        ), new JavaIsoVisitor<ExecutionContext>() {
                            @Override
                            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                                Set<J.Annotation> annotations = FindAnnotations.find(classDecl, EXTEND_WITH_MOCKITO_EXTENSION);
                                if (!annotations.isEmpty()) {
                                    maybeAddImport(MOCKITO_SETTINGS_FQ);
                                    maybeAddImport(MOCKITO_STRICTNESS_FQ);
                                    classDecl = JavaTemplate.builder("@MockitoSettings(strictness = Strictness.WARN)")
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
                        })
        );
    }
}
