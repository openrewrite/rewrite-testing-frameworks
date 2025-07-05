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
package org.openrewrite.java.testing.mockito;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.trait.Annotated;
import org.openrewrite.java.tree.J;

public class NoInitializationForInjectMock extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove `@InjectMocks` when field is initialized";
    }

    @Override
    public String getDescription() {
        return "Remove `@InjectMocks` annotation from fields that are initialized, " +
                "as Mockito would take the initializer value instead of constructing a mock.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public @Nullable J preVisit(@NonNull J tree, ExecutionContext ctx) {
                J j = maybeRemoveAnnotation(tree, ctx);
                if (j != tree) {
                    maybeRemoveImport("org.mockito.InjectMocks");
                }
                return j;
            }

            private @Nullable J maybeRemoveAnnotation(@Nullable Tree tree, ExecutionContext ctx) {
                return (J) new Annotated.Matcher("@org.mockito.InjectMocks").asVisitor(annotated -> {
                    J.VariableDeclarations vd = annotated.getCursor().firstEnclosing(J.VariableDeclarations.class);
                    if (vd != null && vd.getVariables().get(0).getInitializer() != null) {
                        return null; // Remove the annotation if the variable is initialized
                    }
                    return annotated.getTree();
                }).visit(tree, ctx);
            }
        };
    }
}
