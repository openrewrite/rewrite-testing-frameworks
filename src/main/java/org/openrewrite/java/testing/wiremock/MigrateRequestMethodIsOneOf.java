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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.ArrayList;
import java.util.List;

public class MigrateRequestMethodIsOneOf extends Recipe {

    private static final String REQUEST_METHOD = "com.github.tomakehurst.wiremock.http.RequestMethod";

    private static final MethodMatcher IS_ONE_OF = new MethodMatcher(REQUEST_METHOD + " isOneOf(..)");

    /**
     * `isOneOf` is an instance method returning `boolean` on the WireMock 3 on the classpath, so the static
     * pattern building form the template calls has to be declared for it to parse and type check.
     */
    //language=java
    private static final String[] STUBS = {
            "package com.github.tomakehurst.wiremock.matching;\n" +
                    "public abstract class MatchResult {\n" +
                    "  public abstract boolean isExactMatch();\n" +
                    "}\n",
            "package com.github.tomakehurst.wiremock.http;\n" +
                    "import com.github.tomakehurst.wiremock.matching.MatchResult;\n" +
                    "public class RequestMethod {\n" +
                    "  public static native RequestMethod isOneOf(RequestMethod... methods);\n" +
                    "  public native MatchResult match(RequestMethod value);\n" +
                    "}\n"};

    @Getter
    final String displayName = "Migrate `RequestMethod.isOneOf` to the matcher it became";

    @Getter
    final String description = "WireMock 3's `isOneOf` was an instance method answering whether the method was " +
            "one of those given. In 4.x it is static and builds a matcher instead, so `method.isOneOf(GET, POST)` " +
            "quietly stops being a boolean. Rewrite it as " +
            "`RequestMethod.isOneOf(GET, POST).match(method).isExactMatch()`, which asks the same question of the " +
            "new API.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(IS_ONE_OF),
                new JavaVisitor<ExecutionContext>() {

                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                        if (!IS_ONE_OF.matches(m) || m.getSelect() == null || !answersABoolean(m)) {
                            return m;
                        }

                        // The method under test moves from the receiver into the matcher's argument
                        StringBuilder code = new StringBuilder("RequestMethod.isOneOf(");
                        List<Object> parameters = new ArrayList<>();
                        for (int i = 0; i < m.getArguments().size(); i++) {
                            Expression argument = m.getArguments().get(i);
                            if (argument instanceof J.Empty) {
                                continue;
                            }
                            if (!parameters.isEmpty()) {
                                code.append(", ");
                            }
                            code.append("#{any()}");
                            parameters.add(argument);
                        }
                        code.append(").match(#{any(").append(REQUEST_METHOD).append(")}).isExactMatch()");
                        parameters.add(m.getSelect());

                        maybeAddImport(REQUEST_METHOD);
                        return JavaTemplate.builder(code.toString())
                                .imports(REQUEST_METHOD)
                                .javaParser(JavaParser.fromJavaVersion().dependsOn(STUBS))
                                .build()
                                .apply(getCursor(), m.getCoordinates().replace(), parameters.toArray());
                    }

                    /**
                     * Only WireMock 3's instance method hands back a `boolean`; the static one this recipe
                     * generates returns a pattern, so checking the return type keeps it from being rewritten twice
                     * and leaves code already written against 4.x alone.
                     */
                    private boolean answersABoolean(J.MethodInvocation method) {
                        JavaType.Method methodType = method.getMethodType();
                        return methodType != null &&
                                JavaType.Primitive.Boolean == methodType.getReturnType();
                    }
                });
    }
}
