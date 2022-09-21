package org.openrewrite.java.testing.cucumber;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.MethodDeclaration;
import org.openrewrite.java.tree.J.MethodInvocation;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.JavaType.Method;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.maven.ChangeDependencyGroupIdAndArtifactId;

public class CucumberJava8ToCucumberJava extends Recipe {

    public static final String IO_CUCUMBER_JAVA8 = "io.cucumber.java8";

    public CucumberJava8ToCucumberJava() {
        doNext(new ChangeDependencyGroupIdAndArtifactId(
                "io.cucumber", "cucumber-java8",
                "io.cucumber", "cucumber-java",
                null, null));

    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesMethod<>(IO_CUCUMBER_JAVA8 + ".* *(..)");
    }

    @Override
    public String getDisplayName() {
        return "TODO";
    }

    @Override
    public String getDescription() {
        return "TODO";
    }

    @Override
    public @Nullable Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(10);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new CucumberJava8Visitor();
    }

    static class CucumberJava8Visitor extends JavaVisitor<ExecutionContext> {
        @Override
        public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext p) {
            List<TypeTree> interfaces = classDecl.getImplements();
            if (interfaces == null || interfaces.isEmpty()) {
                return super.visitClassDeclaration(classDecl, p);
            }

            List<TypeTree> retained = new ArrayList<>();
            for (TypeTree typeTree : interfaces) {
                if (typeTree.getType() instanceof JavaType.Class clazz
                        && IO_CUCUMBER_JAVA8.equals(clazz.getPackageName())) {
                    maybeRemoveImport(clazz.getFullyQualifiedName());
                    maybeAddImport("io.cucumber.java.%s.*".formatted(clazz.getClassName().toLowerCase()), false);
                } else {
                    retained.add(typeTree);
                }
            }
            return classDecl.withImplements(retained);
        }

        @Override
        public J visitMethodDeclaration(MethodDeclaration method, ExecutionContext p) {
            if (!method.isConstructor()) {
                return super.visitMethodDeclaration(method, p);
            }

            System.out.println(method);

            // TODO Only remove empty constructor
//            return method.withTemplate(JavaTemplate.builder(this::getCursor, ";").build(),
//                    method.getCoordinates().replace());
            return method;
        }

        @Override
        public J visitMethodInvocation(MethodInvocation method, ExecutionContext p) {
            System.out.println(method);
            Method methodType = method.getMethodType();
            System.out.println(methodType);

            // TODO Auto-generated method stub
            return super.visitMethodInvocation(method, p);
        }
    }
}
