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

import static java.util.stream.Collectors.toList;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import static org.openrewrite.java.testing.jmockit.JMockitBlockType.MockUp;
import static org.openrewrite.java.tree.Flag.Static;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class JMockitMockUpToMockito extends Recipe {
    @Override
    public String getDisplayName() {
        return "Rewrite JMockit MockUp to Mockito statements";
    }

    @Override
    public String getDescription() {
        return "Rewrites JMockit `MockUp` blocks to Mockito statements.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(MockUp.getFqn(), false), new JMockitMockUpToMockitoVisitor());
    }

    private static class JMockitMockUpToMockitoVisitor extends JavaIsoVisitor<ExecutionContext> {
        private void appendMethod(StringBuilder sb, J.MethodDeclaration m) {
            sb.append("(");
            if (!m.getParameters().isEmpty()) {
                for (int i = 0; i < m.getParameters().size(); i++) {
                    Statement s = m.getParameters().get(i);
                    if (s instanceof J.VariableDeclarations) {
                        J.VariableDeclarations param = (J.VariableDeclarations) s;
                        if (param.getType() instanceof JavaType.Primitive) {
                            switch (param.getType().toString()) {
                                case "int":
                                    sb.append("anyInt()");
                                    break;
                                case "long":
                                    sb.append("anyLong()");
                                    break;
                                case "double":
                                    sb.append("anyDouble()");
                                    break;
                                case "float":
                                    sb.append("anyFloat()");
                                    break;
                                case "short":
                                    sb.append("anyShort()");
                                    break;
                                case "byte":
                                    sb.append("anyByte()");
                                    break;
                                case "char":
                                    sb.append("anyChar()");
                                    break;
                                case "boolean":
                                    sb.append("anyBoolean()");
                                    break;
                            }
                        } else {
                            String paramType = param.getTypeAsFullyQualified().getClassName();
                            sb.append("nullable(").append(paramType).append(".class").append(")");
                        }
                    }
                    if (i < m.getParameters().size() - 1) {
                        sb.append(", ");
                    }
                }
            }
            sb.append(")").append(")");
            sb.append(".thenAnswer(invocation -> {");
            for (Statement s : m.getBody().getStatements()) {
                sb.append(s.printTrimmed());
                sb.append(";");
            }
            sb.append("});");
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDeclaration, ExecutionContext ctx) {
            J.MethodDeclaration md = super.visitMethodDeclaration(methodDeclaration, ctx);
            if (md.getBody() == null) {
                return md;
            }

            // Create the new statement
            J.Block body = md.getBody();
            List<String> shouldClose = new ArrayList<>();
            for (Statement statement : body.getStatements()) {
                if (!(statement instanceof J.NewClass)) {
                    continue;
                }

                J.NewClass newClass = (J.NewClass) statement;
                if (newClass.getClazz() == null || newClass.getBody() == null) {
                    continue;
                }

                JavaType mockType = ((J.ParameterizedType) newClass.getClazz()).getTypeParameters().get(0).getType();
                String className = TypeUtils.asFullyQualified(mockType).getClassName();
                String mockName = className.replace(".", "_");
                StringBuilder mockStatements = new StringBuilder();

                List<Statement> mockedMethods = newClass.getBody()
                        .getStatements()
                        .stream()
                        .filter(s -> s instanceof J.MethodDeclaration)
                        .filter(s -> ((J.MethodDeclaration) s).getLeadingAnnotations().stream()
                                .anyMatch(o -> TypeUtils.isOfClassType(o.getType(), "mockit.Mock")))
                        // Ignore void methods
                        .filter(s -> !"void".equals(((J.MethodDeclaration) s).getReturnTypeExpression().toString()))
                        .collect(toList());

                Set<String> staticMethods = ((JavaType.Class) mockType)
                        .getMethods()
                        .stream()
                        .filter(m -> m.getFlags().contains(Static))
                        .map(JavaType.Method::getName)
                        .collect(Collectors.toSet());

                // Static
                List<Statement> methods = mockedMethods
                        .stream()
                        .filter(s -> staticMethods.contains(((J.MethodDeclaration) s).getSimpleName()))
                        .collect(toList());
                if (!methods.isEmpty()) {
                    mockStatements.append("MockedStatic<").append(className).append("> mockStatic").append(mockName).append(" = mockStatic(").append(className).append(".class);");
                    for (Statement method : methods) {
                        J.MethodDeclaration m = (J.MethodDeclaration) method;
                        mockStatements.append("mockStatic").append(mockName).append(".when(() -> ").append(className).append(".").append(m.getSimpleName());
                        appendMethod(mockStatements, m);
                    }
                    maybeAddImport("org.mockito.MockedStatic");
                    shouldClose.add("mockStatic" + mockName);
                }

                // Instance
                methods = mockedMethods
                        .stream()
                        .filter(s -> !staticMethods.contains(((J.MethodDeclaration) s).getSimpleName()))
                        .collect(toList());
                if (!methods.isEmpty()) {
                    mockStatements.append("MockedConstruction<").append(className).append("> mockObj").append(mockName).append(" = mockConstruction(").append(className).append(".class, (mock, context) -> {");
                    for (Statement method : methods) {
                        J.MethodDeclaration m = (J.MethodDeclaration) method;
                        mockStatements.append("when(mock.").append(m.getSimpleName());
                        appendMethod(mockStatements, m);
                    }
                    mockStatements.append("});");

                    maybeAddImport("org.mockito.MockedConstruction", null, false);
                    shouldClose.add("mockObj" + mockName);
                }

                StringBuilder otherStatements = new StringBuilder();
                newClass.getBody()
                        .getStatements()
                        .stream()
                        .filter(s -> {
                            if (s instanceof J.MethodDeclaration) {
                                return ((J.MethodDeclaration) s).getLeadingAnnotations().stream()
                                        .noneMatch(o -> TypeUtils.isOfClassType(o.getType(), "mockit.Mock"));
                            }
                            return true;
                        })
                        .forEach(s -> {
                            otherStatements.append(s.printTrimmed());
                            otherStatements.append(";");
                        });

                JavaTemplate tpl = JavaTemplate
                        .builder(otherStatements.toString() + mockStatements)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-5"))
                        .imports("org.mockito.MockedStatic", "org.mockito.MockedConstruction")
                        .staticImports("org.mockito.Mockito.*")
                        .contextSensitive()
                        .build();
                md = maybeAutoFormat(md, tpl.apply(updateCursor(md), statement.getCoordinates().replace()), ctx);
            }

            for (String o : shouldClose) {
                JavaTemplate tpl = JavaTemplate
                        .builder(o + ".close();")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-5"))
                        .imports("org.mockito.MockedStatic", "org.mockito.MockedConstruction")
                        .staticImports("org.mockito.Mockito.*")
                        .contextSensitive()
                        .build();
                md = maybeAutoFormat(md, tpl.apply(updateCursor(md), md.getBody().getCoordinates().lastStatement()), ctx);
            }

            maybeAddImport("org.mockito.Mockito", "*", false);
            maybeRemoveImport("mockit.Mock");
            maybeRemoveImport("mockit.MockUp");

            return md;
        }
    }
}
