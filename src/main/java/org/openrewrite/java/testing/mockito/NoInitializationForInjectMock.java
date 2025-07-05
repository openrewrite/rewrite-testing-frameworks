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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.Set;

public class NoInitializationForInjectMock extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove `@InjectMocks` annotation or initializer";
    }

    @Override
    public String getDescription() {
        return "Remove either the `@InjectMocks` annotation from fields, or the initializer, based on the initializer.\n" +
                " * In the case of a no-args constructor, remove the initializer and retain the annotation.\n" +
                " * In the case of any other initializer, remove the annotation and retain the initializer.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>("org.mockito.InjectMocks", false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.@Nullable VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        J.VariableDeclarations vds = super.visitVariableDeclarations(multiVariable, ctx);
                        Set<J.Annotation> annotations = FindAnnotations.find(vds, "@org.mockito.InjectMocks");
                        if (!annotations.isEmpty()) {
                            J.VariableDeclarations.NamedVariable namedVariable = vds.getVariables().get(0);
                            Expression initializer = namedVariable.getInitializer();
                            if (initializer != null) {
                                if (initializer instanceof J.NewClass &&
                                        (((J.NewClass) initializer).getArguments().isEmpty() ||
                                                (((J.NewClass) initializer).getArguments().get(0) instanceof J.Empty))) {
                                    return autoFormat(
                                            vds
                                                    .withModifiers(ListUtils.map(vds.getModifiers(), m -> m.getType() == J.Modifier.Type.Final ? null : m))
                                                    .withVariables(ListUtils.map(vds.getVariables(), v -> v.withInitializer(null))),
                                            ctx);
                                }
                                maybeRemoveImport("org.mockito.InjectMocks");
                                return (J.VariableDeclarations) new RemoveAnnotationVisitor(new AnnotationMatcher("@org.mockito.InjectMocks"))
                                        .visit(vds, ctx);
                            }
                        }
                        return vds;
                    }
                });
    }
}
