package org.openrewrite.java.testing.mockito;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
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

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.powermock.core.classloader.annotations.PrepareForTest");
    }


    private static class PowerMockitoMockStaticToMockitoVisitor extends JavaVisitor<ExecutionContext> {
        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
            J.ClassDeclaration cd = (J.ClassDeclaration) super.visitClassDeclaration(classDecl, executionContext);
            if (cd.getExtends() != null && cd.getExtends().getType() != null) {
                JavaType.FullyQualified fullQualifiedExtension = TypeUtils.asFullyQualified(cd.getExtends().getType());
                if (fullQualifiedExtension != null && POWER_MOCK_TEST_CASE_CONFIG.equals(fullQualifiedExtension.getFullyQualifiedName())) {
                    cd = cd.withExtends(null);
                }
            }
            maybeRemoveImport(POWER_MOCK_TEST_CASE_CONFIG);
            return cd;
        }
    }
}
