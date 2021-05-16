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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MigrateJUnitTestCase extends Recipe {

    private static final String JUNIT_TEST_CASE_FQN = "junit.framework.TestCase";
    private static final String OBJECT_FQN = "java.lang.Object";

    private static final ThreadLocal<JavaParser> JAVA_PARSER_THREAD_LOCAL = ThreadLocal.withInitial(() ->
            JavaParser.fromJavaVersion()
                    .dependsOn(Collections.singletonList(Parser.Input.fromString(
                            "package org.junit.jupiter.api;\n" +
                                    "public @interface Test {}\n" +
                                    "public @interface AfterEach {}\n" +
                                    "public @interface BeforeEach {}")))
                    .build());

    private static boolean isSupertypeTestCase(@Nullable JavaType.Class javaTypeClass) {
        if (javaTypeClass == null || javaTypeClass.getSupertype() == null || OBJECT_FQN.equals(javaTypeClass.getFullyQualifiedName())) {
            return false;
        }
        JavaType.FullyQualified fqType = TypeUtils.asFullyQualified(javaTypeClass);
        if (fqType != null && JUNIT_TEST_CASE_FQN.equals(fqType.getFullyQualifiedName())) {
            return true;
        }
        return isSupertypeTestCase(javaTypeClass.getSupertype());
    }

    @Override
    public String getDisplayName() {
        return "Migrate JUnit4 `TestCase` to JUnit5";
    }

    @Override
    public String getDescription() {
        return "Convert JUnit TestCase to JUnit 5 Jupiter tests";
    }

    @Override
    protected @Nullable TreeVisitor<?, ExecutionContext> getApplicableTest() {
        return new UsesType<>(JUNIT_TEST_CASE_FQN + ".*");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                if (cu.getClasses().stream().findAny().isPresent()) {
                    doAfterVisit(new TestCaseVisitor());
                }
                doAfterVisit(new AssertToAssertions.AssertToAssertionsVisitor());
                doAfterVisit(new UseStaticImport("org.junit.jupiter.api.Assertions assert*(..)"));
                doAfterVisit(new UseStaticImport("org.junit.jupiter.api.Assertions fail*(..)"));
                return cu;
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
                if (fullQualifiedExtension != null && JUNIT_TEST_CASE_FQN.equals(fullQualifiedExtension.getFullyQualifiedName())) {
                    cd = cd.withExtends(null);
                }
            }
            maybeRemoveImport(JUNIT_TEST_CASE_FQN);
            doAfterVisit(new ChangeType("junit.framework.TestCase", "org.junit.Assert"));
            return cd;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, executionContext);
            String name = mi.getSimpleName();
            // setUp and tearDown will be invoked via Before and After annotations, setName is not convertible
            if ("setUp".equals(name) || "tearDown".equals(name) || "setName".equals(name)) {
                return null;
            }
            return mi;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
            J.MethodDeclaration md = super.visitMethodDeclaration(method, executionContext);
            if (md.getSimpleName().startsWith("test")) {
                md = updateMethodDeclarationAnnotationAndModifier(md, "@Test", "org.junit.jupiter.api.Test");
            } else if (md.getSimpleName().equals("setUp")) {
                md = updateMethodDeclarationAnnotationAndModifier(md, "@BeforeEach", "org.junit.jupiter.api.BeforeEach");
            } else if (md.getSimpleName().equals("tearDown")) {
                md = updateMethodDeclarationAnnotationAndModifier(md, "@AfterEach", "org.junit.jupiter.api.AfterEach");
            }
            return md;
        }

        private J.MethodDeclaration updateMethodDeclarationAnnotationAndModifier(J.MethodDeclaration methodDeclaration, String annotation, String fullyQualifiedAnnotation) {
            J.MethodDeclaration md = methodDeclaration.withTemplate(template(annotation)
                            .javaParser(JAVA_PARSER_THREAD_LOCAL.get())
                            .imports(fullyQualifiedAnnotation).build(),
                    methodDeclaration.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
            md = maybeAddPublicModifier(md);
            md = maybeRemoveOverrideAnnotation(md);
            maybeAddImport(fullyQualifiedAnnotation);
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
