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
package org.openrewrite.java.testing.wiremock;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

import static java.util.Arrays.asList;

public class RemoveDuplicateContentTypeHeader extends WiremockThreeOnlyRecipe {

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String RESPONSE_DEFINITION_BUILDER =
            "com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder";
    private static final String HTTP_HEADER = "com.github.tomakehurst.wiremock.http.HttpHeader";

    private static final MethodMatcher WITH_HEADER =
            new MethodMatcher(RESPONSE_DEFINITION_BUILDER + " withHeader(..)");
    private static final MethodMatcher HTTP_HEADER_FACTORY =
            new MethodMatcher(HTTP_HEADER + " httpHeader(..)");
    private static final MethodMatcher HTTP_HEADER_CONSTRUCTOR =
            new MethodMatcher(HTTP_HEADER + " <constructor>(..)");

    @Getter
    final String displayName = "Keep a single `Content-Type` response header on WireMock stubs";

    @Getter
    final String description = "WireMock 3 ran on Jetty 11, which stripped every `Content-Type` response header " +
            "but the last, so a stub configured with several of them served only one. WireMock 4 returns all of " +
            "them, and some clients reject a response carrying more than one. Keep only the last value, which is " +
            "the one WireMock 3 actually sent. This is a 3 to 4 migration step: run against a project already on " +
            "4.x that deliberately serves more than one `Content-Type`, it would drop values that are currently " +
            "reaching the client.";

    @Override
    TreeVisitor<?, ExecutionContext> collapseDuplicates() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(WITH_HEADER),
                        new UsesMethod<>(HTTP_HEADER_FACTORY),
                        new UsesMethod<>(HTTP_HEADER_CONSTRUCTOR)),
                new JavaIsoVisitor<ExecutionContext>() {

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                        if (!WITH_HEADER.matches(m) && !HTTP_HEADER_FACTORY.matches(m)) {
                            return m;
                        }
                        List<Expression> kept = keepLastValue(m.getArguments());
                        return kept == null ? m : m.withArguments(kept);
                    }

                    @Override
                    public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        J.NewClass n = super.visitNewClass(newClass, ctx);
                        if (!HTTP_HEADER_CONSTRUCTOR.matches(n)) {
                            return n;
                        }
                        List<Expression> kept = keepLastValue(n.getArguments());
                        return kept == null ? n : n.withArguments(kept);
                    }

                    /**
                     * @return the header name followed by only the last value, or {@code null} when this is not a
                     * `Content-Type` carrying more than one value.
                     */
                    private @Nullable List<Expression> keepLastValue(List<Expression> arguments) {
                        // The name plus at least two values; a single `String...` or `Collection` argument
                        // holding the values cannot be split apart here
                        if (arguments.size() < 3 || !isContentType(arguments.get(0))) {
                            return null;
                        }
                        for (int i = 1; i < arguments.size(); i++) {
                            if (!TypeUtils.isString(arguments.get(i).getType())) {
                                return null;
                            }
                        }
                        return asList(arguments.get(0), arguments.get(arguments.size() - 1));
                    }

                    /**
                     * Header names are case insensitive, and WireMock treats them so.
                     */
                    private boolean isContentType(Expression name) {
                        if (!(name instanceof J.Literal)) {
                            return false;
                        }
                        Object value = ((J.Literal) name).getValue();
                        return value instanceof String && CONTENT_TYPE.equalsIgnoreCase((String) value);
                    }
                });
    }
}
