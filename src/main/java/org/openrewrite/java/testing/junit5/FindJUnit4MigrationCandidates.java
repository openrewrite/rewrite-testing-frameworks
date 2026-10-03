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
package org.openrewrite.java.testing.junit5;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A module must keep its JUnit 4 lifecycle and dependencies when a runner has no
 * replacement in the migration. Guarding individual annotations would leave a
 * mixture of test engines, inherited lifecycle methods and incompatible rules.
 */
public class FindJUnit4MigrationCandidates extends ScanningRecipe<Set<JavaProject>> {
    private static final Set<String> SUPPORTED_RUNNERS = new HashSet<>(Arrays.asList(
            "org.junit.runners.JUnit4",
            "org.junit.runners.BlockJUnit4ClassRunner",
            "org.junit.runners.Parameterized",
            "org.junit.experimental.runners.Enclosed",
            "junitparams.JUnitParamsRunner",
            "org.mockito.runners.MockitoJUnitRunner",
            "org.mockito.runners.MockitoJUnitRunner$Silent",
            "org.mockito.runners.MockitoJUnitRunner$Strict",
            "org.mockito.junit.MockitoJUnitRunner",
            "org.mockito.junit.MockitoJUnitRunner$Silent",
            "org.mockito.junit.MockitoJUnitRunner$Strict",
            "org.mockito.junit.MockitoJUnitRunner$StrictStubs",
            "io.vertx.ext.unit.junit.VertxUnitRunner",
            "org.jboss.arquillian.junit.Arquillian",
            "org.jboss.byteman.contrib.bmunit.BMUnitRunner"
    ));

    private static final Set<String> SUPPORTED_RULES = new HashSet<>(Arrays.asList(
            "org.junit.rules.TemporaryFolder",
            "org.junit.rules.ExpectedException",
            "org.junit.rules.TestName",
            "org.junit.rules.Timeout",
            "org.mockito.junit.MockitoRule",
            "org.mockito.junit.MockitoTestRule",
            "org.assertj.core.api.JUnitSoftAssertions",
            "org.assertj.core.api.JUnitBDDSoftAssertions",
            "com.github.tomakehurst.wiremock.junit.WireMockRule",
            "org.junit.contrib.java.lang.system.EnvironmentVariables",
            "okhttp3.mockwebserver.MockWebServer",
            "com.squareup.okhttp.mockwebserver.MockWebServer"
    ));

    @Getter
    final String displayName = "Find modules eligible for JUnit Jupiter migration";

    @Getter
    final String description = "Find modules whose JUnit 4 integrations have replacements in the migration, " +
            "preserving production JUnit APIs, unsupported runner and rule lifecycles, and their dependencies.";

    @Override
    public Set<JavaProject> getInitialValue(ExecutionContext ctx) {
        return new HashSet<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<JavaProject> unsupported) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                String path = cu.getSourcePath().toString().replace('\\', '/');
                boolean production = cu.getMarkers().findFirst(JavaSourceSet.class)
                        .map(sourceSet -> "main".equals(sourceSet.getName()))
                        .orElse(path.startsWith("src/main/") || path.contains("/src/main/"));
                if (production && Arrays.asList("org.junit.*", "org.junit.rules..*", "org.junit.runner..*",
                        "org.junit.runners..*", "junit.framework..*").stream().anyMatch(type ->
                        new UsesType<>(type, false).visit(cu, ctx) != cu)) {
                    // Test libraries expose JUnit 4 contracts to consumers. Removing their
                    // compile dependency is not a test-framework migration.
                    unsupported.add(cu.getMarkers().findFirst(JavaProject.class).orElse(null));
                }
                return super.visitCompilationUnit(cu, ctx);
            }

            private void retainModule() {
                unsupported.add(getCursor().firstEnclosingOrThrow(J.CompilationUnit.class)
                        .getMarkers().findFirst(JavaProject.class).orElse(null));
            }

            private boolean unsupportedRule(J.Annotation annotation, @Nullable JavaType type) {
                if (TypeUtils.isOfClassType(annotation.getType(), "org.junit.ClassRule")) {
                    // ExternalResourceSupport only finds @Rule members, not @ClassRule.
                    return !TypeUtils.isOfClassType(type, "org.junit.rules.TemporaryFolder");
                }
                return TypeUtils.isOfClassType(annotation.getType(), "org.junit.Rule") &&
                        SUPPORTED_RULES.stream().noneMatch(supported -> TypeUtils.isOfClassType(type, supported));
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations variables, ExecutionContext ctx) {
                if (variables.getLeadingAnnotations().stream().anyMatch(a ->
                        unsupportedRule(a, variables.getType()))) {
                    retainModule();
                }
                return super.visitVariableDeclarations(variables, ctx);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                // The native rule migrations operate on fields. A supported return type alone
                // does not make a method-form rule safe to migrate.
                if (method.getLeadingAnnotations().stream().anyMatch(a ->
                        TypeUtils.isOfClassType(a.getType(), "org.junit.Rule") ||
                        TypeUtils.isOfClassType(a.getType(), "org.junit.ClassRule"))) {
                    retainModule();
                }
                return super.visitMethodDeclaration(method, ctx);
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                if (TypeUtils.isOfClassType(annotation.getType(), "org.junit.runner.RunWith") &&
                        annotation.getArguments() != null) {
                    for (Expression argument : annotation.getArguments()) {
                        Expression value = argument instanceof J.Assignment ? ((J.Assignment) argument).getAssignment() : argument;
                        if (value instanceof J.FieldAccess && "class".equals(((J.FieldAccess) value).getSimpleName())) {
                            JavaType.FullyQualified runner =
                                    TypeUtils.asFullyQualified(((J.FieldAccess) value).getTarget().getType());
                            if (runner == null || !SUPPORTED_RUNNERS.contains(runner.getFullyQualifiedName())) {
                                retainModule();
                            }
                        }
                    }
                }
                return super.visitAnnotation(annotation, ctx);
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<JavaProject> unsupported) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree == null) {
                    return null;
                }
                JavaProject project = tree.getMarkers().findFirst(JavaProject.class).orElse(null);
                return unsupported.contains(project) ? tree : SearchResult.found(tree);
            }
        };
    }
}
