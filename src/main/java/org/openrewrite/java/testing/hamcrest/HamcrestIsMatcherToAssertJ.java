/*
 * Copyright 2024 the original author or authors.
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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

public class HamcrestIsMatcherToAssertJ extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate Hamcrest `is(Object)` to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Migrate Hamcrest `is(Object)` to AssertJ `Assertions.assertThat(..)`.";
    }

    static final MethodMatcher IS_OBJECT_MATCHER = new MethodMatcher("org.hamcrest.*Matchers is(..)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(IS_OBJECT_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInvocation, ExecutionContext ctx) {

                // Switch between one or the other depending on whether actual argument is an array or not
                List<Expression> arguments = methodInvocation.getArguments();
                String replacement = 2 <= arguments.size() &&
                                     TypeUtils.asArray(arguments.get(arguments.size() - 2).getType()) != null ?
                        "containsExactly" : "isEqualTo";
                doAfterVisit(new HamcrestMatcherToAssertJ("is", replacement, null).getVisitor());

                return super.visitMethodInvocation(methodInvocation, ctx);
            }
        });
    }
}
