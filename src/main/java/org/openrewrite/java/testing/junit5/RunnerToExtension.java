/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.time.Duration;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = true)
public class RunnerToExtension extends Recipe {

    @Option(displayName = "Runners",
            description = "The fully qualified class names of the JUnit4 runners to replace. Sometimes several runners are replaced by a single JUnit Jupiter extension.",
            example = "org.springframework.test.context.junit4.SpringRunner")
    List<String> runners;

    @Option(displayName = "Extension",
            description = "The fully qualified class names of the JUnit Jupiter extension.",
            example = "org.springframework.test.context.junit.jupiter.SpringExtension")
    String extension;


    @JsonCreator
    public RunnerToExtension(@JsonProperty("runners") List<String> runners,@JsonProperty("extension") String extension) {
        this.runners = runners;
        this.extension = extension;
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                for (String runner : runners) {
                    doAfterVisit(new UsesType<>(runner));
                }
                return cu;
            }
        };
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
  public Duration getEstimatedEffortPerOccurrence() {
    return Duration.ofMinutes(5);
  }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private final JavaType.Class extensionType = JavaType.ShallowClass.build(extension);
            @Nullable
            private JavaTemplate extendsWithTemplate;
            private JavaTemplate getExtendsWithTemplate() {
                if (extendsWithTemplate == null) {
                    extendsWithTemplate = JavaTemplate.builder(this::getCursor, "@ExtendWith(#{}.class)")
                            .javaParser(() -> JavaParser.fromJavaVersion()
                                    .classpath("junit", "junit-jupiter-api")
                                    .dependsOn( "package " + extensionType.getPackageName() + ";\n" +
                                            "import org.junit.jupiter.api.extension.Extension;\n" +
                                            "public class " + extensionType.getClassName() + " implements Extension {}")
                                    .build())
                            .imports("org.junit.jupiter.api.extension.ExtendWith",
                                    "org.junit.jupiter.api.extension.Extension", extension)
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
                        cd = cd.withTemplate(getExtendsWithTemplate(),
                                runWith.getCoordinates().replace(),
                                extensionType.getClassName());
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
                        md = md.withTemplate(getExtendsWithTemplate(),
                                runWith.getCoordinates().replace(),
                                extensionType.getClassName());
                        maybeAddImport("org.junit.jupiter.api.extension.ExtendWith");
                        maybeAddImport(extension);
                        maybeRemoveImport("org.junit.runner.RunWith");
                        maybeRemoveImport(runner);
                    }
                }

                return md;
            }
        };
    }
}
