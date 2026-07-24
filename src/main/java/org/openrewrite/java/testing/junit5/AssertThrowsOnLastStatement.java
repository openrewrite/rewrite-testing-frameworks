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

import lombok.Getter;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.VariableNameUtils;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.kotlin.KotlinTemplate;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.staticanalysis.LambdaBlockToExpression;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;


public class AssertThrowsOnLastStatement extends Recipe {

    private static final Pattern NUMBER_SUFFIX_PATTERN = Pattern.compile("^(.+?)(\\d+)$");

    @Getter
    final String displayName = "Applies JUnit 5 `assertThrows` on last statement in lambda block only";

    @Getter
    final String description = "Applies JUnit 5 `assertThrows` on last statement in lambda block only. " +
            "In rare cases may cause compilation errors if the lambda uses effectively non final variables. " +
            "In some cases, tests might fail if earlier statements in the lambda block throw exceptions.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        MethodMatcher assertThrowsMatcher = new MethodMatcher(
                "org.junit.jupiter.api.Assertions assertThrows(java.lang.Class, org.junit.jupiter.api.function.Executable, ..)");
        return Preconditions.check(new UsesMethod<>(assertThrowsMatcher), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                // Only Java and Kotlin are supported, as the extracted variable declaration is rendered per language
                return (sourceFile instanceof J.CompilationUnit || sourceFile instanceof K.CompilationUnit) &&
                        super.isAcceptable(sourceFile, ctx);
            }

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
                        final Statement newLambdaStatement = extractExpressionArguments(body, lambdaStatement, variableAssignments, prefix);
                        J.MethodInvocation newAssertThrows = methodInvocation.withArguments(
                                ListUtils.map(arguments, (argIdx, argument) -> {
                                    // The second argument is the lambda which is tested.
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

            private Statement extractExpressionArguments(J.Block body, Statement lambdaStatement, List<Statement> precedingVars, Space varPrefix) {
                if (lambdaStatement instanceof J.MethodInvocation) {
                    boolean kotlin = getCursor().firstEnclosing(K.CompilationUnit.class) != null;
                    J.MethodInvocation mi = (J.MethodInvocation) lambdaStatement;
                    Map<String, Integer> generatedVariableSuffixes = new HashMap<>();
                    return mi.withArguments(ListUtils.map(mi.getArguments(), e -> {
                        if (e instanceof J.Identifier || e instanceof J.Literal || e instanceof J.Empty || e instanceof J.Lambda || e instanceof J.TypeCast || e instanceof J.FieldAccess) {
                            return e;
                        }

                        // Unattributed (`None`/`Unknown`), `void` or `null` typed expressions can't be rendered as a
                        // typed variable declaration; leave them inline rather than generating an invalid template
                        // (`= expr` assignments or `void`/`<unknown>` declarations that fail to parse)
                        JavaType type = e.getType();
                        if (type instanceof JavaType.Unknown ||
                                type == JavaType.Primitive.None || type == JavaType.Primitive.Void || type == JavaType.Primitive.Null) {
                            return e;
                        }

                        String variableName = getVariableName(e, generatedVariableSuffixes);

                        // Kotlin infers the type; add `val name = expr` as a statement on the method body, since replacing
                        // the expression in place would wrap the template as `var o = <template>` and fail to parse
                        if (kotlin) {
                            J.Block methodBody = getCursor().firstEnclosingOrThrow(J.MethodDeclaration.class).getBody();
                            Cursor bodyCursor = new Cursor(getCursor(), methodBody);
                            J.Block applied = KotlinTemplate.builder("val #{} = #{any()}")
                                    .build()
                                    .apply(bodyCursor, methodBody.getCoordinates().lastStatement(), variableName, e);
                            List<Statement> appliedStatements = applied.getStatements();
                            J.VariableDeclarations varDecl = (J.VariableDeclarations) appliedStatements.get(appliedStatements.size() - 1);
                            precedingVars.add(varDecl.withPrefix(varPrefix).withType(e.getType()));
                            return varDecl.getVariables().get(0).getName().withPrefix(e.getPrefix()).withType(e.getType());
                        }

                        Object variableTypeShort = "Object";
                        JavaType variableTypeFqn = null;
                        if (type instanceof JavaType.Primitive) {
                            variableTypeShort = type.toString();
                            variableTypeFqn = type;
                        } else if (type instanceof JavaType.Parameterized) {
                            JavaType.Parameterized paramType = (JavaType.Parameterized) type;
                            // TODO look into possibly employing `TypeUtils.toString()` here, possibly with some changes upstream allowing for non-fully-qualified names
                            variableTypeShort = buildParameterizedTypeName(paramType);
                            variableTypeFqn = paramType;
                            maybeAddImport(paramType.getFullyQualifiedName(), false);
                            for (JavaType typeParam : paramType.getTypeParameters()) {
                                if (typeParam instanceof JavaType.FullyQualified) {
                                    maybeAddImport(((JavaType.FullyQualified) typeParam).getFullyQualifiedName(), false);
                                }
                            }
                        } else if (type instanceof JavaType.FullyQualified) {
                            JavaType.FullyQualified aClass = (JavaType.FullyQualified) type;
                            variableTypeShort = aClass.getClassName();
                            variableTypeFqn = aClass;
                            maybeAddImport(aClass.getFullyQualifiedName(), false);
                        }

                        Cursor blockCursor = new Cursor(getCursor(), body);
                        Cursor c = new Cursor(blockCursor, lambdaStatement);
                        try {
                            J.VariableDeclarations varDecl = JavaTemplate.apply("#{} #{} = #{any()};", c, lambdaStatement.getCoordinates().replace(), variableTypeShort, variableName, e);
                            precedingVars.add(varDecl.withPrefix(varPrefix).withType(variableTypeFqn));
                            return varDecl.getVariables().get(0).getName().withPrefix(e.getPrefix()).withType(variableTypeFqn);
                        } catch (Exception ex) {
                            // Some types (e.g. anonymous or local classes) don't render as a valid variable
                            // declaration; leave the argument inline rather than failing the whole recipe run
                            return e;
                        }
                    }));
                }
                return lambdaStatement;
            }

            private String getVariableName(Expression e, Map<String, Integer> generatedVariableSuffixes) {
                String variableName;
                if (e instanceof J.MethodInvocation) {
                    J.MethodInvocation mi = (J.MethodInvocation) e;
                    String name = mi.getSimpleName();
                    if (isShortFactoryMethodName(name)) {
                        name = getTypeBasedVariableName(mi);
                    } else {
                        name = name.replaceAll("^get", "");
                        name = name.replaceAll("^is", "");
                    }
                    name = StringUtils.uncapitalize(name);
                    variableName = VariableNameUtils.generateVariableName(!name.isEmpty() ? name : "x", getCursor(), VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER);
                } else {
                    variableName = VariableNameUtils.generateVariableName("x", getCursor(), VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER);
                }
                return ensureUniqueVariableName(variableName, generatedVariableSuffixes);
            }

            private boolean isShortFactoryMethodName(String name) {
                // Factory method names that don't make meaningful variable names
                return "of".equals(name) || "from".equals(name) || "copyOf".equals(name);
            }

            private String getTypeBasedVariableName(J.MethodInvocation mi) {
                JavaType type = mi.getType();
                if (type instanceof JavaType.FullyQualified) {
                    return ((JavaType.FullyQualified) type).getClassName();
                }
                return "x";
            }

            private String buildParameterizedTypeName(JavaType.Parameterized paramType) {
                StringBuilder sb = new StringBuilder(paramType.getClassName());
                List<JavaType> typeParams = paramType.getTypeParameters();
                if (!typeParams.isEmpty()) {
                    sb.append("<");
                    for (int i = 0; i < typeParams.size(); i++) {
                        if (i > 0) {
                            sb.append(", ");
                        }
                        sb.append(getTypeName(typeParams.get(i)));
                    }
                    sb.append(">");
                }
                return sb.toString();
            }

            private String getTypeName(JavaType type) {
                if (type instanceof JavaType.Parameterized) {
                    return buildParameterizedTypeName((JavaType.Parameterized) type);
                } else if (type instanceof JavaType.FullyQualified) {
                    return ((JavaType.FullyQualified) type).getClassName();
                } else if (type instanceof JavaType.Primitive) {
                    return type.toString();
                } else if (type instanceof JavaType.Array) {
                    return getTypeName(((JavaType.Array) type).getElemType()) + "[]";
                }
                return "Object";
            }

            private String ensureUniqueVariableName(String variableName, Map<String, Integer> generatedVariableSuffixes) {
                Set<String> existingVariablesInScope = VariableNameUtils.findNamesInScope(getCursor());
                Matcher matcher = NUMBER_SUFFIX_PATTERN.matcher(variableName);
                if (matcher.matches()) {
                    String prefix = matcher.group(1);
                    int suffix = Integer.parseInt(matcher.group(2));
                    generatedVariableSuffixes.putIfAbsent(prefix, suffix);
                    variableName = prefix;
                }
                if (generatedVariableSuffixes.containsKey(variableName)) {
                    int suffix = generatedVariableSuffixes.get(variableName);
                    while (existingVariablesInScope.contains(variableName + suffix)) {
                        suffix++;
                    }
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
