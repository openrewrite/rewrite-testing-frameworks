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
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.format.AutoFormatVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * This refactor visitor will replace JUnit 4's "Before", "BeforeClass", "After", and "AfterClass" annotations with their
 * JUnit 5 equivalents. Additionally, this visitor will reduce the visibility of methods marked with those annotations
 * to "package" to comply with JUnit 5 best practices.
 *
 * <PRE>
 * org.junit.Before --> org.junit.jupiter.api.BeforeEach
 * org.junit.After --> org.junit.jupiter.api.AfterEach
 * org.junit.BeforeClass --> org.junit.jupiter.api.BeforeAll
 * org.junit.AfterClass --> org.junit.jupiter.api.AfterAll
 * </PRE>
 */
public class UpdateBeforeAfterAnnotations extends Recipe {

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new UpdateBeforeAfterAnnotationsVisitor();
    }

    public static class UpdateBeforeAfterAnnotationsVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            //This visitor handles changing the method visibility for any method annotated with one of the four before/after
            //annotations. It registers visitors that will sweep behind it making the type changes.
            doAfterVisit(new ChangeType("org.junit.Before", "org.junit.jupiter.api.BeforeEach"));
            doAfterVisit(new ChangeType("org.junit.After", "org.junit.jupiter.api.AfterEach"));
            doAfterVisit(new ChangeType("org.junit.BeforeClass", "org.junit.jupiter.api.BeforeAll"));
            doAfterVisit(new ChangeType("org.junit.AfterClass", "org.junit.jupiter.api.AfterAll"));

            return super.visitCompilationUnit(cu, ctx);
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
            
            List<J.Annotation> annotations = new ArrayList<>(m.getAnnotations());
            for (J.Annotation a : annotations) {

                if (TypeUtils.isOfClassType(a.getType(), "org.junit.Before") ||
                        TypeUtils.isOfClassType(a.getType(), "org.junit.After") ||
                        TypeUtils.isOfClassType(a.getType(), "org.junit.BeforeClass") ||
                        TypeUtils.isOfClassType(a.getType(), "org.junit.AfterClass")) {

                    //If we found the annotation, we change the visibility of the method to package. Also need to format
                    //the method declaration because the previous visibility likely had formatting that is removed.
                    m = m.withModifiers(
                            ListUtils.map(m.getModifiers(),
                                    modifier -> (modifier.getType() == J.Modifier.Type.Private ||
                                            modifier.getType() == J.Modifier.Type.Public ||
                                            modifier.getType() == J.Modifier.Type.Protected) ? null : modifier)
                    );
                    m = (J.MethodDeclaration) new AutoFormatVisitor<>().visit(m, ctx, getCursor().dropParentUntil(it -> it instanceof J));
                    break;
                }
            }
            return m;
        }
    }
}
