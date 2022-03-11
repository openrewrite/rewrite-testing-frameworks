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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@SuppressWarnings("SimplifyStreamApiCallChains")
@Value
@EqualsAndHashCode(callSuper = true)
public class TestsShouldIncludeAssertions extends Recipe {
    private static final List<String> TEST_ANNOTATIONS = Collections.singletonList("org.junit.jupiter.api.Test");

    private static final List<String> assertions = Arrays.asList(
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

    @Override
    public String getDisplayName() {
        return "Include an assertion in tests";
    }

    @Override
    public String getDescription() {
        return "For tests not having any assertions, wrap the statements with JUnit Jupiter's `Assertions#assertThrowDoesNotThrow(..)`.";
    }

  @Override
  public Duration getEstimatedEffortPerOccurrence() {
    return Duration.ofMinutes(5);
  }

    @Override
    public Validated validate() {
        Validated validated = super.validate()
                .and(Validated.required("assertions", assertions));
        if (validated.isValid()) {
            validated = validated.and(Validated.test(
                    "assertions",
                    "Assertions must not be empty and at least contain org.junit.jupiter.api.Assertions",
                    assertions,
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
        return new TestShouldIncludeAssertionsVisitor();
    }

    private static class TestShouldIncludeAssertionsVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final Supplier<JavaParser> ASSERTJ_JAVA_PARSER = () -> JavaParser.fromJavaVersion()
                .dependsOn(Parser.Input.fromResource("/META-INF/rewrite/JupiterAssertions.java", "---")).build();

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext
                executionContext) {
            if ((!methodIsTest(method) || method.getBody() == null)
                    || methodHasAssertion(method.getBody())) {
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

        private boolean isAssertion(J.MethodInvocation methodInvocation) {
            if (methodInvocation.getMethodType() == null) {
                return false;
            }
            String fqt = methodInvocation.getMethodType().getDeclaringType().getFullyQualifiedName();
            for (String assertionClassOrPackage : assertions) {
                if (fqt.startsWith(assertionClassOrPackage)) {
                    return true;
                }
            }
            if (methodInvocation.getMethodType().getDeclaringType() != null) {
                String methodFqn = methodInvocation.getMethodType().getDeclaringType().getFullyQualifiedName() + "." + methodInvocation.getSimpleName();
                for (String assertMethod : assertions) {
                    if (assertMethod.equals(methodFqn)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
