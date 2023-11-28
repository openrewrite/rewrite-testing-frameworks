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
    private final J.Block expectationsBlock;
    private final ExecutionContext ctx;

    ArgumentMatchersRewriter(JavaVisitor<ExecutionContext> visitor, J.Block expectationsBlock, ExecutionContext ctx) {
        this.visitor = visitor;
        this.expectationsBlock = expectationsBlock;
        this.ctx = ctx;
    }

    J.Block rewrite() {
        try {
            List<Statement> newStatements = new ArrayList<>(expectationsBlock.getStatements().size());
            for (Statement expectationStatement : expectationsBlock.getStatements()) {
                // for each statement, check if it's a method invocation and replace any argument matchers
                if (!(expectationStatement instanceof J.MethodInvocation)) {
                    newStatements.add(expectationStatement);
                    continue;
                }
                J.MethodInvocation methodInvocation = (J.MethodInvocation) expectationStatement;
                List<Expression> newArguments = rewriteMethodArgumentMatchers(methodInvocation.getArguments());
                newStatements.add(methodInvocation.withArguments(newArguments));
            }
            return expectationsBlock.withStatements(newStatements);
        } catch (Exception e) {
            // if anything goes wrong, just return the original expectations block
            return expectationsBlock;
        }
    }

    private List<Expression> rewriteMethodArgumentMatchers(List<Expression> arguments) {
        List<Expression> newArguments = new ArrayList<>(arguments.size());
        for (Expression methodArgument : arguments) {
            newArguments.add(rewriteMethodArgument(methodArgument));
        }
        return newArguments;
    }

    private Expression rewriteMethodArgument(Expression methodArgument) {
        if (!isArgumentMatcher(methodArgument)) {
            return methodArgument;
        }
        String argumentMatcher, template;
        List<Object> templateParams = new ArrayList<>();
        if (!(methodArgument instanceof J.TypeCast)) {
            argumentMatcher = ((J.Identifier) methodArgument).getSimpleName();
            template = argumentMatcher + "()";
            return rewriteMethodArgument(argumentMatcher, template, methodArgument, templateParams);
        }
        J.TypeCast tc = (J.TypeCast) methodArgument;
        argumentMatcher = ((J.Identifier) tc.getExpression()).getSimpleName();
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
        if (MOCKITO_COLLECTION_MATCHERS.containsKey(fqn)) {
            // mockito has specific argument matchers for collections
            argumentMatcher = MOCKITO_COLLECTION_MATCHERS.get(fqn);
            template = argumentMatcher + "()";
        } else {
            // rewrite parameter from ((<type>) any) to <type>.class
            templateParams.add(JavaTemplate.builder("#{}.class")
                    .javaParser(JavaParser.fromJavaVersion())
                    .build()
                    .apply(
                            new Cursor(visitor.getCursor(), tc),
                            tc.getCoordinates().replace(),
                            className
                    ));
            template = argumentMatcher + "(#{any(java.lang.Class)})";
        }
        return rewriteMethodArgument(argumentMatcher, template, methodArgument, templateParams);
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
}
