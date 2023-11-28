package org.openrewrite.java.testing.jmockit;

import org.jetbrains.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.List;

class SetupStatementsRewriter {

    private final JavaVisitor<ExecutionContext> visitor;
    private final J.Block methodBody;

    SetupStatementsRewriter(JavaVisitor<ExecutionContext> visitor, J.Block methodBody) {
        this.visitor = visitor;
        this.methodBody = methodBody;
    }

    J.Block rewrite() {
        J.Block newBody = methodBody;
        try {
            // iterate over each statement in the method body, find Expectations blocks and rewrite them
            for (Statement s : methodBody.getStatements()) {
                if (!JMockitUtils.isExpectationsNewClassStatement(s)) {
                    continue;
                }

                J.NewClass nc = (J.NewClass) s;
                assert nc.getBody() != null;
                // statement needs to be moved directly before expectations class instantiation
                JavaCoordinates coordinates = nc.getCoordinates().before();
                J.Block expectationsBlock = (J.Block) nc.getBody().getStatements().get(0);
                List<Statement> newExpectationsBlockStatements = new ArrayList<>();
                for (Statement expectationStatement : expectationsBlock.getStatements()) {
                    if (!isSetupStatement(expectationStatement)) {
                        newExpectationsBlockStatements.add(expectationStatement);
                        continue;
                    }
                    newBody = copySetupStatement(expectationStatement, newBody, coordinates);
                    // subsequent setup statements are moved in order
                    coordinates = expectationStatement.getCoordinates().after();
                }
                // the expectations block needs to have the statement removed
                J.Block newExpectationsBlock = expectationsBlock.withStatements(newExpectationsBlockStatements);
                List<Statement> newExpectationsStatements = new ArrayList<>();
                newExpectationsStatements.add(newExpectationsBlock);
                assert nc.getBody() != null;
                nc = nc.withBody(nc.getBody().withStatements(newExpectationsStatements));

                newBody = JavaTemplate.builder("#{any()}")
                        .javaParser(JavaParser.fromJavaVersion())
                        .build()
                        .apply(
                                new Cursor(visitor.getCursor(), newBody),
                                nc.getCoordinates().replace(),
                                nc
                        );
            }
        }  catch (Exception e) {
            // if anything goes wrong, just return the original method body
            return methodBody;
        }
        return newBody;
    }

    private static boolean isSetupStatement(Statement expectationStatement) {
        boolean isSetupStatement = true;
        if (expectationStatement instanceof J.MethodInvocation) {
            // a method invocation on a mock is not a setup statement
            J.MethodInvocation methodInvocation = (J.MethodInvocation) expectationStatement;
            if (methodInvocation.getSelect() instanceof J.Identifier) {
                J.Identifier identifier = (J.Identifier) methodInvocation.getSelect();
                JavaType.Variable fieldType = identifier.getFieldType();

                // if type information is missing, don't move the statement
                if (fieldType == null) {
                    return false;
                }
                for (JavaType.FullyQualified annotationType : fieldType.getAnnotations()) {
                    if (TypeUtils.isAssignableTo("mockit.Mocked", annotationType)
                            || TypeUtils.isAssignableTo("org.mockito.Mock", annotationType)) {
                        isSetupStatement = false;
                        break;
                    }
                }
            }
        } else if (expectationStatement instanceof J.Assignment) {
            // an assignment to a jmockit reserved field is not a setup statement
            J.Assignment assignment = (J.Assignment) expectationStatement;
            J.Identifier identifier = (J.Identifier) assignment.getVariable();
            if (identifier.getSimpleName().equals("result") || identifier.getSimpleName().equals("times")) {
                isSetupStatement = false;
            }
        }
        return isSetupStatement;
    }

    private J.Block copySetupStatement(Statement setupStatement, J.Block newBody, JavaCoordinates coordinates) {
        return JavaTemplate.builder("#{any()}")
                .javaParser(JavaParser.fromJavaVersion())
                .build()
                .apply(
                        new Cursor(visitor.getCursor(), newBody),
                        coordinates,
                        setupStatement
                );
    }
}
