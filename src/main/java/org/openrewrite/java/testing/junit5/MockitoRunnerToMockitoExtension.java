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
import org.openrewrite.Parser;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AddImport;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.openrewrite.Tree.randomId;

/**
 * Convert @RunWith(MockitoJUnitRunner.class) and @RunWith(MockitoJUnit44Runner.class) to @ExtendWith(MockitoExtension.class)
 * MockitoJUnitRunner was part of the core library, but the MockitoExtension is in its own module.
 * So this will attempt to add a dependency on mockito-junit-jupiter so that MockitoExtension can be on the classpath.
 * <p>
 * This is part of the JUnit5 upgrade rather than the Mockito upgrade because you'd only need this if you were adopting JUnit5.
 * Mockito can be upgraded from 1 to 3 without this on JUnit 4.
 */
public class MockitoRunnerToMockitoExtension extends Recipe {

    public static final String MOCKITO_ANNOTATION_REPLACED_KEY = "mockitoAnnotationReplaced";

    @Override
    public String getDisplayName() {
        return "MockitoRunner To MockitoExtension";
    }

    @Override
    public String getDescription() {
        return "Convert @RunWith(MockitoJUnitRunner.class) and @RunWith(MockitoJUnit44Runner.class) to @ExtendWith(MockitoExtension.class)";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AnnotationUpdateVisitor();
    }

    private static class AnnotationUpdateVisitor extends JavaIsoVisitor<ExecutionContext> {

        private static final String[] mockitoRunnerAnnotationClassNames = new String[]{
                "org.mockito.runners.MockitoJUnitRunner",
                "org.mockito.junit.MockitoJUnitRunner",
                "org.mockito.runners.MockitoJUnit44Runner",
                "org.mockito.junit.MockitoJUnit44Runner"
        };

        private static final List<JavaType.Class> mockitoRunnerAnnotationTypes = Stream.of(
                mockitoRunnerAnnotationClassNames
        )
                .map(JavaType.Class::build)
                .collect(Collectors.toList());

        private static final List<J.Annotation> runWithMockitoAnnotations = mockitoRunnerAnnotationTypes.stream()
                .map(type -> new J.Annotation(
                                randomId(),
                                Space.EMPTY,
                                Markers.EMPTY,
                                FrameworkTypes.runWithIdent,
                                JContainer.build(
                                        Collections.singletonList(
                                                JRightPadded.build(
                                                        new J.FieldAccess(
                                                                randomId(),
                                                                Space.EMPTY,
                                                                Markers.EMPTY,
                                                                J.Identifier.build(randomId(), Space.EMPTY, Markers.EMPTY, type.getClassName(), type),
                                                                JLeftPadded.build(
                                                                        J.Identifier.build(
                                                                                randomId(),
                                                                                Space.EMPTY,
                                                                                Markers.EMPTY,
                                                                                "class",
                                                                                null
                                                                        )
                                                                ),
                                                                JavaType.buildType("java.lang.Class")
                                                        ))
                                        )
                                )
                        )
                )
                .collect(Collectors.toList());

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {

            List<J.Annotation> keepAnnotations = cd.getLeadingAnnotations().stream().filter(
                    a -> !shouldReplaceAnnotation(a)
            ).collect(Collectors.toList());
            if (keepAnnotations.size() != cd.getLeadingAnnotations().size()) {
                ctx.putMessage(MOCKITO_ANNOTATION_REPLACED_KEY, true);
                maybeAddImport("org.junit.jupiter.api.extension.ExtendWith");
                AddImport<ExecutionContext> op = new AddImport<>("org.mockito.junit.jupiter.MockitoExtension", null, false);
                if (!getAfterVisit().contains(op)) {
                    doAfterVisit(op);
                }
                Stream.of(mockitoRunnerAnnotationClassNames).forEach(this::maybeRemoveImport);
                maybeRemoveImport(FrameworkTypes.runWithType);
                cd = cd.withLeadingAnnotations(keepAnnotations);
                cd = cd.withTemplate(
                        template("@ExtendWith(MockitoExtension.class)")
                                .imports("org.junit.jupiter.api.extension.ExtendWith", "org.mockito.junit.jupiter.MockitoExtension")
                                .javaParser( JavaParser.fromJavaVersion().dependsOn(Arrays.asList(
                                        Parser.Input.fromString(
                                                "package org.junit.jupiter.api.extension;\n" +
                                                        "@Target({ ElementType.TYPE, ElementType.METHOD })\n" +
                                                        "public @interface ExtendWith {\n" +
                                                        "Class<? extends Extension>[] value();\n" +
                                                        "}"),
                                        Parser.Input.fromString(
                                                "package org.mockito.junit.jupiter;\n" +
                                                        "public class MockitoExtension {}"
                                        ))).build())
                                .build(),
                        cd.getCoordinates().addAnnotation(
                                // TODO should this use some configuration (similar to styles) for annotation ordering?
                                Comparator.comparing(
                                        a -> TypeUtils.asFullyQualified(a.getType()).getFullyQualifiedName()
                                )
                        )
                );
            }
            return cd;
        }

        private boolean shouldReplaceAnnotation(J.Annotation maybeMockitoRunner) {
            return runWithMockitoAnnotations.stream()
                    .anyMatch(mockitoRunnerAnnotation -> SemanticallyEqual.areEqual(mockitoRunnerAnnotation, maybeMockitoRunner));
        }
    }
}
