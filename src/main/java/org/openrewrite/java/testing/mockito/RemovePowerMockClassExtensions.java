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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

public class RemovePowerMockClassExtensions extends Recipe {

    private static final String POWER_MOCK_CONFIG = "org.powermock.configuration.PowerMockConfiguration";
    private static final String POWER_MOCK_TEST_CASE = "org.powermock.modules.testng.PowerMockTestCase";

    @Getter
    final String displayName = "Remove PowerMock class extensions";

    @Getter
    final String description = "Removes `extends PowerMockConfiguration` and `extends PowerMockTestCase` " +
            "from test classes, as these are PowerMock-specific base classes not needed with Mockito.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>(POWER_MOCK_CONFIG, false),
                        new UsesType<>(POWER_MOCK_TEST_CASE, false)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                        cd = maybeRemoveExtension(cd, POWER_MOCK_CONFIG);
                        return maybeRemoveExtension(cd, POWER_MOCK_TEST_CASE);
                    }

                    private J.ClassDeclaration maybeRemoveExtension(J.ClassDeclaration classDecl, String extensionFQN) {
                        TypeTree extension = classDecl.getExtends();
                        if (extension != null && TypeUtils.isAssignableTo(extensionFQN, extension.getType())) {
                            classDecl = classDecl.withExtends(null);
                            maybeRemoveImport(extensionFQN);
                        }
                        return classDecl;
                    }
                }
        );
    }
}
