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
package org.openrewrite.java.testing.wiremock;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.dependencies.search.ModuleHasDependency;
import org.openrewrite.java.marker.JavaProject;

import java.util.Set;

/// Base for the recipes whose rewrite is only behaviour preserving while the module is still on WireMock 3.
/// Collapsing duplicate `Content-Type` response headers, for instance, merely spells out what Jetty 11 already
/// did; on 4.x the extra values really are served, so the same edit would take headers off the wire.
abstract class WiremockThreeOnlyRecipe extends ScanningRecipe<Set<JavaProject>> {

    private static final ModuleHasDependency ON_WIREMOCK_3 =
            new ModuleHasDependency("org.wiremock", "wiremock*", null, "3.x", null);

    @Override
    public Set<JavaProject> getInitialValue(ExecutionContext ctx) {
        return ON_WIREMOCK_3.getInitialValue(ctx);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<JavaProject> acc) {
        return ON_WIREMOCK_3.getScanner(acc);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<JavaProject> acc) {
        return Preconditions.check(ON_WIREMOCK_3.getVisitor(acc), collapseDuplicates());
    }

    abstract TreeVisitor<?, ExecutionContext> collapseDuplicates();
}
