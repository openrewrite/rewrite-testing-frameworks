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
package org.openrewrite.java.testing.mockito;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.MethodMatcher;

public class PowerMockMemberMatcherFieldToJavaReflection extends Recipe {

    // Also matches the calls made through `MemberModifier` and `PowerMockito`, which inherit it
    private static final MethodMatcher FIELD = new MethodMatcher(
            "org.powermock.api.support.membermodification.MemberMatcher field(java.lang.Class, java.lang.String)");

    @Getter
    final String displayName = "Replace PowerMock `MemberMatcher.field()` with Java reflection";

    @Getter
    final String description = "Replace `MemberMatcher.field(Class, String)`, also when called through " +
            "`MemberModifier` or `PowerMockito`, with `Class.getDeclaredField(String)` plus `setAccessible(true)`, " +
            "as it is the same lookup as `Whitebox.getField(Class, String)`. The field is looked up on the class " +
            "declaring it, found through the class argument and its superclasses; when that class cannot be " +
            "determined, the class hierarchy is searched at runtime, as PowerMock does. A call nested in a larger " +
            "expression, such as `field(Type.class, \"name\").set(target, value)`, is replaced by the `Field`, " +
            "declared before the enclosing statement.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PowerMockWhiteboxGetFieldToJavaReflection.GetFieldVisitor(FIELD).withPrecondition();
    }
}
