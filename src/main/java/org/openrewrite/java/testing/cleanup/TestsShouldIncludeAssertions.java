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
package org.openrewrite.java.testing.cleanup;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@SuppressWarnings("SimplifyStreamApiCallChains")
@Value
@EqualsAndHashCode(callSuper = true)
public class TestsShouldIncludeAssertions extends Recipe {
    private static final List<String> TEST_ANNOTATIONS = Collections.singletonList("org.junit.jupiter.api.Test");

    private static final List<String> DEFAULT_ASSERTIONS = Arrays.asList(
            "org.assertj.core.api",
            "org.junit.jupiter.api.Assertions",
            "org.hamcrest.MatcherAssert",
            "org.mockito.Mockito.verify",
            "org.easymock",
            "org.jmock",
            "mockit",
            "io.restassured",
            "org.springframework.test.web.servlet.ResultActions",
            "com.github.tomakehurst.wiremock.client.WireMock",
            "org.junit.Assert"// rarely, the test annotation is junit5 but the assert is junit4
    );

    @Option(displayName = "Additional assertions",
            description = "A comma delimited list of packages and/or classes that will be identified as assertions. I.E. a common assertion utility `org.foo.TestUtil`.",
            example = "org.foo.TestUtil, org.bar",
            required = false)
    @Nullable
    String additionalAsserts;

    @Override
    public String getDisplayName() {
        return "Include an assertion in tests";
    }

    @Override
    public String getDescription() {
        return "For tests not having any assertions, wrap the statements with JUnit Jupiter's `Assertions#assertThrowDoesNotThrow(..)`.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-2699");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public Validated validate() {
        Validated validated = super.validate()
                .and(Validated.required("assertions", DEFAULT_ASSERTIONS));
        if (validated.isValid()) {
            validated = validated.and(Validated.test(
                    "assertions",
                    "Assertions must not be empty and at least contain org.junit.jupiter.api.Assertions",
                    DEFAULT_ASSERTIONS,
                    a -> a.stream().filter("org.junit.jupiter.api.Assertions"::equals).findAny().isPresent()));
        }
        return validated;
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.junit.jupiter.api.Test");
    }

    @Override
    protected TestShouldIncludeAssertionsVisitor getVisitor() {
        return new TestShouldIncludeAssertionsVisitor(additionalAsserts);
    }

    private static class TestShouldIncludeAssertionsVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final Supplier<JavaParser> ASSERTJ_JAVA_PARSER = () -> JavaParser.fromJavaVersion()
                .dependsOn(Parser.Input.fromResource("/META-INF/rewrite/JupiterAssertions.java", "---")).build();

        private final Map<String, Set<J.Block>> matcherPatternToClassInvocation = new HashMap<>();
        private final List<String> additionalAsserts;

        TestShouldIncludeAssertionsVisitor(@Nullable String additionalAsserts) {
            List<String> assertions = new ArrayList<>();
            if (additionalAsserts != null) {
                assertions.addAll(Arrays.asList(additionalAsserts.split(",\\s*")));
            }
            this.additionalAsserts = assertions;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext
                executionContext) {
            if ((!methodIsTest(method) || method.getBody() == null) ||
                methodHasAssertion(method.getBody()) ||
                methodInvocationInBodyContainsAssertion()) {
                return method;
            }

            J.MethodDeclaration md = super.visitMethodDeclaration(method, executionContext);
            J.Block body = md.getBody();
            if (body != null) {
                md = method.withTemplate(JavaTemplate.builder(this::getCursor, "assertDoesNotThrow(() -> #{any()});")
                                .staticImports("org.junit.jupiter.api.Assertions.assertDoesNotThrow")
                                .javaParser(ASSERTJ_JAVA_PARSER).build(),
                        method.getCoordinates().replaceBody(),
                        body);
                maybeAddImport("org.junit.jupiter.api.Assertions", "assertDoesNotThrow");
            }
            return md;
        }

