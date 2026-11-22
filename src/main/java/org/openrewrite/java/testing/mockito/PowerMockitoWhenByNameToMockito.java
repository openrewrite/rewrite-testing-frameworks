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
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static org.openrewrite.Tree.randomId;

public class PowerMockitoWhenByNameToMockito extends Recipe {

    private static final MethodMatcher WHEN_BY_NAME =
            new MethodMatcher("org.powermock.api.mockito.PowerMockito when(java.lang.Object, java.lang.String, ..)");

    private static final Set<String> BOXED = new HashSet<>(java.util.Arrays.asList(
            "java.lang.Boolean", "java.lang.Byte", "java.lang.Character", "java.lang.Short", "java.lang.Integer",
            "java.lang.Long", "java.lang.Float", "java.lang.Double"));

    @Getter
    final String displayName = "Replace PowerMockito `when(instance, \"method\", args...)` with `Mockito.when(instance.method(args...))`";

    @Getter
    final String description = "Replaces PowerMockito's stubbing of a method by name, `when(instance, \"methodName\", args...)`, " +
            "with the call of the method itself. A method that returns nothing is stubbed with `doAnswer`, `doThrow` or " +
            "`doCallRealMethod` instead. Stubbing of a private method, which Mockito can not do, and of a method that can " +
            "not be resolved to a single one is left unchanged.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(WHEN_BY_NAME), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (method.getSelect() instanceof J.MethodInvocation && WHEN_BY_NAME.matches(method.getSelect())) {
                    J.MethodInvocation when = (J.MethodInvocation) method.getSelect();
                    JavaType.Method target = resolve(when);
                    if (target != null && target.getReturnType() == JavaType.Primitive.Void) {
                        return stubVoid(method, when, target, ctx);
                    }
                }
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (WHEN_BY_NAME.matches(mi)) {
                    JavaType.Method target = resolve(mi);
                    if (target != null && target.getReturnType() != JavaType.Primitive.Void) {
                        return stubReturning(mi, target);
                    }
                }
                return mi;
            }

            /// `when(instance, "method", args)` to `when(instance.method(args))`
            private J.MethodInvocation stubReturning(J.MethodInvocation mi, JavaType.Method target) {
                JavaType.Method whenOfCall = null;
                if (mi.getMethodType() != null) {
                    for (JavaType.Method candidate : mi.getMethodType().getDeclaringType().getMethods()) {
                        if ("when".equals(candidate.getName()) && candidate.getParameterTypes().size() == 1) {
                            whenOfCall = candidate;
                        }
                    }
                }
                if (whenOfCall == null) {
                    return mi;
                }
                J.MethodInvocation call = call(mi, target).withPrefix(Space.EMPTY);
                return mi.withArguments(singletonList(call)).withMethodType(whenOfCall)
                        .withName(mi.getName().withType(whenOfCall));
            }

            /// `when(instance, "method", args).thenAnswer(answer)` to `doAnswer(answer).when(instance).method(args)`
            private J.MethodInvocation stubVoid(J.MethodInvocation method, J.MethodInvocation when, JavaType.Method target,
                                                ExecutionContext ctx) {
                String doer;
                String parameters;
                switch (method.getSimpleName()) {
                    case "thenAnswer":
                        doer = "doAnswer";
                        parameters = "#{any(org.mockito.stubbing.Answer)}";
                        break;
                    case "thenThrow":
                        doer = "doThrow";
                        parameters = method.getArguments().stream().map(a -> "#{any()}").collect(joining(", "));
                        break;
                    case "thenCallRealMethod":
                        doer = "doCallRealMethod";
                        parameters = "";
                        break;
                    default:
                        return super.visitMethodInvocation(method, ctx);
                }
                List<Object> templateArguments = new ArrayList<>();
                if (!parameters.isEmpty()) {
                    method.getArguments().forEach(templateArguments::add);
                }
                templateArguments.add(when.getArguments().get(0));
                J.MethodInvocation doWhen = JavaTemplate.builder("Mockito." + doer + "(" + parameters + ").when(#{any()})")
                        .imports("org.mockito.Mockito")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                        .build()
                        .apply(getCursor(), method.getCoordinates().replace(), templateArguments.toArray())
                        .withPrefix(Space.EMPTY);
                maybeRemoveImport("org.powermock.api.mockito.PowerMockito");
                maybeRemoveImport("org.powermock.api.mockito.PowerMockito.when");
                maybeAddImport("org.mockito.Mockito");
                J.MethodInvocation call = call(when, target);
                return call.withId(method.getId()).withPrefix(method.getPrefix())
                        .withSelect(doWhen).withTypeParameters(null)
                        .withName(call.getName().withPrefix(Space.EMPTY));
            }

            /// `instance.method(args)`, reusing the invocation it replaces for the unchanged parts
            private J.MethodInvocation call(J.MethodInvocation when, JavaType.Method target) {
                List<Expression> arguments = when.getArguments();
                Expression instance = arguments.get(0).withPrefix(Space.EMPTY);
                List<Expression> rest = arguments.subList(2, arguments.size());
                List<Expression> newArguments = rest.isEmpty() ?
                        singletonList(new J.Empty(randomId(), Space.EMPTY, Markers.EMPTY)) :
                        ListUtils.mapFirst(rest, a -> a.withPrefix(Space.EMPTY));
                J.Identifier name = when.getName().withId(randomId()).withSimpleName(target.getName()).withType(target);
                return when.withId(randomId())
                        .withSelect(instance)
                        .withName(name)
                        .withMethodType(target)
                        .withArguments(newArguments);
            }

            private JavaType.@Nullable Method resolve(J.MethodInvocation when) {
                List<Expression> arguments = when.getArguments();
                if (!(arguments.get(1) instanceof J.Literal) || !(((J.Literal) arguments.get(1)).getValue() instanceof String)) {
                    return null;
                }
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(arguments.get(0).getType());
                if (type == null || TypeUtils.isOfClassType(type, "java.lang.Class")) {
                    return null;
                }
                String name = (String) ((J.Literal) arguments.get(1)).getValue();
                List<Expression> rest = arguments.subList(2, arguments.size());
                List<JavaType.Method> candidates = new ArrayList<>();
                for (JavaType.FullyQualified current : hierarchy(type)) {
                    for (JavaType.Method method : current.getMethods()) {
                        if (method.getName().equals(name) && method.getParameterTypes().size() == rest.size() &&
                            !overridden(method, candidates)) {
                            candidates.add(method);
                        }
                    }
                }
                if (candidates.size() != 1) {
                    return null;
                }
                JavaType.Method target = candidates.get(0);
                if (target.hasFlags(Flag.Private) || target.hasFlags(Flag.Static) || !accessible(target) ||
                    target.hasFlags(Flag.Varargs)) {
                    return null;
                }
                for (int i = 0; i < rest.size(); i++) {
                    // `any()` is null, which a primitive parameter can not take
                    if (target.getParameterTypes().get(i) instanceof JavaType.Primitive && !isPrimitiveOrBoxed(rest.get(i).getType())) {
                        return null;
                    }
                }
                return target;
            }

            private boolean accessible(JavaType.Method method) {
                if (method.hasFlags(Flag.Public)) {
                    return true;
                }
                J.Package pkg = getCursor().firstEnclosingOrThrow(JavaSourceFile.class).getPackageDeclaration();
                String testPackage = pkg == null ? "" : pkg.getExpression().printTrimmed(getCursor());
                return !method.hasFlags(Flag.Private) && method.getDeclaringType().getPackageName().equals(testPackage);
            }

            private boolean isPrimitiveOrBoxed(@Nullable JavaType type) {
                return type instanceof JavaType.Primitive ||
                       (type instanceof JavaType.FullyQualified && BOXED.contains(((JavaType.FullyQualified) type).getFullyQualifiedName()));
            }

            private boolean overridden(JavaType.Method method, List<JavaType.Method> found) {
                for (JavaType.Method other : found) {
                    if (other.getParameterTypes().size() == method.getParameterTypes().size()) {
                        boolean same = true;
                        for (int i = 0; i < other.getParameterTypes().size() && same; i++) {
                            same = TypeUtils.isOfType(other.getParameterTypes().get(i), method.getParameterTypes().get(i));
                        }
                        if (same) {
                            return true;
                        }
                    }
                }
                return false;
            }

            private List<JavaType.FullyQualified> hierarchy(JavaType.FullyQualified type) {
                List<JavaType.FullyQualified> all = new ArrayList<>();
                collect(type, all);
                return all;
            }

            private void collect(JavaType.@Nullable FullyQualified type, List<JavaType.FullyQualified> all) {
                if (type == null || all.contains(type)) {
                    return;
                }
                all.add(type);
                collect(type.getSupertype(), all);
                type.getInterfaces().forEach(i -> collect(i, all));
            }
        });
    }
}
