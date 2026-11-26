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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.FindEmptyMethods;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.MethodDeclaration;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

public class RemoveEmptyTests extends Recipe {

    @Getter
    final String displayName = "Remove empty tests without comments";

    @Getter
    final String description = "Removes empty methods with a `@Test` annotation if the body does not have comments. " +
                               "Keeps them in a test class that starts something, such as a Spring context with " +
                               "`@SpringBootTest`, where an empty test such as `contextLoads()` checks that it starts.";

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(2);

    @Getter
    final Set<String> tags = singleton("RSPEC-S1186");

    private static final Set<String> EXTENSION_ANNOTATIONS = new HashSet<>(Arrays.asList(
            "org.junit.jupiter.api.extension.ExtendWith",
            "org.junit.jupiter.api.extension.Extensions",
            "org.junit.runner.RunWith"));

    /**
     * Extensions and runners that start nothing an empty test could check, such as a context or a server.
     */
    private static final Set<String> INERT_EXTENSIONS = new HashSet<>(Arrays.asList(
            "junitparams.JUnitParamsRunner",
            "org.assertj.core.api.junit.jupiter.SoftAssertionsExtension",
            "io.mockk.junit5.MockKExtension",
            "org.easymock.EasyMockExtension",
            "org.junit.experimental.runners.Enclosed",
            "org.junit.experimental.theories.Theories",
            "org.junit.runners.BlockJUnit4ClassRunner",
            "org.junit.runners.JUnit4",
            "org.junit.runners.Parameterized",
            "org.junit.runners.Suite",
            "org.mockito.junit.MockitoJUnitRunner",
            "org.mockito.junit.MockitoJUnitRunner$Silent",
            "org.mockito.junit.MockitoJUnitRunner$Strict",
            "org.mockito.junit.MockitoJUnitRunner$StrictStubs",
            "org.mockito.junit.jupiter.MockitoExtension",
            "org.mockito.runners.MockitoJUnitRunner"));

    /**
     * The package of JUnit's conditions, such as {@code @EnabledOnOs}, whose extensions only decide whether a test
     * runs.
     */
    private static final String JUNIT_CONDITIONS = "org.junit.jupiter.api.condition.";

    private static final String SPRING_TEST_CONTEXT = "org.springframework.test.context";

