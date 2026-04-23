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
package org.openrewrite.java.testing.junit5;

import lombok.Getter;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

public class UpdateMockWebServerDispatcher extends Recipe {
    private static final String OLD_DISPATCHER_FQN = "okhttp3.mockwebserver.Dispatcher";
    private static final String OLD_RECORDED_REQUEST_FQN = "okhttp3.mockwebserver.RecordedRequest";
    private static final String OLD_MOCK_RESPONSE_FQN = "okhttp3.mockwebserver.MockResponse";
    private static final String NEW_MOCK_RESPONSE_FQN = "mockwebserver3.MockResponse";
    private static final String NEW_MOCK_RESPONSE_BUILDER_FQN = "mockwebserver3.MockResponse$Builder";
    private static final MethodMatcher DISPATCH_MATCHER = new MethodMatcher(
            OLD_DISPATCHER_FQN + " dispatch(" + OLD_RECORDED_REQUEST_FQN + ")", true);

    @Getter
    final String displayName = "Preserve `MockResponse` return type for `Dispatcher.dispatch()` overrides";

    @Getter
    final String description = "In mockwebserver3 5.x, `Dispatcher.dispatch()` returns `MockResponse`, not `MockResponse.Builder`. " +
            "Pre-pin the return type to `mockwebserver3.MockResponse` and wrap return expressions with `.build()`, so the subsequent blanket `MockResponse` → `Builder` type change leaves `dispatch()` alone.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(OLD_DISPATCHER_FQN, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                if (!DISPATCH_MATCHER.matches(m.getMethodType())) {
                    return m;
                }
                TypeTree rte = m.getReturnTypeExpression();
                if (rte == null) {
                    return m;
                }
                if (!TypeUtils.isOfClassType(rte.getType(), OLD_MOCK_RESPONSE_FQN)) {
                    return m;
                }

                JavaType.Class mockResponseType = JavaType.ShallowClass.build(NEW_MOCK_RESPONSE_FQN);
                JavaType.Method buildMethodType = new JavaType.Method(
                        null,
                        Flag.Public.getBitMask() | Flag.Final.getBitMask(),
                        JavaType.ShallowClass.build(NEW_MOCK_RESPONSE_BUILDER_FQN),
                        "build",
                        mockResponseType,
                        (List<String>) null,
                        null,
                        null,
                        null,
                        null,
                        null
                );

                // Wrap return expressions with .build(). The outer check already guarantees
                // this is a dispatch override declared to return old MockResponse — every return
                // must become a built MockResponse in v5. Expression types may already be partially
                // retyped to Builder by prior ChangeMethodInvocationReturnType sub-recipes, so we
                // don't gate on the expression's current type.
                m = (J.MethodDeclaration) new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Return visitReturn(J.Return aReturn, ExecutionContext c) {
                        J.Return r = super.visitReturn(aReturn, c);
                        Expression expr = r.getExpression();
                        if (expr == null) {
                            return r;
                        }
                        J.MethodInvocation wrapped = JavaTemplate
                                .builder("#{any(" + OLD_MOCK_RESPONSE_FQN + ")}.build()")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(c, "mockwebserver-4.10"))
                                .build()
                                .apply(new Cursor(getCursor(), expr), expr.getCoordinates().replace(), expr);
                        wrapped = wrapped.withMethodType(buildMethodType)
                                .withName(wrapped.getName().withType(buildMethodType));
                        return r.withExpression(wrapped);
                    }
                }.visitNonNull(m, ctx, getCursor().getParentTreeCursor());

                // Pre-pin the return type to mockwebserver3.MockResponse so the blanket
                // ChangeType(MockResponse -> Builder) that runs next won't match it.
                m = JavaTemplate.builder("MockResponse")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockwebserver3"))
                        .imports(NEW_MOCK_RESPONSE_FQN)
                        .build()
                        .apply(new Cursor(getCursor().getParentOrThrow(), m),
                                ((Expression) m.getReturnTypeExpression()).getCoordinates().replace());
                JavaType.Method methodType = m.getMethodType();
                if (methodType != null) {
                    JavaType.Method updatedMethodType = methodType.withReturnType(mockResponseType);
                    m = m.withMethodType(updatedMethodType)
                            .withName(m.getName().withType(updatedMethodType));
                }
                maybeAddImport(NEW_MOCK_RESPONSE_FQN);
                return m;
            }

        });
    }
}