        private boolean methodIsTest(J.MethodDeclaration methodDeclaration) {
            for (J.Annotation leadingAnnotation : methodDeclaration.getLeadingAnnotations()) {
                for (String testAnnotation : TEST_ANNOTATIONS) {
                    if (TypeUtils.isOfClassType(leadingAnnotation.getType(), testAnnotation)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean methodHasAssertion(J.Block body) {
            AtomicBoolean hasAssertion = new AtomicBoolean(Boolean.FALSE);
            JavaIsoVisitor<AtomicBoolean> findAssertionVisitor = new JavaIsoVisitor<AtomicBoolean>() {
                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean atomicBoolean) {
                    J.MethodInvocation mi = super.visitMethodInvocation(method, atomicBoolean);
                    if (isAssertion(mi)) {
                        atomicBoolean.set(Boolean.TRUE);
                    }
                    return mi;
                }
            };
            findAssertionVisitor.visit(body, hasAssertion);
            return hasAssertion.get();
        }

        private boolean methodInvocationInBodyContainsAssertion() {
            J.ClassDeclaration classDeclaration = getCursor().dropParentUntil(is -> is instanceof J.ClassDeclaration).getValue();

            JavaIsoVisitor<Set<MethodMatcher>> findMethodDeclarationsVisitor = new JavaIsoVisitor<Set<MethodMatcher>>() {
                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Set<MethodMatcher> methodMatchers) {
                    J.MethodInvocation mi = super.visitMethodInvocation(method, methodMatchers);
                    if (classDeclaration.getType() != null && mi.getMethodType() != null) {
                        JavaType.Method mType = mi.getMethodType();
                        if (classDeclaration.getType().getFullyQualifiedName().equals(mType.getDeclaringType().getFullyQualifiedName())) {
                            methodMatchers.add(new MethodMatcher(mType));
                        }
                    }
                    return mi;
                }
            };

            Set<MethodMatcher> methodMatchers = new HashSet<>();
            findMethodDeclarationsVisitor.visit(classDeclaration, methodMatchers);
            Set<J.Block> methodBodies = new HashSet<>();

            methodMatchers.forEach(matcher -> {
                Set<J.Block> declarationBodies = matcherPatternToClassInvocation.computeIfAbsent(matcherPattern(matcher),
                        k -> findMethodDeclarations(classDeclaration, matcher));
                methodBodies.addAll(declarationBodies);
            });
            return methodBodies.stream().anyMatch(this::methodHasAssertion);
        }

        private String matcherPattern(MethodMatcher methodMatcher) {
            return methodMatcher.getTargetTypePattern() + " " + methodMatcher.getMethodNamePattern() + "(" + methodMatcher.getArgumentPattern() + ")";
        }

        private Set<J.Block> findMethodDeclarations(J.ClassDeclaration classDeclaration, MethodMatcher methodMatcher) {
            JavaIsoVisitor<Set<J.Block>> findMethodDeclarationVisitor = new JavaIsoVisitor<Set<J.Block>>() {
                @Override
                public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, Set<J.Block> blocks) {
                    J.MethodDeclaration m = super.visitMethodDeclaration(method, blocks);
                    if (methodMatcher.matches(m, classDeclaration)) {
                        if (m.getBody() != null) {
                            blocks.add(m.getBody());
                        }
                    }
                    return m;
                }
            };

            Set<J.Block> blocks = new HashSet<>();
            findMethodDeclarationVisitor.visit(classDeclaration, blocks);
            return blocks;
        }

        private boolean isAssertion(J.MethodInvocation methodInvocation) {
            if (methodInvocation.getMethodType() == null) {
                return false;
            }
            String fqt = methodInvocation.getMethodType().getDeclaringType().getFullyQualifiedName();
            for (String assertionClassOrPackage : DEFAULT_ASSERTIONS) {
                if (fqt.startsWith(assertionClassOrPackage)) {
                    return true;
                }
            }
            String methodFqn = methodInvocation.getMethodType().getDeclaringType().getFullyQualifiedName() + "." + methodInvocation.getSimpleName();
            for (String assertMethod : DEFAULT_ASSERTIONS) {
                if (assertMethod.equals(methodFqn)) {
                    return true;
                }
            }
            if (additionalAsserts != null) {
                for (String assertionClassOrPackage : additionalAsserts) {
                    if (fqt.startsWith(assertionClassOrPackage)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
