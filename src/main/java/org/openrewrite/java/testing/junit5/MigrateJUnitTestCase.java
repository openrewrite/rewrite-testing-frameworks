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
package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.*;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MigrateJUnitTestCase extends Recipe {

    private static final AnnotationMatcher JUNIT_TEST_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.Test");

    private static boolean isSupertypeTestCase(@Nullable JavaType.FullyQualified fullyQualified) {
        if (fullyQualified == null || fullyQualified.getSupertype() == null || "java.lang.Object".equals(fullyQualified.getFullyQualifiedName())) {
            return false;
        }

        JavaType.FullyQualified fqType = TypeUtils.asFullyQualified(fullyQualified);
        if (fqType != null && "junit.framework.TestCase".equals(fqType.getFullyQualifiedName())) {
            return true;
        }
        return isSupertypeTestCase(fullyQualified.getSupertype());
    }

    @Override
    public String getDisplayName() {
        return "Migrate JUnit 4 `TestCase` to JUnit Jupiter";
    }

    @Override
    public String getDescription() {
        return "Convert JUnit 4 `TestCase` to JUnit Jupiter.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                for (J.ClassDeclaration clazz : cu.getClasses()) {
                    if (TypeUtils.isAssignableTo(JavaType.Class.build("junit.framework.TestCase"), clazz.getType())) {
                        return cu.withMarkers(cu.getMarkers().searchResult());
                    }
                }

                doAfterVisit(new UsesType<>("junit.framework.TestCase"));
                return cu;
            }
        };
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                J.CompilationUnit c = super.visitCompilationUnit(cu, executionContext);
                doAfterVisit(new TestCaseVisitor());
                // ChangeType for org.junit.Assert method invocations because TestCase extends org.junit.Assert
                doAfterVisit(new ChangeType("junit.framework.TestCase", "org.junit.Assert"));
                doAfterVisit(new AssertToAssertions.AssertToAssertionsVisitor());
                doAfterVisit(new UseStaticImport("org.junit.jupiter.api.Assertions assert*(..)"));
                doAfterVisit(new UseStaticImport("org.junit.jupiter.api.Assertions fail*(..)"));
                return c;
            }

            @SuppressWarnings("ConstantConditions")
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, executionContext);
                if ((mi.getSelect() != null && TypeUtils.isOfClassType(mi.getSelect().getType(), "junit.framework.TestCase"))
                        || (mi.getMethodType() != null && TypeUtils.isOfClassType(mi.getMethodType().getDeclaringType(), "junit.framework.TestCase"))) {
                    String name = mi.getSimpleName();
                    // setUp and tearDown will be invoked via Before and After annotations
                    if ("setUp".equals(name) || "tearDown".equals(name)) {
                        return null;
                    } else if ("setName".equals(name)) {
                        mi = mi.withPrefix(mi.getPrefix().withComments(ListUtils.concat(mi.getPrefix().getComments(), new TextComment(false, "", "", Markers.EMPTY))));
                    }
                }
                return mi;
            }
        };
    }

    private static class TestCaseVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final AnnotationMatcher OVERRIDE_ANNOTATION_MATCHER = new AnnotationMatcher("@java.lang.Override");

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
            if (!isSupertypeTestCase(classDecl.getType())) {
                return classDecl;
            }
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);
            if (cd.getExtends() != null && cd.getExtends().getType() != null) {
                JavaType.FullyQualified fullQualifiedExtension = TypeUtils.asFullyQualified(cd.getExtends().getType());
                if (fullQualifiedExtension != null && "junit.framework.TestCase".equals(fullQualifiedExtension.getFullyQualifiedName())) {
                    cd = cd.withExtends(null);
                }
            }
            maybeRemoveImport("junit.framework.TestCase");
            return cd;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
            J.MethodDeclaration md = super.visitMethodDeclaration(method, executionContext);
            if (md.getSimpleName().startsWith("test") && md.getLeadingAnnotations().stream().noneMatch(JUNIT_TEST_ANNOTATION_MATCHER::matches)) {
                md = updateMethodDeclarationAnnotationAndModifier(md, "@Test", "org.junit.jupiter.api.Test");
            } else if ("setUp".equals(md.getSimpleName())) {
                md = updateMethodDeclarationAnnotationAndModifier(md, "@BeforeEach", "org.junit.jupiter.api.BeforeEach");
            } else if ("tearDown".equals(md.getSimpleName())) {
                md = updateMethodDeclarationAnnotationAndModifier(md, "@AfterEach", "org.junit.jupiter.api.AfterEach");
            }
            return md;
        }

        private J.MethodDeclaration updateMethodDeclarationAnnotationAndModifier(J.MethodDeclaration methodDeclaration, String annotation, String fullyQualifiedAnnotation) {
            J.MethodDeclaration md = methodDeclaration;
            if (FindAnnotations.find(methodDeclaration.withBody(null), "@" + fullyQualifiedAnnotation).isEmpty()) {
                md = methodDeclaration.withTemplate(JavaTemplate.builder(this::getCursor, annotation)
                                .javaParser(() -> JavaParser.fromJavaVersion()
                                                .dependsOn(Collections.singletonList(Parser.Input.fromString(
                                                        "package org.junit.jupiter.api;\n" +
                                                                "public @interface Test {}\n" +
                                                                "public @interface AfterEach {}\n" +
                                                                "public @interface BeforeEach {}")))
                                                .build())
                                .imports(fullyQualifiedAnnotation).build(),
                        methodDeclaration.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                md = maybeAddPublicModifier(md);
                md = maybeRemoveOverrideAnnotation(md);
                maybeAddImport(fullyQualifiedAnnotation);
            }
            return md;
        }

        private J.MethodDeclaration maybeAddPublicModifier(J.MethodDeclaration md) {
            List<J.Modifier> modifiers = ListUtils.map(md.getModifiers(), modifier -> {
                if (modifier.getType() == J.Modifier.Type.Protected) {
                    return modifier.withType(J.Modifier.Type.Public);
                } else {
                    return modifier;
                }
            });
            return md.withModifiers(modifiers);
        }

        private J.MethodDeclaration maybeRemoveOverrideAnnotation(J.MethodDeclaration md) {
            return md.withLeadingAnnotations(ListUtils.map(md.getLeadingAnnotations(), annotation -> {
                if (OVERRIDE_ANNOTATION_MATCHER.matches(annotation)) {
                    return null;
                }
                return annotation;
            }));
        }
    }
}
