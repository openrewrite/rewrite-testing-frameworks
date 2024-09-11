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
import org.openrewrite.java.search.FindMissingTypes;
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

            List<String> closeVariables = new ArrayList<>();
            List<Statement> aggregateOtherStatements = new ArrayList<>();
            List<Statement> excludeMockUp = new ArrayList<>();
            for (Statement statement : md.getBody().getStatements()) {
                if (!(statement instanceof J.NewClass)) {
                    continue;
                }

                J.NewClass newClass = (J.NewClass) statement;
                if (newClass.getClazz() == null || newClass.getBody() == null ||
                        !TypeUtils.isOfClassType(newClass.getClazz().getType(), "mockit.MockUp")) {
                    continue;
                }

                excludeMockUp.add(statement);
                JavaType mockType = ((J.ParameterizedType) newClass.getClazz()).getTypeParameters().get(0).getType();
                String className = TypeUtils.asFullyQualified(mockType).getClassName();
                String mockName = className.replace(".", "_");

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
                    StringBuilder tpl = new StringBuilder();
                    tpl.append("MockedStatic<").append(className).append("> mockStatic").append(mockName)
                            .append(" = mockStatic(").append(className).append(".class);");
                    for (Statement method : methods) {
                        J.MethodDeclaration m = (J.MethodDeclaration) method;
                        tpl.append("mockStatic").append(mockName).append(".when(() -> ").append(className).append(".").append(m.getSimpleName());
                        appendMethod(tpl, m);
                    }

                    md = JavaTemplate.builder(tpl.toString())
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-5"))
                            .imports("org.mockito.MockedStatic")
                            .staticImports("org.mockito.Mockito.*")
                            .contextSensitive()
                            .build()
                            .apply(updateCursor(md), statement.getCoordinates().after());
                    maybeAddImport("org.mockito.MockedStatic");
                    closeVariables.add("mockStatic" + mockName);
                }

                // Instance
                methods = mockedMethods
                        .stream()
                        .filter(s -> !staticMethods.contains(((J.MethodDeclaration) s).getSimpleName()))
                        .collect(toList());
                if (!methods.isEmpty()) {
                    StringBuilder tpl = new StringBuilder();
                    tpl.append("MockedConstruction<").append(className).append("> mockObj").append(mockName)
                            .append(" = mockConstruction(").append(className).append(".class, (mock, context) -> {");
                    for (Statement method : methods) {
                        J.MethodDeclaration m = (J.MethodDeclaration) method;
                        tpl.append("when(mock.").append(m.getSimpleName());
                        appendMethod(tpl, m);
                    }
                    tpl.append("});");

                    md = JavaTemplate.builder(tpl.toString())
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-5"))
                            .imports("org.mockito.MockedConstruction")
                            .staticImports("org.mockito.Mockito.*")
                            .contextSensitive()
                            .build()
                            .apply(updateCursor(md), statement.getCoordinates().after());
                    maybeAddImport("org.mockito.MockedConstruction");
                    closeVariables.add("mockObj" + mockName);
                }

                // Only discard @Mock method declarations
                aggregateOtherStatements.addAll(
                        newClass
                                .getBody()
                                .getStatements()
                                .stream()
                                .filter(s -> {
                                    if (s instanceof J.MethodDeclaration) {
                                        return ((J.MethodDeclaration) s).getLeadingAnnotations().stream()
                                                .noneMatch(o -> TypeUtils.isOfClassType(o.getType(), "mockit.Mock"));
                                    }
                                    return true;
                                })
                                .collect(toList())
                );

                maybeAddImport("org.mockito.Mockito", "*", false);
                maybeRemoveImport("mockit.Mock");
                maybeRemoveImport("mockit.MockUp");
            }

            // Add other statements at the front
            List<Statement> newBodyStatements = md.getBody()
                    .getStatements()
                    .stream()
                    .filter(s -> !excludeMockUp.contains(s))
                    .collect(toList());
            newBodyStatements.addAll(0, aggregateOtherStatements);
            md = md.withBody(md.getBody().withStatements(newBodyStatements));

            // Add close statement at the end
            for (String v : closeVariables) {
                md = JavaTemplate.builder(v + ".close();")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-5"))
                        .imports("org.mockito.MockedStatic", "org.mockito.MockedConstruction")
                        .staticImports("org.mockito.Mockito.*")
                        .contextSensitive()
                        .build()
                        .apply(updateCursor(md), md.getBody().getCoordinates().lastStatement());
            }

            doAfterVisit(new FindMissingTypes().getVisitor());
            return maybeAutoFormat(methodDeclaration, md, ctx);
        }
    }
}
