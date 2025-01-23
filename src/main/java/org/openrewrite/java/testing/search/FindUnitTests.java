package org.openrewrite.java.testing.search;

import lombok.Data;
import org.openrewrite.*;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.testing.table.FindUnitTestTable;
import org.openrewrite.java.tree.J;

import java.util.*;

public class FindUnitTests extends ScanningRecipe<FindUnitTests.Accumulator> {

    @Override
    public String getDisplayName() {
        return "Find Unit Tests";
    }

    @Override
    public String getDescription() {
        return "Produces a data table showing examples of how methods declared get used in unit tests.";
    }

    transient FindUnitTestTable unitTestTable = new FindUnitTestTable(this);

    public static class Accumulator {
        HashMap<UnitTest, List<J.MethodInvocation>> unitTestAndTheirMethods;
    }


    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        Accumulator acc = new Accumulator();
        acc.unitTestAndTheirMethods = new HashMap<UnitTest, List<J.MethodInvocation>>();
        return acc;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        // get the method declaration the method invocation is in
                        J.MethodDeclaration methodDeclaration = getCursor().firstEnclosing(J.MethodDeclaration.class);
                        if (methodDeclaration != null
                                && methodDeclaration.getLeadingAnnotations().stream()
                                .filter(o -> o.getAnnotationType() instanceof J.Identifier)
                                .anyMatch(o -> "Test".equals(o.getSimpleName())))  {
                            UnitTest unitTest = new UnitTest();
                            unitTest.clazz = getCursor().firstEnclosingOrThrow(J.ClassDeclaration.class).getType().getFullyQualifiedName();
                            unitTest.unitTestName = methodDeclaration.getSimpleName();
                            unitTest.unitTest = methodDeclaration.printTrimmed(getCursor());
                            if (acc.unitTestAndTheirMethods.containsKey(unitTest)) {
                                acc.unitTestAndTheirMethods.get(unitTest).add(method);
                            }else {
                                List<J.MethodInvocation> methodList = new ArrayList<>();
                                methodList.add(method);
                                acc.unitTestAndTheirMethods.put(unitTest, methodList);
                            }
                        }
                        return super.visitMethodInvocation(method, ctx);
                    }
                };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodDeclaration(J.MethodDeclaration methodDeclaration, ExecutionContext ctx) {
                for (UnitTest unitTest : acc.unitTestAndTheirMethods.keySet()) {
                    for (J.MethodInvocation method : acc.unitTestAndTheirMethods.get(unitTest)) {
                        if (method.getSimpleName().equals(methodDeclaration.getSimpleName())) {
                            unitTestTable.insertRow(ctx, new FindUnitTestTable.Row(
                                    methodDeclaration.getName().toString(),
                                    methodDeclaration.getSimpleName(),
                                    method.printTrimmed(getCursor()),
                                    unitTest.clazz,
                                    unitTest.unitTestName
                            ));
                        }
                    }
                    return super.visitMethodDeclaration(methodDeclaration, ctx);
                }
                return super.visitMethodDeclaration(methodDeclaration, ctx);
            }
        };
    }

}

@Data
class UnitTest{
    String clazz;
    String unitTestName;
    String unitTest;
}
