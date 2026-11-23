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
package org.openrewrite.java.testing.hamcrest;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.util.List;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;

public class QualifyUnmigratedHamcrestAssertions extends Recipe {
    @Getter
    final String displayName = "Qualify the Hamcrest assertions the migration couldn't type";

    @Getter
    final String description = "An `assertThat(actual, matcher)` without a type, such as one on the parameter of a " +
            "lambda whose type wasn't resolved, is left as it is, but the import of Hamcrest's `assertThat` goes " +
            "with the assertions that were migrated. Call it as `MatcherAssert.assertThat(..)`, so that it doesn't " +
            "resolve to AssertJ's.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.hamcrest..*", false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                // only where `assertThat` is AssertJ's, imported statically in place of Hamcrest's
                for (J.Import anImport : cu.getImports()) {
                    if (anImport.isStatic() && "org.assertj.core.api.Assertions".equals(anImport.getTypeName()) &&
                        ("assertThat".equals(anImport.getQualid().getSimpleName()) ||
                         "*".equals(anImport.getQualid().getSimpleName()))) {
                        return super.visitCompilationUnit(cu, ctx);
                    }
                }
                return cu;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                List<Expression> args = mi.getArguments();
                if (!"assertThat".equals(mi.getSimpleName()) || mi.getSelect() != null ||
                    args.size() < 2 || args.size() > 3 || isAssertJ(mi.getMethodType()) ||
                    !isHamcrestMatcher(args.get(args.size() - 1))) {
                    return mi;
                }
                maybeAddImport("org.hamcrest.MatcherAssert");
                J.Identifier matcherAssert = new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(),
                        "MatcherAssert", JavaType.ShallowClass.build("org.hamcrest.MatcherAssert"), null);
                return mi.withSelect(matcherAssert);
            }

            private boolean isAssertJ(JavaType.@Nullable Method type) {
                return type != null && type.getDeclaringType().getFullyQualifiedName().startsWith("org.assertj.");
            }

            private boolean isHamcrestMatcher(Expression expression) {
                if (!(expression instanceof J.MethodInvocation)) {
                    return false;
                }
                JavaType.Method type = ((J.MethodInvocation) expression).getMethodType();
                return type != null && type.getDeclaringType().getFullyQualifiedName().startsWith("org.hamcrest.");
            }
        });
    }
}
