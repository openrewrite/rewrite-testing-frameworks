package org.openrewrite.java.testing.mockito;

import java.util.List;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.TreeVisitingPrinter;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

public class PowerMockitoMockStaticToMockito extends Recipe {

    public static final String POWER_MOCK_TEST_CASE_CONFIG = "org.powermockito.configuration.PowerMockTestCaseConfig";

    @Override
    public String getDisplayName() {
        return "Replace mockStatic Method Call";
    }

    @Override
    public String getDescription() {
        return "Replaces PowerMockito.mockStatic(clazz) by Mockito.mockStatic(clazz).";
    }
    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PowerMockitoMockStaticToMockitoVisitor();
    }

    private static class PowerMockitoMockStaticToMockitoVisitor extends JavaVisitor<ExecutionContext> {

        public static final String PREPARE_FOR_TEST = "org.powermock.core.classloader.annotations.PrepareForTest";
        private final JavaTemplate jt = JavaTemplate.builder(this::getCursor,
                "private MockedStatic<#{}> mocked#{} = #{};").build();

        @Override
        public J visitAnnotation(J.Annotation annotation, ExecutionContext executionContext) {
            // Remove the @PrepareForTest annotation
            if (annotation.getType() != null && TypeUtils.isOfClassType(annotation.getType(), PREPARE_FOR_TEST)) {
                maybeRemoveImport(PREPARE_FOR_TEST);
                // Pass the annotation to the class declaration, in order to add their arguments as fields
                getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, "PrepareForTestAnnotation", annotation);
                return null;
            }
            return super.visitAnnotation(annotation, executionContext);
        }

        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
            System.out.println("------visitCompilationUnit LST Tree Start------");
            System.out.println(TreeVisitingPrinter.printTree(cu));
            System.out.println("------visitCompilationUnit LST Tree End------");
            return (J.CompilationUnit) super.visitCompilationUnit(cu, executionContext);
        }
        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
            J.ClassDeclaration cd = (J.ClassDeclaration) super.visitClassDeclaration(classDecl, executionContext);

            // Remove the extension of class PowerMockTestCaseConfig
            if (cd.getExtends() != null && cd.getExtends().getType() != null) {
                JavaType.FullyQualified fullQualifiedExtension = TypeUtils.asFullyQualified(cd.getExtends().getType());
                if (fullQualifiedExtension != null && POWER_MOCK_TEST_CASE_CONFIG.equals(fullQualifiedExtension.getFullyQualifiedName())) {
                    cd = cd.withExtends(null);
                }
            }
            maybeRemoveImport(POWER_MOCK_TEST_CASE_CONFIG);

            // Add the classes of the arguments in the annotation @PrepareForTest as fields
            // e.g. @PrepareForTest(Calendar.class)
            // becomes
            // private MockStatic mockedCalendar = mockStatic(Calendar.class)
            J.Annotation prepareForTest = getCursor().pollMessage("PrepareForTestAnnotation");
            if (prepareForTest != null) {
                List<Statement> statements = cd.getBody().getStatements();
                JavaCoordinates beforeFirstStatement = statements.get(0).getCoordinates().before();
                prepareForTest.getArguments().stream().map(a -> ((J.NewArray)a).getInitializer()).forEach(i -> i.forEach(argument ->{
                    String simpleClassName = ((J.Identifier)((J.FieldAccess)argument).getTarget()).getSimpleName();
                    statements.add(0, statements.get(0).withTemplate(jt,
                            beforeFirstStatement, simpleClassName, simpleClassName, simpleClassName)
                   );
                }));
                cd.getBody().withStatements(statements);
            }
            return cd;
        }

        @Override
        public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
            J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, executionContext);
            // If the method invocation is org.mockito.Mockito.mockStatic(clazz) ...
            if (mi.getType() != null && TypeUtils.isOfClassType(mi.getType(),"org.mockito.MockedStatic")) {
                return null;
            }
            return mi;
        }
    }
}
