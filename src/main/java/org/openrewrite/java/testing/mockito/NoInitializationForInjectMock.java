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

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.J;

import java.util.Iterator;

public class NoInitializationForInjectMock extends Recipe {

    private static final AnnotationMatcher INJECT_MOCKS = new AnnotationMatcher("@org.mockito.InjectMocks");

    @Override
    public String getDisplayName() {
        return "Remove initialization when using @InjectMocks";
    }

    @Override
    public String getDescription() {
        return "Removes unnecessary initialization for fields annotated with @InjectMocks in Mockito tests.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.mockito.*", false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations variableDeclarations, ExecutionContext ctx) {
                J.VariableDeclarations vd = super.visitVariableDeclarations(variableDeclarations, ctx);

                if (isField(getCursor()) && new AnnotationService().matches(getCursor(), INJECT_MOCKS)) {
                    return vd.withVariables(ListUtils.map(vd.getVariables(), it -> it.withInitializer(null)));
                }

                return vd;
            }

            // copied from org.openrewrite.java.search.FindFieldsOfType.isField(Cursor), should probably become part of the API
            private boolean isField(Cursor cursor) {
                Iterator<Object> path = cursor.getPath();
                while (path.hasNext()) {
                    Object o = path.next();
                    if (o instanceof J.MethodDeclaration) {
                        return false;
                    }
                    if (o instanceof J.ClassDeclaration) {
                        return true;
                    }
                }
                return true;
            }
        });
    }
}
