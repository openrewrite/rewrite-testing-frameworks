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
package org.openrewrite.java.testing.cleanup;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.FindEmptyMethods;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.MethodDeclaration;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;

public class RemoveEmptyTests extends Recipe {

    @Getter
    final String displayName = "Remove empty tests without comments";

    @Getter
    final String description = "Removes empty methods with a `@Test` annotation if the body does not have comments.";

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(2);

    @Getter
    final Set<String> tags = singleton("RSPEC-S1186");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindEmptyMethods(false), new JavaVisitor<ExecutionContext>() {

            @Override
            public @Nullable J visitMethodDeclaration(MethodDeclaration method, ExecutionContext ctx) {
                if (hasTestAnnotation(method) && isEmptyMethod(method)) {
                    //noinspection ConstantConditions
                    return null;
                }
                return super.visitMethodDeclaration(method, ctx);
            }

            private boolean hasTestAnnotation(J.MethodDeclaration method) {
                return method.getLeadingAnnotations().stream()
                        .filter(o -> o.getAnnotationType() instanceof J.Identifier)
                        .anyMatch(o -> "Test".equals(o.getSimpleName()));
            }

            private boolean isEmptyMethod(J.MethodDeclaration method) {
                return !method.isConstructor() &&
                        (method.getBody() == null || method.getBody().getStatements().isEmpty() && method.getBody().getEnd().getComments().isEmpty());
            }
        });
    }
}
