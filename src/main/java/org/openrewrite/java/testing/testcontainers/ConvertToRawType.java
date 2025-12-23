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
package org.openrewrite.java.testing.testcontainers;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

@Value
@EqualsAndHashCode(callSuper = false)
public class ConvertToRawType extends Recipe {

    @Option(displayName = "Fully qualified type name",
            description = "The fully qualified name of the Java class to convert to its raw type.",
            example = "org.testcontainers.containers.PostgreSQLContainer")
    String fullyQualifiedTypeName;

    @Override
    public String getDisplayName() {
        return "Remove parameterized type arguments from a Java class";
    }

    @Override
    public String getDescription() {
        return "Convert parameterized types of a specified Java class to their raw types.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(fullyQualifiedTypeName, false), new JavaVisitor<ExecutionContext>() {
            @Override
            public @Nullable JavaType visitType(@Nullable JavaType javaType, ExecutionContext ctx) {
                if (javaType instanceof JavaType.Parameterized) {
                    JavaType rawType = ((JavaType.Parameterized) javaType).getType();
                    if (TypeUtils.isAssignableTo(fullyQualifiedTypeName, rawType)) {
                        return rawType;
                    }
                }
                return super.visitType(javaType, ctx);
            }

            @Override
            public J visitParameterizedType(J.ParameterizedType type, ExecutionContext ctx) {
                J.ParameterizedType pt = (J.ParameterizedType) super.visitParameterizedType(type, ctx);
                if (TypeUtils.isAssignableTo(fullyQualifiedTypeName, pt.getType())) {
                    return ((J.Identifier) pt.getClazz()).withPrefix(pt.getPrefix()).withType(pt.getType());
                }
                return pt;
            }
        });
    }
}
