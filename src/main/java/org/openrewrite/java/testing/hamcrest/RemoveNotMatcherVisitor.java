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
package org.openrewrite.java.testing.hamcrest;

import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

import java.util.Objects;

class RemoveNotMatcherVisitor extends JavaIsoVisitor<ExecutionContext> {
    static final MethodMatcher NOT_MATCHER = new MethodMatcher("org.hamcrest.*Matchers not(..)");

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
        if (NOT_MATCHER.matches(mi)) {
            maybeRemoveImport("org.hamcrest.Matchers.not");

            if (Objects.requireNonNull(mi.getArguments().get(0).getType()).toString().startsWith("org.hamcrest")) {
                return mi.getArguments().get(0).withPrefix(mi.getPrefix());
            }
            maybeAddImport("org.hamcrest.Matchers", "equalTo");
            return JavaTemplate.builder("equalTo(#{any(java.lang.Object)})")
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "hamcrest-3"))
                    .staticImports("org.hamcrest.Matchers.equalTo")
                    .build()
                    .apply(getCursor(), mi.getCoordinates().replace(), mi.getArguments().get(0));
        }
        return super.visitMethodInvocation(mi, ctx);
    }
}
