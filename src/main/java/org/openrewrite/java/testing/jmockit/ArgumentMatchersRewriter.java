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
import org.openrewrite.java.tree.*;

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

    private static final Map<String, String> FQN_TO_MOCKITO_ARGUMENT_MATCHER = new HashMap<>();

    static {
        FQN_TO_MOCKITO_ARGUMENT_MATCHER.put("java.util.List", "anyList");
        FQN_TO_MOCKITO_ARGUMENT_MATCHER.put("java.util.Set", "anySet");
        FQN_TO_MOCKITO_ARGUMENT_MATCHER.put("java.util.Collection", "anyCollection");
        FQN_TO_MOCKITO_ARGUMENT_MATCHER.put("java.util.Iterable", "anyIterable");
        FQN_TO_MOCKITO_ARGUMENT_MATCHER.put("java.util.Map", "anyMap");
        FQN_TO_MOCKITO_ARGUMENT_MATCHER.put("java.lang.Integer", "anyInt");
        FQN_TO_MOCKITO_ARGUMENT_MATCHER.put("java.lang.Long", "anyLong");
        FQN_TO_MOCKITO_ARGUMENT_MATCHER.put("java.lang.Double", "anyDouble");
        FQN_TO_MOCKITO_ARGUMENT_MATCHER.put("java.lang.Float", "anyFloat");
        FQN_TO_MOCKITO_ARGUMENT_MATCHER.put("java.lang.Boolean", "anyBoolean");
        FQN_TO_MOCKITO_ARGUMENT_MATCHER.put("java.lang.Byte", "anyByte");
        FQN_TO_MOCKITO_ARGUMENT_MATCHER.put("java.lang.Character", "anyChar");
        FQN_TO_MOCKITO_ARGUMENT_MATCHER.put("java.lang.Short", "anyShort");
    }

    private static final Map<JavaType.Primitive, String> PRIMITIVE_TO_MOCKITO_ARGUMENT_MATCHER = new HashMap<>();

    static {
        PRIMITIVE_TO_MOCKITO_ARGUMENT_MATCHER.put(JavaType.Primitive.Int, "anyInt");
        PRIMITIVE_TO_MOCKITO_ARGUMENT_MATCHER.put(JavaType.Primitive.Long, "anyLong");
        PRIMITIVE_TO_MOCKITO_ARGUMENT_MATCHER.put(JavaType.Primitive.Double, "anyDouble");
        PRIMITIVE_TO_MOCKITO_ARGUMENT_MATCHER.put(JavaType.Primitive.Float, "anyFloat");
        PRIMITIVE_TO_MOCKITO_ARGUMENT_MATCHER.put(JavaType.Primitive.Boolean, "anyBoolean");
        PRIMITIVE_TO_MOCKITO_ARGUMENT_MATCHER.put(JavaType.Primitive.Byte, "anyByte");
        PRIMITIVE_TO_MOCKITO_ARGUMENT_MATCHER.put(JavaType.Primitive.Char, "anyChar");
        PRIMITIVE_TO_MOCKITO_ARGUMENT_MATCHER.put(JavaType.Primitive.Short, "anyShort");
    }

    private final JavaVisitor<ExecutionContext> visitor;
    private final ExecutionContext ctx;
    private final J.Block expectationsBlock;

    ArgumentMatchersRewriter(JavaVisitor<ExecutionContext> visitor, ExecutionContext ctx, J.Block expectationsBlock) {
        this.visitor = visitor;
        this.ctx = ctx;
        this.expectationsBlock = expectationsBlock;
    }

    J.Block rewriteJMockitBlock() {
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
        List<Expression> arguments = invocation.getArguments();
        // replace this.matcher with matcher, otherwise it's ignored
        arguments.replaceAll(arg -> {
            if (arg instanceof J.FieldAccess) {
                J.FieldAccess fieldAccess = (J.FieldAccess) arg;
                if (fieldAccess.getTarget() instanceof J.Identifier &&
                        "this".equals(((J.Identifier) fieldAccess.getTarget()).getSimpleName())) {
                    return fieldAccess.getName();
                }
            }
            return arg;
        });

        // if there are no argument matchers, return the invocation as-is
        if (arguments.stream().noneMatch(ArgumentMatchersRewriter::isJmockitArgumentMatcher)) {
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
        String argumentMatcher = null, template = null;
        List<Object> templateParams = new ArrayList<>();
        JavaType type = methodArgument.getType();
        if (type == JavaType.Primitive.Null) {
            // null to isNull()
            argumentMatcher = "isNull";
            template = argumentMatcher + "()";
        } else if (!isJmockitArgumentMatcher(methodArgument)) {
            // <argument> to eq(<argument>)
            argumentMatcher = "eq";
            template = argumentMatcher + "(#{any()})";
            templateParams.add(methodArgument);
        } else if (!(methodArgument instanceof J.TypeCast)) {
            // anyString to anyString(), anyInt to anyInt(), etc.
            argumentMatcher = ((J.Identifier) methodArgument).getSimpleName();
            template = argumentMatcher + "()";
        } else if (TypeUtils.isString(type)) {
            // ((String) any) to anyString()
            argumentMatcher = "anyString";
            template = argumentMatcher + "()";
        } else if (type instanceof JavaType.Primitive) {
            // ((int) any) to anyInt(), ((long) any) to anyLong(), etc
            argumentMatcher = PRIMITIVE_TO_MOCKITO_ARGUMENT_MATCHER.get(type);
            template = argumentMatcher + "()";
        } else if (type instanceof JavaType.FullyQualified || type instanceof JavaType.Array) {
            // ((<type>) any) to any(<type>.class), type can also be simple array
            return rewriteAnyWithClassParameterToArgumentMatcher(methodArgument, type);
        }
        if (template == null || argumentMatcher == null) {
            // unhandled type, return argument unchanged
            return methodArgument;
        }
        return applyArgumentTemplate(methodArgument, argumentMatcher, template, templateParams);
    }

    private Expression applyArgumentTemplate(Expression methodArgument, String argumentMatcher, String template,
            List<Object> templateParams) {
        visitor.maybeAddImport("org.mockito.Mockito", argumentMatcher);
        return JavaTemplate.builder(template)
                .javaParser(JMockitUtils.getJavaParser(ctx))
                .staticImports("org.mockito.Mockito." + argumentMatcher)
                .build()
                .apply(
                        new Cursor(visitor.getCursor(), methodArgument),
                        methodArgument.getCoordinates().replace(),
                        templateParams.toArray());
    }

    private Expression applyClassArgumentTemplate(Expression methodArgument, JavaType.FullyQualified type) {
        // rewrite parameter from ((<type>) any) to any(<type>.class)
        return ((Expression) JavaTemplate.builder("#{}.class")
                .javaParser(JavaParser.fromJavaVersion())
                .imports(type.getFullyQualifiedName())
                .build()
                .apply(
                        new Cursor(visitor.getCursor(), methodArgument),
                        methodArgument.getCoordinates().replace(),
                        type.getClassName()))
                .withType(type);
    }

    private Expression rewriteAnyWithClassParameterToArgumentMatcher(Expression methodArgument, JavaType type) {
        String template;
        List<Object> templateParams = new ArrayList<>();

        if (type instanceof JavaType.FullyQualified) {
            JavaType.FullyQualified fq = (JavaType.FullyQualified) type;
            String argumentMatcher = FQN_TO_MOCKITO_ARGUMENT_MATCHER.get(fq.getFullyQualifiedName());
            if (argumentMatcher != null) {
                // mockito has convenience argument matchers
                template = argumentMatcher + "()";
                return applyArgumentTemplate(methodArgument, argumentMatcher, template, templateParams);
            }
        }
        // mockito uses any(Class) for all other types
        String argumentMatcher = "any";
        template = argumentMatcher + "(#{any(java.lang.Class)})";

        if (type instanceof JavaType.FullyQualified) {
            templateParams.add(applyClassArgumentTemplate(methodArgument, (JavaType.FullyQualified) type));
        } else if (type instanceof JavaType.Array) {
            templateParams.add(applyArrayClassArgumentTemplate(methodArgument, ((JavaType.Array) type).getElemType()));
        }

        J.MethodInvocation invocationArgument = (J.MethodInvocation) applyArgumentTemplate(methodArgument,
                argumentMatcher, template, templateParams);

        // update the Class type parameter and method return type
        Expression classArgument = (Expression) templateParams.get(0);
        if (classArgument.getType() == null ||
                invocationArgument.getMethodType() == null ||
                invocationArgument.getMethodType().getParameterTypes().size() != 1 ||
                !(invocationArgument.getMethodType().getParameterTypes().get(0) instanceof JavaType.Parameterized)) {
            return invocationArgument;
        }
        JavaType.Parameterized newParameterType = ((JavaType.Parameterized) invocationArgument.getMethodType()
                .getParameterTypes().get(0))
                .withTypeParameters(Collections.singletonList(classArgument.getType()));
        JavaType.Method newMethodType = invocationArgument.getMethodType()
                .withReturnType(classArgument.getType())
                .withParameterTypes(Collections.singletonList(newParameterType));
        return invocationArgument.withMethodType(newMethodType);
    }

    private Expression applyArrayClassArgumentTemplate(Expression methodArgument, JavaType elementType) {
        String newArrayElementClassName;
        if (elementType instanceof JavaType.FullyQualified) {
            newArrayElementClassName = ((JavaType.FullyQualified) elementType).getClassName();
        } else if (elementType instanceof JavaType.Primitive) {
            newArrayElementClassName = ((JavaType.Primitive) elementType).getKeyword();
        } else {
            newArrayElementClassName = elementType.getClass().getName();
        }

        return JavaTemplate.builder("#{}[].class")
                .javaParser(JavaParser.fromJavaVersion())
                .build()
                .apply(
                        new Cursor(visitor.getCursor(), methodArgument),
                        methodArgument.getCoordinates().replace(),
                        newArrayElementClassName);
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
