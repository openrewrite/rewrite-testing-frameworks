/*
 * Copyright 2020 the original author or authors.
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
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Arrays;
import java.util.List;

import static org.openrewrite.Parser.Input.fromString;

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

    /**
     * Thread local is not static because it depends on instance variables passed into the recipe.
     */
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    ThreadLocal<JavaParser> javaParser;

    @JsonCreator
    public RunnerToExtension(List<String> runners, String extension) {
        this.runners = runners;
        this.extension = extension;

        //The extension can be null when the framework instantiates an instance for recipe discovery.
        //noinspection ConstantConditions
        if (extension != null) {
            JavaType.Class extensionType = JavaType.Class.build(extension);
            this.javaParser = ThreadLocal.withInitial(() ->

                    JavaParser.fromJavaVersion().dependsOn(Arrays.asList(
                            fromString("package org.junit.jupiter.api.extension;\n" +
                                    "public @interface ExtendWith {\n" +
                                    "   Class<? extends Extension>[] value();\n" +
                                    "}"),
                            fromString("package " + extensionType.getPackageName() + ";\n" +
                                    "public class " + extensionType.getClassName() + " {}"
                            ))).build());
        } else {
            javaParser = null;
        }
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

    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private final JavaType.Class extensionType = JavaType.Class.build(extension);

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                for (String runner : runners) {
                    //noinspection ConstantConditions
                    for (J.Annotation runWith : FindAnnotations.find(classDecl.withBody(null), "@org.junit.runner.RunWith(" + runner + ".class)")) {
                        cd = cd.withTemplate(template("@ExtendWith(#{}.class)")
                                        .javaParser(javaParser::get)
                                        .imports("org.junit.jupiter.api.extension.ExtendWith", extension)
                                        .build(),
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
                        md = md.withTemplate(template("@ExtendWith(#{}.class)")
                                        .javaParser(javaParser::get)
                                        .imports("org.junit.jupiter.api.extension.ExtendWith", extension)
                                        .build(),
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
