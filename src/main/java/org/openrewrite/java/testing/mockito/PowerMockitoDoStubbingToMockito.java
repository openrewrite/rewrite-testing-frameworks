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
import org.openrewrite.Cursor;
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
import org.openrewrite.trait.Comments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.tree.Flag.Private;

public class PowerMockitoDoStubbingToMockito extends Recipe {

    private static final MethodMatcher STUBBER_WHEN_MATCHER =
            new MethodMatcher("org.powermock.api.mockito.expectation.PowerMockitoStubber when(..)");

    private static final MethodMatcher MOCK_STATIC =
            new MethodMatcher("org.powermock.api.mockito.PowerMockito mockStatic(..)");
    private static final MethodMatcher SPY_CLASS =
            new MethodMatcher("org.powermock.api.mockito.PowerMockito spy(java.lang.Class)");
    private static final MethodMatcher MOCKITO_MOCK_STATIC = new MethodMatcher("org.mockito.Mockito mockStatic(..)");

    @Getter
    final String displayName = "Replace PowerMockito `doX().when(instance, \"method\")` with Mockito-compatible stubbing";

    @Getter
    final String description = "Replaces PowerMockito's `doNothing().when(instance, \"methodName\", args...)` stubbing (and that of the other `doX()` methods) with the standard Mockito pattern `doNothing().when(instance).methodName(args...)`. The method name may be a constant. Stubbing of a static method, `doX().when(Type.class, \"methodName\", args...)` or `doX().when(Type.class); Type.methodName(args...)`, becomes `PowerMockito.when(Type.class, \"methodName\", args...).thenX(...)` for the static mock, if the file creates one. Mockito can not stub private methods, so stubbing of a private method (or of one of several overloads that include a private one) is left unchanged and gets a comment. Stubbing that can not be resolved to a single accessible method is left unchanged.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>(STUBBER_WHEN_MATCHER),
                new JavaIsoVisitor<ExecutionContext>() {

                    @Override
                    public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                        J.Block b = super.visitBlock(block, ctx);
                        List<Statement> statements = b.getStatements();
                        List<Statement> rewritten = new ArrayList<>(statements.size());
                        boolean changed = false;
                        for (int i = 0; i < statements.size(); i++) {
                            Statement statement = statements.get(i);
                            if (i + 1 < statements.size() && statement instanceof J.MethodInvocation &&
                                statements.get(i + 1) instanceof J.MethodInvocation) {
                                J.MethodInvocation replaced = stubStaticCallStatement(
                                        (J.MethodInvocation) statement, (J.MethodInvocation) statements.get(i + 1),
                                        new Cursor(getCursor().getParentOrThrow(), b), ctx);
                                if (replaced != null) {
                                    rewritten.add(replaced);
                                    changed = true;
                                    i++;
                                    continue;
                                }
                            }
                            rewritten.add(statement);
                        }
                        return changed ? b.withStatements(rewritten) : b;
                    }

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

                        String targetMethodName = methodName(args.get(1));
                        if (targetMethodName == null) {
                            return mi;
                        }

