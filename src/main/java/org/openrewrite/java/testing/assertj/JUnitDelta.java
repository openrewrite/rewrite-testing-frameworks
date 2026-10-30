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
package org.openrewrite.java.testing.assertj;

import org.jspecify.annotations.Nullable;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

final class JUnitDelta {

    private JUnitDelta() {
    }

    static JavaType.@Nullable Primitive floatingPointDeltaType(J.MethodInvocation mi) {
        // The resolved parameter, not the argument type, tells a delta from a message: JUnit widens an `int` delta
        JavaType.Method methodType = mi.getMethodType();
        if (methodType == null || methodType.getParameterTypes().size() < 3) {
            return null;
        }
        JavaType.Primitive primitive = TypeUtils.asPrimitive(methodType.getParameterTypes().get(2));
        return primitive == JavaType.Primitive.Double || primitive == JavaType.Primitive.Float ? primitive : null;
    }

    static boolean isIntegralType(Expression expression) {
        JavaType.Primitive primitive = TypeUtils.asPrimitive(expression.getType());
        return primitive == JavaType.Primitive.Long || primitive == JavaType.Primitive.Int;
    }

    static boolean isLiteralZero(Expression expression) {
        Expression unwrapped = expression.unwrap();
        Object value = unwrapped instanceof J.Literal ? ((J.Literal) unwrapped).getValue() : null;
        return value instanceof Number && ((Number) value).doubleValue() == 0;
    }

    static String widenedTo(JavaType.Primitive type, Expression argument) {
        // Cast where JUnit widened the argument, so AssertJ picks the `float` or `double` assert and `Offset<T>`
        if (hasType(argument, type)) {
            return "#{any()}";
        }
        return "(" + type.getKeyword() + ") " + (bindsLooserThanCast(argument) ? "(#{any()})" : "#{any()}");
    }

    private static boolean bindsLooserThanCast(Expression expression) {
        return expression instanceof J.Binary || expression instanceof J.Ternary || expression instanceof J.Assignment || expression instanceof J.AssignmentOperation;
    }

    private static boolean hasType(Expression expression, JavaType.Primitive type) {
        if (TypeUtils.asPrimitive(expression.getType()) == type) {
            return true;
        }
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(expression.getType());
        return fq != null && type.getClassName().equals(fq.getFullyQualifiedName());
    }
}
