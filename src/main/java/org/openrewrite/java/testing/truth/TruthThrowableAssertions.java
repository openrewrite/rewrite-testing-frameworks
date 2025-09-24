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
package org.openrewrite.java.testing.truth;

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

public class TruthThrowableAssertions extends Recipe {

    private static final MethodMatcher HAS_MESSAGE_THAT = new MethodMatcher("com.google.common.truth.ThrowableSubject hasMessageThat()");
    private static final MethodMatcher HAS_CAUSE_THAT = new MethodMatcher("com.google.common.truth.ThrowableSubject hasCauseThat()");
    private static final MethodMatcher CONTAINS = new MethodMatcher("com.google.common.truth.StringSubject contains(..)");
    private static final MethodMatcher IS_EQUAL_TO = new MethodMatcher("com.google.common.truth.StringSubject isEqualTo(..)");
    private static final MethodMatcher IS_INSTANCE_OF = new MethodMatcher("com.google.common.truth.ThrowableSubject isInstanceOf(..)");

    @Override
    public String getDisplayName() {
        return "Convert Truth Throwable assertions to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Converts Google Truth's Throwable assertion chains like `hasMessageThat().contains()` to AssertJ equivalents.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(new UsesMethod<>(HAS_MESSAGE_THAT), new UsesMethod<>(HAS_CAUSE_THAT)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                        // Handle hasMessageThat().contains(text)
                        if (CONTAINS.matches(mi) && mi.getSelect() instanceof J.MethodInvocation) {
                            J.MethodInvocation select = (J.MethodInvocation) mi.getSelect();
                            if (HAS_MESSAGE_THAT.matches(select)) {
                                return JavaTemplate.builder("#{any(org.assertj.core.api.AbstractThrowableAssert)}.hasMessageContaining(#{any()})")
                                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                                        .build()
                                        .apply(getCursor(), mi.getCoordinates().replace(),
                                                select.getSelect(), mi.getArguments().get(0));
                            }
                        }

                        // Handle hasMessageThat().isEqualTo(text)
                        if (IS_EQUAL_TO.matches(mi) && mi.getSelect() instanceof J.MethodInvocation) {
                            J.MethodInvocation select = (J.MethodInvocation) mi.getSelect();
                            if (HAS_MESSAGE_THAT.matches(select)) {
                                return JavaTemplate.builder("#{any(org.assertj.core.api.AbstractThrowableAssert)}.hasMessage(#{any()})")
                                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                                        .build()
                                        .apply(getCursor(), mi.getCoordinates().replace(),
                                                select.getSelect(), mi.getArguments().get(0));
                            }
                        }

                        // Handle hasCauseThat().isInstanceOf(type)
                        if (IS_INSTANCE_OF.matches(mi) && mi.getSelect() instanceof J.MethodInvocation) {
                            J.MethodInvocation select = (J.MethodInvocation) mi.getSelect();
                            if (HAS_CAUSE_THAT.matches(select)) {
                                return JavaTemplate.builder("#{any(org.assertj.core.api.AbstractThrowableAssert)}.hasCauseInstanceOf(#{any()})")
                                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                                        .build()
                                        .apply(getCursor(), mi.getCoordinates().replace(),
                                                select.getSelect(), mi.getArguments().get(0));
                            }
                        }

                        return mi;
                    }
                });
    }
}
