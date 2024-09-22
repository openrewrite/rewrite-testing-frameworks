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

import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.*;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.staticanalysis.RemoveUnusedLocalVariables;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.openrewrite.java.testing.jmockit.JMockitBlockType.MockUp;
import static org.openrewrite.java.testing.mockito.MockitoUtils.maybeAddMethodWithAnnotation;
import static org.openrewrite.java.tree.Flag.Private;
import static org.openrewrite.java.tree.Flag.Static;

public class JMockitMockUpToMockito extends Recipe {
    private static final String MOCKITO_CLASSPATH = "mockito-core-5";
    private static final String MOCKITO_ALL_IMPORT = "org.mockito.Mockito.*";
    private static final String JMOCKIT_MOCKUP_IMPORT = "mockit.MockUp";
    private static final String JMOCKIT_MOCK_IMPORT = "mockit.Mock";
    private static final String MOCKITO_STATIC_PREFIX = "mockStatic";
    private static final String MOCKITO_STATIC_IMPORT = "org.mockito.MockedStatic";
    private static final String MOCKITO_CONSTRUCTION_PREFIX = "mockObj";
    private static final String MOCKITO_CONSTRUCTION_IMPORT = "org.mockito.MockedConstruction";

    @Override
    public String getDisplayName() {
        return "Rewrite JMockit MockUp to Mockito statements";
    }

    @Override
    public String getDescription() {
        return "Rewrites JMockit `MockUp` blocks to Mockito statements. This recipe will not rewrite private methods in MockUp.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(MockUp.getFqn(), false), new JMockitMockUpToMockitoVisitor());
    }

    private static class JMockitMockUpToMockitoVisitor extends JavaIsoVisitor<ExecutionContext> {
        private List<String> tearDownMocks = new ArrayList<>();

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

        private void appendAnswer(StringBuilder sb, J.MethodDeclaration md) {
            List<Statement> parameters = md.getParameters();
            for (int i = 0; i < parameters.size(); i++) {
                if (!(parameters.get(i) instanceof J.VariableDeclarations)) {
                    continue;
                }
                J.VariableDeclarations vd = (J.VariableDeclarations) parameters.get(i);
                String className;
                if (vd.getType() instanceof JavaType.Primitive) {
                    className = vd.getType().toString();
                } else {
                    className = vd.getTypeAsFullyQualified().getClassName();
                }
                String varName = vd.getVariables().get(0).getName().getSimpleName();
                sb.append(className).append(" ").append(varName)
                  .append(" = (").append(className).append(") invocation.getArgument(").append(i).append(");");
            }

            boolean hasReturn = false;
            for (Statement s : md.getBody().getStatements()) {
                hasReturn = hasReturn || s instanceof J.Return;
                sb.append(s.print());
                sb.append(";");
            }
            // Avoid syntax error
            if (!hasReturn) {
                sb.append("return null;");
            }
        }