    /**
     * The packages of test frameworks whose extensions and runners start an application, a context or containers.
     */
    private static final List<String> STARTING_FRAMEWORKS = Arrays.asList(
            "cloud.localstack.",
            "io.dropwizard.testing.",
            "io.helidon.",
            "io.micronaut.",
            "io.quarkus.",
            "org.apache.camel.test.",
            "org.jboss.arquillian.",
            "org.jboss.weld.",
            "org.microshed.testing.",
            "org.springframework.kafka.test.",
            SPRING_TEST_CONTEXT + ".",
            "org.testcontainers.");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindEmptyMethods(false), new JavaVisitor<ExecutionContext>() {

            @Override
            public @Nullable J visitMethodDeclaration(MethodDeclaration method, ExecutionContext ctx) {
                if (hasTestAnnotation(method) && isEmptyMethod(method) && !testClassStartsSomething()) {
                    for (J.Annotation annotation : method.getLeadingAnnotations()) {
                        JavaType.FullyQualified type = TypeUtils.asFullyQualified(annotation.getType());
                        if (type != null) {
                            maybeRemoveImport(type);
                        }
                    }
                    return null;
                }
                return super.visitMethodDeclaration(method, ctx);
            }

            /**
             * Whether the class of the test starts something that an empty test checks, such as a Spring context. An
             * extension or runner may, as {@link #mayStartSomething} decides, and so does Spring's test support. So
             * may an annotation that doesn't resolve. JUnit applies the extensions of the class, of its superclasses
             * and interfaces, and of the classes that an inner class is nested in.
             */
            private boolean testClassStartsSomething() {
                boolean nested = false;
                for (Iterator<Object> path = getCursor().getPath(J.ClassDeclaration.class::isInstance); path.hasNext(); ) {
                    J.ClassDeclaration classDecl = (J.ClassDeclaration) path.next();
                    J.ClassDeclaration.Kind.Type kind = classDecl.getKind();
                    if (nested && (kind == J.ClassDeclaration.Kind.Type.Interface || kind == J.ClassDeclaration.Kind.Type.Annotation)) {
                        // A class nested in an interface is static
                        return false;
                    }
                    for (J.Annotation annotation : classDecl.getLeadingAnnotations()) {
                        if (annotationMayStartSomething(annotation)) {
                            return true;
                        }
                    }
                    if (classDecl.getType() != null && inheritsSomethingThatStarts(classDecl.getType(), new HashSet<>())) {
                        return true;
                    }
                    if (kind != J.ClassDeclaration.Kind.Type.Class || classDecl.hasModifier(J.Modifier.Type.Static)) {
                        return false;
                    }
                    nested = true;
                }
                return false;
            }

            private boolean annotationMayStartSomething(J.Annotation annotation) {
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(annotation.getType());
                if (type == null) {
                    return true;
                }
                if (EXTENSION_ANNOTATIONS.contains(type.getFullyQualifiedName())) {
                    return classLiteralsMayStartSomething(annotation);
                }
                return annotationTypeMayStartSomething(type, new HashSet<>());
            }

            private boolean inheritsSomethingThatStarts(JavaType.FullyQualified type, Set<String> seen) {
                List<JavaType.FullyQualified> supertypes = new ArrayList<>(type.getInterfaces());
                if (type.getSupertype() != null) {
                    supertypes.add(type.getSupertype());
                }
                for (JavaType.FullyQualified supertype : supertypes) {
                    if (!seen.add(supertype.getFullyQualifiedName())) {
                        continue;
                    }
                    if (isSpringTestContext(supertype.getPackageName())) {
                        return true;
                    }
                    for (JavaType.FullyQualified annotation : supertype.getAnnotations()) {
                        if (annotationTypeMayStartSomething(annotation, new HashSet<>())) {
                            return true;
                        }
                    }
                    if (inheritsSomethingThatStarts(supertype, seen)) {
                        return true;
                    }
                }
                return false;
            }

            private boolean isSpringTestContext(String packageName) {
                return packageName.equals(SPRING_TEST_CONTEXT) || packageName.startsWith(SPRING_TEST_CONTEXT + ".");
            }

            /**
             * @return Whether an extension or runner that the class literals of an {@code @ExtendWith},
             * {@code @Extensions} or {@code @RunWith} written in source name may start something: {@code X.class} in
             * Java, {@code X} or {@code Outer.X} in Groovy.
             */
            private boolean classLiteralsMayStartSomething(J.Annotation annotation) {
                AtomicBoolean startsSomething = new AtomicBoolean();
                new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.Annotation visitAnnotation(J.Annotation a, AtomicBoolean found) {
                        // Not the name of the annotation, which is a type too
                        if (a.getArguments() != null) {
                            for (Expression argument : a.getArguments()) {
                                visit(argument, found);
                            }
                        }
                        return a;
                    }

                    @Override
                    public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, AtomicBoolean found) {
                        if ("class".equals(fieldAccess.getSimpleName())) {
                            if (mayStartSomething(fieldAccess.getTarget().getType())) {
                                found.set(true);
                            }
                            return fieldAccess;
                        }
                        // A nested class in Groovy, `Outer.X`
                        if (isClassName(fieldAccess.getType(), fieldAccess.getSimpleName(), found)) {
                            return fieldAccess;
                        }
                        return super.visitFieldAccess(fieldAccess, found);
                    }

                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean found) {
                        isClassName(identifier.getType(), identifier.getSimpleName(), found);
                        return identifier;
                    }

