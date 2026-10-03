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
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;

public class PowerMockWhiteboxSetInternalStateToInjectMocks extends Recipe {

    private static final MethodMatcher SET_INTERNAL_STATE =
            new MethodMatcher("org.powermock.reflect.Whitebox setInternalState(java.lang.Object, java.lang.String, java.lang.Object)");

    private static final String MOCK = "org.mockito.Mock";
    private static final String SPY = "org.mockito.Spy";
    private static final String INJECT_MOCKS = "org.mockito.InjectMocks";

    private static final String INJECTABLE = "injectableObjectsUnderTest";

    @Getter
    final String displayName = "Replace PowerMock `Whitebox.setInternalState()` with `@InjectMocks`";

    @Getter
    final String description = "Replaces `Whitebox.setInternalState(objectUnderTest, \"field\", mock)` with Mockito's " +
            "own field injection, annotating the object under test with `@InjectMocks`. Only applies when the value " +
            "is a `@Mock` or `@Spy` field whose name matches the field being set, so that Mockito injects the same " +
            "mock the call did; other calls are left to the reflection-based recipe.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(SET_INTERNAL_STATE), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                List<J.VariableDeclarations> fields = fieldsOf(classDecl);
                List<String> injectable = new ArrayList<>();
                for (J.MethodInvocation call : setInternalStateCalls(classDecl)) {
                    String targetName = injectableTargetName(call, fields);
                    if (targetName != null) {
                        injectable.add(targetName);
                    }
                }
                if (injectable.isEmpty()) {
                    return super.visitClassDeclaration(classDecl, ctx);
                }

