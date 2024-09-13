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
import static java.util.stream.Collectors.toMap;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import static org.openrewrite.java.testing.jmockit.JMockitBlockType.MockUp;
import static org.openrewrite.java.tree.Flag.Private;
import static org.openrewrite.java.tree.Flag.Static;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
        private String getMatcher(JavaType s) {
            if (s instanceof JavaType.Primitive) {
                switch (s.toString()) {
                    case "int":
                        return "anyInt()";
                    case "long":
                        return "anyLong()";
                    case "double":
                        return "anyDouble()";
                    case "float":
                        return "anyFloat()";
                    case "short":
                        return "anyShort()";
                    case "byte":
                        return "anyByte()";
                    case "char":
                        return "anyChar()";
                    case "boolean":
                        return "anyBoolean()";
                }
            } else if (s instanceof JavaType.Array) {
                String elem = TypeUtils.asArray(s).getElemType().toString();
                return "nullable(" + elem + "[].class)";
            }
            return "nullable(" + TypeUtils.asFullyQualified(s).getClassName() + ".class)";
        }

        private void appendAnswer(StringBuilder sb, List<Statement> statements) {
            boolean hasReturn = false;
            for (Statement s : statements) {
                hasReturn = hasReturn || s instanceof J.Return;
                sb.append(s.print());
                sb.append(";");
            }
            if (!hasReturn) {
                sb.append("return null;");
            }
        }

        private void appendDoAnswer(StringBuilder sb, J.MethodDeclaration m, String objName) {
            sb.append("doAnswer(invocation -> {");
            appendAnswer(sb, m.getBody().getStatements());
            sb.append("}).when(").append(objName).append(").").append(m.getSimpleName()).append("(");

            sb.append(m.getParameters()
                    .stream()
                    .filter(p -> p instanceof J.VariableDeclarations)
                    .map(o -> getMatcher(((J.VariableDeclarations) o).getType()))
                    .collect(Collectors.joining(", ")));
            sb.append(");");
        }

        private void appendCallRealMethod(StringBuilder sb, JavaType.Method m) {
            sb.append("(");
            for (int i = 0; i < m.getParameterTypes().size(); i++) {
                JavaType s = m.getParameterTypes().get(i);
                sb.append(getMatcher(s));
                if (i < m.getParameterTypes().size() - 1) {
                    sb.append(", ");
                }
            }
            sb.append(")).thenCallRealMethod();");
        }

        private String addMockStaticMethods(JavaType.Class clazz, String className, String mockName, Map<J.MethodDeclaration, JavaType.Method> mockedMethods) {
            StringBuilder tpl = new StringBuilder();

            // Handle mocked static mockedMethods
            tpl.append("MockedStatic<").append(className).append("> mockStatic").append(mockName)
                    .append(" = mockStatic(").append(className).append(".class);");
            for (J.MethodDeclaration m : mockedMethods.keySet()) {
                tpl.append("mockStatic").append(mockName);
                tpl.append(".when(() -> ").append(className).append(".").append(m.getSimpleName()).append("(");
                tpl.append(m.getParameters()
                        .stream()
                        .filter(p -> p instanceof J.VariableDeclarations)
                        .map(o -> getMatcher(((J.VariableDeclarations) o).getType()))
                        .collect(Collectors.joining(", ")));
                tpl.append(")).thenAnswer(invocation -> {");
                appendAnswer(tpl, m.getBody().getStatements());
                tpl.append("});");
            }

            // Call real method for non private, static methods
            clazz.getMethods()
                    .stream()
                    .filter(m -> !m.isConstructor())
                    .filter(m -> !m.getFlags().contains(Private))
                    .filter(m -> m.getFlags().contains(Static))
                    .filter(m -> !mockedMethods.containsValue(m))
                    .forEach(m -> {
                        tpl.append("mockStatic").append(mockName).append(".when(() -> ")
                                .append(className).append(".").append(m.getName());
                        appendCallRealMethod(tpl, m);
                        tpl.append(");");
                    });

            return tpl.toString();
        }

        private String addMockInstanceMethods(JavaType.Class clazz, String className, String mockName, Map<J.MethodDeclaration, JavaType.Method> mockedMethods) {
            StringBuilder tpl = new StringBuilder();
            tpl.append("MockedConstruction<").append(className).append("> mockObj").append(mockName)
                    .append(" = mockConstruction(").append(className).append(".class, (mock, context) -> {");
            for (J.MethodDeclaration m : mockedMethods.keySet()) {
                appendDoAnswer(tpl, m, "mock");
            }

            // Call real method for non private, non static methods
            clazz.getMethods()
                    .stream()
                    .filter(m -> !m.isConstructor())
                    .filter(m -> !m.getFlags().contains(Static))
                    .filter(m -> !m.getFlags().contains(Private))
                    .filter(m -> !mockedMethods.containsValue(m))
                    .forEach(m -> {
                        tpl.append("when(mock.").append(m.getName());
                        appendCallRealMethod(tpl, m);
                    });

            tpl.append("});");
            return tpl.toString();
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDeclaration, ExecutionContext ctx) {
            J.MethodDeclaration md = super.visitMethodDeclaration(methodDeclaration, ctx);
            if (md.getBody() == null) {
                return md;
            }

            List<String> closeVariables = new ArrayList<>();
            for (Statement statement : md.getBody().getStatements()) {
                if (!(statement instanceof J.NewClass)) {
                    continue;
                }

                J.NewClass newClass = (J.NewClass) statement;
                if (newClass.getClazz() == null || newClass.getBody() == null ||
                        !TypeUtils.isOfClassType(newClass.getClazz().getType(), "mockit.MockUp")) {
                    continue;
                }

                // Only discard @Mock method declarations
                List<Statement> otherStatements = newClass
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
                        .collect(toList());
                List<Statement> bodyStatements = md.getBody().getStatements();
                bodyStatements.addAll(bodyStatements.indexOf(statement), otherStatements);
                md = md.withBody(md.getBody().withStatements(bodyStatements));

                JavaType mockType = ((J.ParameterizedType) newClass.getClazz()).getTypeParameters().get(0).getType();
                String className = TypeUtils.asFullyQualified(mockType).getClassName();
                String mockName = className.replace(".", "_");

                Map<J.MethodDeclaration, JavaType.Method> mockedMethods = newClass.getBody()
                        .getStatements()
                        .stream()
                        .filter(s -> s instanceof J.MethodDeclaration)
                        .filter(s -> ((J.MethodDeclaration) s).getLeadingAnnotations().stream()
                                .anyMatch(o -> TypeUtils.isOfClassType(o.getType(), "mockit.Mock")))
                        .map(s -> {
                            J.MethodDeclaration method = (J.MethodDeclaration) s;
                            Optional<JavaType.Method> found = TypeUtils.findDeclaredMethod(
                                    TypeUtils.asFullyQualified(mockType),
                                    method.getSimpleName(),
                                    method.getMethodType().getParameterTypes()
                            );
                            if (found.isPresent()) {
                                JavaType.Method m = found.get();
                                if (!m.getFlags().contains(Private)) {
                                    return new AbstractMap.SimpleEntry<>(method, found.get());
                                }
                            }
                            return null;
                        })
                        .filter(Objects::nonNull)
                        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

                // Static
                Map<J.MethodDeclaration, JavaType.Method> mockedPublicStaticMethods = mockedMethods
                        .entrySet()
                        .stream()
                        .filter(m -> m.getValue().getFlags().contains(Static))
                        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
                if (!mockedPublicStaticMethods.isEmpty()) {
                    String tpl = addMockStaticMethods((JavaType.Class) mockType, className, mockName, mockedPublicStaticMethods);
                    md = JavaTemplate.builder(tpl)
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
                Map<J.MethodDeclaration, JavaType.Method> mockedPublicMethods = mockedMethods
                        .entrySet()
                        .stream()
                        .filter(m -> !m.getValue().getFlags().contains(Static))
                        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
                if (!mockedPublicMethods.isEmpty()) {
                    String tpl = addMockInstanceMethods((JavaType.Class) mockType, className, mockName, mockedPublicMethods);
                    md = JavaTemplate.builder(tpl)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-5"))
                            .imports("org.mockito.MockedConstruction")
                            .staticImports("org.mockito.Mockito.*")
                            .contextSensitive()
                            .build()
                            .apply(updateCursor(md), statement.getCoordinates().after());
                    maybeAddImport("org.mockito.MockedConstruction");
                    closeVariables.add("mockObj" + mockName);
                }

                maybeAddImport("org.mockito.Mockito", "*", false);
                maybeRemoveImport("mockit.Mock");
                maybeRemoveImport("mockit.MockUp");

                // Remove MockUp statement
                bodyStatements = md.getBody().getStatements();
                bodyStatements.remove(statement);
                md = md.withBody(md.getBody().withStatements(bodyStatements));
            }

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

            return maybeAutoFormat(methodDeclaration, md, ctx);
        }
    }
}
