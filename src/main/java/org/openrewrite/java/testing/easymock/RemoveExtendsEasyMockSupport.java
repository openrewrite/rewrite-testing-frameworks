/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.testing.easymock;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
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

    @Getter
    final String displayName = "Remove `extends EasyMockSupport` from test classes";

    @Getter
    final String description = "Remove the `extends` clause from test classes that extend `org.easymock.EasyMockSupport`, or the `EasyMockSupport` of the EasyMock class extension. A test base class that extends it loses its `extends` clause, but its subclasses keep extending it. Calls to the inherited EasyMock methods are left to the other EasyMock to Mockito recipes.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(EASYMOCK, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                if (cd.getExtends() != null && isEasyMockSupport(cd.getExtends().getType())) {
                    maybeRemoveImport(EASYMOCK);
                    maybeRemoveImport(TypeUtils.asFullyQualified(cd.getExtends().getType()));
                    cd = cd.withExtends(null);
                }
                return cd;
            }
        });
    }

    /**
     * `EasyMockSupport` itself or the `classextension` one that extends it, but not a test base class of the project
     * that extends either of them, so that its subclasses keep what they inherit from it.
     */
    private static boolean isEasyMockSupport(@Nullable JavaType type) {
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        return fq != null && fq.getPackageName().startsWith("org.easymock") && TypeUtils.isAssignableTo(EASYMOCK, fq);
    }
}
