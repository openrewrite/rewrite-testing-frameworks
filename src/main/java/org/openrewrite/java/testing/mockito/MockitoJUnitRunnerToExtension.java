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
package org.openrewrite.java.testing.mockito;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.testing.junit5.RunnerToExtension;
import org.openrewrite.java.trait.Annotated;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class MockitoJUnitRunnerToExtension extends Recipe {
    @Override
    public String getDisplayName() {
        return "Replace JUnit 4 MockitoJUnitRunner with junit-jupiter MockitoExtension";
    }


    @Override
    public String getDescription() {
        return "Replace JUnit 4 MockitoJUnitRunner annotations with JUnit 5 `@ExtendWith(MockitoExtension.class)` " +
                "using the appropriate strictness levels (LENIENT, WARN, STRICT_STUBS).";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.mockito.junit.MockitoJUnitRunner*", false), new JavaIsoVisitor<ExecutionContext>() {

            final String runWith = "@org.junit.runner.RunWith";

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                AtomicReference<Strictness> strictness = new AtomicReference<>();
                new Annotated.Matcher(runWith).<AtomicReference<Strictness>>asVisitor((a, s) -> a.getTree().acceptJava(new JavaIsoVisitor<AtomicReference<Strictness>>() {
                    @Override
                    public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, AtomicReference<Strictness> strictness) {
                        for (Strictness strict : Strictness.values()) {
                            if (TypeUtils.isAssignableTo(strict.runner, fieldAccess.getTarget().getType())) {
                                strictness.set(strict);
                                break;
                            }
                        }
                        return fieldAccess;
                    }
                }, s)).visit(cd, strictness);

                if (strictness.get() == null) { // class doesn't have MockitoJunitRunner
                    return cd;
                }

                registerAfterVisit();
                return getTemplate(strictness.get(), ctx)
                        .map(t -> maybeAutoFormat(cd,
                                t.apply(updateCursor(cd), cd.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName))),
                                ctx)).orElse(cd);
            }

            private void registerAfterVisit() {
                doAfterVisit(new RunnerToExtension(Arrays.asList("org.mockito.junit.MockitoJUnitRunner.Silent", "org.mockito.junit.MockitoJUnitRunner.Strict", "org.mockito.junit.MockitoJUnitRunner"),
                        "org.mockito.junit.jupiter.MockitoExtension").getVisitor());
                for (Strictness strictness : Strictness.values()) {
                    maybeRemoveImport(strictness.runner);
                }
                maybeAddImport("org.mockito.quality.Strictness");
                maybeAddImport("org.mockito.junit.jupiter.MockitoSettings");
            }

            private Optional<JavaTemplate> getTemplate(Strictness strictness, ExecutionContext ctx) {
                // MockitoExtension defaults to STRICT_STUBS, no need of explicit setting.
                if (strictness == Strictness.STRICT_STUBS) {
                    return Optional.empty();
                }
                return Optional.of(JavaTemplate.builder("@MockitoSettings(strictness = Strictness." + strictness + ")")
                        .imports("org.mockito.quality.Strictness", "org.mockito.junit.jupiter.MockitoSettings")
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "mockito-junit-jupiter-3.12", "mockito-core-3.12"))
                        .build());
            }
        });
    }

    private enum Strictness {
        LENIENT("org.mockito.junit.MockitoJUnitRunner.Silent"),
        STRICT_STUBS("org.mockito.junit.MockitoJUnitRunner.Strict"),
        WARN("org.mockito.junit.MockitoJUnitRunner");

        final String runner;

        Strictness(String runner) {
            this.runner = runner;
        }
    }
}
