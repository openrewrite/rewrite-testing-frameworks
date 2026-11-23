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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.ShortenFullyQualifiedTypeReferences;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.openrewrite.java.testing.hamcrest.HamcrestRemainingMatchersToAssertJ.typeName;

/**
 * Hamcrest's {@code assertThat(actual, matcher)} has one overload, where AssertJ's {@code assertThat(actual)} has one for
 * each kind of value. An actual value whose type comes from the overload it is passed to, which is {@code null} and the
 * result of a generic method such as {@code <T> T get(int)} called without a type argument, matches several of
 * AssertJ's, so the assertion it is migrated to doesn't compile, and so does a {@code Comparable} that is a
 * {@code Map} or an {@code Iterable} too. Cast it to the type it has in the Hamcrest assertion
 * first, or to {@code Object} when that type has no name, such as {@code Comparable<?>}.
 */
public class CastAmbiguousActualValue extends Recipe {

    private static final MethodMatcher ASSERT_THAT = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(..)");

    @Getter
    final String displayName = "Cast an actual value of a Hamcrest assertion whose type its overload determines";

    @Getter
    final String description = "Cast `null`, and the result of a generic method called without a type argument, that " +
            "Hamcrest's `assertThat(actual, matcher)` asserts on to the type it has there, and a `Comparable` that is a " +
            "`Map` or an `Iterable` too to that, so that the AssertJ assertion it is migrated to resolves to one " +
            "overload of `assertThat`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_THAT), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                List<Expression> args = mi.getArguments();
                if (!ASSERT_THAT.matches(mi) || args.size() < 2) {
                    return mi;
                }
                int index = args.size() - 2;
                Expression actual = args.get(index);
                String type;
                if (actual instanceof J.Literal && ((J.Literal) actual).getType() == JavaType.Primitive.Null) {
                    type = "Object";
                } else if (actual instanceof J.MethodInvocation && returnsItsOwnTypeVariable((J.MethodInvocation) actual)) {
                    String name = typeName(actual.getType());
                    // a wildcard type such as `Comparable<?>` would choose an overload of its own
                    type = name == null || name.contains("?") ? "Object" : name;
                } else {
                    type = collectionTypeOfComparable(actual.getType());
                    if (type == null) {
                        return mi;
                    }
                }
                Expression cast = JavaTemplate.apply("(" + type + ") #{any()}", new Cursor(getCursor(), actual),
                        actual.getCoordinates().replace(), actual);
                doAfterVisit(ShortenFullyQualifiedTypeReferences.modifyOnly(cast));
                return mi.withArguments(ListUtils.map(args, (i, arg) -> i == index ? cast : arg));
            }

            /**
             * A {@code Comparable} that is a {@code Map} or an {@code Iterable} too, such as json-lib's
             * {@code JSONObject}, matches AssertJ's {@code assertThat(Comparable)} and {@code assertThat(Map)}, so it is
             * cast to the {@code Map} or {@code Iterable} it is, whose assertions the matchers of a collection become.
             */
            private @Nullable String collectionTypeOfComparable(@Nullable JavaType type) {
                JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
                if (fq == null || !TypeUtils.isAssignableTo("java.lang.Comparable", type) ||
                    TypeUtils.isAssignableTo("java.nio.file.Path", type)) {
                    return null;
                }
                for (String collection : new String[]{"java.util.Map", "java.lang.Iterable"}) {
                    if (TypeUtils.isAssignableTo(collection, type)) {
                        String name = nearestSupertype(fq, collection);
                        return name == null ? collection : name;
                    }
                }
                return null;
            }

            /**
             * The nearest supertype that is the collection without being {@code Comparable}, as its type arguments
             * name it, such as {@code HashMap<String, Object>}, where the collection's own declaration only has its
             * type variables.
             */
            private @Nullable String nearestSupertype(JavaType.FullyQualified type, String collection) {
                Deque<JavaType.FullyQualified> queue = new ArrayDeque<>();
                Set<String> visited = new HashSet<>();
                queue.add(type);
                while (!queue.isEmpty()) {
                    JavaType.FullyQualified next = queue.poll();
                    if (!visited.add(next.getFullyQualifiedName())) {
                        continue;
                    }
                    if (next != type && TypeUtils.isAssignableTo(collection, next) &&
                        !TypeUtils.isAssignableTo("java.lang.Comparable", next)) {
                        String name = typeName(next);
                        if (name != null) {
                            return name;
                        }
                    }
                    if (next.getSupertype() != null) {
                        queue.add(next.getSupertype());
                    }
                    queue.addAll(next.getInterfaces());
                }
                return null;
            }

            /**
             * Whether the method returns a type variable it declares itself, and is called without a type argument, so
             * that the overload it is passed to determines its type.
             */
            private boolean returnsItsOwnTypeVariable(J.MethodInvocation invocation) {
                JavaType.Method type = invocation.getMethodType();
                if (invocation.getTypeParameters() != null || type == null) {
                    return false;
                }
                // the type of the invocation is resolved, so it is the method as its type declares it that has the
                // type variables
                for (JavaType.Method declared : type.getDeclaringType().getMethods()) {
                    if (declared.getName().equals(type.getName()) &&
                        declared.getParameterTypes().size() == type.getParameterTypes().size() &&
                        declared.getReturnType() instanceof JavaType.GenericTypeVariable &&
                        declared.getDeclaredFormalTypeNames().contains(
                                ((JavaType.GenericTypeVariable) declared.getReturnType()).getName())) {
                        return true;
                    }
                }
                return false;
            }
        });
    }
}
