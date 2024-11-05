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
package org.openrewrite.java.testing.assertj;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.staticanalysis.LambdaBlockToExpression;

public class JUnitAssertThrowsToAssertExceptionType extends Recipe {

    private static final MethodMatcher ASSERT_THROWS_MATCHER = new MethodMatcher("org.junit.jupiter.api.Assertions assertThrows(..)");
    private static final JavaType THROWING_CALLABLE_TYPE = JavaType.buildType("org.assertj.core.api.ThrowableAssert.ThrowingCallable");

    @Override
    public String getDisplayName() {
        return "JUnit AssertThrows to AssertJ exceptionType";
    }

    @Override
    public String getDescription() {
        return "Convert `JUnit#AssertThrows` to `AssertJ#assertThatExceptionOfType` to allow for chained assertions on the thrown exception.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_THROWS_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (ASSERT_THROWS_MATCHER.matches(mi) &&
                    mi.getArguments().size() == 2 &&
                    getCursor().getParentTreeCursor().getValue() instanceof J.Block) {
                    J executable = mi.getArguments().get(1);
                    if (executable instanceof J.Lambda) {
                        executable = ((J.Lambda) executable).withType(THROWING_CALLABLE_TYPE);
                    } else if (executable instanceof J.MemberReference) {
                        executable = ((J.MemberReference) executable).withType(THROWING_CALLABLE_TYPE);
                    } else {
                        executable = null;
                    }

                    if (executable != null) {
                        mi = JavaTemplate
                                .builder("assertThatExceptionOfType(#{any(java.lang.Class)}).isThrownBy(#{any(org.assertj.core.api.ThrowableAssert.ThrowingCallable)})")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3.24"))
                                .staticImports("org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType")
                                .build()
                                .apply(getCursor(), mi.getCoordinates().replace(), mi.getArguments().get(0), executable);
                        maybeAddImport("org.assertj.core.api.AssertionsForClassTypes", "assertThatExceptionOfType", false);
                        maybeRemoveImport("org.junit.jupiter.api.Assertions.assertThrows");
                        maybeRemoveImport("org.junit.jupiter.api.Assertions");

                        doAfterVisit(new LambdaBlockToExpression().getVisitor());
                    }
                }
                return mi;
            }
        });
    }
}
