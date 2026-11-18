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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

import static java.util.Collections.emptyList;

public class JUnitFailToAssertJFail extends Recipe {

    private static final String JUNIT = "org.junit.jupiter.api.Assertions";
    private static final String ASSERTJ = "org.assertj.core.api.Assertions";
    private static final MethodMatcher FAIL_MATCHER = new MethodMatcher(JUNIT + " fail(..)");

    @Getter
    final String displayName = "JUnit `fail` to AssertJ";

    @Getter
    final String description = "Convert JUnit-style `fail()` to AssertJ's `fail()`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(FAIL_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!FAIL_MATCHER.matches(mi)) {
                    return mi;
                }

                List<Expression> args = mi.getArguments();
                String template;
                if (args.size() == 1) {
                    // fail(), fail(String), fail(Supplier<String>), fail(Throwable)
                    Expression arg = args.get(0);
                    if (arg instanceof J.Empty) {
                        template = "fail(\"\");";
                        args = emptyList();
                    } else if (arg instanceof J.Literal || TypeUtils.isAssignableTo("java.lang.String", arg.getType())) {
                        template = "fail(#{any(java.lang.String)});";
                    } else if (TypeUtils.isAssignableTo("java.lang.Throwable", arg.getType())) {
                        template = "fail(\"\", #{any(java.lang.Throwable)});";
                    } else {
                        // A `Supplier<String>` has no AssertJ equivalent; do not silently drop its message
                        return mi;
                    }
                } else {
                    // fail(String, Throwable)
                    template = "fail(#{any(java.lang.String)}, #{any(java.lang.Throwable)});";
                }

                maybeRemoveImport(JUNIT);
                maybeRemoveImport(JUNIT + ".fail");
                maybeAddImport(ASSERTJ, "fail");
                return JavaTemplate.builder(template)
                        .staticImports(ASSERTJ + ".fail")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(), args.toArray());
            }
        });
    }
}
