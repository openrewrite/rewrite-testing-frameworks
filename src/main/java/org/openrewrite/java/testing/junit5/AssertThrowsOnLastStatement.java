/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.testing.junit5;

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.VariableNameUtils;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.staticanalysis.LambdaBlockToExpression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;


public class AssertThrowsOnLastStatement extends Recipe {

    private static final Pattern NUMBER_SUFFIX_PATTERN = Pattern.compile("^(.+?)(\\d+)$");

    @Override
    public String getDisplayName() {
        return "Applies JUnit 5 `assertThrows` on last statement in lambda block only";
    }

    @Override
    public String getDescription() {
        return "Applies JUnit 5 `assertThrows` on last statement in lambda block only. " +
                "In rare cases may cause compilation errors if the lambda uses effectively non final variables. " +
                "In some cases, tests might fail if earlier statements in the lambda block throw exceptions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        MethodMatcher assertThrowsMatcher = new MethodMatcher(
                "org.junit.jupiter.api.Assertions assertThrows(java.lang.Class, org.junit.jupiter.api.function.Executable, ..)");
        return Preconditions.check(new UsesMethod<>(assertThrowsMatcher), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDecl, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(methodDecl, ctx);
                if (m.getBody() == null) {
                    return m;
                }
                doAfterVisit(new LambdaBlockToExpression().getVisitor());
                return m.withBody(m.getBody().withStatements(ListUtils.flatMap(m.getBody().getStatements(), methodStatement -> {
                    J statementToCheck = methodStatement;
                    final J.VariableDeclarations assertThrowsWithVarDec;
                    final J.VariableDeclarations.NamedVariable assertThrowsVar;

                    if (methodStatement instanceof J.VariableDeclarations) {
                        assertThrowsWithVarDec = (J.VariableDeclarations) methodStatement;
                        List<J.VariableDeclarations.NamedVariable> assertThrowsNamedVars = assertThrowsWithVarDec.getVariables();
                        if (assertThrowsNamedVars.size() != 1) {
                            return methodStatement;
                        }

                        // has variable declaration for assertThrows eg Throwable ex = assertThrows(....)
                        assertThrowsVar = assertThrowsNamedVars.get(0);
                        statementToCheck = assertThrowsVar.getInitializer();
                    } else {
                        assertThrowsWithVarDec = null;
                        assertThrowsVar = null;
                    }

                    if (!(statementToCheck instanceof J.MethodInvocation)) {
                        return methodStatement;
                    }

                    J.MethodInvocation methodInvocation = (J.MethodInvocation) statementToCheck;
                    if (!assertThrowsMatcher.matches(methodInvocation)) {
                        return methodStatement;
                    }

                    List<Expression> arguments = methodInvocation.getArguments();
                    if (arguments.size() <= 1) {
                        return methodStatement;
                    }

                    Expression arg = arguments.get(1);
                    if (!(arg instanceof J.Lambda)) {
                        return methodStatement;
                    }

                    J.Lambda lambda = (J.Lambda) arg;
                    if (!(lambda.getBody() instanceof J.Block)) {
                        return methodStatement;
                    }

                    J.Block body = (J.Block) lambda.getBody();
                    List<Statement> lambdaStatements = body.getStatements();

                    // TODO Check to see if last line in lambda does not use a non-final variable

                    // move all the statements from the body into before the method invocation, except last one
                    return ListUtils.flatMap(lambdaStatements, (idx, lambdaStatement) -> {
                        if (idx < lambdaStatements.size() - 1) {
                            return lambdaStatement.withPrefix(methodStatement.getPrefix().withComments(emptyList()));
                        }

                        List<Statement> variableAssignments = new ArrayList<>();
                        Space prefix = methodStatement.getPrefix().withComments(emptyList());
                        final Statement newLambdaStatement = extractExpressionArguments(lambdaStatement, variableAssignments, prefix);
                        J.MethodInvocation newAssertThrows = methodInvocation.withArguments(
                                ListUtils.map(arguments, (argIdx, argument) -> {
                                    if (argIdx == 1) {
                                        // Only retain the last statement in the lambda block
                                        return lambda.withBody(body.withStatements(singletonList(newLambdaStatement)));
                                    }
                                    return argument;
                                })
                        );

                        if (assertThrowsWithVarDec == null) {
                            variableAssignments.add(newAssertThrows);
                            return variableAssignments;
                        }

                        J.VariableDeclarations.NamedVariable newAssertThrowsVar = assertThrowsVar.withInitializer(newAssertThrows);
                        variableAssignments.add(assertThrowsWithVarDec.withVariables(singletonList(newAssertThrowsVar)));
                        return variableAssignments;
                    });
                })));
            }

