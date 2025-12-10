/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.mockito;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.trait.Annotated;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toSet;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER;
import static org.openrewrite.java.VariableNameUtils.generateVariableName;

/**
 * Ensures that all mockStatic calls are properly closed.
 * If mockStatic is in lifecycle methods like @BeforeEach or @BeforeAll,
 * creates a class variable and closes it in @AfterEach or @AfterAll.
 * If mockStatic is inside a test method, wraps it in a try-with-resources block.
 */
public class CloseUnclosedStaticMocks extends Recipe {

    private static final MethodMatcher MOCKED_STATIC_CLOSE_MATCHER = new MethodMatcher("org.mockito.ScopedMock close*(..)");
    private static final MethodMatcher MOCK_STATIC_MATCHER = new MethodMatcher("org.mockito.Mockito mockStatic(..)");
    private static final AnnotationMatcher AFTER_EACH_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.AfterEach");
    private static final AnnotationMatcher AFTER_ALL_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.AfterAll");

    @Override
    public String getDisplayName() {
        return "Close unclosed static mocks";
    }

    @Override
    public String getDescription() {
        return "Ensures that all `mockStatic` calls are properly closed. " +
               "If `mockStatic` is in lifecycle methods like `@BeforeEach` or `@BeforeAll`, " +
               "creates a class variable and closes it in `@AfterEach` or `@AfterAll`. " +
               "If `mockStatic` is inside a test method, wraps it in a try-with-resources block.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(MOCK_STATIC_MATCHER), new CloseUnclosedStaticMocksVisitor());
    }

    private static class CloseUnclosedStaticMocksVisitor extends JavaVisitor<ExecutionContext> {

        @Override
        public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            J j = super.visitCompilationUnit(cu, ctx);
            if (j != cu) {
                maybeAddImport("org.mockito.MockedStatic");
                maybeAddImport("org.junit.jupiter.api.AfterEach");
                maybeAddImport("org.junit.jupiter.api.AfterAll");
            }
            return j;
        }

        @Override
        public J visitTryResource(J.Try.Resource tryResource, ExecutionContext ctx) {
            if (tryResource.getVariableDeclarations() instanceof J.VariableDeclarations) {
                J.VariableDeclarations vd = (J.VariableDeclarations) tryResource.getVariableDeclarations();
                Set<String> tryWithResourceVars =
                        vd.getVariables().stream()
                                .map(J.VariableDeclarations.NamedVariable::getSimpleName)
                                .collect(toSet());
                getCursor()
                        .dropParentUntil(J.MethodDeclaration.class::isInstance)
                        .computeMessageIfAbsent("tryWithResourceVars", k -> new HashSet<>())
                        .addAll(tryWithResourceVars);
            }
            return tryResource;
        }

        @Override
        public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            Cursor cursor = getCursor();
            new Annotated.Matcher("@org.junit.jupiter.api.*").asVisitor(a -> {
                String annotationName = a.getTree().getSimpleName();
                if (annotationName.startsWith("Before")) {
                    cursor.putMessage(MethodType.class.getSimpleName(), MethodType.LIFECYCLE);
                } else if (annotationName.endsWith("Test")) {
                    cursor.putMessage(MethodType.class.getSimpleName(), MethodType.TESTABLE);
                }
                return a.getTree();
            }).visit(method, ctx);

