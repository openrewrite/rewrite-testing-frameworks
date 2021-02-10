/*
 * Copyright 2020 the original author or authors.
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
import org.openrewrite.java.tree.J;

import java.util.Optional;

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
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MockUtilsToStaticVisitor();
    }

    public static class MockUtilsToStaticVisitor extends JavaVisitor<ExecutionContext> {

        private final MethodMatcher methodMatcher = new MethodMatcher("org.mockito.internal.util.MockUtil MockUtil()");
        private final ChangeMethodTargetToStatic changeMethodTargetToStatic = new ChangeMethodTargetToStatic(
                "org.mockito.internal.util.MockUtil *(..)",
                "org.mockito.internal.util.MockUtil"
        );

        public MockUtilsToStaticVisitor() {
            setCursoringOn();
        }

        @Override
        public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            doAfterVisit(changeMethodTargetToStatic);
            return super.visitCompilationUnit(cu, ctx);
        }

        @Override
        public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
            if (methodMatcher.matches(newClass)) {
                // Check to see if the new MockUtil() is being assigned to a variable or field, like
                // MockUtil util = new MockUtil();
                // If it is, then we'll get rid of it

                // TODO this is probably not right, now that cursors contain padding
                Optional.ofNullable(getCursor().getParent())
                        .filter(it -> it.getValue() instanceof J.VariableDeclarations.NamedVariable)
                        .map(Cursor::getParent)
                        .map(Cursor::getValue)
                        .filter(it -> it instanceof J.VariableDeclarations.VariableDeclarations)
                        .ifPresent(namedVar -> doAfterVisit(new DeleteStatement<>((J.VariableDeclarations) namedVar)));
            }
            return super.visitNewClass(newClass, ctx);
        }
    }
}