                    /**
                     * @return Whether the name is the class literal of a type, marking it found when it may start
                     * something.
                     */
                    private boolean isClassName(@Nullable JavaType javaType, String simpleName, AtomicBoolean found) {
                        JavaType.FullyQualified type = TypeUtils.asFullyQualified(javaType);
                        if (type == null || !names(type, simpleName)) {
                            return false;
                        }
                        if (mayStartSomething(type)) {
                            found.set(true);
                        }
                        return true;
                    }
                }.visit(annotation, startsSomething);
                return startsSomething.get();
            }

            /**
             * @return Whether a name written in source is the simple name of the type, so that it refers to the type.
             */
            private boolean names(JavaType.FullyQualified type, String simpleName) {
                String className = type.getClassName();
                return className.equals(simpleName) || className.endsWith("." + simpleName);
            }

            /**
             * @return Whether the annotation, as the types record it, is or is annotated with an {@code @ExtendWith},
             * {@code @Extensions} or {@code @RunWith} that registers an extension or runner that may start something.
             */
            private boolean annotationTypeMayStartSomething(JavaType.FullyQualified annotation, Set<String> seen) {
                if (EXTENSION_ANNOTATIONS.contains(annotation.getFullyQualifiedName())) {
                    if (!(annotation instanceof JavaType.Annotation)) {
                        // The types don't record which extension it is
                        return true;
                    }
                    for (JavaType.Annotation.ElementValue value : ((JavaType.Annotation) annotation).getValues()) {
                        for (JavaType referenced : referenceValues(value)) {
                            if (referencedValueMayStartSomething(referenced, seen)) {
                                return true;
                            }
                        }
                    }
                    return false;
                }
                if (!seen.add(annotation.getFullyQualifiedName())) {
                    return false;
                }
                for (JavaType.FullyQualified meta : annotation.getAnnotations()) {
                    if (annotationTypeMayStartSomething(meta, seen)) {
                        return true;
                    }
                }
                return false;
            }

            /**
             * @return Whether a value of an {@code @ExtendWith}, {@code @Extensions} or {@code @RunWith}, which is an
             * extension class or, in {@code @Extensions}, an {@code @ExtendWith}, may start something.
             */
            private boolean referencedValueMayStartSomething(JavaType referenced, Set<String> seen) {
                if (referenced instanceof JavaType.FullyQualified &&
                    EXTENSION_ANNOTATIONS.contains(((JavaType.FullyQualified) referenced).getFullyQualifiedName())) {
                    return annotationTypeMayStartSomething((JavaType.FullyQualified) referenced, seen);
                }
                return mayStartSomething(referenced);
            }

            private List<JavaType> referenceValues(JavaType.Annotation.ElementValue value) {
                if (value instanceof JavaType.Annotation.ArrayElementValue) {
                    JavaType[] references = ((JavaType.Annotation.ArrayElementValue) value).getReferenceValues();
                    return references == null ? emptyList() : Arrays.asList(references);
                }
                if (value instanceof JavaType.Annotation.SingleElementValue) {
                    JavaType reference = ((JavaType.Annotation.SingleElementValue) value).getReferenceValue();
                    return reference == null ? emptyList() : singletonList(reference);
                }
                return emptyList();
            }

            /**
             * @return Whether the type or one of its superclasses, such as Spring's runner that Camel's extends, is of a
             * framework that starts an application.
             */
            private boolean isOfStartingFramework(JavaType.FullyQualified type) {
                Set<String> seen = new HashSet<>();
                for (JavaType.FullyQualified t = type; t != null && seen.add(t.getFullyQualifiedName()); t = t.getSupertype()) {
                    for (String framework : STARTING_FRAMEWORKS) {
                        if (t.getFullyQualifiedName().startsWith(framework)) {
                            return true;
                        }
                    }
                }
                return false;
            }

            /**
             * @return Whether an extension or runner may start something an empty test checks. One of a framework
             * that starts an application does, and so does a JUnit 5 extension that sets up the test instance, such
             * as by injecting what it starts into it. Other extensions and runners, such as those that only check
             * something around each test, don't. A type that doesn't resolve may.
             */
            private boolean mayStartSomething(@Nullable JavaType extension) {
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(extension);
                if (type == null) {
                    return true;
                }
                String fqn = type.getFullyQualifiedName();
                if (INERT_EXTENSIONS.contains(fqn) || fqn.startsWith(JUNIT_CONDITIONS)) {
                    return false;
                }
                if (isOfStartingFramework(type)) {
                    return true;
                }
                if (TypeUtils.isAssignableTo("org.junit.jupiter.api.extension.Extension", type)) {
                    return TypeUtils.isAssignableTo("org.junit.jupiter.api.extension.TestInstancePostProcessor", type) ||
                           TypeUtils.isAssignableTo("org.junit.jupiter.api.extension.TestInstanceFactory", type);
                }
                // Neither an extension nor a runner, as far as the types tell, when its supertypes don't resolve
                return !TypeUtils.isAssignableTo("org.junit.runner.Runner", type);
            }

            private boolean hasTestAnnotation(J.MethodDeclaration method) {
                return method.getLeadingAnnotations().stream()
                        .map(o -> TypeUtils.asFullyQualified(o.getType()))
                        .anyMatch(type -> type != null && "Test".equals(type.getClassName()));
            }

            private boolean isEmptyMethod(J.MethodDeclaration method) {
                return !method.isConstructor() &&
                        (method.getBody() == null || method.getBody().getStatements().isEmpty() && method.getBody().getEnd().getComments().isEmpty());
            }
        });
    }
}
