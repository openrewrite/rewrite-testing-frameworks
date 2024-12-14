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
package org.openrewrite.java.testing.junit5;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

@Value
@EqualsAndHashCode(callSuper = false)
public class EnclosedToNested extends Recipe {
    private static final String ENCLOSED = "org.junit.experimental.runners.Enclosed";
    private static final String RUN_WITH = "org.junit.runner.RunWith";
    private static final String RUN_WITH_ENCLOSED = String.format("@%s(%s.class)", RUN_WITH, ENCLOSED);

    @Override
    public String getDisplayName() {
        return "JUnit 4 `@RunWith(Enclosed.class)` to JUnit Jupiter `@Nested`";
    }

    @Override
    public String getDescription() {
        return "Removes the `Enclosed` specification from a class, with `Nested` added to its inner classes by `AddMissingNested`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(ENCLOSED, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                maybeRemoveImport(ENCLOSED);
                maybeRemoveImport(RUN_WITH);
                return (J.ClassDeclaration) new RemoveAnnotationVisitor(new AnnotationMatcher(RUN_WITH_ENCLOSED)).visitNonNull(cd, ctx);
            }
        });
    }
}
