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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

public class MigrateMethodOrdererAlphanumeric extends Recipe {

    private static final String METHOD_ORDERER = "org.junit.jupiter.api.MethodOrderer";
    private static final String ALPHANUMERIC = METHOD_ORDERER + ".Alphanumeric";
    private static final JavaType METHOD_NAME = JavaType.ShallowClass.build(METHOD_ORDERER + "$MethodName");

    @Getter
    final String displayName = "Migrate `MethodOrderer.Alphanumeric` to `MethodOrderer.MethodName`";

    @Getter
    final String description = "JUnit 6 removed the `MethodOrderer.Alphanumeric` class. " +
            "This recipe migrates usages to `MethodOrderer.MethodName` which provides similar functionality.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // ChangeType has issues with nested classes, so we do this manually
        return Preconditions.check(new UsesType<>(ALPHANUMERIC, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess fa = super.visitFieldAccess(fieldAccess, ctx);
                // Renaming the import in place keeps it in step with the code, so no import is added or removed.
                // visitIdentifier has already renamed a typed name, so match on the name as written.
                if ("Alphanumeric".equals(fieldAccess.getSimpleName()) &&
                        TypeUtils.isOfClassType(fa.getTarget().getType(), METHOD_ORDERER)) {
                    return fa.withName(fa.getName().withSimpleName("MethodName")).withType(retype(fa.getType()));
                }
                return fa;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier id = super.visitIdentifier(identifier, ctx);
                if ("Alphanumeric".equals(id.getSimpleName()) &&
                    TypeUtils.isOfClassType(id.getType(), ALPHANUMERIC)) {
                    return id.withSimpleName("MethodName").withType(METHOD_NAME);
                }
                return id;
            }

            // A static import of a class is untyped, and J.Import.getTypeName() relies on that to name the class
            private @Nullable JavaType retype(@Nullable JavaType type) {
                return TypeUtils.isOfClassType(type, ALPHANUMERIC) ? METHOD_NAME : type;
            }
        });
    }
}
