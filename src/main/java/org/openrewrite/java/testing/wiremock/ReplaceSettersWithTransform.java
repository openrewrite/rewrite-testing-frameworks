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
package org.openrewrite.java.testing.wiremock;

import lombok.Getter;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.VariableNameUtils;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

public class ReplaceSettersWithTransform extends Recipe {

    private static final String STUB_MAPPING = "com.github.tomakehurst.wiremock.stubbing.StubMapping";
    private static final String RESPONSE_DEFINITION = "com.github.tomakehurst.wiremock.http.ResponseDefinition";
    private static final String METADATA = "com.github.tomakehurst.wiremock.common.Metadata";
    private static final String PARAMETERS = "com.github.tomakehurst.wiremock.extension.Parameters";
    private static final String TRANSFORM = "transform";
    private static final String CONSUMER = "java.util.function.Consumer";

    // WireMock 3 setter names mapped onto their WireMock 4 builder equivalents. Setters without one, such as
    // `StubMapping#setDirty(boolean)`, are deliberately absent so the compiler flags them where they are left.
    private static final Map<String, Map<String, String>> BUILDER_SETTERS = new HashMap<>();

    static {
        Map<String, String> stubMapping = new LinkedHashMap<>();
        for (String sameName : asList(
                "setId",
                "setInsertionIndex",
                "setMetadata",
                "setName",
                "setNewScenarioState",
                "setPersistent",
                "setPostServeActions",
                "setPriority",
                "setRequest",
                "setRequiredScenarioState",
                "setResponse",
                "setScenarioName",
                "setServeEventListeners")) {
            stubMapping.put(sameName, sameName);
        }
        // The builder dropped the `uuid` alias for `id`, and the longer serve event listener name
        stubMapping.put("setUuid", "setId");
        stubMapping.put("setServeEventListenerDefinitions", "setServeEventListeners");
        BUILDER_SETTERS.put(STUB_MAPPING, stubMapping);

        Map<String, String> responseDefinition = new LinkedHashMap<>();
        responseDefinition.put("setOriginalRequest", "setOriginalRequest");
        BUILDER_SETTERS.put(RESPONSE_DEFINITION, responseDefinition);
    }

    private static final Map<String, MethodMatcher> SETTER_MATCHERS = new HashMap<>();

    static {
        BUILDER_SETTERS.forEach((type, setters) -> SETTER_MATCHERS.put(type, new MethodMatcher(type + " set*(..)")));
    }

    // WireMock 3's `Metadata` and its `Parameters` subclass were a `LinkedHashMap`; 4.x keeps the `Map` interface
    // but throws from every mutator, so `put` compiles as before and only fails once the test runs.
    private static final MethodMatcher MAP_PUT = new MethodMatcher("java.util.Map put(..)", true);

    @Getter
    final String displayName = "Replace WireMock setter calls with `transform()`";

    @Getter
    final String description = "WireMock 4 made `StubMapping`, `ResponseDefinition`, `Metadata` and `Parameters` " +
            "immutable, removing their setters in favour of a builder reached through `transform(Consumer<Builder>)`. " +
            "Rewrite in place mutation such as `stubMapping.setRequest(pattern);` to `stubMapping = " +
            "stubMapping.transform(builder -> builder.setRequest(pattern));`, and `parameters.put(key, value);` to " +
            "`parameters = parameters.transform(builder -> builder.attr(key, value));`, collapsing consecutive calls " +
            "on the same receiver into a single `transform` call. The `Map` mutators on `Metadata` still compile on " +
            "4.x but throw `UnsupportedOperationException`, so those left behind, for instance on a receiver that " +
            "cannot be assigned to, deserve a look.";

    @SuppressWarnings("unchecked")
    private static TreeVisitor<?, ExecutionContext>[] usesAnySetter() {
        return Stream.concat(
                        SETTER_MATCHERS.values().stream()
                                .map(matcher -> (TreeVisitor<?, ExecutionContext>) new UsesMethod<ExecutionContext>(matcher)),
                        Stream.of(new UsesType<ExecutionContext>(METADATA, false), new UsesType<ExecutionContext>(PARAMETERS, false)))
                .toArray(TreeVisitor[]::new);
    }

