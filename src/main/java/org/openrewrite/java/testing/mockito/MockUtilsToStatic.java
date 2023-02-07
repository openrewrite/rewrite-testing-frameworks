/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.testing.mockito;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeMethodTargetToStatic;
import org.openrewrite.java.DeleteStatement;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.time.Duration;

/**
 * In Mockito 1 you use a code snippet like:
 * <p>
 * new MockUtil().isMock(foo);
 * <p>
 * In Mockito 2+ this class now has a private constructor and only exposes static methods:
 * <p>
 * MockUtil.isMock(foo);
 * <p>
 * This recipe makes a best-effort attempt to remove MockUtil instances, but if someone did something unexpected like
 * subclassing MockUtils that will not be handled and will have to be hand-remediated.
 */
public class MockUtilsToStatic extends Recipe {

    @Override
    public String getDisplayName() {
        return "Use static form of Mockito `MockUtil`";
    }

    @Override
    public String getDescription() {
        return "Best-effort attempt to remove Mockito `MockUtil` instances.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MockUtilsToStaticVisitor();
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.mockito.internal.util.MockUtil");
    }

    public static class MockUtilsToStaticVisitor extends JavaVisitor<ExecutionContext> {
        private static final MethodMatcher METHOD_MATCHER = new MethodMatcher("org.mockito.internal.util.MockUtil <constructor>()");
        private final ChangeMethodTargetToStatic changeMethodTargetToStatic = new ChangeMethodTargetToStatic(
                "org.mockito.internal.util.MockUtil *(..)",
                "org.mockito.internal.util.MockUtil",
                null, null
        );

        @Override
        public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            doAfterVisit(changeMethodTargetToStatic);
            return super.visitCompilationUnit(cu, ctx);
        }

        @Override
        public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
            if (METHOD_MATCHER.matches(newClass)) {
                // Check to see if the new MockUtil() is being assigned to a variable or field, like
                // MockUtil util = new MockUtil();
                // If it is, then we'll get rid of it

                Cursor parent = getCursor().dropParentUntil(J.class::isInstance);
                if (parent.getValue() instanceof J.VariableDeclarations.NamedVariable) {
                    Object namedVar = parent.dropParentUntil(J.class::isInstance).getValue();
                    if (namedVar instanceof J.VariableDeclarations) {
                        doAfterVisit(new DeleteStatement<>((J.VariableDeclarations) namedVar));
                    }
                }
            }
            return super.visitNewClass(newClass, ctx);
        }
    }

  @Override
  public Duration getEstimatedEffortPerOccurrence() {
    return Duration.ofMinutes(5);
  }
}
