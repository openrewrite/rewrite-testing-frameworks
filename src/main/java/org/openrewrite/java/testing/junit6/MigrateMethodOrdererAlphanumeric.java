/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.testing.junit6;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

public class MigrateMethodOrdererAlphanumeric extends Recipe {

    private static final String METHOD_ORDERER = "org.junit.jupiter.api.MethodOrderer";
    private static final String ALPHANUMERIC = METHOD_ORDERER + ".Alphanumeric";
    private static final String METHOD_NAME = METHOD_ORDERER + ".MethodName";

    @Override
    public String getDisplayName() {
        return "Migrate MethodOrderer.Alphanumeric to MethodOrderer.MethodName";
    }

    @Override
    public String getDescription() {
        return "JUnit 6 removed the `MethodOrderer.Alphanumeric` class. This recipe migrates usages to `MethodOrderer.MethodName` which provides similar functionality.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(ALPHANUMERIC, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess fa = super.visitFieldAccess(fieldAccess, ctx);
                if (TypeUtils.isOfClassType(fa.getType(), ALPHANUMERIC)) {
                    maybeRemoveImport(ALPHANUMERIC);
                    maybeAddImport(METHOD_NAME);
                    return JavaTemplate.builder("MethodName")
                            .contextSensitive()
                            .imports(METHOD_NAME)
                            .javaParser(JavaParser.fromJavaVersion()
                                    .classpathFromResources(ctx, "junit-jupiter-api"))
                            .build()
                            .apply(getCursor(), fa.getCoordinates().replace());
                }
                return fa;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier id = super.visitIdentifier(identifier, ctx);
                if (TypeUtils.isOfClassType(id.getType(), ALPHANUMERIC)) {
                    JavaType.FullyQualified fq = TypeUtils.asFullyQualified(id.getType());
                    if (fq != null && ALPHANUMERIC.equals(fq.getFullyQualifiedName())) {
                        maybeRemoveImport(ALPHANUMERIC);
                        maybeAddImport(METHOD_NAME);
                        return JavaTemplate.builder("MethodName")
                                .contextSensitive()
                                .imports(METHOD_NAME)
                                .javaParser(JavaParser.fromJavaVersion()
                                        .classpathFromResources(ctx, "junit-jupiter-api"))
                                .build()
                                .apply(getCursor(), id.getCoordinates().replace());
                    }
                }
                return id;
            }
        });
    }
}