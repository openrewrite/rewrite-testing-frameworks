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
package org.openrewrite.java.testing.cleanup;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddMissingTestAnnotation extends Recipe {

    private static final String JUNIT5_TEST = "org.junit.jupiter.api.Test";
    private static final String JUNIT4_TEST = "org.junit.Test";

    private static final Set<String> LIFECYCLE_ANNOTATIONS = new HashSet<>(Arrays.asList(
            JUNIT5_TEST,
            JUNIT4_TEST,
            "org.junit.jupiter.api.BeforeEach",
            "org.junit.jupiter.api.AfterEach",
            "org.junit.jupiter.api.BeforeAll",
            "org.junit.jupiter.api.AfterAll",
            "org.junit.jupiter.api.Disabled",
            "org.junit.jupiter.api.RepeatedTest",
            "org.junit.jupiter.params.ParameterizedTest",
            "org.junit.Before",
            "org.junit.After",
            "org.junit.BeforeClass",
            "org.junit.AfterClass",
            "org.junit.Ignore"
    ));

    private static final List<String> ASSERTION_PACKAGES = Arrays.asList(
            "org.assertj.core.api",
            "org.hamcrest.MatcherAssert",
            "org.junit.Assert",
            "org.junit.jupiter.api.Assertions",
            "org.mockito.Mockito.verify",
            "org.mockito.Mockito.verifyNoInteractions",
            "org.mockito.Mockito.verifyNoMoreInteractions"
    );

    @Option(displayName = "Add annotation",
            description = "When enabled, adds `@Test` annotation to flagged methods. " +
                    "When disabled (default), only marks methods with a search result.",
            required = false)
    @Nullable
    Boolean addAnnotation;

    @Override
    public String getDisplayName() {
        return "Add missing `@Test` annotations";
    }

    @Override
    public String getDescription() {
        return "Detects methods that appear to be tests (name starts with `test` and body contains assertions) " +
                "but are missing a `@Test` annotation. By default only flags such methods; " +
                "set `addAnnotation` to `true` to automatically add `@Test`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>(JUNIT5_TEST, false),
                        new UsesType<>(JUNIT4_TEST, false)
                ),
                new AddMissingTestAnnotationVisitor()
        );
    }

    private class AddMissingTestAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {

        private @Nullable String currentTestFramework;

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            // Determine which test framework the class uses: JUnit 5, JUnit 4, or null if ambiguous / none
            String previousFramework = currentTestFramework;
            currentTestFramework = determineTestFramework(classDecl);
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
            currentTestFramework = previousFramework;
            return cd;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            if (currentTestFramework == null) {
                return method;
            }

            // Skip if method already has any test or lifecycle annotation
            if (hasAnyTestOrLifecycleAnnotation(method)) {
                return method;
            }

            // Skip constructors
            if (method.isConstructor()) {
                return method;
            }

            // Skip non-void methods
            if (method.getReturnTypeExpression() != null &&
                    !TypeUtils.isOfType(method.getReturnTypeExpression().getType(), JavaType.Primitive.Void)) {
                return method;
            }

            // Skip methods with parameters
            if (!method.getParameters().isEmpty() &&
                    !(method.getParameters().size() == 1 && method.getParameters().get(0) instanceof J.Empty)) {
                return method;
            }

            // Skip private or static methods
            if (method.hasModifier(J.Modifier.Type.Private) || method.hasModifier(J.Modifier.Type.Static)) {
                return method;
            }

            // Skip methods with no body
            if (method.getBody() == null || method.getBody().getStatements().isEmpty()) {
                return method;
            }

            // Require both signals: name starts with "test" AND body contains assertions
            boolean nameStartsWithTest = method.getSimpleName().startsWith("test");
            boolean bodyHasAssertions = methodBodyHasAssertions(method.getBody());

            if (!nameStartsWithTest || !bodyHasAssertions) {
                return method;
            }

            // Either flag or add annotation matching the framework used by the class
            if (Boolean.TRUE.equals(addAnnotation)) {
                String annotationFqn = currentTestFramework;
                String classpathResource = JUNIT5_TEST.equals(currentTestFramework) ? "junit-jupiter-api-5" : "junit-4";

                maybeAddImport(annotationFqn);
                return JavaTemplate.builder("@Test")
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, classpathResource))
                        .imports(annotationFqn)
                        .build()
                        .apply(
                                getCursor(),
                                method.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName))
                        );
            } else {
                return SearchResult.found(method, "Missing @Test annotation");
            }
        }

        private @Nullable String determineTestFramework(J.ClassDeclaration classDecl) {
            if (classDecl.getBody() == null) {
                return null;
            }
            boolean hasJunit4 = false;
            boolean hasJunit5 = false;
            for (org.openrewrite.java.tree.Statement stmt : classDecl.getBody().getStatements()) {
                if (stmt instanceof J.MethodDeclaration) {
                    J.MethodDeclaration md = (J.MethodDeclaration) stmt;
                    for (J.Annotation ann : md.getLeadingAnnotations()) {
                        if (TypeUtils.isOfClassType(ann.getType(), JUNIT5_TEST)) {
                            hasJunit5 = true;
                        } else if (TypeUtils.isOfClassType(ann.getType(), JUNIT4_TEST)) {
                            hasJunit4 = true;
                        }
                    }
                }
            }
            // If the class uses both JUnit 4 and JUnit 5, leave every method untouched
            // because it is ambiguous which framework a new test method should use.
            if (hasJunit4 && hasJunit5) {
                return null;
            }
            if (hasJunit5) {
                return JUNIT5_TEST;
            }
            if (hasJunit4) {
                return JUNIT4_TEST;
            }
            return null;
        }

        private boolean hasAnyTestOrLifecycleAnnotation(J.MethodDeclaration method) {
            for (J.Annotation ann : method.getLeadingAnnotations()) {
                for (String lifecycle : LIFECYCLE_ANNOTATIONS) {
                    if (TypeUtils.isOfClassType(ann.getType(), lifecycle)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean methodBodyHasAssertions(J.Block body) {
            AtomicBoolean found = new AtomicBoolean(false);
            new JavaIsoVisitor<AtomicBoolean>() {
                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean flag) {
                    J.MethodInvocation mi = super.visitMethodInvocation(method, flag);
                    if (mi.getMethodType() != null) {
                        String fqt = mi.getMethodType().getDeclaringType().getFullyQualifiedName();
                        String methodFqn = fqt + "." + mi.getSimpleName();
                        for (String assertionRef : ASSERTION_PACKAGES) {
                            if (fqt.startsWith(assertionRef) || methodFqn.equals(assertionRef)) {
                                flag.set(true);
                                return mi;
                            }
                        }
                    }
                    return mi;
                }
            }.visit(body, found);
            return found.get();
        }
    }
}
