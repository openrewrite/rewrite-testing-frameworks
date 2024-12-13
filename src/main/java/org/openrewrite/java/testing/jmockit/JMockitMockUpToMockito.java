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
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.staticanalysis.LambdaBlockToExpression;
import org.openrewrite.staticanalysis.VariableReferences;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.openrewrite.java.testing.jmockit.JMockitUtils.MOCKITO_ALL_IMPORT;
import static org.openrewrite.java.testing.jmockit.JMockitUtils.getJavaParser;
import static org.openrewrite.java.testing.mockito.MockitoUtils.maybeAddMethodWithAnnotation;
import static org.openrewrite.java.tree.Flag.Private;
import static org.openrewrite.java.tree.Flag.Static;

public class JMockitMockUpToMockito extends Recipe {

    private static final String JMOCKIT_MOCKUP_IMPORT = "mockit.MockUp";
    private static final String JMOCKIT_MOCK_IMPORT = "mockit.Mock";
    private static final String MOCKITO_MATCHER_IMPORT = "org.mockito.ArgumentMatchers.*";
    private static final String MOCKITO_DELEGATEANSWER_IMPORT = "org.mockito.AdditionalAnswers.delegatesTo";
    private static final String MOCKITO_STATIC_PREFIX = "mockStatic";
    private static final String MOCKITO_STATIC_IMPORT = "org.mockito.MockedStatic";
    private static final String MOCKITO_MOCK_PREFIX = "mock";
    private static final String MOCKITO_CONSTRUCTION_PREFIX = "mockCons";
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
        return Preconditions.check(new UsesType<>(JMOCKIT_MOCKUP_IMPORT, false), new JMockitMockUpToMockitoVisitor());
    }

    private static class JMockitMockUpToMockitoVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final Map<String, J.Identifier> tearDownMocks = new HashMap<>();

        /**
         * Handle at class level because need to handle the case where when there is a MockUp in a setup method, and we
         * need to close the migrated mockCons in the teardown, yet the teardown method comes before the setup method
         */
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

            AtomicReference<J.ClassDeclaration> cdRef = new AtomicReference<>(classDecl);
            mds.forEach(md -> md.getBody()
                    .getStatements()
                    .stream()
                    .filter(this::isMockUpStatement)
                    .map(J.NewClass.class::cast)
                    .forEach(newClass -> {
                        String className = ((J.ParameterizedType) newClass.getClazz()).getTypeParameters().get(0).toString();

                        Map<J.MethodDeclaration, JavaType.Method> mockedMethods = getMockUpMethods(newClass);

                        // Add mockStatic field
                        if (mockedMethods.values().stream().anyMatch(m -> m.getFlags().contains(Static))) {
                            cdRef.set(JavaTemplate.builder("private MockedStatic #{};")
                                    .contextSensitive()
                                    .javaParser(getJavaParser(ctx))
                                    .imports(MOCKITO_STATIC_IMPORT)
                                    .staticImports(MOCKITO_ALL_IMPORT)
                                    .build()
                                    .apply(
                                            new Cursor(getCursor().getParentOrThrow(), cdRef.get()),
                                            cdRef.get().getBody().getCoordinates().firstStatement(),
                                            MOCKITO_STATIC_PREFIX + className
                                    ));
                            J.VariableDeclarations mockField = (J.VariableDeclarations) cdRef.get().getBody().getStatements().get(0);
                            J.Identifier mockFieldId = mockField.getVariables().get(0).getName();
                            tearDownMocks.put(MOCKITO_STATIC_PREFIX + className, mockFieldId);
                        }
                        // Add mockConstruction field
                        if (mockedMethods.values().stream().anyMatch(m -> !m.getFlags().contains(Static))) {
                            cdRef.set(JavaTemplate.builder("private MockedConstruction #{};")
                                    .contextSensitive()
                                    .javaParser(getJavaParser(ctx))
                                    .imports(MOCKITO_CONSTRUCTION_IMPORT)
                                    .staticImports(MOCKITO_ALL_IMPORT)
                                    .build()
                                    .apply(
                                            updateCursor(cdRef.get()),
                                            cdRef.get().getBody().getCoordinates().firstStatement(),
                                            MOCKITO_CONSTRUCTION_PREFIX + className
                                    ));
                            J.VariableDeclarations mockField = (J.VariableDeclarations) cdRef.get().getBody().getStatements().get(0);
                            J.Identifier mockFieldId = mockField.getVariables().get(0).getName();
                            tearDownMocks.put(MOCKITO_CONSTRUCTION_PREFIX + className, mockFieldId);
                        }
                    }));

            J.ClassDeclaration cd = maybeAddMethodWithAnnotation(this, cdRef.get(), ctx, true, "tearDown",
                    "@org.junit.After",
                    "@After",
                    "junit-4.13",
                    "org.junit.After",
                    "");

            return super.visitClassDeclaration(cd, ctx);
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
                            .javaParser(getJavaParser(ctx))
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

            boolean isBeforeTest = isSetUpMethod(md);
            List<String> varDeclarationInTry = new ArrayList<>();
            List<String> mockStaticMethodInTry = new ArrayList<>();
            List<String> mockConstructionMethodInTry = new ArrayList<>();
            List<Statement> encloseStatements = new ArrayList<>();
            List<Statement> residualStatements = new ArrayList<>();
            for (Statement statement : md.getBody().getStatements()) {
                if (!isMockUpStatement(statement)) {
                    encloseStatements.add(statement);
                    continue;
                }

                J.NewClass newClass = (J.NewClass) statement;

                // Only discard @Mock method declarations
                residualStatements.addAll(newClass
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
                        .collect(toList())
                );

                JavaType mockType = ((J.ParameterizedType) newClass.getClazz()).getTypeParameters().get(0).getType();
                String className = ((J.ParameterizedType) newClass.getClazz()).getTypeParameters().get(0).toString();

                Map<J.MethodDeclaration, JavaType.Method> mockedMethods = getMockUpMethods(newClass);

                // Add MockStatic
                Map<J.MethodDeclaration, JavaType.Method> mockedPublicStaticMethods = mockedMethods
                        .entrySet()
                        .stream()
                        .filter(m -> m.getValue().getFlags().contains(Static))
                        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
                if (!mockedPublicStaticMethods.isEmpty()) {
                    if (isBeforeTest) {
                        String tpl = getMockStaticDeclarationInBefore(className) +
                                     getMockStaticMethods((JavaType.Class) mockType, className, mockedPublicStaticMethods);

                        md = JavaTemplate.builder(tpl)
                                .contextSensitive()
                                .javaParser(getJavaParser(ctx))
                                .imports(MOCKITO_STATIC_IMPORT)
                                .staticImports(MOCKITO_ALL_IMPORT)
                                .build()
                                .apply(
                                        updateCursor(md),
                                        statement.getCoordinates().after(),
                                        tearDownMocks.get(MOCKITO_STATIC_PREFIX + className)
                                );
                    } else {
                        varDeclarationInTry.add(getMockStaticDeclarationInTry(className));
                        mockStaticMethodInTry.add(getMockStaticMethods((JavaType.Class) mockType, className, mockedPublicStaticMethods));
                    }

                    maybeAddImport(MOCKITO_STATIC_IMPORT);
                }

                // Add MockConstruction
                Map<J.MethodDeclaration, JavaType.Method> mockedPublicMethods = mockedMethods
                        .entrySet()
                        .stream()
                        .filter(m -> !m.getValue().getFlags().contains(Static))
                        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
                if (!mockedPublicMethods.isEmpty()) {
                    if (isBeforeTest) {
                        String tpl = getMockConstructionMethods(className, mockedPublicMethods) +
                                     getMockConstructionDeclarationInBefore(className);

                        md = JavaTemplate.builder(tpl)
                                .contextSensitive()
                                .javaParser(getJavaParser(ctx))
                                .imports(MOCKITO_STATIC_IMPORT)
                                .staticImports(MOCKITO_ALL_IMPORT, MOCKITO_DELEGATEANSWER_IMPORT)
                                .build()
                                .apply(
                                        updateCursor(md),
                                        statement.getCoordinates().after(),
                                        tearDownMocks.get(MOCKITO_CONSTRUCTION_PREFIX + className)
                                );
                    } else {
                        varDeclarationInTry.add(getMockConstructionDeclarationInTry(className));
                        mockConstructionMethodInTry.add(getMockConstructionMethods(className, mockedPublicMethods));
                    }

                    maybeAddImport(MOCKITO_CONSTRUCTION_IMPORT);
                    maybeAddImport("org.mockito.Answers", "CALLS_REAL_METHODS", false);
                    maybeAddImport("org.mockito.AdditionalAnswers", "delegatesTo", false);
                }

                List<Statement> statements = md.getBody().getStatements();
                statements.remove(statement);
                md = md.withBody(md.getBody().withStatements(statements));
            }

            if (!varDeclarationInTry.isEmpty()) {
                String tpl = String.join("", mockConstructionMethodInTry) +
                             "try (" +
                             String.join(";", varDeclarationInTry) +
                             ") {" +
                             String.join(";", mockStaticMethodInTry) +
                             "}";

                J.MethodDeclaration residualMd = md.withBody(md.getBody().withStatements(residualStatements));
                residualMd = JavaTemplate.builder(tpl)
                        .contextSensitive()
                        .javaParser(getJavaParser(ctx))
                        .imports(MOCKITO_STATIC_IMPORT, MOCKITO_CONSTRUCTION_IMPORT)
                        .staticImports(MOCKITO_ALL_IMPORT, MOCKITO_MATCHER_IMPORT, MOCKITO_MATCHER_IMPORT, MOCKITO_DELEGATEANSWER_IMPORT)
                        .build()
                        .apply(updateCursor(residualMd), residualMd.getBody().getCoordinates().lastStatement());

                List<Statement> mdStatements = residualMd.getBody().getStatements();
                J.Try try_ = (J.Try) mdStatements.get(mdStatements.size() - 1);

                List<Statement> tryStatements = try_.getBody().getStatements();
                tryStatements.addAll(encloseStatements);
                try_ = try_.withBody(try_.getBody().withStatements(tryStatements));

                mdStatements.set(mdStatements.size() - 1, try_);
                md = md.withBody(residualMd.getBody().withStatements(mdStatements));
            }

            maybeAddImport(MOCKITO_ALL_IMPORT.replace(".*", ""), "*", false);
            maybeRemoveImport(JMOCKIT_MOCK_IMPORT);
            maybeRemoveImport(JMOCKIT_MOCKUP_IMPORT);

            doAfterVisit(new LambdaBlockToExpression().getVisitor());
            return maybeAutoFormat(methodDecl, md, ctx);
        }

        private String getMatcher(JavaType s) {
            maybeAddImport(MOCKITO_MATCHER_IMPORT.replace(".*", ""), "*", false);
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

        private String getAnswerBody(J.MethodDeclaration md) {
            Set<String> usedVariables = new HashSet<>();
            new JavaIsoVisitor<Set<String>>() {
                @Override
                public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, Set<String> ctx) {
                    Cursor scope = getCursor().dropParentUntil((is) -> is instanceof J.ClassDeclaration || is instanceof J.Block || is instanceof J.MethodDeclaration || is instanceof J.ForLoop || is instanceof J.ForEachLoop || is instanceof J.ForLoop.Control || is instanceof J.ForEachLoop.Control || is instanceof J.Case || is instanceof J.Try || is instanceof J.Try.Resource || is instanceof J.Try.Catch || is instanceof J.MultiCatch || is instanceof J.Lambda || is instanceof JavaSourceFile);
                    if (!VariableReferences.findRhsReferences(scope.getValue(), variable.getName()).isEmpty()) {
                        ctx.add(variable.getSimpleName());
                    }
                    return super.visitVariable(variable, ctx);
                }
            }.visit(md, usedVariables);

            StringBuilder sb = new StringBuilder();
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
                            .append(" = invocation.getArgument(").append(i).append(");");
                }
            }

            boolean hasReturn = false;
            for (Statement s : md.getBody().getStatements()) {
                hasReturn |= s instanceof J.Return;
                sb.append(s.print(getCursor())).append(";");
            }
            // Avoid syntax error
            if (!hasReturn) {
                sb.append("return null;");
            }
            return sb.toString();
        }

        private String getCallRealMethod(JavaType.Method m) {
            return "(" +
                   m.getParameterTypes()
                           .stream()
                           .map(this::getMatcher)
                           .collect(Collectors.joining(", ")) +
                   ")).thenCallRealMethod();";
        }

        private String getMockStaticDeclarationInBefore(String className) {
            return "#{any(" + MOCKITO_STATIC_IMPORT + ")}" +
                   " = mockStatic(" + className + ".class);";
        }

        private String getMockStaticDeclarationInTry(String className) {
            return "MockedStatic " + MOCKITO_STATIC_PREFIX + className +
                   " = mockStatic(" + className + ".class)";
        }

        private String getMockStaticMethods(JavaType.Class clazz, String className, Map<J.MethodDeclaration, JavaType.Method> mockedMethods) {
            StringBuilder tpl = new StringBuilder();

            // To generate predictable method order
            List<J.MethodDeclaration> keys = mockedMethods.keySet().stream()
                    .sorted(Comparator.comparing(o -> o.print(getCursor())))
                    .collect(toList());
            for (J.MethodDeclaration m : keys) {
                tpl.append("mockStatic").append(className)
                        .append(".when(() -> ").append(className).append(".").append(m.getSimpleName()).append("(")
                        .append(m.getParameters()
                                .stream()
                                .filter(J.VariableDeclarations.class::isInstance)
                                .map(J.VariableDeclarations.class::cast)
                                .map(J.VariableDeclarations::getType)
                                .map(this::getMatcher)
                                .collect(Collectors.joining(", "))
                        )
                        .append(")).thenAnswer(invocation -> {")
                        .append(getAnswerBody(m))
                        .append("});");
            }

            // Call real method for non private, static methods
            clazz.getMethods()
                    .stream()
                    .filter(m -> !m.isConstructor())
                    .filter(m -> !m.getFlags().contains(Private))
                    .filter(m -> m.getFlags().contains(Static))
                    .filter(m -> !mockedMethods.containsValue(m))
                    .forEach(m -> tpl.append("mockStatic").append(className).append(".when(() -> ")
                            .append(className).append(".").append(m.getName())
                            .append(getCallRealMethod(m))
                            .append(");")
                    );

            return tpl.toString();
        }

        private String getMockConstructionDeclarationInBefore(String className) {
            return "#{any(" + MOCKITO_CONSTRUCTION_IMPORT + ")}" +
                   " = mockConstructionWithAnswer(" + className + ".class, delegatesTo(" + MOCKITO_MOCK_PREFIX + className + "));";
        }

        private String getMockConstructionDeclarationInTry(String className) {
            return "MockedConstruction " + MOCKITO_CONSTRUCTION_PREFIX + className +
                   " = mockConstructionWithAnswer(" + className + ".class, delegatesTo(" + MOCKITO_MOCK_PREFIX + className + "))";
        }

        private String getMockConstructionMethods(String className, Map<J.MethodDeclaration, JavaType.Method> mockedMethods) {
            StringBuilder tpl = new StringBuilder()
                    .append(className)
                    .append(" ")
                    .append(MOCKITO_MOCK_PREFIX).append(className)
                    .append(" = mock(").append(className).append(".class, CALLS_REAL_METHODS);");

            mockedMethods
                    .keySet()
                    .stream()
                    .sorted(Comparator.comparing(o -> o.print(getCursor())))
                    .forEach(m -> tpl.append("doAnswer(invocation -> {")
                            .append(getAnswerBody(m))
                            .append("}).when(").append(MOCKITO_MOCK_PREFIX).append(className).append(").").append(m.getSimpleName()).append("(")
                            .append(m.getParameters()
                                    .stream()
                                    .filter(J.VariableDeclarations.class::isInstance)
                                    .map(J.VariableDeclarations.class::cast)
                                    .map(J.VariableDeclarations::getType)
                                    .map(this::getMatcher)
                                    .collect(Collectors.joining(", "))
                            )
                            .append(");"));

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
    }
}
