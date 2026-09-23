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
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class ReplaceSettersWithTransform extends Recipe {

    private static final String STUB_MAPPING = "com.github.tomakehurst.wiremock.stubbing.StubMapping";
    private static final String RESPONSE_DEFINITION = "com.github.tomakehurst.wiremock.http.ResponseDefinition";
    private static final String TRANSFORM = "transform";
    private static final String CONSUMER = "java.util.function.Consumer";

    /**
     * For each immutable type, the WireMock 3 setter names mapped onto the name of the equivalent
     * setter on the WireMock 4 builder. Setters without a builder equivalent, such as
     * {@code StubMapping#setDirty(boolean)}, are deliberately absent so that they are left in place
     * for the compiler to flag.
     */
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

    @Getter
    final String displayName = "Replace WireMock setter calls with `transform()`";

    @Getter
    final String description = "WireMock 4 made `StubMapping` and `ResponseDefinition` immutable, removing their " +
            "setters in favour of a builder reached through `transform(Consumer<Builder>)`. Rewrite in place " +
            "mutation such as `stubMapping.setRequest(pattern);` to `stubMapping = stubMapping.transform(builder -> " +
            "builder.setRequest(pattern));`, collapsing consecutive setter calls on the same receiver into a single " +
            "`transform` call.";

    @SuppressWarnings("unchecked")
    private static TreeVisitor<?, ExecutionContext>[] usesAnySetter() {
        return SETTER_MATCHERS.values().stream()
                .map(matcher -> (TreeVisitor<?, ExecutionContext>) new UsesMethod<ExecutionContext>(matcher))
                .toArray(TreeVisitor[]::new);
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

                            // Collect the longest run of setter calls on the same receiver that can be chained
                            List<SetterCall> run = new ArrayList<>();
                            run.add(first);
                            int next = i + 1;
                            while (next < statements.size()) {
                                SetterCall candidate = asConvertibleSetterCall(statements.get(next));
                                if (candidate == null ||
                                        !candidate.getOwner().equals(first.getOwner()) ||
                                        !candidate.getReceiverKey().equals(first.getReceiverKey()) ||
                                        // A later argument reading the receiver would see the pre-transform value
                                        referencesReceiver(candidate.getArgument(), first.getReceiverName())) {
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

                    /**
                     * @return the setter call if {@code statement} is an in place mutation this recipe knows how to
                     * rewrite, or {@code null} if it should be left alone.
                     */
                    private @Nullable SetterCall asConvertibleSetterCall(Statement statement) {
                        if (!(statement instanceof J.MethodInvocation)) {
                            return null;
                        }
                        J.MethodInvocation method = (J.MethodInvocation) statement;
                        if (method.getArguments().size() != 1 || method.getSelect() == null) {
                            return null;
                        }
                        for (Map.Entry<String, Map<String, String>> entry : BUILDER_SETTERS.entrySet()) {
                            String owner = entry.getKey();
                            if (!SETTER_MATCHERS.get(owner).matches(method)) {
                                continue;
                            }
                            String builderSetter = entry.getValue().get(method.getSimpleName());
                            if (builderSetter == null) {
                                return null;
                            }
                            Expression select = method.getSelect();
                            if (!isAssignable(select)) {
                                return null;
                            }
                            // Assigning the transformed copy back costs a local its effective finality, so an
                            // argument reading it cannot be moved inside the lambda that now captures it
                            if (isLocal(select) &&
                                    referencesReceiver(method.getArguments().get(0), receiverName(select))) {
                                return null;
                            }
                            return new SetterCall(owner, builderSetter, select,
                                    select.printTrimmed(getCursor()), receiverName(select),
                                    method.getArguments().get(0));
                        }
                        return null;
                    }

                    /**
                     * Assigning the transformed copy back only compiles when the receiver is a variable or field we
                     * can write to.
                     */
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

                    private boolean referencesReceiver(Expression argument, String receiverName) {
                        AtomicBoolean found = new AtomicBoolean();
                        new JavaIsoVisitor<AtomicBoolean>() {
                            @Override
                            public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean found) {
                                if (receiverName.equals(identifier.getSimpleName())) {
                                    found.set(true);
                                }
                                return identifier;
                            }
                        }.visit(argument, found);
                        return found.get();
                    }

                    private Statement toTransform(List<SetterCall> run, Statement original) {
                        SetterCall first = run.get(0);
                        String lambdaParameter = VariableNameUtils.generateVariableName("builder", getCursor(),
                                VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER);

                        StringBuilder code = new StringBuilder("#{any(").append(first.getOwner()).append(")}")
                                .append(".transform(").append(lambdaParameter)
                                .append(" -> ").append(lambdaParameter);
                        List<Object> parameters = new ArrayList<>();
                        parameters.add(first.getReceiver());
                        for (SetterCall call : run) {
                            code.append('.').append(call.getBuilderSetter()).append("(#{any()})");
                            parameters.add(call.getArgument());
                        }
                        code.append(')');

                        // Only the `transform` call is templated; wrapping it in the assignment by hand keeps the
                        // receiver's own type information, which a `#{any()}` placeholder cannot carry as an lvalue
                        Expression transform = JavaTemplate.builder(code.toString())
                                .build()
                                .apply(new Cursor(getCursor(), original), original.getCoordinates().replace(),
                                        parameters.toArray());
                        transform = attributeWiremock4Api(transform, lambdaParameter, first.getOwner());

                        return new J.Assignment(
                                Tree.randomId(),
                                original.getPrefix(),
                                original.getMarkers(),
                                copyWithNewIds(first.getReceiver()).withPrefix(Space.EMPTY),
                                JLeftPadded.<Expression>build(transform.withPrefix(Space.SINGLE_SPACE))
                                        .withBefore(Space.SINGLE_SPACE),
                                first.getReceiver().getType());
                    }

                    /**
                     * `transform` and its builder only exist in WireMock 4, while the source being migrated still
                     * compiles against WireMock 3, so the template cannot attribute either of them.
                     */
                    private Expression attributeWiremock4Api(Expression transform, String lambdaParameter, String owner) {
                        JavaType.Class ownerType = JavaType.ShallowClass.build(owner);
                        JavaType.Class builderType = JavaType.ShallowClass.build(owner + "$Builder");
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
                                            "transformer", JavaType.ShallowClass.build(CONSUMER)));
                                }
                                if (!onBuilder(m.getSelect(), builderType)) {
                                    return m;
                                }
                                Expression argument = m.getArguments().get(0);
                                return m.withMethodType(methodType(builderType, m.getSimpleName(), builderType,
                                        "value", argument.getType() == null ?
                                                JavaType.Unknown.getInstance() : argument.getType()));
                            }
                        }.visitNonNull(transform, 0);
                    }

                    private JavaType.Method methodType(JavaType.FullyQualified declaringType, String name,
                                                       JavaType returnType, String parameterName,
                                                       JavaType parameterType) {
                        return new JavaType.Method(null, Flag.Public.getBitMask(), declaringType, name, returnType,
                                singletonList(parameterName), singletonList(parameterType),
                                emptyList(), emptyList(), emptyList(), emptyList());
                    }

                    /** Whether {@code select} is the builder the lambda received, or an earlier call chained on it. */
                    private boolean onBuilder(@Nullable Expression select, JavaType.Class builderType) {
                        if (select instanceof J.Identifier) {
                            return builderType.equals(((J.Identifier) select).getType());
                        }
                        return select instanceof J.MethodInvocation &&
                                ((J.MethodInvocation) select).getMethodType() != null &&
                                builderType.equals(((J.MethodInvocation) select).getMethodType().getDeclaringType());
                    }

                    /**
                     * The receiver appears both as the assignment target and inside the `transform` call, and every
                     * element of an LST has to carry its own id.
                     */
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
    private static class SetterCall {
        String owner;
        String builderSetter;
        Expression receiver;
        String receiverKey;
        String receiverName;
        Expression argument;
    }
}
