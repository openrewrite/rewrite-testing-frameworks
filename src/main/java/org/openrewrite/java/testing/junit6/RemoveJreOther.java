/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.testing.junit6;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Arrays;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class RemoveJreOther extends Recipe {

    private static final String JRE_TYPE = "org.junit.jupiter.api.condition.JRE";

    private static final List<AnnotationMatcher> JRE_ANNOTATIONS = Arrays.asList(
            new AnnotationMatcher("@org.junit.jupiter.api.condition.EnabledOnJre"),
            new AnnotationMatcher("@org.junit.jupiter.api.condition.DisabledOnJre"));

    @Getter
    final String displayName = "Flag deprecated `JRE.OTHER` in `@EnabledOnJre`/`@DisabledOnJre`";

    @Getter
    final String description = "JUnit 6.1 deprecated `JRE.OTHER` in favor of `int`/`int[]` annotation attributes. " +
            "`JRE.OTHER` matches every JRE that has no constant, including future ones, so removing it " +
            "(or the annotation's last array element) changes which JREs the test runs on, and has no mechanical " +
            "replacement. This recipe makes no edits; it marks the annotations that use `JRE.OTHER` for manual review.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(JRE_TYPE, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                if (JRE_ANNOTATIONS.stream().noneMatch(m -> m.matches(a)) || a.getArguments() == null) {
                    return a;
                }
                for (Expression arg : a.getArguments()) {
                    Expression value = arg;
                    if (arg instanceof J.Assignment) {
                        J.Assignment assignment = (J.Assignment) arg;
                        if (!(assignment.getVariable() instanceof J.Identifier) ||
                                !"value".equals(((J.Identifier) assignment.getVariable()).getSimpleName())) {
                            continue;
                        }
                        value = assignment.getAssignment();
                    }
                    if (containsJreOther(value)) {
                        return SearchResult.found(a, "JRE.OTHER is deprecated and has no mechanical replacement; " +
                                "removing it would change which JREs match");
                    }
                }
                return a;
            }

            private boolean containsJreOther(Expression expr) {
                if (expr instanceof J.NewArray) {
                    List<Expression> initializer = ((J.NewArray) expr).getInitializer();
                    return initializer != null && initializer.stream().anyMatch(this::isJreOther);
                }
                return isJreOther(expr);
            }

            private boolean isJreOther(Expression expr) {
                if (expr instanceof J.FieldAccess) {
                    J.FieldAccess fa = (J.FieldAccess) expr;
                    return "OTHER".equals(fa.getSimpleName()) &&
                            TypeUtils.isOfClassType(fa.getTarget().getType(), JRE_TYPE);
                }
                if (expr instanceof J.Identifier) {
                    J.Identifier id = (J.Identifier) expr;
                    return "OTHER".equals(id.getSimpleName()) &&
                            TypeUtils.isOfClassType(id.getType(), JRE_TYPE);
                }
                return false;
            }
        });
    }
}
