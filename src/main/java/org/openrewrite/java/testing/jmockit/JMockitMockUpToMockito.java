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
import lombok.SneakyThrows;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import static org.openrewrite.java.testing.jmockit.JMockitBlockType.MockUp;
import static org.openrewrite.java.testing.mockito.MockitoUtils.maybeAddMethodWithAnnotation;
import static org.openrewrite.java.tree.Flag.Private;
import static org.openrewrite.java.tree.Flag.Static;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.staticanalysis.RemoveUnusedLocalVariables;

import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
        private final Map<String, J.Identifier> tearDownMocks = new HashMap<>();

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

        @SneakyThrows
        private void appendAnswer(StringBuilder sb, J.MethodDeclaration md) {
            Method findRefs = RemoveUnusedLocalVariables.class
              .getDeclaredClasses()[0]
              .getDeclaredMethod("findRhsReferences", J.class, J.Identifier.class);
            findRefs.setAccessible(true);

            Set<String> usedVariables = new HashSet<>();
            (new JavaIsoVisitor<Set<String>>() {
                @SneakyThrows
                public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, Set<String> ctx) {
                    Cursor scope = getCursor().dropParentUntil((is) -> is instanceof J.ClassDeclaration || is instanceof J.Block || is instanceof J.MethodDeclaration || is instanceof J.ForLoop || is instanceof J.ForEachLoop || is instanceof J.ForLoop.Control || is instanceof J.ForEachLoop.Control || is instanceof J.Case || is instanceof J.Try || is instanceof J.Try.Resource || is instanceof J.Try.Catch || is instanceof J.MultiCatch || is instanceof J.Lambda || is instanceof JavaSourceFile);
                    List<J> refs = (List<J>) findRefs.invoke(null, scope.getValue(), variable.getName());
                    if (!refs.isEmpty()) {
                        ctx.add(variable.getSimpleName());
                    }
                    return super.visitVariable(variable, ctx);
                }
            }).visit(md, usedVariables);

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
                if (usedVariables.contains(varName)) {
                    sb.append(className).append(" ").append(varName)
                      .append(" = (").append(className).append(") invocation.getArgument(").append(i).append(");");
                }
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
            if (isBeforeTest) {
                tpl.append("#{any(").append(MOCKITO_STATIC_IMPORT).append(")}");
            } else {
                tpl.append("MockedStatic ").append(MOCKITO_STATIC_PREFIX).append(mockName);
            }
            tpl.append(" = mockStatic(").append(className).append(".class);");

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
            // Handle mocked construction mockedMethods
            if (isBeforeTest) {
                tpl.append("#{any(").append(MOCKITO_CONSTRUCTION_IMPORT).append(")}");
            } else {
                tpl.append("MockedConstruction ").append(MOCKITO_CONSTRUCTION_PREFIX).append(mockName);
            }
            tpl.append(" = mockConstruction(").append(className).append(".class, (mock, context) -> {");

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
                      cd[0] = JavaTemplate.builder("private MockedStatic #{};")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, MOCKITO_CLASSPATH))
                        .imports(MOCKITO_STATIC_IMPORT)
                        .staticImports(MOCKITO_ALL_IMPORT)
                        .build()
                        .apply(
                          new Cursor(getCursor().getParentOrThrow(), cd[0]),
                          cd[0].getBody().getCoordinates().firstStatement(),
                          MOCKITO_STATIC_PREFIX + mockName
                        );
                      J.VariableDeclarations mockField = (J.VariableDeclarations) cd[0].getBody().getStatements().get(0);
                      J.Identifier mockFieldId = mockField.getVariables().get(0).getName();
                      tearDownMocks.put(MOCKITO_STATIC_PREFIX + mockName, mockFieldId);
                  }
                  // Add mockConstruction field
                  if (mockedMethods.values().stream().anyMatch(m -> !m.getFlags().contains(Static))) {
                      cd[0] = JavaTemplate.builder("private MockedConstruction #{};")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, MOCKITO_CLASSPATH))
                        .imports(MOCKITO_CONSTRUCTION_IMPORT)
                        .staticImports(MOCKITO_ALL_IMPORT)
                        .build()
                        .apply(
                          updateCursor(cd[0]),
                          cd[0].getBody().getCoordinates().firstStatement(),
                          MOCKITO_CONSTRUCTION_PREFIX + mockName
                        );
                      J.VariableDeclarations mockField = (J.VariableDeclarations) cd[0].getBody().getStatements().get(0);
                      J.Identifier mockFieldId = mockField.getVariables().get(0).getName();
                      tearDownMocks.put(MOCKITO_CONSTRUCTION_PREFIX + mockName, mockFieldId);
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
                for (J.Identifier id : tearDownMocks.values()) {
                    String type = TypeUtils.asFullyQualified(id.getFieldType().getType()).getFullyQualifiedName();
                    md = JavaTemplate.builder("#{any(" + type + ")}.closeOnDemand();")
                      .contextSensitive()
                      .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, MOCKITO_CLASSPATH))
                      .imports(MOCKITO_STATIC_IMPORT, MOCKITO_CONSTRUCTION_IMPORT)
                      .staticImports(MOCKITO_ALL_IMPORT)
                      .build()
                      .apply(
                        updateCursor(md),
                        md.getBody().getCoordinates().lastStatement(),
                        id
                      );
                }
                return md;
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
                    JavaTemplate tpl = JavaTemplate.builder(
                        addMockStaticMethods(isBeforeTest, (JavaType.Class) mockType, className, mockName, mockedPublicStaticMethods)
                      )
                      .contextSensitive()
                      .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, MOCKITO_CLASSPATH))
                      .imports(MOCKITO_STATIC_IMPORT)
                      .staticImports(MOCKITO_ALL_IMPORT)
                      .build();
                    if (isBeforeTest) {
                        md = tpl.apply(
                          updateCursor(md),
                          statement.getCoordinates().after(),
                          tearDownMocks.get(MOCKITO_STATIC_PREFIX + mockName)
                        );
                    } else {
                        md = tpl.apply(
                          updateCursor(md),
                          statement.getCoordinates().after()
                        );
                    }

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
                    JavaTemplate tpl = JavaTemplate.builder(
                        addMockInstanceMethods(isBeforeTest, (JavaType.Class) mockType, className, mockName, mockedPublicMethods)
                      )
                      .contextSensitive()
                      .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, MOCKITO_CLASSPATH))
                      .imports(MOCKITO_CONSTRUCTION_IMPORT)
                      .staticImports(MOCKITO_ALL_IMPORT)
                      .build();
                    if (isBeforeTest) {
                        md = tpl.apply(
                          updateCursor(md),
                          statement.getCoordinates().after(),
                          tearDownMocks.get(MOCKITO_CONSTRUCTION_PREFIX + mockName)
                        );
                    } else {
                        md = tpl.apply(
                          updateCursor(md),
                          statement.getCoordinates().after()
                        );
                    }

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

            return maybeAutoFormat(methodDecl, md, ctx);
        }
    }
}
