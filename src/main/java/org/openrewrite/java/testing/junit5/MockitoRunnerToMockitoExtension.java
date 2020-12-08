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

import org.openrewrite.AutoConfigure;
import org.openrewrite.CompositeRefactorVisitor;
import org.openrewrite.Formatting;
import org.openrewrite.java.JavaIsoRefactorVisitor;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.maven.AddDependency;
import org.openrewrite.maven.MavenRefactorVisitor;
import org.openrewrite.maven.tree.Maven;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.testing.junit5.FrameworkTypes.extendWithIdent;
import static org.openrewrite.java.testing.junit5.FrameworkTypes.extendWithType;
import static org.openrewrite.java.testing.junit5.FrameworkTypes.runWithIdent;
import static org.openrewrite.java.testing.junit5.FrameworkTypes.runWithType;

/**
 * Convert @RunWith(MockitoJUnitRunner.class) and @RunWith(MockitoJUnit44Runner.class) to @ExtendWith(MockitoExtension.class)
 * MockitoJUnitRunner was part of the core library, but the MockitoExtension is in its own module.
 * So this will attempt to add a dependency on mockito-junit-jupiter so that MockitoExtension can be on the classpath.
 *
 * This is part of the JUnit5 upgrade rather than the Mockito upgrade because you'd only need this if you were adopting JUnit5.
 * Mockito can be upgraded from 1 to 3 without this on JUnit 4.
 */
@AutoConfigure
public class MockitoRunnerToMockitoExtension extends CompositeRefactorVisitor {

    public MockitoRunnerToMockitoExtension() {
        AnnotationUpdate annotationUpdate = new AnnotationUpdate();
        DependencyUpdate dependencyUpdate = new DependencyUpdate(annotationUpdate);
        addVisitor(annotationUpdate);
        addVisitor(dependencyUpdate);
    }

    private static class AnnotationUpdate extends JavaIsoRefactorVisitor {
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
                                runWithIdent,
                                new J.Annotation.Arguments(
                                        randomId(),
                                        Collections.singletonList(
                                                new J.FieldAccess(
                                                        randomId(),
                                                        J.Ident.build(
                                                                randomId(),
                                                                type.getClassName(),
                                                                type,
                                                                EMPTY
                                                        ),
                                                        J.Ident.build(randomId(), "class", null, EMPTY),
                                                        JavaType.Class.build("java.lang.Class"),
                                                        EMPTY
                                                )
                                        ),
                                        EMPTY
                                ),
                                EMPTY
                        )
                )
                .collect(toList());

        private static final JavaType.Class mockitoExtensionType = JavaType.Class.build("org.mockito.junit.jupiter.MockitoExtension");
        private static final J.Annotation extendWithMockitoExtensionAnnotation = new J.Annotation(
                randomId(),
                extendWithIdent,
                new J.Annotation.Arguments(
                        randomId(),
                        Collections.singletonList(
                                new J.FieldAccess(
                                        randomId(),
                                        J.Ident.build(
                                                randomId(),
                                                "MockitoExtension",
                                                mockitoExtensionType,
                                                EMPTY
                                        ),
                                        J.Ident.build(randomId(), "class", null, EMPTY),
                                        JavaType.Class.build("java.lang.Class"),
                                        EMPTY
                                )
                        ),
                        EMPTY
                ),
                EMPTY
        );

        private boolean performedRefactor = false;
        public boolean getPerformedRefactor() {
            return performedRefactor;
        }

        @Override
        public void nextCycle() {
            performedRefactor = false;
        }

        public AnnotationUpdate() {
            setCursoringOn();
        }

        @Override
        public J.ClassDecl visitClassDecl(J.ClassDecl cd) {
            List<J.Annotation> annotations = cd.getAnnotations().stream()
                    .map(this::mockitoRunnerToMockitoExtension)
                    .collect(toList());
            if(performedRefactor) {
                return cd.withAnnotations(annotations);
            }
            return cd;
        }

        private J.Annotation mockitoRunnerToMockitoExtension(J.Annotation maybeMockitoAnnotation) {
            if(!shouldReplaceAnnotation(maybeMockitoAnnotation)) {
                return maybeMockitoAnnotation;
            }
            performedRefactor = true;
            Formatting originalFormatting = maybeMockitoAnnotation.getFormatting();

            J.Annotation extendWithSpringExtension = extendWithMockitoExtensionAnnotation.withFormatting(originalFormatting);
            maybeAddImport(extendWithType);
            maybeAddImport(mockitoExtensionType);
            runWithMockitoAnnotationTypes.stream()
                    .forEach(this::maybeRemoveImport);
            maybeRemoveImport(runWithType);

            return extendWithSpringExtension;
        }

        private boolean shouldReplaceAnnotation(J.Annotation maybeMockitoRunner) {
            return runWithMockitoAnnotations.stream()
                    .filter(mockitoRunnerAnnotation -> new SemanticallyEqual(mockitoRunnerAnnotation).visit(maybeMockitoRunner))
                    .findAny()
                    .isPresent();
        }
    }

    private static class DependencyUpdate extends MavenRefactorVisitor {
        final AnnotationUpdate annotationUpdate;

        private DependencyUpdate(AnnotationUpdate annotationUpdate) {
            this.annotationUpdate = annotationUpdate;
        }

        @Override
        public Maven visitMaven(Maven maven) {
            if(annotationUpdate.getPerformedRefactor()) {
                AddDependency addMockitoJunitJupiterDependency = new AddDependency();
                addMockitoJunitJupiterDependency.setGroupId("org.mockito");
                addMockitoJunitJupiterDependency.setArtifactId("mockito-junit-jupiter");
                addMockitoJunitJupiterDependency.setVersion("3.x");
                addMockitoJunitJupiterDependency.setScope("test");
                if (!andThen().contains(addMockitoJunitJupiterDependency)) {
                    andThen(addMockitoJunitJupiterDependency);
                }
            }
            return super.visitMaven(maven);
        }
    }
}
