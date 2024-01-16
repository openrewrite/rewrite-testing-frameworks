/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.jmockit;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;

import java.util.*;

class ArgumentMatchersRewriter {

    private static final Set<String> JMOCKIT_ARGUMENT_MATCHERS = new HashSet<>();

    static {
        JMOCKIT_ARGUMENT_MATCHERS.add("anyString");
        JMOCKIT_ARGUMENT_MATCHERS.add("anyInt");
        JMOCKIT_ARGUMENT_MATCHERS.add("anyLong");
        JMOCKIT_ARGUMENT_MATCHERS.add("anyDouble");
        JMOCKIT_ARGUMENT_MATCHERS.add("anyFloat");
        JMOCKIT_ARGUMENT_MATCHERS.add("anyBoolean");
        JMOCKIT_ARGUMENT_MATCHERS.add("anyByte");
        JMOCKIT_ARGUMENT_MATCHERS.add("anyChar");
        JMOCKIT_ARGUMENT_MATCHERS.add("anyShort");
        JMOCKIT_ARGUMENT_MATCHERS.add("any");
    }

    private static final Map<String, String> MOCKITO_COLLECTION_MATCHERS = new HashMap<>();

    static {
        MOCKITO_COLLECTION_MATCHERS.put("java.util.List", "anyList");
        MOCKITO_COLLECTION_MATCHERS.put("java.util.Set", "anySet");
        MOCKITO_COLLECTION_MATCHERS.put("java.util.Collection", "anyCollection");
        MOCKITO_COLLECTION_MATCHERS.put("java.util.Iterable", "anyIterable");
        MOCKITO_COLLECTION_MATCHERS.put("java.util.Map", "anyMap");
    }

    private final JavaVisitor<ExecutionContext> visitor;
    private final ExecutionContext ctx;
    private final J.Block expectationsBlock;

    ArgumentMatchersRewriter(JavaVisitor<ExecutionContext> visitor, ExecutionContext ctx, J.Block expectationsBlock) {
        this.visitor = visitor;
        this.ctx = ctx;
        this.expectationsBlock = expectationsBlock;
    }

    J.Block rewriteExpectationsBlock() {
        List<Statement> newStatements = new ArrayList<>(expectationsBlock.getStatements().size());
        for (Statement expectationStatement : expectationsBlock.getStatements()) {
            // for each statement, check if it's a method invocation and replace any argument matchers
            if (!(expectationStatement instanceof J.MethodInvocation)) {
                newStatements.add(expectationStatement);
                continue;
            }
            newStatements.add(rewriteMethodInvocation((J.MethodInvocation) expectationStatement));
        }
        return expectationsBlock.withStatements(newStatements);
    }

    private J.MethodInvocation rewriteMethodInvocation(J.MethodInvocation invocation) {
        if (invocation.getSelect() instanceof J.MethodInvocation) {
            invocation = invocation.withSelect(rewriteMethodInvocation((J.MethodInvocation) invocation.getSelect()));
        }
        // in mockito, argument matchers must be used for all arguments or none
        boolean hasArgumentMatcher = false;
        List<Expression> arguments = invocation.getArguments();
        for (Expression methodArgument : arguments) {
            if (isJmockitArgumentMatcher(methodArgument)) {
                hasArgumentMatcher = true;
                break;
            }
        }
        // if there are no argument matchers, return the invocation as-is
        if (!hasArgumentMatcher) {
            return invocation;
        }
        // replace each argument with the appropriate argument matcher
        List<Expression> newArguments = new ArrayList<>(arguments.size());
        for (Expression argument : arguments) {
            newArguments.add(rewriteMethodArgument(argument));
        }
        return invocation.withArguments(newArguments);
    }

    private Expression rewriteMethodArgument(Expression methodArgument) {
        String argumentMatcher, template;
        if (!isJmockitArgumentMatcher(methodArgument)) {
            if (methodArgument instanceof J.Literal) {
                return rewritePrimitiveToArgumentMatcher((J.Literal) methodArgument);
            } else if (methodArgument instanceof J.Identifier) {
                return rewriteIdentifierToArgumentMatcher((J.Identifier) methodArgument);
            } else if (methodArgument instanceof J.FieldAccess) {
                return rewriteIdentifierToArgumentMatcher(((J.FieldAccess) methodArgument).getName());
            } else {
                // unhandled type, return argument unchanged
                return methodArgument;
            }
        }
        if (!(methodArgument instanceof J.TypeCast)) {
            argumentMatcher = ((J.Identifier) methodArgument).getSimpleName();
            template = argumentMatcher + "()";
            return applyArgumentTemplate(methodArgument, argumentMatcher, template, new ArrayList<>());
        }
        return rewriteTypeCastToArgumentMatcher(methodArgument);
    }

