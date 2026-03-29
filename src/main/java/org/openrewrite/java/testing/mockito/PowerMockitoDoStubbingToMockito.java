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
import org.jspecify.annotations.Nullable;
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
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.openrewrite.Tree.randomId;

public class PowerMockitoDoStubbingToMockito extends Recipe {

    private static final MethodMatcher STUBBER_WHEN_MATCHER =
            new MethodMatcher("org.powermock.api.mockito.expectation.PowerMockitoStubber when(..)");

    @Getter
    final String displayName = "Replace PowerMockito `doX().when(instance, \"method\")` with Mockito-compatible stubbing";

    @Getter
    final String description = "Replaces PowerMockito's private method stubbing pattern " +
            "`doNothing().when(instance, \"methodName\", args...)` with the standard Mockito pattern " +
            "`doNothing().when(instance).methodName(args...)`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>("org.powermock.api.mockito.PowerMockito", false),
                new JavaIsoVisitor<ExecutionContext>() {

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                        if (!STUBBER_WHEN_MATCHER.matches(mi)) {
                            return mi;
                        }

                        List<Expression> args = mi.getArguments();
                        if (args.size() < 2) {
                            return mi;
                        }

                        // Second argument must be a String literal (the method name to stub)
                        if (!(args.get(1) instanceof J.Literal) || !(((J.Literal) args.get(1)).getValue() instanceof String)) {
                            return mi;
                        }

                        // Skip static method variant where first arg is a Class literal
                        Expression firstArg = args.get(0);
                        if (firstArg instanceof J.FieldAccess && "class".equals(((J.FieldAccess) firstArg).getSimpleName())) {
                            return mi;
                        }

                        String targetMethodName = (String) ((J.Literal) args.get(1)).getValue();
                        List<Expression> extraArgs = new ArrayList<>(args.subList(2, args.size()));

                        // Rewrite: doX().when(instance, "method", args...) → doX().when(instance).method(args...)

                        // 1. Create when(instance) - strip method name and extra args, keep just the instance
                        // Update the when() method type: PowerMockitoStubber.when(T,String,Object...) returns void,
                        // but Mockito's Stubber.when(T) returns T — we need the return type set to the instance type
                        // so the chained method call resolves correctly.
                        JavaType instanceType = firstArg.getType();
                        JavaType.Method originalWhenType = mi.getMethodType();
                        JavaType.Method updatedWhenType = null;
                        if (originalWhenType != null && instanceType != null) {
                            updatedWhenType = originalWhenType
                                    .withReturnType(instanceType)
                                    .withParameterTypes(Collections.singletonList(instanceType))
                                    .withParameterNames(Collections.singletonList("mock"));
                        }
                        J.MethodInvocation whenWithInstance = mi
                                .withPrefix(Space.EMPTY)
                                .withArguments(Collections.singletonList(firstArg.withPrefix(Space.EMPTY)))
                                .withMethodType(updatedWhenType)
                                .withName(mi.getName().withType(updatedWhenType));

                        // 2. Build .method(args...) chained on when(instance)
                        List<Expression> newArgs;
                        if (extraArgs.isEmpty()) {
                            newArgs = Collections.singletonList(
                                    new J.Empty(randomId(), Space.EMPTY, Markers.EMPTY));
                        } else {
                            newArgs = new ArrayList<>();
                            for (int i = 0; i < extraArgs.size(); i++) {
                                newArgs.add(i == 0 ? extraArgs.get(i).withPrefix(Space.EMPTY) : extraArgs.get(i));
                            }
                        }

                        JavaType.Method resolvedMethodType = resolveTargetMethod(
                                firstArg.getType(), targetMethodName, extraArgs.size());

                        J.Identifier newName = mi.getName()
                                .withSimpleName(targetMethodName)
                                .withType(resolvedMethodType);

                        return mi
                                .withSelect(whenWithInstance)
                                .withName(newName)
                                .withMethodType(resolvedMethodType)
                                .withArguments(newArgs);
                    }

                    /**
                     * Resolve the target method from the instance type and the method name.
                     * Returns null if the method cannot be unambiguously resolved.
                     */
                    private JavaType.@Nullable Method resolveTargetMethod(
                            @Nullable JavaType targetType, String methodName, int expectedParamCount) {
                        if (!(targetType instanceof JavaType.FullyQualified)) {
                            return null;
                        }
                        JavaType.Method match = null;
                        for (JavaType.FullyQualified current = (JavaType.FullyQualified) targetType;
                             current != null; current = current.getSupertype()) {
                            for (JavaType.Method method : current.getMethods()) {
                                if (method.getName().equals(methodName) &&
                                        method.getParameterTypes().size() == expectedParamCount) {
                                    if (match != null) {
                                        return null; // ambiguous overload
                                    }
                                    match = method;
                                }
                            }
                        }
                        return match;
                    }
                }
        );
    }
}
