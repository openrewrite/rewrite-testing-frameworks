/*
 * Copyright 2024 the original author or authors.
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
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.testing.junit5.RunnerToExtension;
import org.openrewrite.java.tree.J;

import java.util.Comparator;

import static java.util.Collections.singletonList;

/**
 * @deprecated Use MockitoJUnitRunnerToExtension instead.
 */
@Deprecated
public class MockitoJUnitRunnerSilentToExtension extends Recipe {

    @Getter
    final String displayName = "JUnit 4 MockitoJUnitRunner.Silent to JUnit Jupiter MockitoExtension with LENIENT settings";

    @Getter
    final String description = "Replace `@RunWith(MockitoJUnitRunner.Silent.class)` with `@ExtendWith(MockitoExtension.class)` and `@MockitoSettings(strictness = Strictness.LENIENT)`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.mockito.junit.MockitoJUnitRunner$Silent", false), new JavaIsoVisitor<ExecutionContext>() {

            final AnnotationMatcher silentRunnerMatcher = new AnnotationMatcher("@org.junit.runner.RunWith(org.mockito.junit.MockitoJUnitRunner.Silent.class)");

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                if (cd.getLeadingAnnotations().stream().anyMatch(silentRunnerMatcher::matches)) {
                    JavaTemplate template = JavaTemplate.builder("@MockitoSettings(strictness = Strictness.LENIENT)")
                            .imports("org.mockito.quality.Strictness", "org.mockito.junit.jupiter.MockitoSettings")
                            .javaParser(JavaParser.fromJavaVersion()
                                    .classpathFromResources(ctx, "mockito-junit-jupiter-3.12", "mockito-core-3.12"))
                            .build();
                    cd = maybeAutoFormat(cd, template.apply(updateCursor(cd), cd.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName))), ctx);
                    doAfterVisit(new RunnerToExtension(singletonList("org.mockito.junit.MockitoJUnitRunner$Silent"),
                            "org.mockito.junit.jupiter.MockitoExtension").getVisitor());
                    maybeRemoveImport("org.mockito.junit.MockitoJUnitRunner");
                    maybeAddImport("org.mockito.quality.Strictness");
                    maybeAddImport("org.mockito.junit.jupiter.MockitoSettings");
                }
                return cd;
            }
        });
    }
}