            private Statement extractExpressionArguments(Statement lambdaStatement, List<Statement> precedingVars, Space varPrefix) {
                if (lambdaStatement instanceof J.MethodInvocation) {
                    J.MethodInvocation mi = (J.MethodInvocation) lambdaStatement;
                    Map<String, Integer> generatedVariableSuffixes = new HashMap<>();
                    return mi.withArguments(ListUtils.map(mi.getArguments(), e -> {
                        if (e instanceof J.Identifier || e instanceof J.Literal || e instanceof J.Empty || e == null) {
                            return e;
                        }

                        Object variableTypeShort = "Object";
                        JavaType variableTypeFqn = null;
                        if (e.getType() instanceof JavaType.Primitive) {
                            variableTypeShort = e.getType().toString();
                            variableTypeFqn = e.getType();
                        } else if (e.getType() instanceof JavaType.FullyQualified) {
                            JavaType.FullyQualified aClass = (JavaType.FullyQualified) e.getType();
                            variableTypeShort = aClass.getClassName();
                            variableTypeFqn = aClass;
                            maybeAddImport(aClass.getFullyQualifiedName(), false);
                        }

                        JavaTemplate.Builder builder = JavaTemplate.builder("#{} #{} = #{any()};");
                        J.VariableDeclarations varDecl = builder.build()
                                .apply(new Cursor(getCursor(), lambdaStatement),
                                        lambdaStatement.getCoordinates().replace(),
                                        variableTypeShort, getVariableName(e, generatedVariableSuffixes), e);
                        precedingVars.add(varDecl
                                .withPrefix(varPrefix).withType(variableTypeFqn));
                        return varDecl.getVariables().get(0).getName()
                                .withPrefix(e.getPrefix()).withType(variableTypeFqn);
                    }));
                }
                return lambdaStatement;
            }

            private String getVariableName(Expression e, Map<String, Integer> generatedVariableSuffixes) {
                String variableName;
                if (e instanceof J.MethodInvocation) {
                    String name = ((J.MethodInvocation) e).getSimpleName();
                    name = name.replaceAll("^get", "");
                    name = name.replaceAll("^is", "");
                    name = StringUtils.uncapitalize(name);
                    variableName = VariableNameUtils.generateVariableName(!name.isEmpty() ? name : "x", new Cursor(getCursor(), e), VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER);
                } else {
                    variableName = VariableNameUtils.generateVariableName("x", new Cursor(getCursor(), e), VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER);
                }
                Matcher matcher = NUMBER_SUFFIX_PATTERN.matcher(variableName);
                if (matcher.matches()) {
                    String prefix = matcher.group(1);
                    int suffix = Integer.parseInt(matcher.group(2));
                    generatedVariableSuffixes.putIfAbsent(prefix, suffix);
                    variableName = prefix;
                }
                if (generatedVariableSuffixes.containsKey(variableName)) {
                    int suffix = generatedVariableSuffixes.get(variableName);
                    generatedVariableSuffixes.put(variableName, suffix + 1);
                    variableName += suffix;
                } else {
                    generatedVariableSuffixes.put(variableName, 1);
                }
                return variableName;
            }
        });
    }
}