                        // The static variant, `doX().when(Type.class, "method", args...)`, is stubbed on the static mock
                        Expression firstArg = args.get(0);
                        if (isClassLiteral(firstArg)) {
                            J.MethodInvocation stubbed = stubStatic(mi, firstArg, targetMethodName,
                                    args.subList(2, args.size()), getCursor(), mi.getCoordinates().replace(), ctx);
                            return stubbed == null ? mi : stubbed;
                        }
                        if (TypeUtils.isOfClassType(firstArg.getType(), "java.lang.Class")) {
                            return mi;
                        }

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
                        if (candidates.size() != 1 || candidates.get(0).hasFlags(Flag.Static) ||
                            candidates.get(0).hasFlags(Flag.Varargs) || !accessible(candidates.get(0))) {
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

                    private void collect(JavaType.@Nullable FullyQualified type, List<JavaType.FullyQualified> all) {
                        if (type == null || all.contains(type)) {
                            return;
                        }
                        all.add(type);
                        collect(type.getSupertype(), all);
                        type.getInterfaces().forEach(i -> collect(i, all));
                    }

                    /**
                     * A method of a supertype that a subtype method already found overrides.
                     */
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

                    /**
                     * Package-private and protected methods can only be called from the test's own package.
                     */
                    private boolean accessible(JavaType.Method method) {
                        if (method.hasFlags(Flag.Public)) {
                            return true;
                        }
                        J.Package pkg = getCursor().firstEnclosingOrThrow(JavaSourceFile.class).getPackageDeclaration();
                        String testPackage = pkg == null ? "" : pkg.getExpression().printTrimmed(getCursor());
                        return method.getDeclaringType().getPackageName().equals(testPackage);
                    }

                    private boolean isClassLiteral(Expression expression) {
                        return expression instanceof J.FieldAccess && "class".equals(((J.FieldAccess) expression).getSimpleName());
                    }

                    /**
                     * The method name of the stubbing: a string literal, or a constant that a literal initializes
                     * in this source file.
                     */
                    private @Nullable String methodName(Expression expression) {
                        if (expression instanceof J.Literal) {
                            Object value = ((J.Literal) expression).getValue();
                            return value instanceof String ? (String) value : null;
                        }
                        if (expression instanceof J.Identifier || expression instanceof J.FieldAccess) {
                            JavaType.Variable field = expression instanceof J.Identifier ?
                                    ((J.Identifier) expression).getFieldType() : ((J.FieldAccess) expression).getName().getFieldType();
                            if (field == null || !field.hasFlags(Flag.Static, Flag.Final) ||
                                !TypeUtils.isString(field.getType())) {
                                return null;
                            }
                            String[] found = new String[1];
                            new JavaIsoVisitor<Integer>() {
                                @Override
                                public J.VariableDeclarations.NamedVariable visitVariable(
                                        J.VariableDeclarations.NamedVariable variable, Integer p) {
                                    if (variable.getSimpleName().equals(field.getName()) &&
                                        variable.getVariableType() != null &&
                                        TypeUtils.isOfType(variable.getVariableType().getOwner(), field.getOwner()) &&
                                        variable.getInitializer() instanceof J.Literal &&
                                        ((J.Literal) variable.getInitializer()).getValue() instanceof String) {
                                        found[0] = (String) ((J.Literal) variable.getInitializer()).getValue();
                                    }
                                    return variable;
                                }
                            }.visit(getCursor().firstEnclosingOrThrow(JavaSourceFile.class), 0);
                            return found[0];
                        }
                        return null;
                    }

                    /**
                     * `doX(..).when(Type.class); Type.method(args);` as `PowerMockito.when(Type.class, "method", args).thenX(..)`.
                     */
                    private J.@Nullable MethodInvocation stubStaticCallStatement(
                            J.MethodInvocation when, J.MethodInvocation call, Cursor blockCursor, ExecutionContext ctx) {
                        if (!STUBBER_WHEN_MATCHER.matches(when) || when.getArguments().size() != 1 ||
                            !isClassLiteral(when.getArguments().get(0)) || call.getMethodType() == null ||
                            !call.getMethodType().hasFlags(Flag.Static) || call.getMethodType().hasFlags(Flag.Varargs) ||
                            !(call.getSelect() instanceof TypeTree || call.getSelect() == null)) {
                            return null;
                        }
                        JavaType.FullyQualified literal = TypeUtils.asFullyQualified(
                                ((J.FieldAccess) when.getArguments().get(0)).getTarget().getType());
                        if (literal == null || !TypeUtils.isOfType(literal, call.getMethodType().getDeclaringType())) {
                            return null;
                        }
                        List<Expression> callArguments = call.getArguments().get(0) instanceof J.Empty ?
                                Collections.<Expression>emptyList() : call.getArguments();
                        J.MethodInvocation stubbed = stubStatic(when, when.getArguments().get(0), call.getSimpleName(),
                                callArguments, new Cursor(blockCursor, when), when.getCoordinates().replace(), ctx);
                        return stubbed == null ? null : stubbed.withPrefix(when.getPrefix());
                    }

                    private J.@Nullable MethodInvocation stubStatic(
                            J.MethodInvocation when, Expression type, String name, List<Expression> arguments,
                            Cursor cursor, JavaCoordinates coordinates, ExecutionContext ctx) {
                        List<J.MethodInvocation> doers = doers(when);
                        if (doers == null || !staticallyMocked(type)) {
                            return null;
                        }
                        StringBuilder template = new StringBuilder("PowerMockito.when(#{any(java.lang.Class)}, \"" + name + "\"");
                        List<Object> templateArguments = new ArrayList<>();
                        templateArguments.add(type);
                        for (Expression argument : arguments) {
                            template.append(", #{any()}");
                            templateArguments.add(argument);
                        }
                        template.append(")");
                        for (J.MethodInvocation doer : doers) {
                            List<Expression> doerArguments = doer.getArguments().get(0) instanceof J.Empty ?
                                    Collections.<Expression>emptyList() : doer.getArguments();
                            switch (doer.getSimpleName()) {
                                case "doReturn":
                                    template.append(".thenReturn(");
                                    appendAll(template, templateArguments, doerArguments, "java.lang.Object");
                                    break;
                                case "doThrow":
                                    template.append(".thenThrow(");
                                    for (int i = 0; i < doerArguments.size(); i++) {
                                        template.append(i == 0 ? "" : ", ").append(isClassLiteral(doerArguments.get(i)) ||
                                                TypeUtils.isOfClassType(doerArguments.get(i).getType(), "java.lang.Class") ?
                                                "#{any(java.lang.Class)}" : "#{any(java.lang.Throwable)}");
                                        templateArguments.add(doerArguments.get(i));
                                    }
                                    template.append(")");
                                    break;
                                case "doAnswer":
                                    template.append(".thenAnswer(");
                                    appendAll(template, templateArguments, doerArguments, "org.mockito.stubbing.Answer");
                                    break;
                                case "doNothing":
                                    template.append(".thenAnswer(invocation -> null)");
                                    break;
                                case "doCallRealMethod":
                                    template.append(".thenCallRealMethod()");
                                    break;
                                default:
                                    return null;
                            }
                        }
                        J.MethodInvocation stubbed = JavaTemplate.builder(template.toString())
                                .imports("org.powermock.api.mockito.PowerMockito")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx,
                                        "mockito-core-3.12", "powermock-core-1", "powermock-api-mockito-1",
                                        "powermock-api-support-1", "powermock-reflect-1"))
                                .build()
                                .apply(cursor, coordinates, templateArguments.toArray());
                        maybeAddImport("org.powermock.api.mockito.PowerMockito");
                        for (J.MethodInvocation doer : doers) {
                            maybeRemoveImport("org.powermock.api.mockito.PowerMockito." + doer.getSimpleName());
                        }
                        return stubbed;
                    }

                    /**
                     * Whether this file creates a static mock or spy of the type, which is what the stubbing needs.
                     */
                    private boolean staticallyMocked(Expression classLiteral) {
                        JavaType.FullyQualified type = TypeUtils.asFullyQualified(((J.FieldAccess) classLiteral).getTarget().getType());
                        if (type == null) {
                            return false;
                        }
                        return new JavaIsoVisitor<AtomicBoolean>() {
                            @Override
                            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean found) {
                                if (MOCK_STATIC.matches(method) || SPY_CLASS.matches(method) || MOCKITO_MOCK_STATIC.matches(method)) {
                                    for (Expression argument : method.getArguments()) {
                                        if (isClassLiteral(argument) &&
                                            TypeUtils.isOfType(type, ((J.FieldAccess) argument).getTarget().getType())) {
                                            found.set(true);
                                        }
                                    }
                                }
                                return super.visitMethodInvocation(method, found);
                            }
                        }.reduce(getCursor().firstEnclosingOrThrow(JavaSourceFile.class), new AtomicBoolean()).get();
                    }

                    private void appendAll(StringBuilder template, List<Object> templateArguments,
                                           List<Expression> arguments, String type) {
                        for (int i = 0; i < arguments.size(); i++) {
                            template.append(i == 0 ? "" : ", ").append("#{any(").append(type).append(")}");
                            templateArguments.add(arguments.get(i));
                        }
                        template.append(")");
                    }

                    /**
                     * The `doX(..)` calls the stubbing chain starts with, in order, or null when it starts with
                     * something else.
                     */
                    private @Nullable List<J.MethodInvocation> doers(J.MethodInvocation when) {
                        List<J.MethodInvocation> doers = new ArrayList<>();
                        Expression select = when.getSelect();
                        while (select instanceof J.MethodInvocation) {
                            J.MethodInvocation doer = (J.MethodInvocation) select;
                            if (doer.getMethodType() == null || !doer.getSimpleName().startsWith("do")) {
                                return null;
                            }
                            doers.add(0, doer);
                            select = doer.getSelect();
                            String declaring = doer.getMethodType().getDeclaringType().getFullyQualifiedName();
                            if ("org.powermock.api.mockito.PowerMockito".equals(declaring)) {
                                return select == null || select instanceof TypeTree ? doers : null;
                            }
                        }
                        return null;
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
                        List<JavaType.FullyQualified> hierarchy = new ArrayList<>();
                        collect((JavaType.FullyQualified) targetType, hierarchy);
                        for (JavaType.FullyQualified current : hierarchy) {
                            for (JavaType.Method method : current.getMethods()) {
                                if (method.getName().equals(methodName) &&
                                        method.getParameterTypes().size() == expectedParamCount &&
                                        !overridden(method, candidates)) {
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
