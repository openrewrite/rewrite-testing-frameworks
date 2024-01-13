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
            J.MethodInvocation methodInvocation = rewriteMethodInvocation((J.MethodInvocation) expectationStatement);
            newStatements.add(methodInvocation);
        }
        return expectationsBlock.withStatements(newStatements);
    }

    private J.MethodInvocation rewriteMethodInvocation(J.MethodInvocation invocation) {
        if (invocation.getSelect() instanceof J.MethodInvocation) {
            invocation = invocation.withSelect(rewriteMethodInvocation((J.MethodInvocation) invocation.getSelect()));
        }
        JavaType.Method methodType = invocation.getMethodType();
        if (methodType == null) {
            throw new IllegalStateException("Missing method type information for method invocation: " + invocation);
        }
        List<Expression> newArguments = rewriteMethodArgumentMatchers(invocation.getArguments(), invocation.getMethodType().getParameterTypes());
        return invocation.withArguments(newArguments);
    }

    private List<Expression> rewriteMethodArgumentMatchers(List<Expression> arguments, List<JavaType> parameterTypes) {
        boolean hasArgumentMatcher = false;
        for (Expression methodArgument : arguments) {
            if (isArgumentMatcher(methodArgument)) {
                hasArgumentMatcher = true;
            }
        }
        if (!hasArgumentMatcher) {
            return arguments;
        }
        List<Expression> newArguments = new ArrayList<>(arguments.size());
        for (int i = 0; i < arguments.size(); i++) {
            newArguments.add(rewriteMethodArgument(arguments.get(i), parameterTypes.get(i)));
        }
        return newArguments;
    }

    private Expression rewriteMethodArgument(Expression methodArgument, JavaType parameterType) {
        String argumentMatcher, template;
        if (!isArgumentMatcher(methodArgument)) {
            if (methodArgument instanceof J.Literal) {
                argumentMatcher = primitiveToArgumentMatcher((J.Literal) methodArgument);
                template = argumentMatcher + "()";
                return rewriteMethodArgument(argumentMatcher, template, methodArgument, new ArrayList<>());
            } else if (methodArgument instanceof J.Identifier) {
                return rewriteIdentifierToArgumentMatcher((J.Identifier) methodArgument);
            } else if (methodArgument instanceof J.FieldAccess) {
                return rewriteIdentifierToArgumentMatcher(((J.FieldAccess) methodArgument).getName());
            } else {
                throw new IllegalStateException("Unexpected method argument: " + methodArgument + ", class: " + methodArgument.getClass());
            }
        }
        if (!(methodArgument instanceof J.TypeCast)) {
            argumentMatcher = ((J.Identifier) methodArgument).getSimpleName();
            template = argumentMatcher + "()";
            return rewriteMethodArgument(argumentMatcher, template, methodArgument, new ArrayList<>());
        }
        return rewriteTypeCastArgument(methodArgument, new ArrayList<>());
    }

    private Expression rewriteMethodArgument(String argumentMatcher, String template, Expression methodArgument,
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

    private Expression rewriteTypeCastArgument(Expression methodArgument, List<Object> templateParams) {
        J.TypeCast tc = (J.TypeCast) methodArgument;
        String className, fqn;
        JavaType typeCastType = tc.getType();
        if (typeCastType instanceof JavaType.Parameterized) {
            // strip the raw type from the parameterized type
            className = ((JavaType.Parameterized) typeCastType).getType().getClassName();
            fqn = ((JavaType.Parameterized) typeCastType).getType().getFullyQualifiedName();
        } else if (typeCastType instanceof JavaType.FullyQualified) {
            className = ((JavaType.FullyQualified) typeCastType).getClassName();
            fqn = ((JavaType.FullyQualified) typeCastType).getFullyQualifiedName();
        } else {
            throw new IllegalStateException("Unexpected J.TypeCast type: " + typeCastType);
        }
        String template;
        String argumentMatcher = "any";
        if (MOCKITO_COLLECTION_MATCHERS.containsKey(fqn)) {
            // mockito has specific argument matchers for collections
            argumentMatcher = MOCKITO_COLLECTION_MATCHERS.get(fqn);
            template = argumentMatcher + "()";
        } else {
            templateParams.add(rewriteClassMethodArgument(tc, className));
            template = "any(#{any(java.lang.Class)})";
        }
        return rewriteMethodArgument(argumentMatcher, template, methodArgument, templateParams);
    }

    private Expression rewriteIdentifierToArgumentMatcher(J.Identifier methodArgument) {
        if (methodArgument.getType() == null) {
            throw new IllegalStateException("Missing type information for identifier: " + methodArgument);
        }
        String template;
        String argumentMatcher = "any";
        JavaType type = methodArgument.getType();
        List<Object> templateParams = new ArrayList<>();
        if (type instanceof JavaType.FullyQualified) {
            String fqn = ((JavaType.FullyQualified) type).getFullyQualifiedName();
            if (fqn.equals("java.lang.String")) {
                argumentMatcher = "anyString";
                template = argumentMatcher + "()";
            } else {
                templateParams.add(rewriteClassMethodArgument(methodArgument, ((JavaType.FullyQualified) type).getClassName()));
                template = "any(#{any(java.lang.Class)})";
            }
        } else {
            throw new IllegalStateException("Unexpected identifier type: " + type);
        }
        return rewriteMethodArgument(argumentMatcher, template, methodArgument, templateParams);
    }

    // rewrite parameter from ((<type>) any) to any(<type>.class)
    private Expression rewriteClassMethodArgument(Expression methodArgument, String className) {
        return JavaTemplate.builder("#{}.class")
                .javaParser(JavaParser.fromJavaVersion())
                .build()
                .apply(
                        new Cursor(visitor.getCursor(), methodArgument),
                        methodArgument.getCoordinates().replace(),
                        className
                );
    }

    private static boolean isArgumentMatcher(Expression expression) {
        if (expression instanceof J.TypeCast) {
            expression = ((J.TypeCast) expression).getExpression();
        }
        if (!(expression instanceof J.Identifier)) {
            return false;
        }
        J.Identifier identifier = (J.Identifier) expression;
        return JMOCKIT_ARGUMENT_MATCHERS.contains(identifier.getSimpleName());
    }

    private static String primitiveToArgumentMatcher(J.Literal methodArgument) {
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
                throw new IllegalStateException("Unexpected primitive type: " + primitiveType);
        }
        return argumentMatcher;
    }
}
