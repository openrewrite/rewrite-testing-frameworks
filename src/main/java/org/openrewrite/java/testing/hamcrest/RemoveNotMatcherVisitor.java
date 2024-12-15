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

import java.security.InvalidParameterException;
import java.util.Objects;

class RemoveNotMatcherVisitor extends JavaIsoVisitor<ExecutionContext> {
    static final MethodMatcher NOT_MATCHER = new MethodMatcher("org.hamcrest.Matchers not(..)");

    public static boolean getLogicalContext(J.MethodInvocation mi, ExecutionContext ctx) throws InvalidParameterException {
        Object msg = ctx.getMessage(mi.toString());
        if (msg == null) {
            return true;
        } else if (msg instanceof Boolean) {
            return (Boolean) msg;
        } else {
            throw new InvalidParameterException();
        }
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
        if (NOT_MATCHER.matches(mi)) {
            boolean logicalContext;
            if (ctx.pollMessage(mi.toString()) != null) {
                logicalContext = ctx.getMessage(mi.toString());
            } else {
                logicalContext = true;
            }

            maybeRemoveImport("org.hamcrest.Matchers.not");

            J.MethodInvocation result;
            if (Objects.requireNonNull(mi.getArguments().get(0).getType()).toString().startsWith("org.hamcrest")) {
                result = mi.getArguments().get(0).withPrefix(mi.getPrefix());
            } else {
                JavaTemplate template = JavaTemplate.builder("equalTo(#{any(java.lang.Object)})")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "hamcrest-2.2"))
                        .staticImports("org.hamcrest.Matchers.equalTo")
                        .build();
                maybeAddImport("org.hamcrest.Matchers", "equalTo");
                result = template.apply(getCursor(), mi.getCoordinates().replace(), mi.getArguments().get(0));
            }

            ctx.putMessage(result.toString(), !logicalContext);

            return result;
        } else {
            if (ctx.pollMessage(mi.toString()) == null) {
                ctx.putMessage(mi.toString(), true);
            }
        }
        return super.visitMethodInvocation(mi, ctx);
    }
}