    private Expression applyArgumentTemplate(Expression methodArgument, String argumentMatcher, String template,
                                             List<Object> templateParams) {
        visitor.maybeAddImport("org.mockito.Mockito", argumentMatcher);
        return JavaTemplate.builder(template)
                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                .staticImports("org.mockito.Mockito." + argumentMatcher)
                .build()
                .apply(
                        new Cursor(visitor.getCursor(), methodArgument),
                        methodArgument.getCoordinates().replace(),
                        templateParams.toArray()
                );
    }

    private Expression applyClassArgumentTemplate(Expression methodArgument, String className) {
        // rewrite parameter from ((<type>) any) to any(<type>.class)
        return JavaTemplate.builder("#{}.class")
                .javaParser(JavaParser.fromJavaVersion())
                .build()
                .apply(
                        new Cursor(visitor.getCursor(), methodArgument),
                        methodArgument.getCoordinates().replace(),
                        className
                );
    }

    private Expression rewriteFullyQualifiedArgument(Expression methodArgument, String className, String fqn) {
        String template;
        List<Object> templateParams = new ArrayList<>();
        String argumentMatcher = "any";
        if (MOCKITO_COLLECTION_MATCHERS.containsKey(fqn)) {
            // mockito has specific argument matchers for collections
            argumentMatcher = MOCKITO_COLLECTION_MATCHERS.get(fqn);
            template = argumentMatcher + "()";
        } else if (fqn.equals("java.lang.String")) {
            argumentMatcher = "anyString";
            template = argumentMatcher + "()";
        } else {
            templateParams.add(applyClassArgumentTemplate(methodArgument, className));
            template = "any(#{any(java.lang.Class)})";
        }
        return applyArgumentTemplate(methodArgument, argumentMatcher, template, templateParams);
    }

    private Expression rewriteIdentifierToArgumentMatcher(J.Identifier methodArgument) {
        if (!(methodArgument.getType() instanceof JavaType.FullyQualified)) {
            return methodArgument;
        }
        JavaType.FullyQualified type = (JavaType.FullyQualified) methodArgument.getType();
        return rewriteFullyQualifiedArgument(methodArgument, type.getClassName(), type.getFullyQualifiedName());
    }

    private Expression rewriteTypeCastToArgumentMatcher(Expression methodArgument) {
        J.TypeCast tc = (J.TypeCast) methodArgument;
        String className, fqn;
        JavaType typeCastType = tc.getType();
        if (typeCastType instanceof JavaType.Parameterized) {
            // strip the raw type from the parameterized type
            className = ((JavaType.Parameterized) typeCastType).getType().getClassName();
            fqn = ((JavaType.Parameterized) typeCastType).getType().getFullyQualifiedName();
        } else if (typeCastType instanceof JavaType.Class) {
            className = ((JavaType.Class) typeCastType).getClassName();
            fqn = ((JavaType.Class) typeCastType).getFullyQualifiedName();
        } else {
            // unhandled type, return argument unchanged
            return methodArgument;
        }
        return rewriteFullyQualifiedArgument(tc, className, fqn);
    }

    private Expression rewritePrimitiveToArgumentMatcher(J.Literal methodArgument) {
        String argumentMatcher;
        JavaType.Primitive primitiveType = methodArgument.getType();
        switch (Objects.requireNonNull(primitiveType)) {
            case Boolean:
                argumentMatcher = "anyBoolean";
                break;
            case Byte:
                argumentMatcher = "anyByte";
                break;
            case Char:
                argumentMatcher = "anyChar";
                break;
            case Double:
                argumentMatcher = "anyDouble";
                break;
            case Float:
                argumentMatcher = "anyFloat";
                break;
            case Int:
                argumentMatcher = "anyInt";
                break;
            case Long:
                argumentMatcher = "anyLong";
                break;
            case Short:
                argumentMatcher = "anyShort";
                break;
            case String:
                argumentMatcher = "anyString";
                break;
            case Null:
                argumentMatcher = "isNull";
                break;
            default:
                // unhandled type, return argument unchanged
                return methodArgument;
        }
        String template = argumentMatcher + "()";
        return applyArgumentTemplate(methodArgument, argumentMatcher, template, new ArrayList<>());
    }

    private static boolean isJmockitArgumentMatcher(Expression expression) {
        if (expression instanceof J.TypeCast) {
            expression = ((J.TypeCast) expression).getExpression();
        }
        if (!(expression instanceof J.Identifier)) {
            return false;
        }
        J.Identifier identifier = (J.Identifier) expression;
        return JMOCKIT_ARGUMENT_MATCHERS.contains(identifier.getSimpleName());
    }
}
