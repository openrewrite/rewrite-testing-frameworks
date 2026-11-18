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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.trait.Comments;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.tree.Flag.Private;

public class PowerMockitoDoStubbingToMockito extends Recipe {

    private static final MethodMatcher STUBBER_WHEN_MATCHER =
            new MethodMatcher("org.powermock.api.mockito.expectation.PowerMockitoStubber when(..)");

    @Getter
    final String displayName = "Replace PowerMockito `doX().when(instance, \"method\")` with Mockito-compatible stubbing";

    @Getter
    final String description = "Replaces PowerMockito's `doNothing().when(instance, \"methodName\", args...)` stubbing (and that of the other `doX()` methods) with the standard Mockito pattern `doNothing().when(instance).methodName(args...)`. Mockito can not stub private methods, so stubbing of a private method (or of one of several overloads that include a private one) is left unchanged and gets a comment. Stubbing that can not be resolved to a single method is left unchanged.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>(STUBBER_WHEN_MATCHER),
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
                        List<Expression> extraArgs = args.subList(2, args.size());

                        // Private methods cannot be stubbed with standard Mockito; leave unchanged and add a comment
                        // Any private candidate counts, as overloads with the same arity can not be told apart here
                        List<JavaType.Method> candidates = findCandidates(
                                firstArg.getType(), targetMethodName, extraArgs.size());
                        if (candidates.stream().anyMatch(m -> m.getFlags().contains(Private))) {
                            String comment = " PowerMock private method stubbing is not supported by Mockito. Refactor the private method to package-private or extract to a collaborator.";
                            return Comments.of(updateCursor(mi)).comment(comment, Comments.Placement.BEFORE, "\n" + mi.getPrefix().getIndent());
                        }

                        // Without a single resolved target we can not tell whether it is private, so leave it alone
                        if (candidates.size() != 1) {
                            return mi;
                        }

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
                                    .withParameterTypes(singletonList(instanceType))
                                    .withParameterNames(singletonList("mock"));
                        }
                        // a new call inside the one it replaces, so with ids of its own
                        J.MethodInvocation whenWithInstance = mi
                                .withId(randomId())
                                .withPrefix(Space.EMPTY)
                                .withArguments(singletonList(firstArg.withPrefix(Space.EMPTY)))
                                .withMethodType(updatedWhenType)
                                .withName(mi.getName().withId(randomId()).withType(updatedWhenType));

                        // 2. Build .method(args...) chained on when(instance)
                        List<Expression> newArgs = extraArgs.isEmpty() ?
                                emptyList() :
                                ListUtils.mapFirst(extraArgs, a -> a.withPrefix(Space.EMPTY));

                        JavaType.Method resolvedMethodType = candidates.get(0);

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
                     * Find the methods of the instance type and its supertypes with this name and parameter count.
                     */
                    private List<JavaType.Method> findCandidates(
                            @Nullable JavaType targetType, String methodName, int expectedParamCount) {
                        if (!(targetType instanceof JavaType.FullyQualified)) {
                            return emptyList();
                        }
                        List<JavaType.Method> candidates = new ArrayList<>();
                        for (JavaType.FullyQualified current = (JavaType.FullyQualified) targetType;
                             current != null; current = current.getSupertype()) {
                            for (JavaType.Method method : current.getMethods()) {
                                if (method.getName().equals(methodName) &&
                                        method.getParameterTypes().size() == expectedParamCount) {
                                    candidates.add(method);
                                }
                            }
                        }
                        return candidates;
                    }
                }
        );
    }
}
