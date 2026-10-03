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
            "preserving unsupported runner and rule lifecycles and their dependencies.";

    @Override
    public Set<JavaProject> getInitialValue(ExecutionContext ctx) {
        return new HashSet<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<JavaProject> unsupported) {
        return new JavaIsoVisitor<ExecutionContext>() {
            private void retainModule() {
                unsupported.add(getCursor().firstEnclosingOrThrow(J.CompilationUnit.class)
                        .getMarkers().findFirst(JavaProject.class).orElse(null));
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations variables, ExecutionContext ctx) {
                if (variables.getLeadingAnnotations().stream().anyMatch(a ->
                        TypeUtils.isOfClassType(a.getType(), "org.junit.Rule")) &&
                        SUPPORTED_RULES.stream().noneMatch(type -> TypeUtils.isOfClassType(variables.getType(), type))) {
                    retainModule();
                }
                return super.visitVariableDeclarations(variables, ctx);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                if (method.getLeadingAnnotations().stream().anyMatch(a ->
                        TypeUtils.isOfClassType(a.getType(), "org.junit.Rule")) &&
                        (method.getMethodType() == null || SUPPORTED_RULES.stream().noneMatch(type ->
                                TypeUtils.isOfClassType(method.getMethodType().getReturnType(), type)))) {
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
