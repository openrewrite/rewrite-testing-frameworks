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
package org.openrewrite.java.testing.junit5;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.List;

@SuppressWarnings("DuplicatedCode")
@Value
@EqualsAndHashCode(callSuper = false)
public class RunnerToExtension extends Recipe {

    @Option(displayName = "Runners",
            description = "The fully qualified class names of the JUnit 4 runners to replace. Sometimes several runners are replaced by a single JUnit Jupiter extension.",
            example = "[ org.springframework.test.context.junit4.SpringRunner ]")
    List<String> runners;

    @Option(displayName = "Extension",
            description = "The fully qualified class names of the JUnit Jupiter extension.",
            example = "org.springframework.test.context.junit.jupiter.SpringExtension")
    String extension;


    @JsonCreator
    public RunnerToExtension(@JsonProperty("runners") List<String> runners, @JsonProperty("extension") String extension) {
        this.runners = runners;
        this.extension = extension;
    }

    @Override
    public String getDisplayName() {
        return "JUnit 4 `@RunWith` to JUnit Jupiter `@ExtendWith`";
    }

    @Override
    public String getDescription() {
        return "Replace runners with the JUnit Jupiter extension equivalent.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        @SuppressWarnings("unchecked") TreeVisitor<?, ExecutionContext> precondition =
                Preconditions.or(runners.stream().map(r -> new UsesType<>(r, false)).toArray(UsesType[]::new));
        return Preconditions.check(precondition, new JavaIsoVisitor<ExecutionContext>() {
            private final JavaType.Class extensionType = JavaType.ShallowClass.build(extension);

            @Nullable
            private JavaTemplate extendsWithTemplate;

            private JavaTemplate getExtendsWithTemplate(ExecutionContext ctx) {
                if (extendsWithTemplate == null) {
                    extendsWithTemplate = JavaTemplate.builder("@ExtendWith(#{}.class)")
                            .javaParser(JavaParser.fromJavaVersion()
                                    .classpathFromResources(ctx, "junit-jupiter-api-5.9")
                                    .dependsOn("package " + extensionType.getPackageName() + ";\n" +
                                               "import org.junit.jupiter.api.extension.Extension;\n" +
                                               "public class " + extensionType.getClassName() + " implements Extension {}"))
                            .imports("org.junit.jupiter.api.extension.ExtendWith",
                                    "org.junit.jupiter.api.extension.Extension",
                                    extension)
                            .build();
                }
                return extendsWithTemplate;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                for (String runner : runners) {
                    //noinspection ConstantConditions
                    for (J.Annotation runWith : FindAnnotations.find(classDecl.withBody(null), "@org.junit.runner.RunWith(" + runner + ".class)")) {
                        cd = getExtendsWithTemplate(ctx).apply(
                                updateCursor(cd),
                                runWith.getCoordinates().replace(),
                                extensionType.getClassName()
                        );
                        maybeAddImport("org.junit.jupiter.api.extension.ExtendWith");
                        maybeAddImport(extension);
                        maybeRemoveImport("org.junit.runner.RunWith");
                        maybeRemoveImport(runner);
                    }
                }

                return cd;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);

                for (String runner : runners) {
                    for (J.Annotation runWith : FindAnnotations.find(method.withBody(null), "@org.junit.runner.RunWith(" + runner + ".class)")) {
                        md = getExtendsWithTemplate(ctx).apply(
                                updateCursor(md),
                                runWith.getCoordinates().replace(),
                                extensionType.getClassName()
                        );
                        maybeAddImport("org.junit.jupiter.api.extension.ExtendWith");
                        maybeAddImport(extension);
                        maybeRemoveImport("org.junit.runner.RunWith");
                        maybeRemoveImport(runner);
                    }
                }

                return md;
            }
        });
    }
}
