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
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.IsLikelyNotTest;
import org.openrewrite.java.search.IsLikelyTest;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class FindUnitTests extends ScanningRecipe<FindUnitTests.Accumulator> {

    private transient @Nullable Accumulator acc = new Accumulator();
    transient FindUnitTestTable unitTestTable = new FindUnitTestTable(this);

    public FindUnitTests() {
    }

    public FindUnitTests(Accumulator acc) {
        this.acc = acc;
    }

    @Override
    public String getDisplayName() {
        return "Find unit tests";
    }

    @Override
    public String getDescription() {
        return "Produces a data table showing how methods are used in unit tests.";
    }

    public static class Accumulator {
        private final Map<String, AccumulatorValue> unitTestsByKey = new HashMap<>();

        public void addMethodInvocation(String clazz, String testName, String testBody, J.MethodInvocation invocation) {
            String key = clazz + "#" + testName;
            AccumulatorValue value = unitTestsByKey.get(key);
            if (value == null) {
                UnitTest unitTest = new UnitTest(clazz, testName, testBody);
                value = new AccumulatorValue(unitTest, new HashSet<>());
                unitTestsByKey.put(key, value);
            }
            value.getMethodInvocations().add(invocation);
        }

        public Map<String, AccumulatorValue> getUnitTestsByKey() {
            return unitTestsByKey;
        }
    }


    @Value
    public static class AccumulatorValue {
        UnitTest unitTest;
        Set<J.MethodInvocation> methodInvocations;
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        if (acc != null) return acc;
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        JavaVisitor<ExecutionContext> scanningVisitor = new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                // Identify the method declaration that encloses this invocation
                J.MethodDeclaration methodDeclaration = getCursor().firstEnclosing(J.MethodDeclaration.class);
                if (methodDeclaration != null &&
                        methodDeclaration.getLeadingAnnotations().stream()
                                .filter(o -> o.getAnnotationType() instanceof J.Identifier)
                                .anyMatch(o -> "Test".equals(o.getSimpleName()))) {
                    String clazz = getCursor().firstEnclosingOrThrow(J.ClassDeclaration.class)
                            .getType().getFullyQualifiedName();

                    String testName = methodDeclaration.getSimpleName();

                    String testBody = methodDeclaration.printTrimmed(getCursor());

                    acc.addMethodInvocation(clazz, testName, testBody, method);
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
                // Iterate over each stored AccumulatorValue
                for (AccumulatorValue value : acc.getUnitTestsByKey().values()) {
                    UnitTest unitTest = value.getUnitTest();
                    for (J.MethodInvocation invocation : value.getMethodInvocations()) {
                        // If the invoked method name matches the current methodDeclaration's name,
                        // we assume we've found "usage" of that method inside the test
                        if (invocation.getSimpleName().equals(methodDeclaration.getSimpleName())) {
                            unitTestTable.insertRow(ctx, new FindUnitTestTable.Row(
                                    methodDeclaration.getName().toString(),
                                    methodDeclaration.getSimpleName(),
                                    invocation.printTrimmed(getCursor()),
                                    unitTest.getClazz(),
                                    unitTest.getUnitTestName()
                            ));
                        }
                    }
                }
                SearchResult.found(methodDeclaration);
                return super.visitMethodDeclaration(methodDeclaration, ctx);
            }
        };

        return Preconditions.check(new IsLikelyNotTest().getVisitor(), tableRowVisitor);
    }
}
