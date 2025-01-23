/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.testing.search;

import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.IsLikelyNotTest;
import org.openrewrite.java.search.IsLikelyTest;
import org.openrewrite.java.tree.J;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.singletonList;

public class FindUnitTests extends ScanningRecipe<FindUnitTests.Accumulator> {

    @Override
    public String getDisplayName() {
        return "Find unit tests";
    }

    @Override
    public String getDescription() {
        return "Produces a data table showing examples of how methods declared get used in unit tests.";
    }

    transient FindUnitTestTable unitTestTable = new FindUnitTestTable(this);

    public static class Accumulator {
        Map<UnitTest, Set<J.MethodInvocation>> unitTestAndTheirMethods = new HashMap<>();
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        JavaVisitor<ExecutionContext> scanningVisitor = new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                // get the method declaration the method invocation is in
                J.MethodDeclaration methodDeclaration = getCursor().firstEnclosing(J.MethodDeclaration.class);
                if (methodDeclaration != null
                        && methodDeclaration.getLeadingAnnotations().stream()
                        .filter(o -> o.getAnnotationType() instanceof J.Identifier)
                        .anyMatch(o -> "Test".equals(o.getSimpleName()))) {
                    UnitTest unitTest = new UnitTest(
                            getCursor().firstEnclosingOrThrow(J.ClassDeclaration.class).getType().getFullyQualifiedName(),
                            methodDeclaration.getSimpleName(),
                            methodDeclaration.printTrimmed(getCursor()));
                    acc.unitTestAndTheirMethods.merge(unitTest,
                            new HashSet<>(singletonList(method)),
                            (a, b) -> {
                                a.addAll(b);
                                return a;
                            });
                }
                return super.visitMethodInvocation(method, ctx);
            }
        };
        return Preconditions.check(new IsLikelyTest().getVisitor(), scanningVisitor);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        JavaVisitor<ExecutionContext> tableRowVisitor = new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodDeclaration(J.MethodDeclaration methodDeclaration, ExecutionContext ctx) {
                for (Map.Entry<UnitTest, Set<J.MethodInvocation>> entry : acc.unitTestAndTheirMethods.entrySet()) {
                    for (J.MethodInvocation method : entry.getValue()) {
                        if (method.getSimpleName().equals(methodDeclaration.getSimpleName())) {
                            unitTestTable.insertRow(ctx, new FindUnitTestTable.Row(
                                    methodDeclaration.getName().toString(),
                                    methodDeclaration.getSimpleName(),
                                    method.printTrimmed(getCursor()),
                                    entry.getKey().getClazz(),
                                    entry.getKey().getUnitTestName()
                            ));
                        }
                    }
                }
                return super.visitMethodDeclaration(methodDeclaration, ctx);
            }
        };
        return Preconditions.check(new IsLikelyNotTest().getVisitor(), tableRowVisitor);
    }

}

@Value
class UnitTest {
    String clazz;
    String unitTestName;
    String unitTest;
}
