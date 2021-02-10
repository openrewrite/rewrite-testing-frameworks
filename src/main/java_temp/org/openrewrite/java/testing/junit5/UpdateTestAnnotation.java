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
package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singletonList;

public class UpdateTestAnnotation extends Recipe {

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new UpdateTestAnnotationVisitor();
    }

    private static class UpdateTestAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {

        public UpdateTestAnnotationVisitor() {
            setCursoringOn();
        }

        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            doAfterVisit(new ChangeType("org.junit.Test", "org.junit.jupiter.api.Test"));
            return super.visitCompilationUnit(cu, ctx);
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

            boolean changed = false;
            List<J.Annotation> annotations = new ArrayList<>(m.getAnnotations());
            for (int i = 0, annotationsSize = annotations.size(); i < annotationsSize; i++) {

                J.Annotation a = annotations.get(i);
                if (TypeUtils.isOfClassType(a.getType(), "org.junit.Test")) {
                    //If we found the annotation, we change the visibility of the method to package. Because the
                    m = m.withModifiers(ListUtils.map(m.getModifiers(), mod -> {
                                J.Modifier.Type modifierType = mod.getType();
                                return (modifierType == J.Modifier.Type.Protected || modifierType == J.Modifier.Type.Private || modifierType == J.Modifier.Type.Public) ? null : mod;
                            }));
                    m = maybeAutoFormat(method, m, ctx);

                    annotations.set(i, a.withArguments(null));
                    if(a.getArguments() == null) {
                        continue;
                    }
                    List<Expression> args = a.getArguments();
                    for (Expression arg : args) {
                        if (arg instanceof J.Assignment) {
                            J.Assignment assign = (J.Assignment) arg;
                            String assignParamName = ((J.Identifier) assign.getVariable()).getSimpleName();
                            Expression e = assign.getAssignment();
                            if(m.getBody() == null) {
                                continue;
                            }
                            if(assignParamName.equals("expected")) {
                                List<Statement> statements = m.getBody().getStatements();
                                J.MethodInvocation assertThrows = AssertionsBuilder.assertThrows(e, statements);

                                AddImport<ExecutionContext> addAssertThrows = new AddImport<>("org.junit.jupiter.api.Assertions", "assertThrows", false);
                                doAfterVisit(addAssertThrows);
                                andThen(new AutoFormat(assertThrows));

                                m = m.withBody(m.getBody().withStatements(singletonList(assertThrows)));
                            } else if (assignParamName.equals("timeout")) {
                                AddAnnotation.Scoped aa = new AddAnnotation.Scoped(m, "org.junit.jupiter.api.Timeout", e.withFormatting(EMPTY));
                                andThen(aa);
                                andThen(new AutoFormat(m));
                            }
                        }
                        changed = true;
                    }
                }
            }

            if (changed) {
                m = m.withAnnotations(annotations);
            }
            return m;
        }
    }
}
