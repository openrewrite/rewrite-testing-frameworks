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
package org.openrewrite.java.testing.testng;

import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

final class TestNgAsserts {

    private TestNgAsserts() {
    }

    /**
     * A TestNG {@code assertEquals(actual, expected, delta)} overload uses a floating-point delta; distinguish it from
     * the {@code assertEquals(actual, expected, message)} String overload by the third argument's type.
     */
    static boolean isFloatingPointType(Expression expression) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(expression.getType());
        if (fullyQualified != null) {
            String typeName = fullyQualified.getFullyQualifiedName();
            return "java.lang.Double".equals(typeName) || "java.lang.Float".equals(typeName);
        }

        JavaType.Primitive parameterType = TypeUtils.asPrimitive(expression.getType());
        return parameterType == JavaType.Primitive.Double || parameterType == JavaType.Primitive.Float;
    }
}
