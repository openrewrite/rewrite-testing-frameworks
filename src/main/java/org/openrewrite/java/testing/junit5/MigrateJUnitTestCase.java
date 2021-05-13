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
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MigrateJUnitTestCase extends Recipe {

    private static final String JUNIT_TEST_CASE_FQN = "junit.framework.TestCase";

    private static final ThreadLocal<JavaParser> JAVA_PARSER_THREAD_LOCAL = ThreadLocal.withInitial(() ->
            JavaParser.fromJavaVersion()
                    .dependsOn(Collections.singletonList(Parser.Input.fromString(
                            "package org.junit.jupiter.api;\n" +
                                    "public @interface Test {}\n" +
                                    "public @interface AfterEach {}\n" +
                                    "public @interface BeforeEach {}")))
                    .build());

    @Override
    public String getDisplayName() {
        return "TestCase to Jupiter tests";
    }

    @Override
    public String getDescription() {
        return "Convert JUnit TestCase to JUnit 5 Jupiter tests";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TestCaseVisitor();
    }

    private static class TestCaseVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final JavaType.Class JUNIT_ASSERT_TYPE = JavaType.Class.build("org.junit.Assert");
        private static final AnnotationMatcher OVERRIDE_ANNOTATION_MATCHER = new AnnotationMatcher("@java.lang.Override");
        private static final AnnotationMatcher TEST_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.Test");
        private static final AnnotationMatcher BEFORE_EACH_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.BeforeEach");
        private static final AnnotationMatcher AFTER_EACH_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.AfterEach");
        private static final AnnotationMatcher JUNIT_BEFORE_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.Before");
        private static final AnnotationMatcher JUNIT_AFTER_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.After");
        private static final AnnotationMatcher JUNIT_TEST_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.Test");

        private static final String TEST_CASE_MESSAGE_KEY = "junit-test-case";
        private static final String OBJECT_FQN = "java.lang.Object";

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
            // Because TestCase extends Assert and assertions may be referenced from TestCase even if the class is not a TestCase
            // it is necessary to add a cursor message stating that the class extends TestCase so the TestCase.Assert assertions can be processed
            if (isSupertypeTestCase(classDecl.getType())) {
                getCursor().putMessage(TEST_CASE_MESSAGE_KEY, Boolean.TRUE);
            }
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);
            if (cd.getExtends() != null && cd.getExtends().getType() != null) {
                JavaType.FullyQualified fullQualifiedExtension = TypeUtils.asFullyQualified(cd.getExtends().getType());
                if (fullQualifiedExtension != null && JUNIT_TEST_CASE_FQN.equals(fullQualifiedExtension.getFullyQualifiedName())) {
                    cd = cd.withExtends(null);
                }
            }
            maybeRemoveImport(JUNIT_TEST_CASE_FQN);
            return cd;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, executionContext);
            String name = mi.getSimpleName();
            // setUp and tearDown will be invoked via Before and After annotations, setName is not convertible
            if (("setUp".equals(name) || "tearDown".equals(name) || "setName".equals(name))
                    && Boolean.TRUE.equals(getCursor().getNearestMessage(TEST_CASE_MESSAGE_KEY))) {
                return null;
            }
            // Change TestCase.Assert types to JUnit 5 Assertions
            else if (mi.getType() != null && mi.getType().getDeclaringType() != null
                    && JUNIT_TEST_CASE_FQN.equals(mi.getType().getDeclaringType().getFullyQualifiedName())) {
                mi = mi.withType(mi.getType().withDeclaringType(JUNIT_ASSERT_TYPE));
                doAfterVisit(new AssertToAssertions.AssertToAssertionsVisitor());
                maybeAddImport("org.junit.jupiter.api.Assertions", mi.getSimpleName());
            }
            return mi;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
            J.MethodDeclaration md = super.visitMethodDeclaration(method, executionContext);
            if (Boolean.TRUE.equals(getCursor().getNearestMessage(TEST_CASE_MESSAGE_KEY))) {
                if (md.getSimpleName().startsWith("test")
                        && !hasAnnotation(md.getLeadingAnnotations(), TEST_ANNOTATION_MATCHER, JUNIT_TEST_ANNOTATION_MATCHER)) {
                    md = md.withTemplate(template("@Test")
                                    .javaParser(JAVA_PARSER_THREAD_LOCAL.get())
                                    .imports("org.junit.jupiter.api.Test").build(),
                            md.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                    maybeAddImport("org.junit.jupiter.api.Test");
                } else if (md.getSimpleName().equals("setUp")
                        && !hasAnnotation(md.getLeadingAnnotations(), BEFORE_EACH_ANNOTATION_MATCHER, JUNIT_BEFORE_ANNOTATION_MATCHER)) {
                    md = md.withTemplate(template("@BeforeEach")
                                    .javaParser(JAVA_PARSER_THREAD_LOCAL.get())
                                    .imports("org.junit.jupiter.api.BeforeEach").build(),
                            md.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                    md = maybeAddPublicModifier(md);
                    md = maybeRemoveOverrideAnnotation(md);
                    maybeAddImport("org.junit.jupiter.api.BeforeEach");
                } else if (md.getSimpleName().equals("tearDown")
                        && !hasAnnotation(md.getLeadingAnnotations(), AFTER_EACH_ANNOTATION_MATCHER, JUNIT_AFTER_ANNOTATION_MATCHER)) {
                    md = md.withTemplate(template("@AfterEach").javaParser(JAVA_PARSER_THREAD_LOCAL.get())
                                    .imports("org.junit.jupiter.api.AfterEach").build(),
                            md.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                    md = maybeAddPublicModifier(md);
                    md = maybeRemoveOverrideAnnotation(md);
                    maybeAddImport("org.junit.jupiter.api.AfterEach");
                }
            }
            return md;
        }

        private boolean hasAnnotation(List<J.Annotation> annotationList, AnnotationMatcher... annotationMatchers) {
            if (annotationList.isEmpty()) {
                return false;
            }
            for (AnnotationMatcher annotationMatcher : annotationMatchers) {
                if (annotationList.stream().filter(annotationMatcher::matches).findAny().isPresent()) {
                    return true;
                }
            }
            return false;
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

        private boolean isSupertypeTestCase(@Nullable JavaType.Class javaTypeClass) {
            if (javaTypeClass == null || javaTypeClass.getSupertype() == null || OBJECT_FQN.equals(javaTypeClass.getFullyQualifiedName())) {
                return false;
            }
            JavaType.FullyQualified fqType = TypeUtils.asFullyQualified(javaTypeClass);
            if (fqType != null && JUNIT_TEST_CASE_FQN.equals(fqType.getFullyQualifiedName())) {
                return true;
            }
            return isSupertypeTestCase(javaTypeClass.getSupertype());
        }
    }
}