        private void appendDoAnswer(StringBuilder sb, J.MethodDeclaration m, String objName) {
            sb.append("doAnswer(invocation -> {");
            appendAnswer(sb, m);
            sb.append("}).when(").append(objName).append(").").append(m.getSimpleName()).append("(");

            sb.append(m.getParameters()
              .stream()
              .filter(J.VariableDeclarations.class::isInstance)
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

        private String addMockStaticMethods(boolean isBeforeTest, JavaType.Class clazz, String className, String mockName, Map<J.MethodDeclaration, JavaType.Method> mockedMethods) {
            StringBuilder tpl = new StringBuilder();

            // Handle mocked static mockedMethods
            if (!isBeforeTest) {
                tpl.append("MockedStatic<").append(className).append("> ");
            }
            tpl.append(MOCKITO_STATIC_PREFIX).append(mockName).append(" = mockStatic(").append(className).append(".class);");

            // To generate predictable method order
            List<J.MethodDeclaration> keys = mockedMethods.keySet().stream()
              .sorted(Comparator.comparing((J.MethodDeclaration::print)))
              .collect(toList());
            for (J.MethodDeclaration m : keys) {
                tpl.append("mockStatic").append(mockName);
                tpl.append(".when(() -> ").append(className).append(".").append(m.getSimpleName()).append("(");
                tpl.append(m.getParameters()
                  .stream()
                  .filter(J.VariableDeclarations.class::isInstance)
                  .map(o -> getMatcher(((J.VariableDeclarations) o).getType()))
                  .collect(Collectors.joining(", ")));
                tpl.append(")).thenAnswer(invocation -> {");
                appendAnswer(tpl, m);
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

        private String addMockInstanceMethods(boolean isBeforeTest, JavaType.Class clazz, String className, String mockName, Map<J.MethodDeclaration, JavaType.Method> mockedMethods) {
            StringBuilder tpl = new StringBuilder();
            // Handle mocked static mockedMethods
            if (!isBeforeTest) {
                tpl.append("MockedConstruction<").append(className).append("> ");
            }
            tpl.append(MOCKITO_CONSTRUCTION_PREFIX).append(mockName)
              .append(" = mockConstruction(").append(className).append(".class, (mock, context) -> {");

            // To generate predictable method order
            List<J.MethodDeclaration> keys = mockedMethods.keySet().stream()
              .sorted(Comparator.comparing((J.MethodDeclaration::print)))
              .collect(toList());
            for (J.MethodDeclaration m : keys) {
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

        private boolean isMockUpStatement(Tree tree) {
            return tree instanceof J.NewClass &&
              ((J.NewClass) tree).getClazz() != null &&
              TypeUtils.isOfClassType(((J.NewClass) tree).getClazz().getType(), JMOCKIT_MOCKUP_IMPORT);
        }

        private boolean isSetUpMethod(J.MethodDeclaration md) {
            return md
              .getLeadingAnnotations()
              .stream()
              .anyMatch(o -> TypeUtils.isOfClassType(o.getType(), "org.junit.Before"));
        }

        private boolean isTearDownMethod(J.MethodDeclaration md) {
            return md
              .getLeadingAnnotations()
              .stream()
              .anyMatch(o -> TypeUtils.isOfClassType(o.getType(), "org.junit.After"));
        }

        private Map<J.MethodDeclaration, JavaType.Method> getMockUpMethods(J.NewClass newClass) {
            JavaType mockType = ((J.ParameterizedType) newClass.getClazz()).getTypeParameters().get(0).getType();
            return newClass.getBody()
              .getStatements()
              .stream()
              .filter(J.MethodDeclaration.class::isInstance)
              .map(J.MethodDeclaration.class::cast)
              .filter(s -> s.getLeadingAnnotations().stream()
                .anyMatch(o -> TypeUtils.isOfClassType(o.getType(), JMOCKIT_MOCK_IMPORT)))
              .map(method -> {
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
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            // Handle @Before/@BeforeEach mockUp
            Set<J.MethodDeclaration> mds = TreeVisitor.collect(
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext ctx) {
                        if (isSetUpMethod(md)) {
                            return SearchResult.found(md);
                        }
                        return super.visitMethodDeclaration(md, ctx);
                    }
                },
                classDecl,
                new HashSet<>()
              )
              .stream()
              .filter(J.MethodDeclaration.class::isInstance)
              .map(J.MethodDeclaration.class::cast)
              .collect(Collectors.toSet());
            if (mds.isEmpty()) {
                return super.visitClassDeclaration(classDecl, ctx);
            }

            final J.ClassDeclaration[] cd = {classDecl};
            mds.forEach(md -> md.getBody()
              .getStatements()
              .stream()
              .filter(this::isMockUpStatement)
              .map(J.NewClass.class::cast)
              .forEach(newClass -> {
                  JavaType mockType = ((J.ParameterizedType) newClass.getClazz()).getTypeParameters().get(0).getType();
                  String className = TypeUtils.asFullyQualified(mockType).getClassName();
                  String mockName = className.replace(".", "_");
                  Map<J.MethodDeclaration, JavaType.Method> mockedMethods = getMockUpMethods(newClass);

                  // Add mockStatic field
                  if (mockedMethods.values().stream().anyMatch(m -> m.getFlags().contains(Static))) {
                      cd[0] = JavaTemplate.builder("private MockedStatic<#{}> " + MOCKITO_STATIC_PREFIX + "#{};")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, MOCKITO_CLASSPATH))
                        .imports(MOCKITO_STATIC_IMPORT)
                        .staticImports(MOCKITO_ALL_IMPORT)
                        .build()
                        .apply(
                          new Cursor(getCursor().getParentOrThrow(), cd[0]),
                          cd[0].getBody().getCoordinates().firstStatement(),
                          className,
                          mockName
                        );
                      tearDownMocks.add(MOCKITO_STATIC_PREFIX + mockName);
                  }
                  // Add mockConstruction field
                  if (mockedMethods.values().stream().anyMatch(m -> !m.getFlags().contains(Static))) {
                      cd[0] = JavaTemplate.builder("private MockedConstruction<" + className + "> " + MOCKITO_CONSTRUCTION_PREFIX + mockName + ";")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, MOCKITO_CLASSPATH))
                        .imports(MOCKITO_CONSTRUCTION_IMPORT)
                        .staticImports(MOCKITO_ALL_IMPORT)
                        .build()
                        .apply(
                          updateCursor(cd[0]),
                          cd[0].getBody().getCoordinates().firstStatement()
                        );
                      tearDownMocks.add(MOCKITO_CONSTRUCTION_PREFIX + mockName);
                  }
              }));

            cd[0] = maybeAddMethodWithAnnotation(this, cd[0], ctx, "tearDown",
              "@org.junit.After",
              "@After",
              "junit-4.13",
              "org.junit.After",
              "");

            return super.visitClassDeclaration(cd[0], ctx);
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDecl, ExecutionContext ctx) {
            J.MethodDeclaration md = methodDecl;
            if (md.getBody() == null) {
                return md;
            }
            if (isTearDownMethod(md)) {
                for (String v : tearDownMocks) {
                    md = JavaTemplate.builder(v + ".closeOnDemand();")
                      .contextSensitive()
                      .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, MOCKITO_CLASSPATH))
                      .imports(MOCKITO_STATIC_IMPORT, MOCKITO_CONSTRUCTION_IMPORT)
                      .staticImports(MOCKITO_ALL_IMPORT)
                      .build()
                      .apply(
                        updateCursor(md),
                        md.getBody().getCoordinates().lastStatement()
                      );
                }
                return super.visitMethodDeclaration(md, ctx);
            }

            List<String> openedMocks = new ArrayList<>();
            boolean isBeforeTest = isSetUpMethod(md);
            for (Statement statement : md.getBody().getStatements()) {
                if (!isMockUpStatement(statement)) {
                    continue;
                }

                J.NewClass newClass = (J.NewClass) statement;

                // Only discard @Mock method declarations
                List<Statement> otherStatements = newClass
                  .getBody()
                  .getStatements()
                  .stream()
                  .filter(s -> {
                      if (s instanceof J.MethodDeclaration) {
                          return ((J.MethodDeclaration) s).getLeadingAnnotations().stream()
                            .noneMatch(o -> TypeUtils.isOfClassType(o.getType(), JMOCKIT_MOCK_IMPORT));
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

                Map<J.MethodDeclaration, JavaType.Method> mockedMethods = getMockUpMethods(newClass);

                // Add MockStatic
                Map<J.MethodDeclaration, JavaType.Method> mockedPublicStaticMethods = mockedMethods
                  .entrySet()
                  .stream()
                  .filter(m -> m.getValue().getFlags().contains(Static))
                  .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
                if (!mockedPublicStaticMethods.isEmpty()) {
                    md = JavaTemplate.builder(
                        addMockStaticMethods(isBeforeTest, (JavaType.Class) mockType, className, mockName, mockedPublicStaticMethods)
                      )
                      .contextSensitive()
                      .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, MOCKITO_CLASSPATH))
                      .imports(MOCKITO_STATIC_IMPORT)
                      .staticImports(MOCKITO_ALL_IMPORT)
                      .build()
                      .apply(updateCursor(md), statement.getCoordinates().after());
                    maybeAddImport(MOCKITO_STATIC_IMPORT);
                    openedMocks.add(MOCKITO_STATIC_PREFIX + mockName);
                }

                // Add MockConstruction
                Map<J.MethodDeclaration, JavaType.Method> mockedPublicMethods = mockedMethods
                  .entrySet()
                  .stream()
                  .filter(m -> !m.getValue().getFlags().contains(Static))
                  .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
                if (!mockedPublicMethods.isEmpty()) {
                    md = JavaTemplate.builder(
                        addMockInstanceMethods(isBeforeTest, (JavaType.Class) mockType, className, mockName, mockedPublicMethods)
                      )
                      .contextSensitive()
                      .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, MOCKITO_CLASSPATH))
                      .imports(MOCKITO_CONSTRUCTION_IMPORT)
                      .staticImports(MOCKITO_ALL_IMPORT)
                      .build()
                      .apply(updateCursor(md), statement.getCoordinates().after());
                    maybeAddImport(MOCKITO_CONSTRUCTION_IMPORT);
                    openedMocks.add(MOCKITO_CONSTRUCTION_PREFIX + mockName);
                }

                maybeAddImport("org.mockito.Mockito", "*", false);
                maybeRemoveImport(JMOCKIT_MOCK_IMPORT);
                maybeRemoveImport(JMOCKIT_MOCKUP_IMPORT);

                // Remove MockUp statement
                bodyStatements = md.getBody().getStatements();
                bodyStatements.remove(statement);
                md = md.withBody(md.getBody().withStatements(bodyStatements));
            }

            // Add close statement at the end
            if (!isBeforeTest) {
                for (String v : openedMocks) {
                    md = JavaTemplate.builder(v + ".closeOnDemand();")
                      .contextSensitive()
                      .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, MOCKITO_CLASSPATH))
                      .imports(MOCKITO_STATIC_IMPORT, MOCKITO_CONSTRUCTION_IMPORT)
                      .staticImports(MOCKITO_ALL_IMPORT)
                      .build()
                      .apply(updateCursor(md), md.getBody().getCoordinates().lastStatement());
                }
            }

            doAfterVisit(new RemoveUnusedLocalVariables(new String[]{}).getVisitor());
            return maybeAutoFormat(methodDecl, md, ctx);
        }
    }
}
