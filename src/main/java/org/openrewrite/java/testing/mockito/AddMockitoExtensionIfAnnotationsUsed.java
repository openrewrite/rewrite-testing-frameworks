/*
 * Copyright 2025 the original author or authors.
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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.IsLikelyTest;
import org.openrewrite.java.tree.J;

import static org.openrewrite.Preconditions.*;

public class AddMockitoExtensionIfAnnotationsUsed extends Recipe {
    @Override
    public String getDisplayName() {
        return "Adds Mockito extensions to Mockito tests";
    }

    @Override
    public String getDescription() {
        return "Adds `@ExtendWith(MockitoExtension.class)` to tests using `@Mock` or `@Captor`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return check(and(
                new IsLikelyTest().getVisitor(),
                not(new FindAnnotations("org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)", false).getVisitor()),
                or(new FindAnnotations("org.mockito.Captor", false).getVisitor(),
                        new FindAnnotations("org.mockito.Mock", false).getVisitor())), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {

                maybeAddImport("org.mockito.junit.jupiter.MockitoExtension");
                maybeAddImport("org.junit.jupiter.api.extension.ExtendWith");

                J.Annotation extendsWith = ((J.ClassDeclaration) JavaTemplate.builder("@ExtendWith(MockitoExtension.class)")
                        .imports("org.mockito.junit.jupiter.MockitoExtension")
                        .imports("org.junit.jupiter.api.extension.ExtendWith")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api", "mockito-junit-jupiter"))
                        .build()
                        .apply(getCursor(), classDecl.getCoordinates().replaceAnnotations()))
                        .getLeadingAnnotations().get(0);

                return autoFormat(classDecl.withLeadingAnnotations(ListUtils.concat(classDecl.getLeadingAnnotations(), extendsWith)), ctx);
            }
        });
    }
}
