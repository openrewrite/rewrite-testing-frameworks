package org.openrewrite.java.testing.cucumber;

import java.time.Duration;
import java.util.List;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.ClassDeclaration;

public class CucumberAnnotationToSuite extends Recipe {

    private static final String IO_CUCUMBER_JUNIT_PLATFORM_ENGINE_CUCUMBER = "io.cucumber.junit.platform.engine.Cucumber";
    private static final AnnotationMatcher ANNOTATION_MATCHER = new AnnotationMatcher(
            IO_CUCUMBER_JUNIT_PLATFORM_ENGINE_CUCUMBER);

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
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(ClassDeclaration cd, ExecutionContext p) {
                J.ClassDeclaration classDecl = super.visitClassDeclaration(cd, p);
                if (classDecl.getAllAnnotations().stream().noneMatch(ANNOTATION_MATCHER::matches)) {
                    return classDecl;
                }

                maybeRemoveImport(IO_CUCUMBER_JUNIT_PLATFORM_ENGINE_CUCUMBER);
                maybeAddImport(SUITE);
                maybeAddImport(SELECT_CLASSPATH_RESOURCE);

                return classDecl.withLeadingAnnotations(ListUtils.flatMap(classDecl.getLeadingAnnotations(), ann -> {
                    String code = "@Suite\n@SelectClasspathResource(\"#{}\")";
                    String path = classDecl.getType().getPackageName().replace('.', '/');
                    JavaTemplate template = JavaTemplate.builder(this::getCursor, code)
                            .javaParser(
                                    () -> JavaParser.fromJavaVersion().classpath("junit-platform-suite-api").build())
                            .imports(SUITE, SELECT_CLASSPATH_RESOURCE)
                            .build();
                    List<J.Annotation> list = ann.withTemplate(template, ann.getCoordinates().replace(), path);
                    return list;
                }));
            }
        };
    }

}
