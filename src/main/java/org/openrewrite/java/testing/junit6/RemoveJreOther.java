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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

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
    final String displayName = "Remove deprecated `JRE.OTHER` from `@EnabledOnJre`/`@DisabledOnJre` arrays";

    @Getter
    final String description = "JUnit 6.1 deprecated `JRE.OTHER` in favor of `int`/`int[]` annotation attributes. " +
            "This recipe removes `JRE.OTHER` entries from `@EnabledOnJre` and `@DisabledOnJre` array " +
            "values when other JRE constants remain. Lone `JRE.OTHER` usages are left untouched " +
            "because they have no mechanical replacement; review them manually.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(JRE_TYPE, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                if (JRE_ANNOTATIONS.stream().noneMatch(m -> m.matches(a)) || a.getArguments() == null) {
                    return a;
                }
                return a.withArguments(ListUtils.map(a.getArguments(), arg -> {
                    if (arg instanceof J.Assignment) {
                        J.Assignment assignment = (J.Assignment) arg;
                        if (assignment.getVariable() instanceof J.Identifier &&
                                "value".equals(((J.Identifier) assignment.getVariable()).getSimpleName())) {
                            Expression stripped = stripOtherFromArray(assignment.getAssignment());
                            return stripped == assignment.getAssignment() ? assignment : assignment.withAssignment(stripped);
                        }
                        return assignment;
                    }
                    return stripOtherFromArray(arg);
                }));
            }

            private Expression stripOtherFromArray(Expression expr) {
                if (!(expr instanceof J.NewArray)) {
                    return expr;
                }
                J.NewArray array = (J.NewArray) expr;
                List<Expression> filtered = ListUtils.map(array.getInitializer(), el -> isJreOther(el) ? null : el);
                return filtered == array.getInitializer() ? expr : array.withInitializer(filtered);
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
