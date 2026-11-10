/*
 * Copyright 2026 the original author or authors.
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
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.IsLikelyTest;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.staticanalysis.groovy.GroovyFileChecker;
import org.openrewrite.staticanalysis.java.JavaFileChecker;
import org.openrewrite.staticanalysis.kotlin.KotlinFileChecker;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static org.openrewrite.Preconditions.*;

public class FindTestClassesWithoutTests extends ScanningRecipe<FindTestClassesWithoutTests.Accumulator> {

    private static final String TEST_CASE = "junit.framework.TestCase";
    private static final String TESTNG_TEST = "org.testng.annotations.Test";
    private static final String NESTED = "org.junit.jupiter.api.Nested";
    private static final String RUN_WITH = "org.junit.runner.RunWith";
    private static final String PARAMETERIZED = "org.junit.runners.Parameterized";
    private static final String JUNIT3_TEST = "junit.framework.Test";

    private static final Set<String> TEST_ANNOTATIONS = new HashSet<>(Arrays.asList(
            "org.junit.Test",
            "org.junit.jupiter.api.Test",
            "org.junit.jupiter.api.TestTemplate",
            "org.junit.jupiter.api.RepeatedTest",
            "org.junit.jupiter.params.ParameterizedTest",
            "org.junit.jupiter.api.TestFactory",
            "org.junit.experimental.theories.Theory",
            TESTNG_TEST,
            "org.testng.annotations.Factory",
            "com.tngtech.archunit.junit.ArchTest",
            "com.googlecode.zohhak.api.TestWith",
            "org.junit.platform.commons.annotation.Testable"));

    private static final List<String> TEST_LIKE_NAMES = Arrays.asList("Theory", "Property", "TestWith", "Nested");

    private static final List<String> SUITE_ANNOTATIONS = Arrays.asList(
            "org.junit.platform.suite.api.Suite",
            "org.junit.platform.suite.api.SelectClasses",
            "org.junit.platform.suite.api.SelectPackages",
            "io.cucumber.junit.platform.engine.Cucumber");

    // Runners that build their tests from other classes or from files
    private static final List<String> SUITE_RUNNERS = Arrays.asList(
            "org.junit.runners.Suite",
            "io.cucumber.junit.Cucumber",
            "cucumber.api.junit.Cucumber",
            "au.com.dius.pact.provider.junit.PactRunner",
            "com.intuit.karate.junit4.Karate");

    // Specifications that declare their tests in a DSL rather than in annotated methods
    private static final List<String> SPECIFICATIONS = Arrays.asList(
            "io.kotest.core.spec.Spec",
            "org.spekframework.spek2.Spek",
            "spock.lang.Specification");

    @Getter
    final String displayName = "Find test classes without tests";

    @Getter
    final String description = "Finds classes in test sources that are named `*Test`, `*Tests` or `*TestCase`, or extend " +
            "JUnit 3 `TestCase`, but contain no tests. Such classes suggest that code is tested when it is not.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S2187");

    public static class Accumulator {
        // Types whose unresolved test-like annotations or interfaces the type mapping dropped
        final Set<String> mayHideTests = new HashSet<>();
        // Types do not list their member classes
        final Map<String, List<JavaType.FullyQualified>> nestedClasses = new HashMap<>();
        // A missing supertype on these means it was not resolved; Kotlin omits it when a class only implements interfaces
        final Set<String> declaredInSources = new HashSet<>();
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return check(jvmSourceFiles(), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                JavaType.FullyQualified type = classDecl.getType();
                if (type != null) {
                    acc.declaredInSources.add(type.getFullyQualifiedName());
                    if (hasUnresolvedTestTypes(classDecl)) {
                        acc.mayHideTests.add(type.getFullyQualifiedName());
                    }
                    J.ClassDeclaration enclosing = getCursor().getParentTreeCursor().firstEnclosing(J.ClassDeclaration.class);
                    if (enclosing != null && enclosing.getType() != null &&
                            service(AnnotationService.class).getAllAnnotations(getCursor()).stream()
                                    .anyMatch(a -> TypeUtils.isOfClassType(a.getType(), NESTED) ||
                                            isUnresolvedTestLikeAnnotation(a))) {
                        acc.nestedClasses.computeIfAbsent(enclosing.getType().getFullyQualifiedName(), k -> new ArrayList<>())
                                .add(type);
                    }
                }
                return super.visitClassDeclaration(classDecl, ctx);
            }
        });
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return check(and(jvmSourceFiles(), new IsLikelyTest().getVisitor()), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                // Only top-level classes; `@Nested` classes count towards their enclosing class
                if (isTestClass(classDecl) && !containsTests(classDecl.getType(), acc, new HashSet<>())) {
                    return classDecl.withName(SearchResult.found(classDecl.getName()));
                }
                return classDecl;
            }
        });
    }

    private static TreeVisitor<?, ExecutionContext> jvmSourceFiles() {
        return or(new JavaFileChecker<>(), new KotlinFileChecker<>(), new GroovyFileChecker<>());
    }

    private static boolean isTestClass(J.ClassDeclaration cd) {
        if (cd.getKind() != J.ClassDeclaration.Kind.Type.Class || cd.hasModifier(J.Modifier.Type.Abstract) ||
                // Kotlin's sealed classes are abstract, and their modifier is a language extension
                cd.getModifiers().stream().anyMatch(m -> m.getType() == J.Modifier.Type.Sealed || "sealed".equals(m.getKeyword())) ||
                cd.getType() == null || isSuite(cd.getType()) ||
                SPECIFICATIONS.stream().anyMatch(fqn -> TypeUtils.isAssignableTo(fqn, cd.getType()))) {
            return false;
        }
        String name = cd.getSimpleName();
        return name.endsWith("Test") || name.endsWith("Tests") || name.endsWith("TestCase") ||
                TypeUtils.isAssignableTo(TEST_CASE, cd.getType());
    }

    // `@RunWith` is `@Inherited`, so supertypes are checked too
    private static boolean isSuite(JavaType.@Nullable FullyQualified type) {
        if (type == null) {
            return false;
        }
        for (JavaType.FullyQualified annotation : type.getAnnotations()) {
            if (SUITE_ANNOTATIONS.contains(annotation.getFullyQualifiedName())) {
                return true;
            }
            if (RUN_WITH.equals(annotation.getFullyQualifiedName()) && annotation instanceof JavaType.Annotation) {
                List<JavaType.Annotation.ElementValue> values = ((JavaType.Annotation) annotation).getValues();
                Object value = values.isEmpty() ? null : values.get(0).getValue();
                JavaType.FullyQualified runner = value instanceof JavaType ? TypeUtils.asFullyQualified((JavaType) value) : null;
                // An unresolved runner may be a suite; `Parameterized` is a `Suite` that needs tests
                if (runner == null || (!TypeUtils.isAssignableTo(PARAMETERIZED, runner) &&
                        SUITE_RUNNERS.stream().anyMatch(fqn -> TypeUtils.isAssignableTo(fqn, runner)))) {
                    return true;
                }
            }
        }
        return isSuite(type.getSupertype());
    }

    private static boolean containsTests(JavaType.@Nullable FullyQualified type, Accumulator acc, Set<String> visited) {
        return containsTests(type, TypeUtils.isAssignableTo(TEST_CASE, type), isTestNgClass(type), acc, visited);
    }

    // TestNG's class level `@Test`, also on a superclass, makes every public method a test
    private static boolean isTestNgClass(JavaType.@Nullable FullyQualified type) {
        return type != null && (type.getAnnotations().stream().anyMatch(a -> TESTNG_TEST.equals(a.getFullyQualifiedName())) ||
                isTestNgClass(type.getSupertype()));
    }

    // `visited` stops cycles such as a `@Nested` class extending its enclosing class
    private static boolean containsTests(JavaType.@Nullable FullyQualified type, boolean junit3TestCase,
                                         boolean testNgClassLevelTest, Accumulator acc, Set<String> visited) {
        if (type == null || TypeUtils.isObject(type) || !visited.add(type.getFullyQualifiedName())) {
            return false;
        }
        // Unresolved test-like annotations, interfaces or superclass are not in the type; assume tests
        if (acc.mayHideTests.contains(type.getFullyQualifiedName()) ||
                (type.getSupertype() == null && type.getKind() == JavaType.FullyQualified.Kind.Class &&
                        !acc.declaredInSources.contains(type.getFullyQualifiedName()))) {
            return true;
        }
        return type.getMethods().stream().anyMatch(m -> isTestMethod(m, junit3TestCase, testNgClassLevelTest, acc.mayHideTests)) ||
                type.getMembers().stream()
                        .anyMatch(f -> f.getAnnotations().stream().anyMatch(a -> isTestAnnotation(a, acc.mayHideTests))) ||
                acc.nestedClasses.getOrDefault(type.getFullyQualifiedName(), emptyList()).stream()
                        .anyMatch(nested -> containsTests(nested, acc, visited)) ||
                containsTests(type.getSupertype(), junit3TestCase, testNgClassLevelTest, acc, visited) ||
                type.getInterfaces().stream().anyMatch(i -> containsTests(i, junit3TestCase, testNgClassLevelTest, acc, visited));
    }

    private static boolean isTestMethod(JavaType.Method method, boolean junit3TestCase, boolean testNgClassLevelTest,
                                        Set<String> mayHideTests) {
        if (method.isConstructor()) {
            return false;
        }
        if (method.getAnnotations().stream().anyMatch(a -> isTestAnnotation(a, mayHideTests))) {
            return true;
        }
        if (!method.hasFlags(Flag.Public)) {
            return false;
        }
        if (method.hasFlags(Flag.Static)) {
            // A JUnit 3 style `suite()` method
            return "suite".equals(method.getName()) && method.getParameterTypes().isEmpty() &&
                    TypeUtils.isAssignableTo(JUNIT3_TEST, method.getReturnType());
        }
        if (testNgClassLevelTest) {
            return method.getAnnotations().stream().map(JavaType.FullyQualified::getFullyQualifiedName)
                    .noneMatch(fqn -> fqn.startsWith("org.testng.annotations.Before") ||
                            fqn.startsWith("org.testng.annotations.After") ||
                            "org.testng.annotations.DataProvider".equals(fqn));
        }
        return junit3TestCase && method.getName().startsWith("test") && method.getParameterTypes().isEmpty() &&
                method.getReturnType() == JavaType.Primitive.Void;
    }

    private static boolean hasUnresolvedTestTypes(J.ClassDeclaration cd) {
        if ((cd.getExtends() != null && TypeUtils.asFullyQualified(cd.getExtends().getType()) == null) ||
                (cd.getImplements() != null && cd.getImplements().stream().anyMatch(i -> TypeUtils.asFullyQualified(i.getType()) == null))) {
            return true;
        }
        return new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public @Nullable J preVisit(J tree, AtomicBoolean found) {
                // Only annotations on the class, its fields and its methods are part of its type
                Object parent = getCursor().getParentTreeCursor().getValue();
                if ((tree instanceof J.ClassDeclaration && tree != cd) || (tree instanceof J.Block && parent != cd) ||
                        (tree instanceof J.VariableDeclarations && parent instanceof J.MethodDeclaration)) {
                    stopAfterPreVisit();
                } else if (tree instanceof J.Annotation && isUnresolvedTestLikeAnnotation((J.Annotation) tree)) {
                    found.set(true);
                }
                return tree;
            }
        }.reduce(cd, new AtomicBoolean()).get();
    }

    // An unresolved annotation may be a test annotation, or `@Nested`, when its name looks like one
    private static boolean isUnresolvedTestLikeAnnotation(J.Annotation annotation) {
        if (TypeUtils.asFullyQualified(annotation.getType()) != null) {
            return false;
        }
        String name = annotation.getSimpleName();
        return name.endsWith("Test") || TEST_LIKE_NAMES.contains(name);
    }

    private static boolean isTestAnnotation(JavaType.FullyQualified annotation, Set<String> mayHideTests) {
        return isTestAnnotation(annotation, mayHideTests, new HashSet<>());
    }

    // Composed annotations may nest; `seen` stops cycles such as `@Retention` annotating itself.
    // A source annotation with unresolved test-like meta-annotations may be a test annotation.
    private static boolean isTestAnnotation(JavaType.FullyQualified annotation, Set<String> mayHideTests, Set<String> seen) {
        String fqn = annotation.getFullyQualifiedName();
        return TEST_ANNOTATIONS.contains(fqn) || mayHideTests.contains(fqn) ||
                (seen.add(fqn) && annotation.getAnnotations().stream().anyMatch(meta -> isTestAnnotation(meta, mayHideTests, seen)));
    }
}