                getCursor().putMessage(INJECTABLE, injectable);
                J.ClassDeclaration cd = removeReplacedCalls(classDecl, fields, ctx);
                maybeRemoveImport("org.powermock.reflect.Whitebox");
                maybeAddImport(INJECT_MOCKS);
                return super.visitClassDeclaration(cd, ctx);
            }

            /**
             * The name of the object under test when the call is nothing more than the injection Mockito
             * performs itself, or null when it is not.
             */
            private @Nullable String injectableTargetName(J.MethodInvocation call, List<J.VariableDeclarations> fields) {
                List<Expression> args = call.getArguments();
                String fieldName = literalValue(args.get(1));
                if (fieldName == null) {
                    return null;
                }
                J.VariableDeclarations target = fieldNamed(fields, simpleName(args.get(0)));
                J.VariableDeclarations value = fieldNamed(fields, simpleName(args.get(2)));
                if (target == null || value == null ||
                        hasAnnotation(target, MOCK) || hasAnnotation(target, SPY) ||
                        !(hasAnnotation(value, MOCK) || hasAnnotation(value, SPY))) {
                    return null;
                }
                // Mockito injects by type and then by name. Requiring the names to line up keeps it injecting
                // the same mock even when the object under test has several fields of that type.
                if (!fieldName.equals(variableName(value))) {
                    return null;
                }
                // Mockito injects through exactly one strategy: when it can build the object with a
                // parameterized constructor it does that and never touches the fields, so the call being
                // replaced would not be performed at all. Field injection only happens for an object
                // Mockito does not construct, or one whose only constructor takes no arguments.
                if (!(hasInitializer(target) || onlyHasANoArgumentConstructor(target))) {
                    return null;
                }
                if (hasAnnotation(target, INJECT_MOCKS)) {
                    return variableName(target);
                }
                // A field reassigned elsewhere would have the injected instance overwritten.
                return constructedWithoutArguments(target) && !isReassigned(variableName(target)) ?
                        variableName(target) : null;
            }

            private J.ClassDeclaration removeReplacedCalls(J.ClassDeclaration classDecl, List<J.VariableDeclarations> fields, ExecutionContext ctx) {
                return (J.ClassDeclaration) new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Block visitBlock(J.Block block, ExecutionContext blockCtx) {
                        J.Block b = super.visitBlock(block, blockCtx);
                        return b.withStatements(ListUtils.filter(b.getStatements(), statement ->
                                !(statement instanceof J.MethodInvocation &&
                                        SET_INTERNAL_STATE.matches((J.MethodInvocation) statement) &&
                                        injectableTargetName((J.MethodInvocation) statement, fields) != null)));
                    }
                }.visitNonNull(classDecl, ctx, getCursor().getParentOrThrow());
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                List<String> injectable = getCursor().getNearestMessage(INJECTABLE);
                if (injectable == null || !injectable.contains(variableName(multiVariable)) ||
                        hasAnnotation(multiVariable, INJECT_MOCKS)) {
                    return super.visitVariableDeclarations(multiVariable, ctx);
                }
                // Mockito constructs the object under test, so its own initializer would be overwritten.
                J.VariableDeclarations withoutInitializer = multiVariable.withVariables(
                        ListUtils.map(multiVariable.getVariables(), v -> v.withInitializer(null)));
                J.VariableDeclarations annotated = JavaTemplate.builder("@InjectMocks")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                        .imports(INJECT_MOCKS)
                        .build()
                        .apply(updateCursor(withoutInitializer),
                                withoutInitializer.getCoordinates().addAnnotation((a, b) -> 0));
                return maybeAutoFormat(multiVariable, annotated, ctx);
            }

            /** Whether the field is assigned anywhere in the class, which would overwrite what Mockito injects. */
            private boolean isReassigned(@Nullable String fieldName) {
                if (fieldName == null) {
                    return true;
                }
                J.ClassDeclaration enclosing = getCursor().firstEnclosingOrThrow(J.ClassDeclaration.class);
                return new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.Assignment visitAssignment(J.Assignment assignment, AtomicBoolean found) {
                        if (fieldName.equals(simpleName(assignment.getVariable()))) {
                            found.set(true);
                        }
                        return super.visitAssignment(assignment, found);
                    }
                }.reduce(enclosing, new AtomicBoolean()).get();
            }

            private List<J.MethodInvocation> setInternalStateCalls(J.ClassDeclaration classDecl) {
                List<J.MethodInvocation> calls = new ArrayList<>();
                new JavaIsoVisitor<List<J.MethodInvocation>>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, List<J.MethodInvocation> p) {
                        if (SET_INTERNAL_STATE.matches(method)) {
                            p.add(method);
                        }
                        return super.visitMethodInvocation(method, p);
                    }
                }.visit(classDecl, calls);
                return calls;
            }
        });
    }

    private static List<J.VariableDeclarations> fieldsOf(J.ClassDeclaration classDecl) {
        List<J.VariableDeclarations> fields = new ArrayList<>();
        for (Statement statement : classDecl.getBody().getStatements()) {
            if (statement instanceof J.VariableDeclarations) {
                fields.add((J.VariableDeclarations) statement);
            }
        }
        return fields;
    }

    private static J.@Nullable VariableDeclarations fieldNamed(List<J.VariableDeclarations> fields, @Nullable String name) {
        if (name == null) {
            return null;
        }
        for (J.VariableDeclarations field : fields) {
            if (name.equals(variableName(field))) {
                return field;
            }
        }
        return null;
    }

    private static @Nullable String variableName(J.VariableDeclarations field) {
        return field.getVariables().size() == 1 ? field.getVariables().get(0).getSimpleName() : null;
    }

    /** The name a `foo` or `this.foo` argument refers to. */
    private static @Nullable String simpleName(Expression expression) {
        if (expression instanceof J.Identifier) {
            return ((J.Identifier) expression).getSimpleName();
        }
        if (expression instanceof J.FieldAccess && ((J.FieldAccess) expression).getTarget() instanceof J.Identifier &&
                "this".equals(((J.Identifier) ((J.FieldAccess) expression).getTarget()).getSimpleName())) {
            return ((J.FieldAccess) expression).getSimpleName();
        }
        return null;
    }

    private static @Nullable String literalValue(Expression expression) {
        return expression instanceof J.Literal && ((J.Literal) expression).getValue() instanceof String ?
                (String) ((J.Literal) expression).getValue() : null;
    }

    private static boolean hasAnnotation(J.VariableDeclarations field, String annotation) {
        for (J.Annotation a : field.getLeadingAnnotations()) {
            if (TypeUtils.isOfClassType(a.getType(), annotation)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasInitializer(J.VariableDeclarations field) {
        return field.getVariables().size() == 1 && field.getVariables().get(0).getInitializer() != null;
    }

    /**
     * Whether the object under test has no constructor Mockito could inject through, so that it falls
     * back to injecting the fields the replaced call was setting.
     */
    private static boolean onlyHasANoArgumentConstructor(J.VariableDeclarations field) {
        JavaType.FullyQualified type = TypeUtils.asFullyQualified(field.getType());
        if (type == null) {
            return false;
        }
        for (JavaType.Method method : type.getMethods()) {
            if (method.isConstructor() && !method.getParameterTypes().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Whether the field is initialized with a no-argument constructor, which is what `@InjectMocks` replaces. */
    private static boolean constructedWithoutArguments(J.VariableDeclarations field) {
        if (field.getVariables().size() != 1) {
            return false;
        }
        Expression initializer = field.getVariables().get(0).getInitializer();
        return initializer instanceof J.NewClass &&
                ((J.NewClass) initializer).getBody() == null &&
                (((J.NewClass) initializer).getArguments().isEmpty() ||
                        ((J.NewClass) initializer).getArguments().get(0) instanceof J.Empty);
    }
}
