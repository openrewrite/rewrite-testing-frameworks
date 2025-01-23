package org.openrewrite.java.testing.search;

import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.testing.table.FindUnitTestTable;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.java.tree.JavaType;

import java.util.*;
import java.util.stream.Collectors;

public class FindUnitTests extends ScanningRecipe<FindUnitTests.Accumulator> {

    @Override
    public String getDisplayName() {
        return "Find LST provenance";
    }

    @Override
    public String getDescription() {
        return "Produces a data table showing what versions of OpenRewrite/Moderne tooling was used to produce a given LST.";
    }

    transient FindUnitTestTable unitTestTable = new FindUnitTestTable(this);

    public static class Accumulator {
        List<UnitTest> unitTestAndTheirMethods;
    }


    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        Accumulator acc = new Accumulator();
        acc.unitTestAndTheirMethods = new ArrayList<UnitTest>();
        return acc;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return Preconditions.check(
                new HasTestAnnotationVisitor(),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        // get the method declaration it's in
                        J.MethodDeclaration methodDeclaration = getCursor().firstEnclosing(J.MethodDeclaration.class);
                        if (methodDeclaration != null
                                && methodDeclaration.getLeadingAnnotations().stream()
                                .filter(o -> o.getAnnotationType() instanceof J.Identifier)
                                .anyMatch(o -> "Test".equals(o.getSimpleName())))  {
                            UnitTest unitTest = new UnitTest();
                            unitTest.ctx = ctx;
                            unitTest.unitTestName = methodDeclaration.getSimpleName();
                            unitTest.unitTest = methodDeclaration.printTrimmed(getCursor());
                            acc.unitTestAndTheirMethods.add(unitTest);
                        }
                        return super.visitMethodInvocation(method, ctx);
                    }
                });
    }


}

class HasTestAnnotationVisitor extends JavaVisitor<ExecutionContext> {

    @Override
    public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
        if (hasTestAnnotation(method)) {
            return super.visitMethodDeclaration(method, ctx);
        }
        return null;
    }

    public boolean hasTestAnnotation(J.MethodDeclaration method) {
        return method.getLeadingAnnotations().stream()
                .filter(o -> o.getAnnotationType() instanceof J.Identifier)
                .anyMatch(o -> "Test".equals(o.getSimpleName()));
    }
}

class UnitTest{
    ExecutionContext ctx;
    String unitTestName;
    String unitTest;
    List<J.MethodDeclaration> methods = new ArrayList<>();
}