    /**
     * The builder call standing in for a WireMock 3 mutator, or {@code null} when there is none.
     */
    private static @Nullable BuilderCall builderCallFor(J.MethodInvocation method) {
        Expression select = method.getSelect();
        if (select == null) {
            return null;
        }
        if (method.getArguments().size() == 2 && MAP_PUT.matches(method)) {
            // Only the exact types: `transform` hands back a `Metadata` or `Parameters`, not a user's subclass
            if (TypeUtils.isOfClassType(select.getType(), PARAMETERS)) {
                return new BuilderCall(PARAMETERS, METADATA + "$Builder", "attr");
            }
            if (TypeUtils.isOfClassType(select.getType(), METADATA)) {
                return new BuilderCall(METADATA, METADATA + "$Builder", "attr");
            }
            return null;
        }
        if (method.getArguments().size() != 1) {
            return null;
        }
        for (Map.Entry<String, Map<String, String>> entry : BUILDER_SETTERS.entrySet()) {
            String owner = entry.getKey();
            if (SETTER_MATCHERS.get(owner).matches(method)) {
                String builderSetter = entry.getValue().get(method.getSimpleName());
                return builderSetter == null ? null : new BuilderCall(owner, owner + "$Builder", builderSetter);
            }
        }
        return null;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(usesAnySetter()),
                new JavaIsoVisitor<ExecutionContext>() {

                    @Override
                    public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                        J.Block b = super.visitBlock(block, ctx);

                        List<Statement> statements = b.getStatements();
                        List<Statement> rewritten = new ArrayList<>(statements.size());
                        boolean changed = false;
                        for (int i = 0; i < statements.size(); ) {
                            SetterCall first = asConvertibleSetterCall(statements.get(i));
                            if (first == null) {
                                rewritten.add(statements.get(i++));
                                continue;
                            }

                            List<SetterCall> run = new ArrayList<>();
                            run.add(first);
                            int next = i + 1;
                            while (next < statements.size()) {
                                SetterCall candidate = asConvertibleSetterCall(statements.get(next));
                                if (candidate == null ||
                                        !candidate.getBuilderCall().getOwner().equals(first.getBuilderCall().getOwner()) ||
                                        !candidate.getReceiverKey().equals(first.getReceiverKey()) ||
                                        // A later argument reading the receiver would see the pre-transform value
                                        referencesAny(candidate.getArguments(), singleton(first.getReceiverName()))) {
                                    break;
                                }
                                run.add(candidate);
                                next++;
                            }

                            rewritten.add(toTransform(run, statements.get(i)));
                            changed = true;
                            i = next;
                        }

                        return changed ? b.withStatements(rewritten) : b;
                    }

                    private @Nullable SetterCall asConvertibleSetterCall(Statement statement) {
                        if (!(statement instanceof J.MethodInvocation)) {
                            return null;
                        }
                        J.MethodInvocation method = (J.MethodInvocation) statement;
                        BuilderCall builderCall = builderCallFor(method);
                        Expression select = method.getSelect();
                        if (builderCall == null || select == null || !isAssignable(select)) {
                            return null;
                        }
                        // The arguments move inside a lambda, where any local they read has to be effectively
                        // final; locals assigned to anywhere, receivers this recipe assigns to included, are not
                        Set<String> reassigned = reassignedLocals();
                        if (isLocal(select)) {
                            reassigned.add(receiverName(select));
                        }
                        if (referencesAny(method.getArguments(), reassigned)) {
                            return null;
                        }
                        return new SetterCall(builderCall, select, select.printTrimmed(getCursor()),
                                receiverName(select), method.getArguments());
                    }

                    // Names of the locals and parameters in the enclosing method that are not effectively final,
                    // or will not be once their mutators become `transform` assignments.
                    private Set<String> reassignedLocals() {
                        Set<String> reassigned = new HashSet<>();
                        J scope = getCursor().firstEnclosing(J.MethodDeclaration.class);
                        if (scope == null) {
                            scope = getCursor().firstEnclosing(J.ClassDeclaration.class);
                        }
                        if (scope == null) {
                            return reassigned;
                        }
                        new JavaIsoVisitor<Set<String>>() {
                            @Override
                            public J.Assignment visitAssignment(J.Assignment assignment, Set<String> names) {
                                addIfLocal(assignment.getVariable(), names);
                                return super.visitAssignment(assignment, names);
                            }

                            @Override
                            public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignment, Set<String> names) {
                                addIfLocal(assignment.getVariable(), names);
                                return super.visitAssignmentOperation(assignment, names);
                            }

                            @Override
                            public J.Unary visitUnary(J.Unary unary, Set<String> names) {
                                if (unary.getOperator().isModifying()) {
                                    addIfLocal(unary.getExpression(), names);
                                }
                                return super.visitUnary(unary, names);
                            }

                            @Override
                            public J.ForLoop visitForLoop(J.ForLoop forLoop, Set<String> names) {
                                // A classic `for` loop counter is updated by the loop itself
                                for (Statement init : forLoop.getControl().getInit()) {
                                    if (init instanceof J.VariableDeclarations) {
                                        for (J.VariableDeclarations.NamedVariable variable : ((J.VariableDeclarations) init).getVariables()) {
                                            names.add(variable.getSimpleName());
                                        }
                                    }
                                }
                                return super.visitForLoop(forLoop, names);
                            }

                            @Override
                            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Set<String> names) {
                                if (builderCallFor(method) != null) {
                                    addIfLocal(method.getSelect(), names);
                                }
                                return super.visitMethodInvocation(method, names);
                            }

                            private void addIfLocal(@Nullable Expression expression, Set<String> names) {
                                if (expression != null && isLocal(expression)) {
                                    names.add(((J.Identifier) expression).getSimpleName());
                                }
                            }
                        }.visit(scope, reassigned);
                        return reassigned;
                    }

                    // Assigning the copy back only compiles when the receiver is something we can write to.
                    private boolean isAssignable(Expression select) {
                        if (select instanceof J.FieldAccess) {
                            JavaType.Variable field = ((J.FieldAccess) select).getName().getFieldType();
                            if (field == null || field.hasFlags(Flag.Final)) {
                                return false;
                            }
                            // `this.mapping` and `Holder.MAPPING` style receivers are safe to repeat and assign to
                            return isSimpleAccessChain(((J.FieldAccess) select).getTarget());
                        }
                        if (!(select instanceof J.Identifier)) {
                            return false;
                        }
                        JavaType.Variable variable = ((J.Identifier) select).getFieldType();
                        if (variable == null || variable.hasFlags(Flag.Final)) {
                            return false;
                        }
                        // A local captured by a lambda has to stay effectively final, so leave those alone
                        return variable.getOwner() instanceof JavaType.FullyQualified || !withinLambda();
                    }

                    private boolean isLocal(Expression select) {
                        if (!(select instanceof J.Identifier)) {
                            return false;
                        }
                        JavaType.Variable variable = ((J.Identifier) select).getFieldType();
                        return variable != null && !(variable.getOwner() instanceof JavaType.FullyQualified);
                    }

                    private String receiverName(Expression select) {
                        if (select instanceof J.FieldAccess) {
                            return ((J.FieldAccess) select).getSimpleName();
                        }
                        return ((J.Identifier) select).getSimpleName();
                    }

                    private boolean isSimpleAccessChain(Expression expression) {
                        if (expression instanceof J.Identifier) {
                            return true;
                        }
                        return expression instanceof J.FieldAccess &&
                                isSimpleAccessChain(((J.FieldAccess) expression).getTarget());
                    }

                    private boolean withinLambda() {
                        for (Iterator<Object> path = getCursor().getPath(); path.hasNext(); ) {
                            Object parent = path.next();
                            if (parent instanceof J.Lambda) {
                                return true;
                            }
                            if (parent instanceof J.MethodDeclaration) {
                                return false;
                            }
                        }
                        return false;
                    }

                    private boolean referencesAny(List<Expression> arguments, Set<String> names) {
                        AtomicBoolean found = new AtomicBoolean();
                        JavaIsoVisitor<AtomicBoolean> visitor = new JavaIsoVisitor<AtomicBoolean>() {
                            @Override
                            public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean found) {
                                if (names.contains(identifier.getSimpleName())) {
                                    found.set(true);
                                }
                                return identifier;
                            }
                        };
                        for (Expression argument : arguments) {
                            visitor.visit(argument, found);
                        }
                        return found.get();
                    }

                    private Statement toTransform(List<SetterCall> run, Statement original) {
                        SetterCall first = run.get(0);
                        String lambdaParameter = VariableNameUtils.generateVariableName("builder", getCursor(),
                                VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER);

                        StringBuilder code = new StringBuilder("#{any(").append(first.getBuilderCall().getOwner()).append(")}")
                                .append(".transform(").append(lambdaParameter)
                                .append(" -> ").append(lambdaParameter);
                        List<Object> parameters = new ArrayList<>();
                        parameters.add(first.getReceiver());
                        // A run of setters reads better one per line than as a single long chain
                        String chainIndent = "\n" + original.getPrefix().getIndent() + "        ";
                        for (SetterCall call : run) {
                            if (run.size() > 1) {
                                code.append(chainIndent);
                            }
                            code.append('.').append(call.getBuilderCall().getMethod()).append('(');
                            for (int a = 0; a < call.getArguments().size(); a++) {
                                code.append(a == 0 ? "#{any()}" : ", #{any()}");
                                parameters.add(call.getArguments().get(a));
                            }
                            code.append(')');
                        }
                        code.append(')');

                        // Only the `transform` call is templated; wrapping it in the assignment by hand keeps the
                        // receiver's own type information, which a `#{any()}` placeholder cannot carry as an lvalue
                        Expression transform = JavaTemplate.apply(code.toString(), new Cursor(getCursor(), original), original.getCoordinates().replace(), parameters.toArray());
                        transform = attributeWiremock4Api(transform, lambdaParameter, first.getBuilderCall());

                        return new J.Assignment(
                                Tree.randomId(),
                                original.getPrefix(),
                                original.getMarkers(),
                                copyWithNewIds(first.getReceiver()).withPrefix(Space.EMPTY),
                                JLeftPadded.<Expression>build(transform.withPrefix(Space.SINGLE_SPACE))
                                        .withBefore(Space.SINGLE_SPACE),
                                first.getReceiver().getType());
                    }

                    // `transform` and its builder only exist in WireMock 4, so the template cannot attribute them
                    // against the WireMock 3 the source still compiles against.
                    private Expression attributeWiremock4Api(Expression transform, String lambdaParameter, BuilderCall builderCall) {
                        JavaType.Class ownerType = JavaType.ShallowClass.build(builderCall.getOwner());
                        JavaType.Class builderType = JavaType.ShallowClass.build(builderCall.getBuilder());
                        return (Expression) new JavaIsoVisitor<Integer>() {
                            @Override
                            public J.Identifier visitIdentifier(J.Identifier identifier, Integer unused) {
                                if (lambdaParameter.equals(identifier.getSimpleName())) {
                                    return identifier.withType(builderType);
                                }
                                return identifier;
                            }

                            @Override
                            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Integer unused) {
                                J.MethodInvocation m = super.visitMethodInvocation(method, unused);
                                if (m.getMethodType() != null) {
                                    return m;
                                }
                                if (TRANSFORM.equals(m.getSimpleName()) && !onBuilder(m.getSelect(), builderType)) {
                                    return m.withMethodType(methodType(ownerType, TRANSFORM, ownerType,
                                            singletonList("transformer"), singletonList(JavaType.ShallowClass.build(CONSUMER))));
                                }
                                if (!onBuilder(m.getSelect(), builderType)) {
                                    return m;
                                }
                                List<String> parameterNames = new ArrayList<>();
                                List<JavaType> parameterTypes = new ArrayList<>();
                                for (Expression argument : m.getArguments()) {
                                    parameterNames.add("arg" + parameterNames.size());
                                    parameterTypes.add(argument.getType() == null ?
                                            JavaType.Unknown.getInstance() : argument.getType());
                                }
                                return m.withMethodType(methodType(builderType, m.getSimpleName(), builderType,
                                        parameterNames, parameterTypes));
                            }
                        }.visitNonNull(transform, 0);
                    }

                    private JavaType.Method methodType(JavaType.FullyQualified declaringType, String name,
                                                       JavaType returnType, List<String> parameterNames,
                                                       List<JavaType> parameterTypes) {
                        return new JavaType.Method(null, Flag.Public.getBitMask(), declaringType, name, returnType,
                                parameterNames, parameterTypes,
                                emptyList(), emptyList(), emptyList(), emptyList());
                    }

                    // Whether `select` is the builder the lambda received, or an earlier call chained on it.
                    private boolean onBuilder(@Nullable Expression select, JavaType.Class builderType) {
                        if (select instanceof J.Identifier) {
                            return TypeUtils.isOfType(builderType, ((J.Identifier) select).getType());
                        }
                        return select instanceof J.MethodInvocation &&
                                ((J.MethodInvocation) select).getMethodType() != null &&
                                TypeUtils.isOfType(builderType, ((J.MethodInvocation) select).getMethodType().getDeclaringType());
                    }

                    // The receiver appears twice in the output, and every element of an LST needs its own id.
                    private Expression copyWithNewIds(Expression expression) {
                        return (Expression) new JavaIsoVisitor<Integer>() {
                            @Override
                            public @Nullable J visit(@Nullable Tree tree, Integer unused) {
                                J visited = super.visit(tree, unused);
                                return visited == null ? null : visited.withId(Tree.randomId());
                            }
                        }.visitNonNull(expression, 0);
                    }
                });
    }

    @Value
    private static class BuilderCall {
        String owner;
        String builder;
        String method;
    }

    @Value
    private static class SetterCall {
        BuilderCall builderCall;
        Expression receiver;
        String receiverKey;
        String receiverName;
        List<Expression> arguments;
    }
}
