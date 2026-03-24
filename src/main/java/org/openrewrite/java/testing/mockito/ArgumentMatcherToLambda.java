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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;

public class ArgumentMatcherToLambda extends Recipe {

    @Getter
    final String displayName = "Convert `ArgumentMatcher<T>` anonymous class to lambda";

    @Getter
    final String description = "Converts anonymous `ArgumentMatcher<T>` implementations with `matches(Object)` " +
            "to lambda expressions with the correct parameter type. " +
            "In Mockito 1.x, `ArgumentMatcher<T>` extended Hamcrest's `BaseMatcher` and `matches` always took `Object`. " +
            "In Mockito 2+, `ArgumentMatcher<T>` is a functional interface where `matches` takes `T`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>("org.mockito.ArgumentMatcher", false),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        J.NewClass nc = (J.NewClass) super.visitNewClass(newClass, ctx);

                        if (nc.getClazz() == null || nc.getBody() == null) {
                            return nc;
                        }

                        JavaType.Parameterized matcherType = TypeUtils.asParameterized(nc.getClazz().getType());
                        if (matcherType == null ||
                            !TypeUtils.isAssignableTo("org.mockito.ArgumentMatcher", matcherType) ||
                            matcherType.getTypeParameters().isEmpty()) {
                            return nc;
                        }

                        JavaType typeArg = matcherType.getTypeParameters().get(0);

                        // Body must have exactly one method declaration (the matches method)
                        List<Statement> statements = nc.getBody().getStatements();
                        if (statements.size() != 1 || !(statements.get(0) instanceof J.MethodDeclaration)) {
                            return nc;
                        }

                        J.MethodDeclaration matchesMethod = (J.MethodDeclaration) statements.get(0);
                        if (!"matches".equals(matchesMethod.getSimpleName()) ||
                            matchesMethod.getParameters().size() != 1 ||
                            !(matchesMethod.getParameters().get(0) instanceof J.VariableDeclarations)) {
                            return nc;
                        }

                        J.VariableDeclarations param = (J.VariableDeclarations) matchesMethod.getParameters().get(0);
                        String paramName = param.getVariables().get(0).getSimpleName();
                        J.Block body = matchesMethod.getBody();
                        if (body == null) {
                            return nc;
                        }

                        // Remove casts of the parameter to the target type in the body
                        // only when the parameter was Object but the type argument is more specific
                        boolean paramIsObject = TypeUtils.isOfClassType(param.getType(), "java.lang.Object");
                        JavaType.FullyQualified fqType = TypeUtils.asFullyQualified(typeArg);
                        if (paramIsObject && fqType != null && !TypeUtils.isOfClassType(typeArg, "java.lang.Object")) {
                            body = (J.Block) new RemoveParameterCasts(paramName, fqType.getFullyQualifiedName())
                                    .visitNonNull(body, ctx, getCursor());
                        }

                        // Build lambda body: expression for single return, block otherwise
                        J lambdaBody;
                        List<Statement> bodyStatements = body.getStatements();
                        if (bodyStatements.size() == 1 && bodyStatements.get(0) instanceof J.Return) {
                            J.Return returnStmt = (J.Return) bodyStatements.get(0);
                            Expression returnExpr = returnStmt.getExpression();
                            if (returnExpr != null) {
                                lambdaBody = returnExpr.withPrefix(Space.format(" "));
                            } else {
                                return nc;
                            }
                        } else {
                            lambdaBody = body.withPrefix(Space.format(" "));
                        }

                        // Build lambda parameter (inferred type, no parentheses for single param)
                        J.Identifier lambdaParam = new J.Identifier(
                                randomId(), Space.EMPTY, Markers.EMPTY,
                                emptyList(), paramName, typeArg, null
                        );

                        J.Lambda.Parameters lambdaParams = new J.Lambda.Parameters(
                                randomId(), Space.EMPTY, Markers.EMPTY,
                                false,
                                Collections.singletonList(JRightPadded.build(lambdaParam))
                        );

                        J.Lambda lambda = new J.Lambda(
                                randomId(),
                                Space.format(" "),
                                Markers.EMPTY,
                                lambdaParams,
                                Space.format(" "),
                                lambdaBody,
                                matcherType
                        );

                        // Build type cast: (ArgumentMatcher<T>) lambda
                        TypeTree castTypeTree = buildTypeTree(matcherType);
                        J.ControlParentheses<TypeTree> castClazz = new J.ControlParentheses<>(
                                randomId(), Space.EMPTY, Markers.EMPTY,
                                JRightPadded.build(castTypeTree)
                        );

                        return new J.TypeCast(
                                randomId(),
                                nc.getPrefix(),
                                Markers.EMPTY,
                                castClazz,
                                lambda
                        );
                    }
                }
        );
    }

    private static TypeTree buildTypeTree(JavaType type) {
        JavaType.Parameterized parameterized = TypeUtils.asParameterized(type);
        if (parameterized != null) {
            J.Identifier rawType = new J.Identifier(
                    randomId(), Space.EMPTY, Markers.EMPTY,
                    emptyList(), parameterized.getType().getClassName(), parameterized.getType(), null
            );
            List<JRightPadded<Expression>> typeParams = new ArrayList<>(parameterized.getTypeParameters().size());
            for (JavaType tp : parameterized.getTypeParameters()) {
                typeParams.add(JRightPadded.build((Expression) buildTypeTree(tp)));
            }
            return new J.ParameterizedType(
                    randomId(), Space.EMPTY, Markers.EMPTY,
                    rawType,
                    JContainer.build(Space.EMPTY, typeParams, Markers.EMPTY),
                    parameterized
            );
        }
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        return new J.Identifier(
                randomId(), Space.EMPTY, Markers.EMPTY,
                emptyList(), fq != null ? fq.getClassName() : "Object", type, null
        );
    }

    private static class RemoveParameterCasts extends JavaVisitor<ExecutionContext> {
        private final String paramName;
        private final String targetFqn;

        RemoveParameterCasts(String paramName, String targetFqn) {
            this.paramName = paramName;
            this.targetFqn = targetFqn;
        }

        @Override
        public J visitTypeCast(J.TypeCast typeCast, ExecutionContext ctx) {
            J.TypeCast tc = (J.TypeCast) super.visitTypeCast(typeCast, ctx);
            if (tc.getExpression() instanceof J.Identifier) {
                J.Identifier ident = (J.Identifier) tc.getExpression();
                if (paramName.equals(ident.getSimpleName()) &&
                    TypeUtils.isOfClassType(tc.getClazz().getType(), targetFqn)) {
                    return ident.withPrefix(tc.getPrefix());
                }
            }
            return tc;
        }

        @Override
        public <T extends J> J visitParentheses(J.Parentheses<T> parens, ExecutionContext ctx) {
            J result = super.visitParentheses(parens, ctx);
            if (result instanceof J.Parentheses) {
                J.Parentheses<?> p = (J.Parentheses<?>) result;
                if (p.getTree() instanceof J.Identifier &&
                        paramName.equals(((J.Identifier) p.getTree()).getSimpleName())) {
                    return p.unwrap();
                }
            }
            return result;
        }
    }
}
