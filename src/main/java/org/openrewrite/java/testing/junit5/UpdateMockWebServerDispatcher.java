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
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Collections;
import java.util.List;

public class UpdateMockWebServerDispatcher extends Recipe {
    private static final String DISPATCHER_FQN = "mockwebserver3.Dispatcher";
    private static final String RECORDED_REQUEST_FQN = "mockwebserver3.RecordedRequest";
    private static final String MOCK_RESPONSE_FQN = "mockwebserver3.MockResponse";
    private static final String MOCK_RESPONSE_BUILDER_FQN = "mockwebserver3.MockResponse$Builder";

    @Getter
    final String displayName = "Restore `MockResponse` return type for `Dispatcher.dispatch()` overrides";

    @Getter
    final String description = "In mockwebserver3 5.x, `Dispatcher.dispatch()` returns `MockResponse`, not `MockResponse.Builder`. " +
            "Undo the blanket `MockResponse` → `MockResponse.Builder` rename for `dispatch()` overrides, and wrap return expressions with `.build()`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(DISPATCHER_FQN, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                if (!isDispatchOverride(m)) {
                    return m;
                }
                TypeTree rte = m.getReturnTypeExpression();
                if (rte == null) {
                    return m;
                }
                if (!TypeUtils.isOfClassType(rte.getType(), MOCK_RESPONSE_BUILDER_FQN.replace('$', '.'))) {
                    return m;
                }

                JavaType.Class mockResponseType = JavaType.ShallowClass.build(MOCK_RESPONSE_FQN);
                JavaType.Method buildMethodType = new JavaType.Method(
                        null,
                        Flag.Public.getBitMask() | Flag.Final.getBitMask(),
                        JavaType.ShallowClass.build(MOCK_RESPONSE_BUILDER_FQN),
                        "build",
                        mockResponseType,
                        (List<String>) null,
                        null,
                        null,
                        null,
                        null,
                        null
                );

                // Wrap return expressions with .build()
                m = (J.MethodDeclaration) new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Return visitReturn(J.Return aReturn, ExecutionContext c) {
                        J.Return r = super.visitReturn(aReturn, c);
                        Expression expr = r.getExpression();
                        if (expr == null || !TypeUtils.isOfClassType(expr.getType(), MOCK_RESPONSE_BUILDER_FQN.replace('$', '.'))) {
                            return r;
                        }
                        J.MethodInvocation wrapped = JavaTemplate
                                .builder("#{any(" + MOCK_RESPONSE_BUILDER_FQN + ")}.build()")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(c, "mockwebserver3"))
                                .imports(MOCK_RESPONSE_FQN, MOCK_RESPONSE_FQN + ".Builder")
                                .build()
                                .apply(new Cursor(getCursor(), expr), expr.getCoordinates().replace(), expr);
                        wrapped = wrapped.withMethodType(buildMethodType)
                                .withName(wrapped.getName().withType(buildMethodType));
                        return r.withExpression(wrapped);
                    }
                }.visitNonNull(m, ctx, getCursor().getParentTreeCursor());

                // Change return type expression to MockResponse
                J.Identifier newReturnType = new J.Identifier(
                        Tree.randomId(),
                        rte.getPrefix(),
                        rte.getMarkers(),
                        Collections.emptyList(),
                        "MockResponse",
                        mockResponseType,
                        null
                );
                m = m.withReturnTypeExpression(newReturnType);
                JavaType.Method methodType = m.getMethodType();
                if (methodType != null) {
                    JavaType.Method updatedMethodType = methodType.withReturnType(mockResponseType);
                    m = m.withMethodType(updatedMethodType)
                            .withName(m.getName().withType(updatedMethodType));
                }
                maybeAddImport(MOCK_RESPONSE_FQN);
                return m;
            }

            private boolean isDispatchOverride(J.MethodDeclaration m) {
                if (!"dispatch".equals(m.getSimpleName())) {
                    return false;
                }
                JavaType.Method mt = m.getMethodType();
                if (mt == null || mt.getParameterTypes().size() != 1) {
                    return false;
                }
                JavaType.FullyQualified p = TypeUtils.asFullyQualified(mt.getParameterTypes().get(0));
                return p != null && RECORDED_REQUEST_FQN.equals(p.getFullyQualifiedName());
            }
        });
    }
}
