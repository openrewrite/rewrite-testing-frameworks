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
package org.openrewrite.java.testing.assertj;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Collections;
import java.util.List;

public class JUnitFailToAssertJFail extends AbstractJUnitAssertToAssertThatRecipe {

    private static final String JUNIT = "org.junit.jupiter.api.Assertions";
    private static final String ASSERTJ = "org.assertj.core.api.Assertions";

    @Override
    public String getDisplayName() {
        return "JUnit fail to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Convert JUnit-style `fail()` to AssertJ's `fail()`.";
    }

    public JUnitFailToAssertJFail() {
        super("fail(..)");
    }

    @Override
    protected JUnitAssertionVisitor getJUnitAssertionVisitor(JUnitAssertionConfig config) {
        return new JUnitAssertionVisitor(config) {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = method;
                if (!config.getMethodMatcher().matches(mi)) {
                    return mi;
                }

                List<Expression> args = mi.getArguments();
                if (args.size() == 1) {
                    // fail(), fail(String), fail(Supplier<String>), fail(Throwable)
                    if (args.get(0) instanceof J.Empty) {
                        mi = JavaTemplate.builder(ASSERTJ + ".fail(\"\");")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, ASSERTJ_CORE))
                                .build()
                                .apply(getCursor(), mi.getCoordinates().replace());
                    } else if (args.get(0) instanceof J.Literal ||
                               TypeUtils.isAssignableTo("java.lang.String", args.get(0).getType())) {
                        mi = JavaTemplate.builder(ASSERTJ + ".fail(#{any()});")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, ASSERTJ_CORE))
                                .build()
                                .apply(getCursor(), mi.getCoordinates().replace(), args.get(0));
                    } else {
                        mi = JavaTemplate.builder(ASSERTJ + ".fail(\"\", #{any()});")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, ASSERTJ_CORE))
                                .build()
                                .apply(getCursor(), mi.getCoordinates().replace(), args.get(0));
                    }
                } else {
                    // fail(String, Throwable)
                    String anyArgs = String.join(",", Collections.nCopies(args.size(), "#{any()}"));
                    mi = JavaTemplate.builder(ASSERTJ + ".fail(" + anyArgs + ");")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, ASSERTJ_CORE))
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), args.toArray());
                }

                doAfterVisit(new RemoveUnusedImports().getVisitor());
                doAfterVisit(new UnqualifiedMethodInvocations());
                return mi;
            }

            class UnqualifiedMethodInvocations extends JavaIsoVisitor<ExecutionContext> {
                private final MethodMatcher INTERNAL_FAIL_MATCHER = new MethodMatcher(ASSERTJ + " fail(..)");

                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                    J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                    if (!INTERNAL_FAIL_MATCHER.matches(mi)) {
                        return mi;
                    }

                    maybeAddImport(ASSERTJ, "fail", false);
                    maybeRemoveImport(JUNIT + ".fail");

                    List<Expression> arguments = mi.getArguments();
                    String anyArgs = String.join(",", Collections.nCopies(arguments.size(), "#{any()}"));
                    return JavaTemplate.builder("fail(" + anyArgs + ");")
                            .staticImports(ASSERTJ + ".fail")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, ASSERTJ_CORE))
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), arguments.toArray());
                }
            }
        };
    }
}
