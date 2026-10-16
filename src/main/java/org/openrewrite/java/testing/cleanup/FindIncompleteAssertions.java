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
package org.openrewrite.java.testing.cleanup;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Loop;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;

public class FindIncompleteAssertions extends Recipe {

    private static final List<MethodMatcher> ENTRY_POINTS = Arrays.asList(
            new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)"),
            new MethodMatcher("org.assertj.core.api.AssertionsForClassTypes assertThat(..)"),
            new MethodMatcher("org.assertj.core.api.AssertionsForInterfaceTypes assertThat(..)"),
            new MethodMatcher("org.assertj.core.api.Java6Assertions assertThat(..)"),
            new MethodMatcher("org.assertj.core.api.WithAssertions assertThat(..)", true),
            new MethodMatcher("org.fest.assertions.api.Assertions assertThat(..)"),
            new MethodMatcher("org.fest.assertions.Assertions assertThat(..)"),
            new MethodMatcher("com.google.common.truth.Truth assertThat(..)"),
            new MethodMatcher("com.google.common.truth.StandardSubjectBuilder that(..)", true),
            new MethodMatcher("com.google.common.truth.SimpleSubjectBuilder that(..)"),
            new MethodMatcher("org.mockito.Mockito verify(..)"),
            new MethodMatcher("org.mockito.InOrder verify(..)", true)
    );

    // AssertJ methods that configure or navigate, but do not assert anything themselves
    private static final Set<String> ASSERTJ_NON_ASSERTING = new HashSet<>(Arrays.asList(
            "as",
            "describedAs",
            "extracting",
            "overridingErrorMessage",
            "usingComparator",
            "usingDefaultComparator",
            "usingElementComparator",
            "usingRecursiveAssertion",
            "usingRecursiveComparison",
            "withFailMessage",
            "withRepresentation"
    ));

    private static final List<String> RECURSIVE_ASSERTS = Arrays.asList(
            "org.assertj.core.api.RecursiveAssertionAssert",
            "org.assertj.core.api.RecursiveComparisonAssert"
    );

    // Configuration methods of the recursive asserts; none of their assertions start with these
    private static final List<String> RECURSIVE_CONFIGURATION_PREFIXES = Arrays.asList(
            "comparingOnly",
            "ignoring",
            "using",
            "with"
    );

    @Getter
    final String displayName = "Find incomplete assertions";

    @Getter
    final String description = "Find AssertJ, Fest and Truth `assertThat(..)` and Mockito `verify(..)` calls that are not followed by an assertion or a verified method call, so they check nothing.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S2970");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new JavaFileChecker<>(),
                        Preconditions.or(ENTRY_POINTS.stream().map(UsesMethod::new).toArray(UsesMethod[]::new))
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (isPendingAssertion(mi) && isResultDiscarded(method)) {
                            return SearchResult.found(mi);
                        }
                        return mi;
                    }

                    private boolean isResultDiscarded(J.MethodInvocation method) {
                        Object parent = getCursor().getParentTreeCursor().getValue();
                        if (parent instanceof J.Block || parent instanceof J.Label || parent instanceof J.If ||
                                parent instanceof J.If.Else || parent instanceof Loop) {
                            return true;
                        }
                        if (parent instanceof J.Case) {
                            J.Case aCase = (J.Case) parent;
                            if (aCase.getStatements().contains(method)) {
                                return true;
                            }
                            return aCase.getBody() == method &&
                                    getCursor().dropParentUntil(p -> p instanceof J.Switch || p instanceof J.SwitchExpression).getValue() instanceof J.Switch;
                        }
                        if (parent instanceof J.Lambda) {
                            J.Lambda lambda = (J.Lambda) parent;
                            return lambda.getBody() == method && isVoidFunctionalInterface(lambda.getType());
                        }
                        return false;
                    }
                }
        );
    }

    private static boolean isPendingAssertion(J.MethodInvocation method) {
        J.MethodInvocation current = method;
        while (isAssertJNonAsserting(current) && current.getSelect() instanceof J.MethodInvocation) {
            current = (J.MethodInvocation) current.getSelect();
        }
        JavaType.Method type = current.getMethodType();
        if (type == null || type.getReturnType() == JavaType.Primitive.Void) {
            return false;
        }
        for (MethodMatcher entryPoint : ENTRY_POINTS) {
            if (entryPoint.matches(current)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAssertJNonAsserting(J.MethodInvocation method) {
        JavaType.Method type = method.getMethodType();
        if (type == null) {
            return false;
        }
        String name = type.getName();
        JavaType.FullyQualified declaringType = type.getDeclaringType();
        if (RECURSIVE_ASSERTS.contains(declaringType.getFullyQualifiedName())) {
            return RECURSIVE_CONFIGURATION_PREFIXES.stream().anyMatch(name::startsWith);
        }
        return ASSERTJ_NON_ASSERTING.contains(name) &&
                "org.assertj.core.api".equals(declaringType.getPackageName());
    }

    private static boolean isVoidFunctionalInterface(@Nullable JavaType type) {
        JavaType.Method sam = findSingleAbstractMethod(TypeUtils.asFullyQualified(type), new HashSet<>());
        return sam != null && sam.getReturnType() == JavaType.Primitive.Void;
    }

    private static JavaType.@Nullable Method findSingleAbstractMethod(JavaType.@Nullable FullyQualified type, Set<String> implemented) {
        if (type == null) {
            return null;
        }
        Set<String> implementedHere = new HashSet<>(implemented);
        for (JavaType.Method m : type.getMethods()) {
            if (!isAbstract(m) && !m.hasFlags(Flag.Static)) {
                implementedHere.add(signature(m));
            }
        }
        for (JavaType.Method m : type.getMethods()) {
            if (isAbstract(m) && !implementedHere.contains(signature(m)) && !isObjectMethod(m)) {
                return m;
            }
        }
        for (JavaType.FullyQualified i : type.getInterfaces()) {
            JavaType.Method sam = findSingleAbstractMethod(i, implementedHere);
            if (sam != null) {
                return sam;
            }
        }
        return null;
    }

    private static boolean isAbstract(JavaType.Method method) {
        // Default methods carry both the `Abstract` and `Default` flags
        return method.hasFlags(Flag.Abstract) && !method.hasFlags(Flag.Default);
    }

    private static String signature(JavaType.Method method) {
        return method.getName() + "/" + method.getParameterTypes().size();
    }

    private static boolean isObjectMethod(JavaType.Method method) {
        String signature = signature(method);
        return "equals/1".equals(signature) || "hashCode/0".equals(signature) || "toString/0".equals(signature);
    }
}
