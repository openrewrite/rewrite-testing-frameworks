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
package org.openrewrite.java.testing.gatling;

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
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

public class MigrateGatlingExitBlockOnFail extends Recipe {

    private static final String CORE_DSL = "io.gatling.javaapi.core.CoreDsl";
    private static final String CHAIN = "#{any(io.gatling.javaapi.core.ChainBuilder)}";

    // Both the static `CoreDsl` method and the instance method of `Errors`, which structure builders implement
    private static final MethodMatcher EXIT_BLOCK_ON_FAIL =
            new MethodMatcher("io.gatling.javaapi.core..* exitBlockOnFail(io.gatling.javaapi.core.ChainBuilder)", true);

    @Getter
    final String displayName = "Migrate Gatling `exitBlockOnFail(chain)` to `exitBlockOnFail().on(chain)`";

    @Getter
    final String description = "Gatling 3.10 changed the Java SDK's `exitBlockOnFail` to take the chain in a " +
            "separate `on` call, so `exitBlockOnFail(chain)` becomes `exitBlockOnFail().on(chain)`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(EXIT_BLOCK_ON_FAIL), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J j = super.visitMethodInvocation(method, ctx);
                if (!(j instanceof J.MethodInvocation) || !EXIT_BLOCK_ON_FAIL.matches((J.MethodInvocation) j)) {
                    return j;
                }
                J.MethodInvocation m = (J.MethodInvocation) j;
                JavaType.Method methodType = m.getMethodType();
                if (methodType == null) {
                    return m;
                }

                if (methodType.hasFlags(Flag.Static)) {
                    if (m.getSelect() == null) {
                        return template("exitBlockOnFail().on(" + CHAIN + ")", ctx)
                                .apply(getCursor(), m.getCoordinates().replace(), m.getArguments().get(0));
                    }
                    return template("CoreDsl.exitBlockOnFail().on(" + CHAIN + ")", ctx)
                            .apply(getCursor(), m.getCoordinates().replace(), m.getArguments().get(0));
                }

                JavaType.FullyQualified receiver = m.getSelect() == null ? null : TypeUtils.asFullyQualified(m.getSelect().getType());
                if (receiver == null) {
                    return m;
                }
                return template("#{any(" + receiver.getFullyQualifiedName() + ")}.exitBlockOnFail().on(" + CHAIN + ")", ctx)
                        .apply(getCursor(), m.getCoordinates().replace(), m.getSelect(), m.getArguments().get(0));
            }
        });
    }

    private static JavaTemplate template(String code, ExecutionContext ctx) {
        return JavaTemplate.builder(code)
                .imports(CORE_DSL)
                .staticImports(CORE_DSL + ".exitBlockOnFail")
                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "gatling-core-3.16", "gatling-core-java-3.16"))
                .build();
    }
}
