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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.testing.junit5.FrameworkTypes.*;

/**
 * JUnit4 Spring test classes are annotated with @RunWith(SpringRunner.class)
 * Turn this into the JUnit5-compatible @ExtendsWith(SpringExtension.class)
 */
public class SpringRunnerToSpringExtension extends Recipe {

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new SpringRunnerToSpringExtensionVisitor();
    }

    public static class SpringRunnerToSpringExtensionVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final JavaType.Class springExtensionType =
                JavaType.Class.build("org.springframework.test.context.junit.jupiter.SpringExtension");
        private static final JavaType.Class springRunnerType =
                JavaType.Class.build("org.springframework.test.context.junit4.SpringRunner");

        // Reference @RunWith(SpringRunner.class) annotation for semantically equal to compare against
        private static final J.Annotation runWithSpringRunnerAnnotation = new J.Annotation(
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
                                                J.Identifier.build(
                                                        randomId(),
                                                        "SpringRunner",
                                                        springRunnerType
                                                ),
                                                JLeftPadded.build(J.Identifier.build(randomId(), "class", null)),
                                                JavaType.Class.build("java.lang.Class")
                                        )
                                )
                        )
                )
        );

        private static final JavaType.Class springJUnit4ClassRunnerType =
                JavaType.Class.build("org.springframework.test.context.junit4.SpringJUnit4ClassRunner");

        // Reference @RunWith(SpringJUnit4ClassRunner.class) annotation for semantically equal to compare against
        private static final J.Annotation runWithSpringJUnit4ClassRunnerAnnotation = new J.Annotation(
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
                                                J.Identifier.build(
                                                        randomId(),
                                                        "SpringJUnit4ClassRunner",
                                                        springJUnit4ClassRunnerType
                                                ),
                                                JLeftPadded.build(J.Identifier.build(randomId(), "class", null)),
                                                JavaType.Class.build("java.lang.Class")
                                        )
                                )
                        )
                )
        );

        private static final J.Annotation extendWithSpringExtensionAnnotation = new J.Annotation(
                randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                extendWithIdent,
                JContainer.build(
                        Collections.singletonList(
                                JRightPadded.build(
                                        new J.FieldAccess(
                                                randomId(),
                                                Space.EMPTY,
                                                Markers.EMPTY,
                                                J.Identifier.build(
                                                        randomId(),
                                                        "SpringExtension",
                                                        springExtensionType
                                                ),
                                                JLeftPadded.build(J.Identifier.build(randomId(), "class", null)),
                                                JavaType.Class.build("java.lang.Class")
                                        )
                                )
                        )
                )
        );

        public SpringRunnerToSpringExtensionVisitor() {
            setCursoringOn();
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
            if (cd.getAnnotations().stream().anyMatch(this::shouldReplaceAnnotation)) {
                List<J.Annotation> annotations = cd.getAnnotations().stream()
                        .map(this::springRunnerToSpringExtension)
                        .collect(Collectors.toList());

                return cd.withAnnotations(annotations);
            }
            return cd;
        }

        /**
         * Converts annotations like @RunWith(SpringRunner.class) and @RunWith(SpringJUnit4ClassRunner.class) into @ExtendWith(SpringExtension.class)
         * Leaves other annotations untouched and returns as-is.
         * <p>
         * NOT a pure function. Side effects include:
         * Adding imports for ExtendWith and SpringExtension
         * Removing imports for RunWith and SpringRunner
         */
        private J.Annotation springRunnerToSpringExtension(J.Annotation maybeSpringRunner) {
            if (!shouldReplaceAnnotation(maybeSpringRunner)) {
                return maybeSpringRunner;
            }
            J.ClassDeclaration parent = getCursor().firstEnclosing(J.ClassDeclaration.class);
            assert parent != null;
            maybeAddImport(extendWithType);
            maybeAddImport(springExtensionType);
            maybeRemoveImport(springRunnerType);
            maybeRemoveImport(springJUnit4ClassRunnerType);
            maybeRemoveImport(runWithType);

            return extendWithSpringExtensionAnnotation;
        }

        private boolean shouldReplaceAnnotation(J.Annotation maybeSpringRunner) {
            return SemanticallyEqual.areEqual(runWithSpringRunnerAnnotation, maybeSpringRunner)
                    || SemanticallyEqual.areEqual(runWithSpringJUnit4ClassRunnerAnnotation, maybeSpringRunner);
        }
    }
}