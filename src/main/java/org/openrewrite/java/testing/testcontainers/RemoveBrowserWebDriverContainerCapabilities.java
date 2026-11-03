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
package org.openrewrite.java.testing.testcontainers;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.HashSet;
import java.util.Set;

public class RemoveBrowserWebDriverContainerCapabilities extends Recipe {

    private static final String BROWSER_WEB_DRIVER_CONTAINER = "org.testcontainers.containers.BrowserWebDriverContainer";
    private static final MethodMatcher WITH_CAPABILITIES = new MethodMatcher(BROWSER_WEB_DRIVER_CONTAINER + " withCapabilities(..)", true);
    private static final MethodMatcher WITH_DESIRED_CAPABILITIES = new MethodMatcher(BROWSER_WEB_DRIVER_CONTAINER + " withDesiredCapabilities(..)", true);

    @Getter
    final String displayName = "Remove `BrowserWebDriverContainer` capabilities when an image is given";

    @Getter
    final String description = "Testcontainers 2 removed `withCapabilities(..)` and `withDesiredCapabilities(..)` from `BrowserWebDriverContainer`. " +
            "With an explicit image the capabilities were only used by `getWebDriver()`, which Testcontainers 2 removed as well, " +
            "so the call is removed from a chain that starts with `new BrowserWebDriverContainer(image)`. " +
            "Without an image the capabilities chose the browser image, so those calls are left for a manual migration.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(new UsesMethod<>(WITH_CAPABILITIES), new UsesMethod<>(WITH_DESIRED_CAPABILITIES)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (mi.getSelect() instanceof J.MethodInvocation) {
                            J.MethodInvocation select = (J.MethodInvocation) mi.getSelect();
                            if (isRemovable(select)) {
                                removeImportsOf(select);
                                return mi.withSelect(select.getSelect());
                            }
                        }
                        return mi;
                    }

                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                        J.VariableDeclarations.NamedVariable v = super.visitVariable(variable, ctx);
                        if (v.getInitializer() instanceof J.MethodInvocation && isRemovable((J.MethodInvocation) v.getInitializer())) {
                            removeImportsOf((J.MethodInvocation) v.getInitializer());
                            Expression select = ((J.MethodInvocation) v.getInitializer()).getSelect();
                            return v.withInitializer(select.withPrefix(v.getInitializer().getPrefix()));
                        }
                        return v;
                    }

                    private void removeImportsOf(J.MethodInvocation removed) {
                        Set<JavaType.FullyQualified> types = new HashSet<>();
                        JavaIsoVisitor<Set<JavaType.FullyQualified>> typeCollector = new JavaIsoVisitor<Set<JavaType.FullyQualified>>() {
                            @Override
                            public @Nullable JavaType visitType(@Nullable JavaType javaType, Set<JavaType.FullyQualified> found) {
                                JavaType.FullyQualified fq = TypeUtils.asFullyQualified(javaType);
                                if (fq != null) {
                                    found.add(fq);
                                }
                                return javaType;
                            }
                        };
                        for (Expression argument : removed.getArguments()) {
                            typeCollector.visit(argument, types, getCursor());
                        }
                        types.forEach(this::maybeRemoveImport);
                    }
                });
    }

    private static boolean isRemovable(J.MethodInvocation mi) {
        if (!WITH_CAPABILITIES.matches(mi) && !WITH_DESIRED_CAPABILITIES.matches(mi)) {
            return false;
        }
        Expression root = mi.getSelect();
        while (root instanceof J.MethodInvocation) {
            root = ((J.MethodInvocation) root).getSelect();
        }
        return root instanceof J.NewClass &&
                TypeUtils.isAssignableTo(BROWSER_WEB_DRIVER_CONTAINER, root.getType()) &&
                ((J.NewClass) root).getArguments().stream().noneMatch(J.Empty.class::isInstance);
    }
}
