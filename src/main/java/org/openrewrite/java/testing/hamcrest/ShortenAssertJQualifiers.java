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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.ShortenFullyQualifiedTypeReferences;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

public class ShortenAssertJQualifiers extends Recipe {
    @Getter
    final String displayName = "Shorten the fully qualified AssertJ entry points the migration wrote";

    @Getter
    final String description = "Where a Hamcrest `assertThat` is still imported statically, the migration calls " +
            "AssertJ's as `org.assertj.core.api.Assertions.assertThat(..)`, which a variable named `org` hides. " +
            "Shorten it to `Assertions.assertThat(..)` with an import, unless another `Assertions` is in scope.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new UsesType<>("org.assertj.core.api.Assertions", false),
                new UsesType<>("org.assertj.core.api.Assumptions", false)), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (mi.getSelect() instanceof J.FieldAccess &&
                    (TypeUtils.isOfClassType(mi.getSelect().getType(), "org.assertj.core.api.Assertions") ||
                     TypeUtils.isOfClassType(mi.getSelect().getType(), "org.assertj.core.api.Assumptions"))) {
                    doAfterVisit(ShortenFullyQualifiedTypeReferences.modifyOnly(mi.getSelect()));
                }
                return mi;
            }
        });
    }
}
