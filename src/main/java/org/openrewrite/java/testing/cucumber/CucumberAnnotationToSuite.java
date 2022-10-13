package org.openrewrite.java.testing.cucumber;

import java.text.RuleBasedCollator;
import java.time.Duration;
import java.util.Comparator;
import java.util.function.Supplier;

import lombok.SneakyThrows;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.ClassDeclaration;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.maven.AddDependency;

public class CucumberAnnotationToSuite extends Recipe {

    private static final String IO_CUCUMBER_JUNIT_PLATFORM_ENGINE_CUCUMBER = "io.cucumber.junit.platform.engine.Cucumber";

    private static final String SUITE = "org.junit.platform.suite.api.Suite";
    private static final String SELECT_CLASSPATH_RESOURCE = "org.junit.platform.suite.api.SelectClasspathResource";

    @Override
    public String getDisplayName() {
        return "Replace @Cucumber with @Suite";
    }

    @Override
    public String getDescription() {
        return "Replace @Cucumber with @Suite and @SelectClasspathResource(\"cucumber/annotated/class/package\").";
    }

    @Override
    public @Nullable Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>(IO_CUCUMBER_JUNIT_PLATFORM_ENGINE_CUCUMBER);
    }

    @Override
    protected JavaIsoVisitor<ExecutionContext> getVisitor() {
        final AnnotationMatcher cucumberAnnoMatcher = new AnnotationMatcher("@" + IO_CUCUMBER_JUNIT_PLATFORM_ENGINE_CUCUMBER);

        return new JavaIsoVisitor<ExecutionContext>() {
            @SneakyThrows
            @Override
            public J.ClassDeclaration visitClassDeclaration(ClassDeclaration cd, ExecutionContext p) {
                J.ClassDeclaration classDecl = super.visitClassDeclaration(cd, p);
                if (classDecl.getAllAnnotations().stream().noneMatch(cucumberAnnoMatcher::matches)) {
                    return classDecl;
                }

                Supplier<JavaParser> javaParserSupplier = () -> JavaParser.fromJavaVersion().dependsOn(
                                "package org.junit.platform.suite.api; public @interface Suite {}",
                                "package org.junit.platform.suite.api; public @interface SelectClasspathResource { String value(); }").build();

                JavaType.FullyQualified classFqn = TypeUtils.asFullyQualified(classDecl.getType());
                if (classFqn != null) {
                    maybeRemoveImport(IO_CUCUMBER_JUNIT_PLATFORM_ENGINE_CUCUMBER);
                    maybeAddImport(SUITE);
                    maybeAddImport(SELECT_CLASSPATH_RESOURCE);
                    doAfterVisit(new AddDependency("org.junit.platform", "junit-platform-suite", "latest.release", null,
                            "test", true, "org.junit.platform.suite.*", null, null, false, null));

                    final String classDeclPath = classFqn.getPackageName().replace('.','/');
                    classDecl = classDecl.withLeadingAnnotations(ListUtils.map(classDecl.getLeadingAnnotations(), ann -> {
                        if (cucumberAnnoMatcher.matches(ann)) {
                            String code = "@SelectClasspathResource(\"#{}\")";
                            JavaTemplate template = JavaTemplate.builder(this::getCursor, code)
                                    .javaParser(javaParserSupplier)
                                    .imports(SELECT_CLASSPATH_RESOURCE)
                                    .build();
                            return ann.withTemplate(template, ann.getCoordinates().replace(), classDeclPath);
                        }
                        return ann;
                    }));
                    classDecl = classDecl.withTemplate(JavaTemplate.builder(this::getCursor, "@Suite")
                            .javaParser(javaParserSupplier)
                            .imports(SUITE)
                            .build(), classDecl.getCoordinates().addAnnotation(Comparator.comparing(
                            J.Annotation::getSimpleName,
                            new RuleBasedCollator("< SelectClasspathResource")
                    )));
                }
                return classDecl;
            }
        };
    }

}
