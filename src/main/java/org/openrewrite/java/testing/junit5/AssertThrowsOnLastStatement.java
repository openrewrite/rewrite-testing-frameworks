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
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.trait.Scope;
import org.openrewrite.java.tree.*;
import org.openrewrite.kotlin.KotlinTemplate;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.staticanalysis.LambdaBlockToExpression;
import org.openrewrite.staticanalysis.java.JavaFileChecker;
import org.openrewrite.staticanalysis.kotlin.KotlinFileChecker;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static java.util.Collections.*;


public class AssertThrowsOnLastStatement extends Recipe {

    @Getter
    final String displayName = "Applies JUnit 5 `assertThrows` on last statement in lambda block only";

    @Getter
    final String description = "Applies JUnit 5 `assertThrows` on last statement in lambda block only. " +
            "In rare cases may cause compilation errors if the lambda uses effectively non final variables. " +
            "In some cases, tests might fail if earlier statements in the lambda block throw exceptions.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S5783");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        MethodMatcher assertThrowsMatcher = new MethodMatcher(
                "org.junit.jupiter.api.Assertions assertThrows(java.lang.Class, org.junit.jupiter.api.function.Executable, ..)");
        return Preconditions.check(Preconditions.and(
                // Only Java and Kotlin are supported, as the extracted variable declaration is rendered per language
                Preconditions.or(new JavaFileChecker<>(), new KotlinFileChecker<>()),
                new UsesMethod<>(assertThrowsMatcher)), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDecl, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(methodDecl, ctx);
                if (m.getBody() == null) {
                    return m;
                }
                List<Statement> methodStatements = m.getBody().getStatements();
                Cursor methodBody = new Cursor(getCursor(), m.getBody());
                return m.withBody(m.getBody().withStatements(ListUtils.flatMap(methodStatements, (index, methodStatement) -> {
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
                    if (lambdaStatements.isEmpty()) {
                        return methodStatement;
                    }

                    Predicate<JavaType> mustStayInLambda = mustStayInLambda(arguments.get(0), m);
                    if (lambdaStatements.subList(0, lambdaStatements.size() - 1).stream().anyMatch(s -> mayThrow(s, mustStayInLambda))) {
                        return methodStatement;
                    }

                    // A local moved out of the lambda is in scope for the other arguments of the call and the rest of
                    // the method body, where a later statement may declare its name again or read a field it would shadow
                    Cursor statementCursor = new Cursor(methodBody, methodStatement);
                    List<Cursor> following = new ArrayList<>();
                    for (Expression otherArgument : arguments) {
                        if (otherArgument != lambda) {
                            following.add(new Cursor(statementCursor, otherArgument));
                        }
                    }
                    for (Statement later : methodStatements.subList(index + 1, methodStatements.size())) {
                        following.add(new Cursor(methodBody, later));
                    }
                    Cursor lambdaBody = new Cursor(new Cursor(statementCursor, lambda), body);
                    if (Scope.at(lambdaBody).collidesAcross(following)) {
                        return methodStatement;
                    }

                    // TODO Check to see if last line in lambda does not use a non-final variable

                    // move all the statements from the body into before the method invocation, except last one
                    return ListUtils.flatMap(lambdaStatements, (idx, lambdaStatement) -> {
                        if (idx < lambdaStatements.size() - 1) {
                            return lambdaStatement.withPrefix(methodStatement.getPrefix().withComments(emptyList()));
                        }

                        List<Statement> variableAssignments = new ArrayList<>();
                        Space prefix = methodStatement.getPrefix().withComments(emptyList());
                        final Statement newLambdaStatement = extractExpressionArguments(body, lambdaStatement, mustStayInLambda, variableAssignments, prefix);
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
                        if (getCursor().firstEnclosing(J.CompilationUnit.class) != null) {
                            newAssertThrows = (J.MethodInvocation) new LambdaBlockToExpression().getVisitor().visitNonNull(newAssertThrows, ctx, getCursor());
                        }

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

            private Statement extractExpressionArguments(J.Block body, Statement lambdaStatement, Predicate<JavaType> mustStayInLambda, List<Statement> precedingVars, Space varPrefix) {
                if (lambdaStatement instanceof J.MethodInvocation) {
                    boolean kotlin = getCursor().firstEnclosing(K.CompilationUnit.class) != null;
                    J.MethodInvocation mi = (J.MethodInvocation) lambdaStatement;
                    // The names hoisted so far in this method, which the tree the cursor holds does not show yet
                    Set<String> hoisted = getCursor().computeMessageIfAbsent("hoistedNames", k -> new HashSet<>());
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

                        String typeName = getTypeName(type);
                        if ((!kotlin && typeName == null) || mayThrow(e, mustStayInLambda)) {
                            return e;
                        }

                        String variableName = getVariableName(e, hoisted);

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

                        maybeAddImports(type);

                        Cursor blockCursor = new Cursor(getCursor(), body);
                        Cursor c = new Cursor(blockCursor, lambdaStatement);
                        try {
                            J.VariableDeclarations varDecl = JavaTemplate.apply("#{} #{} = #{any()};", c, lambdaStatement.getCoordinates().replace(), typeName, variableName, e);
                            precedingVars.add(varDecl.withPrefix(varPrefix).withType(type));
                            return varDecl.getVariables().get(0).getName().withPrefix(e.getPrefix()).withType(type);
                        } catch (Exception ex) {
                            // Some types (e.g. anonymous or local classes) don't render as a valid variable
                            // declaration; leave the argument inline rather than failing the whole recipe run
                            return e;
                        }
                    }));
                }
                return lambdaStatement;
            }

            // Moving a call out of the lambda makes the test fail when it throws the expected exception, and breaks
            // compilation when it declares a checked exception the test method doesn't
            private Predicate<JavaType> mustStayInLambda(Expression exceptionClass, J.MethodDeclaration testMethod) {
                JavaType.Parameterized classType = TypeUtils.asParameterized(exceptionClass.getType());
                JavaType expected = classType != null && classType.getTypeParameters().size() == 1 ? classType.getTypeParameters().get(0) : null;
                boolean kotlin = getCursor().firstEnclosing(K.CompilationUnit.class) != null;
                List<NameTree> handled = testMethod.getThrows() == null ? emptyList() : testMethod.getThrows();
                return thrown -> expected == null ||
                        TypeUtils.isAssignableTo(expected, thrown) ||
                        TypeUtils.isAssignableTo(thrown, expected) ||
                        !kotlin && isChecked(thrown) && handled.stream().noneMatch(h -> TypeUtils.isAssignableTo(h.getType(), thrown));
            }

            private boolean isChecked(JavaType exception) {
                return !TypeUtils.isAssignableTo("java.lang.RuntimeException", exception) &&
                        !TypeUtils.isAssignableTo("java.lang.Error", exception);
            }

            private boolean mayThrow(J tree, Predicate<JavaType> mustStayInLambda) {
                return new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J preVisit(J t, AtomicBoolean found) {
                        if (found.get()) {
                            stopAfterPreVisit();
                        } else if (t instanceof J.MethodInvocation || t instanceof J.NewClass) {
                            JavaType.Method methodType = ((MethodCall) t).getMethodType();
                            found.set(methodType == null || methodType.getThrownExceptions().stream().anyMatch(mustStayInLambda));
                        }
                        return t;
                    }
                }.reduce(tree, new AtomicBoolean()).get();
            }

            private void maybeAddImports(JavaType type) {
                if (type instanceof JavaType.Array) {
                    maybeAddImports(((JavaType.Array) type).getElemType());
                } else if (type instanceof JavaType.FullyQualified) {
                    maybeAddImport((JavaType.FullyQualified) type);
                    if (type instanceof JavaType.Parameterized) {
                        for (JavaType typeParam : ((JavaType.Parameterized) type).getTypeParameters()) {
                            maybeAddImports(typeParam);
                        }
                    }
                }
            }

            private String getVariableName(Expression e, Set<String> hoisted) {
                String name = "x";
                if (e instanceof J.MethodInvocation) {
                    J.MethodInvocation mi = (J.MethodInvocation) e;
                    name = mi.getSimpleName();
                    if (isShortFactoryMethodName(name)) {
                        name = getTypeBasedVariableName(mi);
                    } else {
                        name = name.replaceAll("^get", "");
                        name = name.replaceAll("^is", "");
                    }
                    name = StringUtils.uncapitalize(name);
                    if (name.isEmpty()) {
                        name = "x";
                    }
                }
                String variableName = Scope.at(getCursor()).freeName(name, hoisted::contains);
                hoisted.add(variableName);
                return variableName;
            }

            private boolean isShortFactoryMethodName(String name) {
                // Factory method names that don't make meaningful variable names
                return "of".equals(name) || "from".equals(name) || "copyOf".equals(name);
            }

            private String getTypeBasedVariableName(J.MethodInvocation mi) {
                JavaType type = mi.getType();
                if (type instanceof JavaType.FullyQualified) {
                    // The class name of a nested type is qualified by its outer types
                    String className = ((JavaType.FullyQualified) type).getClassName();
                    return className.substring(className.lastIndexOf('.') + 1);
                }
                return "x";
            }

            private @Nullable String buildParameterizedTypeName(JavaType.Parameterized paramType) {
                List<JavaType> typeParams = paramType.getTypeParameters();
                if (typeParams.isEmpty()) {
                    return paramType.getClassName();
                }
                StringJoiner joiner = new StringJoiner(", ", paramType.getClassName() + "<", ">");
                for (JavaType typeParam : typeParams) {
                    String typeParamName = getTypeName(typeParam);
                    if (typeParamName == null) {
                        return null;
                    }
                    joiner.add(typeParamName);
                }
                return joiner.toString();
            }

            private @Nullable String getTypeName(JavaType type) {
                if (type instanceof JavaType.Parameterized) {
                    return buildParameterizedTypeName((JavaType.Parameterized) type);
                } else if (type instanceof JavaType.FullyQualified && !(type instanceof JavaType.Unknown)) {
                    return ((JavaType.FullyQualified) type).getClassName();
                } else if (type instanceof JavaType.Primitive) {
                    return type.toString();
                } else if (type instanceof JavaType.Array) {
                    String elemTypeName = getTypeName(((JavaType.Array) type).getElemType());
                    return elemTypeName == null ? null : elemTypeName + "[]";
                }
                return null;
            }
        });
    }
}
