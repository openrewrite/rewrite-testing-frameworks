package org.openrewrite.java.testing.junit5;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
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
            description = "The f8ully qualified class names of the JUnit Jupiter extension.",
            example = "org.springframework.test.context.junit.jupiter.SpringExtension")
    String extension;

    @Override
    public String getDisplayName() {
        return "JUnit4 @RunWith to JUnit Jupiter @ExtendWith";
    }

    @Override
    public String getDescription() {
        return "Replace runners with the JUnit Jupiter extension equivalent.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new RunnerToExtensionVisitor();
    }

    private class RunnerToExtensionVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final JavaType.Class extensionType = JavaType.Class.build(extension);

        private final JavaTemplate extendWithTemplate = template("@ExtendWith(" + extensionType.getClassName() + ".class)")
                .doBeforeParseTemplate(System.out::println)
                .javaParser(JavaParser.fromJavaVersion().dependsOn(Arrays.asList(
                        fromString("package org.junit.jupiter.api.extension;\n" +
                                "public @interface ExtendWith {\n" +
                                "   Class<? extends Extension>[] value();\n" +
                                "}"),
                        fromString("package " + extensionType.getPackageName() + ";\n" +
                                "public class " + extensionType.getClassName() + " {}"
                        ))).build())
                .imports("org.junit.jupiter.api.extension.ExtendWith", extension)
                .build();

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

            for (String runner : runners) {
                for (J.Annotation runWith : FindAnnotations.find(classDecl.withBody(null), "@org.junit.runner.RunWith(" + runner + ".class)")) {
                    cd = cd.withTemplate(extendWithTemplate, runWith.getCoordinates().replace());
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
                    md = md.withTemplate(extendWithTemplate, runWith.getCoordinates().replace());
                    maybeAddImport("org.junit.jupiter.api.extension.ExtendWith");
                    maybeAddImport(extension);
                    maybeRemoveImport("org.junit.runner.RunWith");
                    maybeRemoveImport(runner);
                }
            }

            return md;
        }
    }
}
