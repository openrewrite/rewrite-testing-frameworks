package org.openrewrite.java.testing.mockito;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER;
import static org.openrewrite.java.VariableNameUtils.findNamesInScope;
import static org.openrewrite.java.VariableNameUtils.generateVariableName;
import static org.openrewrite.java.trait.Traits.annotated;

/**
 * Ensures that all mockStatic calls are properly closed.
 * If mockStatic is in lifecycle methods like @BeforeEach or @BeforeAll,
 * creates a class variable and closes it in @AfterEach or @AfterAll.
 * If mockStatic is inside a test method, wraps it in a try-with-resources block.
 */
public class CloseUnclosedStaticMocks extends Recipe {

    private static final String LIFECYCLE_METHOD = "lifecycle_method";
    private static final MethodMatcher MOCK_STATIC_MATCHER = new MethodMatcher("org.mockito.Mockito mockStatic(..)");
    private static final AnnotationMatcher BEFORE_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.Before*");
    private static final AnnotationMatcher AFTER_EACH_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.AfterEach");
    private static final AnnotationMatcher AFTER_ALL_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.AfterAll");

    @Override
    public String getDisplayName() {
        return "Close unclosed static mocks";
    }

    @Override
    public String getDescription() {
        return "Ensures that all mockStatic calls are properly closed. " +
               "If mockStatic is in lifecycle methods like @BeforeEach or @BeforeAll, " +
               "creates a class variable and closes it in @AfterEach or @AfterAll. " +
               "If mockStatic is inside a test method, wraps it in a try-with-resources block.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(MOCK_STATIC_MATCHER), new CloseUnclosedStaticMocksVisitor());
    }

    private static class CloseUnclosedStaticMocksVisitor extends JavaVisitor<ExecutionContext> {

        @Override
        public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            J j = super.visitCompilationUnit(cu, ctx);
            maybeAddImport("org.mockito.MockedStatic");
            maybeAddImport("org.junit.jupiter.api.AfterEach");
            maybeAddImport("org.junit.jupiter.api.AfterAll");
            return j;
        }

        @Override
        public J visitTryResource(J.Try.Resource tryResource, ExecutionContext ctx) {
            return tryResource; // skip try resource
        }

        @Override
        public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            Cursor cursor = getCursor();
            annotated(BEFORE_MATCHER).asVisitor(a -> {
                cursor.putMessage(LIFECYCLE_METHOD, a.getTree().getSimpleName());
                return a.getTree();
            }).visit(method, ctx);
            return super.visitMethodDeclaration(method, ctx);
        }

        @Override
        public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
            String lifeCycleMethod = getCursor().getNearestMessage(LIFECYCLE_METHOD);
            if (!MOCK_STATIC_MATCHER.matches(mi) || lifeCycleMethod == null) {
                return mi;
            }
            if (getCursor().getParentTreeCursor().getValue() instanceof J.Block) {
                String mockedClassName = getMockedClassName(mi);
                if (mockedClassName != null) {
                    Cursor classCursor = getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance);
                    String varName = generateVariableName("mockedStatic" + mockedClassName, classCursor, INCREMENT_NUMBER);
                    J.Assignment assignment = JavaTemplate.builder(varName + " = #{any()}")
                            .build()
                            .apply(updateCursor(mi), mi.getCoordinates().replace(), mi);
                    doAfterVisit(new DeclareMockVarAndClose(varName, mockedClassName, lifeCycleMethod.equals("BeforeAll")));
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
                        doAfterVisit(new DeclareMockVarAndClose(varType.getName(), null, varType.getFlags().contains(Flag.Static)));
                    }
                }
            }
            return super.visitAssignment(assignment, ctx);
        }

        @Override
        public J visitVariableDeclarations(J.VariableDeclarations variableDeclarations, ExecutionContext ctx) {
            J.VariableDeclarations vd = (J.VariableDeclarations) super.visitVariableDeclarations(variableDeclarations, ctx);
            J.VariableDeclarations.NamedVariable namedVariable = vd.getVariables().get(0);
            String lifeCycleMethod = getCursor().getNearestMessage(LIFECYCLE_METHOD);
            if (!MOCK_STATIC_MATCHER.matches(namedVariable.getInitializer()) || lifeCycleMethod == null) {
                return vd;
            }
            String varName = namedVariable.getSimpleName();
            String mockedClassName = getMockedClassName((J.MethodInvocation) namedVariable.getInitializer());
            if (mockedClassName != null) {
                doAfterVisit(new DeclareMockVarAndClose(varName, mockedClassName, lifeCycleMethod.equals("BeforeAll")));
                return JavaTemplate.builder(varName + " = #{any()}").contextSensitive().build()
                        .apply(updateCursor(vd), vd.getCoordinates().replace(), namedVariable.getInitializer());
            }
            return vd;
        }

        @Override
        public J visitBlock(J.Block block, ExecutionContext ctx) {
            J.Block b = (J.Block) super.visitBlock(block, ctx);
            if (getCursor().getNearestMessage(LIFECYCLE_METHOD) != null) {
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
                    String varName = generateVariableName("mockedStatic" + mockedClassName, getCursor(), INCREMENT_NUMBER);
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
            if (statement instanceof J.Assignment) {
                J.Assignment assignment = (J.Assignment) statement;
                return MOCK_STATIC_MATCHER.matches(assignment.getAssignment());
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
    }

    @RequiredArgsConstructor
    private static class DeclareMockVarAndClose extends JavaIsoVisitor<ExecutionContext> {
        private final String varName;
        private final String mockedClassName;
        private final boolean isStatic;

        private boolean closed = false;

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = classDecl;
            if (!findNamesInScope(getCursor()).contains(varName)) {
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
            String methodTemplate = String.format("%s void %s() { %s.close(); }", isStatic ? "@AfterAll public static" : "@AfterEach public", methodName, varName);
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
            boolean matched = annotated(annotationMatcher).<AtomicBoolean>asVisitor((a, found) -> {
                found.set(true);
                return a.getTree();
            }).reduce(md, new AtomicBoolean()).get();
            if (!matched) {
                return md;
            }
            closed = true;
            return JavaTemplate.builder(varName + ".close()").contextSensitive().build()
                    .apply(updateCursor(md), md.getBody().getCoordinates().lastStatement());
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInvocation, ExecutionContext ctx) {
            if (methodInvocation.getSelect() instanceof J.Identifier) {
                String selector = ((J.Identifier) methodInvocation.getSelect()).getSimpleName();
                if (selector.equals(varName) && methodInvocation.getSimpleName().equals("close")) {
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
    }
}
