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
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

public class MigrateContentTypeHeaderCharset extends Recipe {

    private static final String CONTENT_TYPE_HEADER = "com.github.tomakehurst.wiremock.http.ContentTypeHeader";
    private static final String STANDARD_CHARSETS = "java.nio.charset.StandardCharsets";

    private static final MethodMatcher CHARSET = new MethodMatcher(CONTENT_TYPE_HEADER + " charset()");

    @Getter
    final String displayName = "Preserve the UTF-8 default of `ContentTypeHeader.charset()`";

    @Getter
    final String description = "WireMock 3's `ContentTypeHeader.charset()` fell back to `UTF_8` when the header " +
            "was missing or carried no charset, while 4.x returns an `Optional` that is empty in those cases. " +
            "Append `orElse(StandardCharsets.UTF_8)` so the value stays what it was; reaching for `get()` or " +
            "`orElse(null)` instead, which is the easy way to make the compiler happy, would turn a missing " +
            "charset into an exception or a null.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(CHARSET),
                new JavaVisitor<ExecutionContext>() {

                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                        if (!CHARSET.matches(m) || m.getSelect() == null || alreadyOptional(m)) {
                            return m;
                        }
                        maybeAddImport(STANDARD_CHARSETS);
                        return JavaTemplate
                                .builder("#{any(" + CONTENT_TYPE_HEADER + ")}.charset().orElse(StandardCharsets.UTF_8)")
                                .imports(STANDARD_CHARSETS)
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "wiremock-core-4"))
                                .build()
                                .apply(getCursor(), m.getCoordinates().replace(), m.getSelect());
                    }

                    /**
                     * Both the calls this recipe has already rewritten and code written against 4.x in the first
                     * place hand back an `Optional`, and neither wants wrapping again.
                     */
                    private boolean alreadyOptional(J.MethodInvocation method) {
                        JavaType.Method methodType = method.getMethodType();
                        return methodType != null &&
                                TypeUtils.isAssignableTo("java.util.Optional", methodType.getReturnType());
                    }
                });
    }
}