            // neither lifecycle nor test method.
            if (cursor.getMessage(MethodType.class.getSimpleName()) == null) {
                return method;
            }
            cursor.putMessage("staticMethod", method.hasModifier(J.Modifier.Type.Static));
            return super.visitMethodDeclaration(method, ctx);
        }

        @Override
        public @Nullable J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
            if (isRedundantCloseOfTryWithResource(mi)) {
                return null;
            }
            if (!MOCK_STATIC_MATCHER.matches(mi) || !insideLifecycleMethod()) {
                return mi;
            }
            if (getCursor().getParentTreeCursor().getValue() instanceof J.Block) {
                String mockedClassName = getMockedClassName(mi);
                if (mockedClassName != null) {
                    String varName = generateMockedVarName(mockedClassName);
                    J.Assignment assignment = JavaTemplate.builder(varName + " = #{any()}")
                            .build()
                            .apply(updateCursor(mi), mi.getCoordinates().replace(), mi);
                    boolean isStatic = Boolean.TRUE.equals(getCursor().getNearestMessage("staticMethod"));
                    doAfterVisit(new DeclareMockVarAndClose(getScopedClassName(), varName, mockedClassName, isStatic));
                    return assignment;
                }
            }
            return mi;
        }

        @Override
        public J visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
            if (MOCK_STATIC_MATCHER.matches(assignment.getAssignment())) {
                if (assignment.getVariable() instanceof J.Identifier) {
                    JavaType.Variable varType = ((J.Identifier) assignment.getVariable()).getFieldType();
                    if (varType != null && varType.getOwner() instanceof JavaType.Class) {
                        doAfterVisit(new DeclareMockVarAndClose(getScopedClassName(), varType.getName(), null, varType.getFlags().contains(Flag.Static)));
                    }
                }
            }
            return super.visitAssignment(assignment, ctx);
        }

        @Override
        public J visitVariableDeclarations(J.VariableDeclarations variableDeclarations, ExecutionContext ctx) {
            J.VariableDeclarations vd = (J.VariableDeclarations) super.visitVariableDeclarations(variableDeclarations, ctx);
            J.VariableDeclarations.NamedVariable namedVariable = vd.getVariables().get(0);
            if (!MOCK_STATIC_MATCHER.matches(namedVariable.getInitializer()) || !insideLifecycleMethod()) {
                return vd;
            }
            if (namedVariable.getInitializer() == null) {
                return vd;
            }
            String varName = namedVariable.getSimpleName();
            String mockedClassName = getMockedClassName((J.MethodInvocation) namedVariable.getInitializer());
            if (mockedClassName != null) {
                boolean isStatic = vd.hasModifier(J.Modifier.Type.Static) ||
                                Boolean.TRUE.equals(getCursor().getNearestMessage("staticMethod"));
                doAfterVisit(new DeclareMockVarAndClose(getScopedClassName(), varName, mockedClassName, isStatic));
                return JavaTemplate.builder(varName + " = #{any()}").contextSensitive().build()
                        .apply(updateCursor(vd), vd.getCoordinates().replace(), namedVariable.getInitializer());
            }
            return vd;
        }

        @Override
        public J visitBlock(J.Block block, ExecutionContext ctx) {
            J.Block b = (J.Block) super.visitBlock(block, ctx);
            if (insideLifecycleMethod()) {
                return b;
            }
            AtomicBoolean removeStatement = new AtomicBoolean(false);
            J.Block b1 = block.withStatements(ListUtils.map(b.getStatements(), statement -> {
                if (!removeStatement.get() && shouldUseTryWithResources(statement)) {
                    J.Try tryWithResource = toTryWithResource(b, statement, ctx);
                    if (tryWithResource != null) {
                        removeStatement.set(true);
                        return (J.Try) super.visitTry(tryWithResource, ctx);
                    }
                }
                return removeStatement.get() ? null : statement;
            }));
            return maybeAutoFormat(b, b1, ctx);
        }

        private J.@Nullable Try toTryWithResource(J.Block block, Statement statement, ExecutionContext ctx) {
            String code = null;
            if (statement instanceof J.MethodInvocation) {
                String mockedClassName = getMockedClassName((J.MethodInvocation) statement);
                if (mockedClassName != null) {
                    String varName = generateMockedVarName(mockedClassName);
                    code = String.format("try(MockedStatic<%s> %s = #{any()}) {}", mockedClassName, varName);
                }
            } else if (statement instanceof J.VariableDeclarations || statement instanceof J.Assignment) {
                code = "try(#{any()}) {}";
            }
            if (code == null) {
                return null;
            }
            J.Try tryWithResources = JavaTemplate.builder(code)
                    .contextSensitive()
                    .imports("org.mockito.MockedStatic")
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-5"))
                    .build().apply(new Cursor(getCursor(), statement), statement.getCoordinates().replace(), statement);
            return maybeAutoFormat(tryWithResources, tryWithResources.withBody(findSuccessorStatements(statement, block)), ctx);
        }

        private @Nullable String getMockedClassName(J.MethodInvocation methodInvocation) {
            JavaType.Parameterized type = TypeUtils.asParameterized(methodInvocation.getType());
            if (type != null && type.getTypeParameters().size() == 1) {
                JavaType.FullyQualified mockedClass = TypeUtils.asFullyQualified(type.getTypeParameters().get(0));
                if (mockedClass != null) {
                    return mockedClass.getClassName();
                }
            }
            return null;
        }

        private boolean shouldUseTryWithResources(@Nullable Statement statement) {
            if (statement instanceof J.VariableDeclarations) {
                J.VariableDeclarations varDecl = (J.VariableDeclarations) statement;
                return MOCK_STATIC_MATCHER.matches(varDecl.getVariables().get(0).getInitializer());
            }
            if (statement instanceof J.MethodInvocation) {
                return MOCK_STATIC_MATCHER.matches((J.MethodInvocation) statement);
            }
            return false;
        }

        private J.Block findSuccessorStatements(Statement statement, J.Block block) {
            List<Statement> successors = new ArrayList<>();
            boolean found = false;
            for (Statement successor : block.getStatements()) {
                if (found) {
                    successors.add(successor);
                }
                found = found || successor == statement;
            }
            return new J.Block(randomId(), Space.EMPTY, Markers.EMPTY,
                    new JRightPadded<>(false, Space.EMPTY, Markers.EMPTY), emptyList(),
                    Space.format(" ")).withStatements(successors);
        }

        private String generateMockedVarName(String mockedClassName) {
            return generateVariableName("mockedStatic" + mockedClassName.replace(".", "_"), getCursor(), INCREMENT_NUMBER);
        }

        private boolean insideLifecycleMethod() {
            return getCursor().getNearestMessage(MethodType.class.getSimpleName()) == MethodType.LIFECYCLE;
        }

        private @Nullable String getScopedClassName() {
            J.ClassDeclaration enclosingClass = getCursor().firstEnclosing(J.ClassDeclaration.class);
            return enclosingClass != null ? enclosingClass.getSimpleName() : null;
        }

        private boolean isRedundantCloseOfTryWithResource(J.MethodInvocation mi) {
            if (!MOCKED_STATIC_CLOSE_MATCHER.matches(mi)) {
                return false;
            }
            if (mi.getSelect() instanceof J.Identifier) {
                J.Identifier ident = (J.Identifier) mi.getSelect();
                Set<String> tryWithResourceVars = getCursor().getNearestMessage("tryWithResourceVars");
                return tryWithResourceVars != null && tryWithResourceVars.contains(ident.getSimpleName());
            }
            return false;
        }
    }

    @RequiredArgsConstructor
    private static class DeclareMockVarAndClose extends JavaIsoVisitor<ExecutionContext> {

        @Nullable
        private final String scopedClassName;

        private final String varName;

        @Nullable
        private final String mockedClassName;

        private final boolean isStatic;

        private boolean closed = false;

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            if (!classDecl.getSimpleName().equals(scopedClassName)) {
                return classDecl;
            }
            J.ClassDeclaration cd = classDecl;
            if (!isVarDeclared(cd, varName)) {
                String modifier = isStatic ? "static " : "";
                String varTemplate = "private " + modifier + "MockedStatic<" + mockedClassName + "> " + varName + ";";
                cd = JavaTemplate.builder(varTemplate)
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-5"))
                        .imports("org.mockito.MockedStatic")
                        .build().apply(getCursor(), classDecl.getBody().getCoordinates().firstStatement());
            }
            cd = super.visitClassDeclaration(cd, ctx);
            if (closed) {
                return cd;
            }
            String methodName = tearDownMethodName(cd);
            String methodTemplate = String.format("%s void %s() { %s.closeOnDemand(); }",
                    isStatic ? "@AfterAll public static" : "@AfterEach public", methodName, varName);
            return JavaTemplate.builder(methodTemplate)
                    .contextSensitive()
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5"))
                    .imports("org.junit.jupiter.api.AfterEach", "org.junit.jupiter.api.AfterAll")
                    .build().apply(updateCursor(cd), classDecl.getBody().getCoordinates().lastStatement());
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDecl, ExecutionContext ctx) {
            J.MethodDeclaration md = super.visitMethodDeclaration(methodDecl, ctx);
            if (closed) {
                return md;
            }
            AnnotationMatcher annotationMatcher = isStatic ? AFTER_ALL_MATCHER : AFTER_EACH_MATCHER;
            boolean matched = new Annotated.Matcher(annotationMatcher).<AtomicBoolean>asVisitor((a, found) -> {
                found.set(true);
                return a.getTree();
            }).reduce(md, new AtomicBoolean()).get();
            if (!matched) {
                return md;
            }
            closed = true;
            return JavaTemplate.builder(varName + ".closeOnDemand()").contextSensitive().build()
                    .apply(updateCursor(md), md.getBody().getCoordinates().lastStatement());
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInvocation, ExecutionContext ctx) {
            if (methodInvocation.getSelect() instanceof J.Identifier) {
                String selector = ((J.Identifier) methodInvocation.getSelect()).getSimpleName();
                if (selector.equals(varName) && methodInvocation.getSimpleName().startsWith("close")) {
                    closed = true;
                }
            }
            return super.visitMethodInvocation(methodInvocation, ctx);
        }

        private String tearDownMethodName(J.ClassDeclaration cd) {
            String methodName = "tearDown";
            int suffix = 0;
            String updatedMethodName = methodName;
            for (Statement st : cd.getBody().getStatements()) {
                if (st instanceof J.MethodDeclaration && ((J.MethodDeclaration) st).getSimpleName().equals(updatedMethodName)) {
                    updatedMethodName = methodName + suffix++;
                }
            }
            return updatedMethodName;
        }

        private boolean isVarDeclared(J.ClassDeclaration cd, String varName) {
            return cd.getBody().getStatements().stream()
                    .filter(s -> s instanceof J.VariableDeclarations)
                    .map(J.VariableDeclarations.class::cast)
                    .flatMap(vd -> vd.getVariables().stream())
                    .anyMatch(var -> var.getSimpleName().equals(varName));
        }
    }

    private enum MethodType {
        LIFECYCLE,
        TESTABLE
    }
}
