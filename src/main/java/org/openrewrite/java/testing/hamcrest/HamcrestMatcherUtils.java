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
package org.openrewrite.java.testing.hamcrest;

import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

final class HamcrestMatcherUtils {
    private HamcrestMatcherUtils() {
    }

    static boolean isLegacyClassMatcher(Expression expression) {
        if (!(expression instanceof J.MethodInvocation)) {
            return false;
        }
        J.MethodInvocation invocation = (J.MethodInvocation) expression;
        JavaType.Method method = invocation.getMethodType();
        if (method == null || method.getParameterTypes().size() != 1 || invocation.getArguments().size() != 1) {
            return true;
        }
        JavaType argument = invocation.getArguments().get(0).getType();
        JavaType parameter = method.getParameterTypes().get(0);
        if (argument == null || argument instanceof JavaType.Unknown || parameter == null ||
            parameter instanceof JavaType.Unknown) {
            return true;
        }
        if (!TypeUtils.isOfClassType(argument, "java.lang.Class") &&
            !TypeUtils.isOfClassType(parameter, "java.lang.Class")) {
            return false;
        }
        // is(Class<T>) returns Matcher<T>; value equality is(T) returns Matcher<T>
        // whose value type matches its parameter, including a Class-valued T.
        if (parameter instanceof JavaType.GenericTypeVariable ||
            !(method.getReturnType() instanceof JavaType.Parameterized) ||
            !TypeUtils.isOfClassType(method.getReturnType(), "org.hamcrest.Matcher")) {
            return true;
        }
        JavaType.Parameterized matcher = (JavaType.Parameterized) method.getReturnType();
        return matcher.getTypeParameters().size() != 1 || !parameter.equals(matcher.getTypeParameters().get(0));
    }
}
