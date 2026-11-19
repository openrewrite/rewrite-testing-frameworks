/*
 * Copyright 2026 the original author or authors.
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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

public class RemovePowerMockObjectFactory extends Recipe {

    private static final String POWER_MOCK_OBJECT_FACTORY = "org.powermock.modules.testng.PowerMockObjectFactory";
    private static final AnnotationMatcher OBJECT_FACTORY = new AnnotationMatcher("@org.testng.annotations.ObjectFactory");

    @Getter
    final String displayName = "Remove the TestNG `PowerMockObjectFactory`";

    @Getter
    final String description = "Removes TestNG `@ObjectFactory` methods that only return a `PowerMockObjectFactory`. " +
            "That object factory loads the test class through PowerMock's class loader, which Mockito has no use for, " +
            "and without it TestNG instantiates the test class itself.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(POWER_MOCK_OBJECT_FACTORY, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);
                if (!(getCursor().getParentTreeCursor().getValue() instanceof J.ClassDeclaration)) {
                    return b;
                }
                return b.withStatements(ListUtils.map(b.getStatements(), statement -> {
                    if (statement instanceof J.MethodDeclaration && returnsOnlyPowerMockObjectFactory((J.MethodDeclaration) statement)) {
                        maybeRemoveImport(POWER_MOCK_OBJECT_FACTORY);
                        maybeRemoveImport("org.testng.annotations.ObjectFactory");
                        maybeRemoveImport("org.testng.IObjectFactory");
                        maybeRemoveImport("org.testng.IObjectFactory2");
                        return null;
                    }
                    return statement;
                }));
            }

            private boolean returnsOnlyPowerMockObjectFactory(J.MethodDeclaration method) {
                if (method.getLeadingAnnotations().stream().noneMatch(OBJECT_FACTORY::matches) || method.getBody() == null ||
                    method.getBody().getStatements().size() != 1) {
                    return false;
                }
                Statement only = method.getBody().getStatements().get(0);
                if (!(only instanceof J.Return)) {
                    return false;
                }
                Expression returned = ((J.Return) only).getExpression();
                return returned instanceof J.NewClass &&
                       TypeUtils.isAssignableTo(POWER_MOCK_OBJECT_FACTORY, ((J.NewClass) returned).getType()) &&
                       ((J.NewClass) returned).getArguments().stream().allMatch(J.Empty.class::isInstance);
            }
        });
    }
}
