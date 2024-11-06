/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.testing.easymock;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

public class RemoveExtendsEasyMockSupport extends Recipe {

    private static final String EASYMOCK = "org.easymock.EasyMockSupport";

    @Override
    public String getDisplayName() {
        return "Migrate Test classes that extend `org.easymock.EasyMockSupport` to use Mockito";
    }

    @Override
    public String getDescription() {
        return "Modify test classes by removing extends EasyMockSupport and replacing EasyMock methods with Mockito equivalents.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(EASYMOCK, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                maybeRemoveImport(EASYMOCK);

                if (cd.getExtends() != null) {
                    JavaType.FullyQualified fqn = TypeUtils.asFullyQualified(cd.getExtends().getType());
                    if (fqn != null && fqn.isAssignableTo(EASYMOCK)) {
                        cd = cd.withExtends(null);
                    }
                }
                return cd;
            }
        });
    }
}
