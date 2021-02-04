/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.maven.AddDependency;
import org.openrewrite.maven.MavenVisitor;
import org.openrewrite.maven.tree.Maven;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.testing.junit5.FrameworkTypes.*;

/**
 * Convert @RunWith(MockitoJUnitRunner.class) and @RunWith(MockitoJUnit44Runner.class) to @ExtendWith(MockitoExtension.class)
 * MockitoJUnitRunner was part of the core library, but the MockitoExtension is in its own module.
 * So this will attempt to add a dependency on mockito-junit-jupiter so that MockitoExtension can be on the classpath.
 * <p>
 * This is part of the JUnit5 upgrade rather than the Mockito upgrade because you'd only need this if you were adopting JUnit5.
 * Mockito can be upgraded from 1 to 3 without this on JUnit 4.
 */
public class MockitoRunnerToMockitoExtension extends Recipe {

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AnnotationUpdateVisitor();
    }

    private static class AnnotationUpdateVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final List<JavaType.Class> runWithMockitoAnnotationTypes = Stream.of(
                "org.mockito.runners.MockitoJUnitRunner",
                "org.mockito.junit.MockitoJUnitRunner",
                "org.mockito.runners.MockitoJUnit44Runner",
                "org.mockito.junit.MockitoJUnit44Runner"
        )
                .map(JavaType.Class::build)
                .collect(toList());

        private static final List<J.Annotation> runWithMockitoAnnotations = runWithMockitoAnnotationTypes.stream()
                .map(type -> new J.Annotation(
                                randomId(),
                                Space.EMPTY,
                                Markers.EMPTY,
                                runWithIdent,
                                JContainer.build(
                                        Collections.singletonList(
                                                JRightPadded.build(
                                                        new J.FieldAccess(
                                                                randomId(),
                                                                Space.EMPTY,
                                                                Markers.EMPTY,
                                                                J.Ident.build(randomId(), type.getClassName(), type),
                                                                JLeftPadded.build(
                                                                        J.Ident.build(
                                                                                randomId(),
                                                                                "class",
                                                                                null
                                                                        )
                                                                ),
                                                                JavaType.Class.build("java.lang.Class")
                                                        ))
                                        )
                                )
                        )
                )
                .collect(toList());

        private static final JavaType.Class mockitoExtensionType = JavaType.Class.build("org.mockito.junit.jupiter.MockitoExtension");
        private static final J.Annotation extendWithMockitoExtensionAnnotation = new J.Annotation(
                randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                extendWithIdent,
                JContainer.build(Collections.singletonList(
                        JRightPadded.build(
                                new J.FieldAccess(
                                        randomId(),
                                        Space.EMPTY,
                                        Markers.EMPTY,
                                        J.Ident.build(randomId(), "MockitoExtension", mockitoExtensionType),
                                        JLeftPadded.build(J.Ident.build(randomId(), Space.EMPTY, Markers.EMPTY, "class", null)),
                                        JavaType.Class.build("java.lang.Class")
                                )
                        ))
                )
        );

        public AnnotationUpdateVisitor() {
            setCursoringOn();
        }

        @Override
        public J.ClassDecl visitClassDecl(J.ClassDecl cd, ExecutionContext ctx) {
            boolean shouldReplaceAnnotation = cd.getAnnotations().stream()
                    .anyMatch(this::shouldReplaceAnnotation);
            if (shouldReplaceAnnotation) {
                doAfterVisit(new DependencyUpdate());
                List<J.Annotation> annotations = cd.getAnnotations().stream()
                        .map(this::mockitoRunnerToMockitoExtension)
                        .collect(toList());
                return cd.withAnnotations(annotations);
            }
            return cd;
        }

        private J.Annotation mockitoRunnerToMockitoExtension(J.Annotation maybeMockitoAnnotation) {
            if (!shouldReplaceAnnotation(maybeMockitoAnnotation)) {
                return maybeMockitoAnnotation;
            }

            maybeAddImport(extendWithType);
            maybeAddImport(mockitoExtensionType);
            runWithMockitoAnnotationTypes.forEach(this::maybeRemoveImport);
            maybeRemoveImport(runWithType);

            return extendWithMockitoExtensionAnnotation;
        }

        private boolean shouldReplaceAnnotation(J.Annotation maybeMockitoRunner) {
            return runWithMockitoAnnotations.stream()
                    .anyMatch(mockitoRunnerAnnotation -> SemanticallyEqual.areEqual(mockitoRunnerAnnotation, maybeMockitoRunner));
        }
    }

    private static class DependencyUpdate extends Recipe {

        @Override
        protected TreeVisitor<?, ExecutionContext> getVisitor() {
            return new DependencyUpdateVisitor();
        }

        private static class DependencyUpdateVisitor extends MavenVisitor<ExecutionContext> {

            @Override
            public Maven visitMaven(Maven maven, ExecutionContext ctx) {
                AddDependency addMockitoJunitJupiterDependency = new AddDependency("org.mockito", "mockito-junit-jupiter", "3.x");
                addMockitoJunitJupiterDependency.setScope("test");
                doAfterVisit(addMockitoJunitJupiterDependency);
                return super.visitMaven(maven, ctx);
            }
        }
    }

}
