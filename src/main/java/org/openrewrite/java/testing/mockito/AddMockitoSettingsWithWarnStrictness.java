/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.testing.mockito;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.Comparator;

import static java.util.Comparator.comparing;

public class AddMockitoSettingsWithWarnStrictness extends Recipe {

    @Getter
    final String displayName = "Add `@MockitoSettings(strictness = Strictness.WARN)` to `@ExtendWith(MockitoExtension.class)` classes";

    @Getter
    final String description = "Adds `@MockitoSettings(strictness = Strictness.WARN)` to test classes that have " +
            "`@ExtendWith(MockitoExtension.class)` but do not already have a `@MockitoSettings` annotation. " +
            "This preserves the lenient stubbing behavior from Mockito 1.x/2.x migrations and prevents " +
            "`UnnecessaryStubbingException` from strict stubbing defaults.";

    private static final String EXTEND_WITH_MOCKITO_EXTENSION =
            "@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)";
    private static final String MOCKITO_SETTINGS = "@org.mockito.junit.jupiter.MockitoSettings";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>("org.mockito.junit.jupiter.MockitoExtension", false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                        if (!FindAnnotations.find(cd.withBody(null), EXTEND_WITH_MOCKITO_EXTENSION).isEmpty() &&
                            FindAnnotations.find(cd.withBody(null), MOCKITO_SETTINGS).isEmpty()) {
                            maybeAddImport("org.mockito.junit.jupiter.MockitoSettings");
                            maybeAddImport("org.mockito.quality.Strictness");
                            return JavaTemplate.builder("@MockitoSettings(strictness = Strictness.WARN)")
                                    .javaParser(JavaParser.fromJavaVersion()
                                            .classpathFromResources(ctx, "mockito-junit-jupiter-3.12", "mockito-core-3.12"))
                                    .imports("org.mockito.junit.jupiter.MockitoSettings", "org.mockito.quality.Strictness")
                                    .build()
                                    .apply(updateCursor(cd), cd.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)));
                        }

                        return cd;
                    }
                });
    }
}
