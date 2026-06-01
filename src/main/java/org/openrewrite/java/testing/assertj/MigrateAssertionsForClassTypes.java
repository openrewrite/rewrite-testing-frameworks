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
package org.openrewrite.java.testing.assertj;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
import java.util.List;

public class MigrateAssertionsForClassTypes extends Recipe {

    private static final String ASSERTIONS_FOR_CLASS_TYPES = "org.assertj.core.api.AssertionsForClassTypes";

    private static final MethodMatcher ASSERT_THAT = new MethodMatcher(ASSERTIONS_FOR_CLASS_TYPES + " assertThat(..)");

    /**
     * Argument types for which the unified {@code Assertions} entry point declares a more specific
     * {@code assertThat} overload than the generic {@code <T> ObjectAssert<T> assertThat(T)} offered by
     * {@code AssertionsForClassTypes}. For these the call must migrate to {@code assertThatObject(..)} to keep
     * returning an {@code ObjectAssert}; otherwise it would re-bind to e.g. {@code IterableAssert} and stop
     * compiling whenever an {@code ObjectAssert}-only assertion is chained.
     */
    private static final List<String> COLLISION_TYPES = Arrays.asList(
            "java.lang.Iterable",
            "java.util.Iterator",
            "java.util.Map",
            "java.nio.file.Path",
            "java.util.stream.Stream",
            "java.util.stream.IntStream",
            "java.util.stream.LongStream",
            "java.util.stream.DoubleStream",
            "java.util.function.Predicate",
            "java.util.function.IntPredicate",
            "java.util.function.LongPredicate",
            "java.util.function.DoublePredicate",
            "java.lang.Comparable",
            "org.assertj.core.api.AssertProvider",
            "org.assertj.core.api.AssertDelegateTarget"
    );

    @Override
    public String getDisplayName() {
        return "Use `Assertions.assertThatObject` for ambiguous `AssertionsForClassTypes.assertThat` calls";
    }

    @Override
    public String getDescription() {
        return "The deprecated `AssertionsForClassTypes.assertThat(T)` always returns an `ObjectAssert`, while the " +
               "unified `Assertions.assertThat` additionally offers more specific overloads (e.g. for `Iterable`, " +
               "`Map`, `Predicate`). For arguments matching those overloads, rename `assertThat` to `assertThatObject` " +
               "so that migrating to `Assertions` keeps returning an `ObjectAssert` and the code keeps compiling.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(ASSERTIONS_FOR_CLASS_TYPES, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!ASSERT_THAT.matches(mi) || mi.getMethodType() == null || mi.getArguments().size() != 1) {
                    return mi;
                }
                // Only the generic `<T> ObjectAssert<T> assertThat(T)` overload returns ObjectAssert; the typed
                // overloads (String, primitives, arrays, ...) return their own assertion types and need no change.
                if (!TypeUtils.isOfClassType(mi.getMethodType().getReturnType(), "org.assertj.core.api.ObjectAssert")) {
                    return mi;
                }
                Expression argument = mi.getArguments().get(0);
                if (!isCollisionType(argument.getType())) {
                    return mi;
                }
                JavaType.Method newType = mi.getMethodType().withName("assertThatObject");
                return mi
                        .withName(mi.getName().withSimpleName("assertThatObject").withType(newType))
                        .withMethodType(newType);
            }

            private boolean isCollisionType(@org.jspecify.annotations.Nullable JavaType type) {
                for (String collisionType : COLLISION_TYPES) {
                    if (TypeUtils.isAssignableTo(collisionType, type)) {
                        return true;
                    }
                }
                return false;
            }
        });
    }
}
