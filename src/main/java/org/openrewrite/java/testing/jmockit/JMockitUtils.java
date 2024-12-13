/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.jmockit;

import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
import java.util.Optional;

import static java.util.Optional.empty;

class JMockitUtils {

    static final String MOCKITO_ALL_IMPORT = "org.mockito.Mockito.*";

    public static JavaParser.Builder<?, ?> getJavaParser(ExecutionContext ctx) {
        return JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12");
    }

    static Optional<JMockitBlockType> getJMockitBlock(Statement s) {
        if (!(s instanceof J.NewClass)) {
            return empty();
        }

        J.NewClass nc = (J.NewClass) s;
        if (nc.getBody() == null || nc.getClazz() == null) {
            return empty();
        }

        JavaType type = nc.getClazz().getType();
        if (type == null) {
            return empty();
        }

        return Arrays.stream(JMockitBlockType.values())
                .filter(supportedType -> TypeUtils.isOfClassType(type, supportedType.getFqn()))
                .findFirst();
    }
}
