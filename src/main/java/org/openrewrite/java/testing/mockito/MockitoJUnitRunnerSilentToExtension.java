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
import org.openrewrite.Recipe;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.testing.junit5.RunnerToExtension;
import org.openrewrite.java.tree.J;

import java.util.Collections;
import java.util.Comparator;

public class MockitoJUnitRunnerSilentToExtension extends Recipe {
    @Override
    public String getDisplayName() {
        return "JUnit 4 MockitoJUnitRunner.Silent to JUnit Jupiter MockitoExtension with LENIENT settings";
    }

    @Override
    public String getDescription() {
        return "Replace `@RunWith(MockitoJUnitRunner.Silent.class)` with `@ExtendWith(MockitoExtension.class)` and `@MockitoSettings(strictness = Strictness.LENIENT)`.";
    }

    @Override
    protected UsesType<ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.mockito.junit.MockitoJUnitRunner$Silent");
    }

    @Override
    protected JavaIsoVisitor<ExecutionContext> getVisitor() {

        return new JavaIsoVisitor<ExecutionContext>() {
            final JavaParser parser = JavaParser.fromJavaVersion().dependsOn(
                    "package org.mockito.quality;" +
                            "public enum Strictness {" +
                            "    LENIENT,WARN,STRICT_STUBS;" +
                            "    private Strictness() {}" +
                            "}",
                    "package org.mockito.junit.jupiter;" +
                            "import org.mockito.quality.Strictness;" +
                            "public @interface MockitoSettings {" +
                            "    Strictness strictness() default Strictness.STRICT_STUBS;" +
                            "}").build();
            final AnnotationMatcher silentRunnerMatcher = new AnnotationMatcher("org.junit.runner.RunWith @RunWith(org.mockito.junit.MockitoJUnitRunner.MockitoJUnitRunner.Silent.class)");

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);
                if (cd.getLeadingAnnotations().stream().anyMatch(silentRunnerMatcher::matches)) {
                    JavaTemplate template = JavaTemplate.builder(this::getCursor, "@MockitoSettings(strictness = Strictness.LENIENT)")
                            .imports("org.mockito.quality.Strictness", "org.mockito.junit.jupiter.MockitoSettings")
                            .javaParser(() -> parser)
                            .build();
                    cd = maybeAutoFormat(cd, cd.withTemplate(template, cd.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName))), executionContext);
                    doAfterVisit(new RunnerToExtension(Collections.singletonList("org.mockito.junit.MockitoJUnitRunner$Silent"),
                            "org.mockito.junit.jupiter.MockitoExtension"));
                    maybeRemoveImport("org.mockito.junit.MockitoJUnitRunner");
                    maybeAddImport("org.mockito.quality.Strictness");
                    maybeAddImport("org.mockito.junit.jupiter.MockitoSettings");
                }
                return cd;
            }
        };
    }
}
