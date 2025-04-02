package org.openrewrite.java.testing.cleanup;

import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotation;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RemoveVisibleForTestingAnnotationWhenUsedInProduction extends ScanningRecipe<RemoveVisibleForTestingAnnotationWhenUsedInProduction.Scanned> {

    public static class Scanned {
        List<JavaType.Method> methods = new ArrayList<>();
        List<JavaType.Variable> fields = new ArrayList<>();
        List<JavaType.Class> classes = new ArrayList<>();
    }

    @Override
    public String getDisplayName() {
        return "Remove `@VisibleForTesting` annotation when target is used in production";
    }

    @Override
    public String getDescription() {
        return "The `@VisibleForTesting` annotation is used when a method or a field has been made more accessible then it would normally be, solely for testing purposes. " +
                "This recipe removes the annotation where such an element is used from production classes. It identifies production classes as classes in `src/main` and test classes as classes in `src/test`. " +
                "It will remove the `@VisibleForTesting` from methods, fields (both member fields and constants), constructors and inner classes. " +
                "This recipe should not be used in an environment where QA tooling acts on the `@VisibleForTesting` annotation.";
    }

    @Override
    public Scanned getInitialValue(ExecutionContext ctx) {
        return new Scanned();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Scanned acc) {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext executionContext) {
                // Mark classes
                if (fieldAccess.getTarget().getType() instanceof JavaType.Class) {
                    JavaType.Class type = (JavaType.Class) fieldAccess.getTarget().getType();
                    if (!acc.classes.contains(type)) {
                        type.getAnnotations().forEach(annotation -> {
                            if ("VisibleForTesting".equals(annotation.getClassName())) {
                                J.CompilationUnit compilationUnit = getCursor().firstEnclosing(J.CompilationUnit.class);
                                if (compilationUnit != null && compilationUnit.getSourcePath().startsWith("src/main")) {
                                    acc.classes.add(type);
                                }
                            }
                        });
                    }
                }
                // Mark fields
                if(fieldAccess.getName().getFieldType() != null && !acc.fields.contains(fieldAccess.getName().getFieldType())) {
                    fieldAccess.getName().getFieldType().getAnnotations().forEach(annotation -> {
                        if ("VisibleForTesting".equals(annotation.getClassName())) {
                            J.CompilationUnit compilationUnit = getCursor().firstEnclosing(J.CompilationUnit.class);
                            if (compilationUnit != null && compilationUnit.getSourcePath().startsWith("src/main")) {
                                acc.fields.add(fieldAccess.getName().getFieldType());
                            }
                        }
                    });
                }

                return super.visitFieldAccess(fieldAccess, executionContext);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (method.getMethodType() != null) {
                    if (!acc.methods.contains(method.getMethodType())) {
                        method.getMethodType().getAnnotations().forEach(annotation -> {
                            if ("VisibleForTesting".equals(annotation.getClassName())) {
                                J.CompilationUnit compilationUnit = getCursor().firstEnclosing(J.CompilationUnit.class);
                                if (compilationUnit != null && compilationUnit.getSourcePath().startsWith("src/main")) {
                                    acc.methods.add(method.getMethodType());
                                }
                            }
                        });
                    }
                }
                return super.visitMethodInvocation(method, executionContext);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext executionContext) {
                // Mark constructors
                if (newClass.getConstructorType() != null) {
                    if(!acc.methods.contains(newClass.getConstructorType())) {
                        newClass.getConstructorType().getAnnotations().forEach(annotation -> {
                            if ("VisibleForTesting".equals(annotation.getClassName())) {
                                J.CompilationUnit compilationUnit = getCursor().firstEnclosing(J.CompilationUnit.class);
                                if (compilationUnit != null && compilationUnit.getSourcePath().startsWith("src/main")) {
                                    acc.methods.add(newClass.getConstructorType());
                                }
                            }
                        });
                    }
                }
                // Mark classes
                if (newClass.getClazz() != null && newClass.getClazz().getType() != null && newClass.getClazz().getType() instanceof JavaType.Class) {
                    JavaType.Class clazzType = (JavaType.Class) newClass.getClazz().getType();
                    if (!acc.classes.contains(clazzType)) {
                        clazzType.getAnnotations().forEach(annotation -> {
                            if ("VisibleForTesting".equals(annotation.getClassName())) {
                                J.CompilationUnit compilationUnit = getCursor().firstEnclosing(J.CompilationUnit.class);
                                if (compilationUnit != null && compilationUnit.getSourcePath().startsWith("src/main")) {
                                    acc.classes.add(clazzType);
                                }
                            }
                        });
                    }
                }
                return super.visitNewClass(newClass, executionContext);
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Scanned acc) {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext executionContext) {
                J.VariableDeclarations variableDeclarations = super.visitVariableDeclarations(multiVariable, executionContext);
                if(!variableDeclarations.getVariables().isEmpty()) {
                    // if none of the variables in the declaration are used from production code, the annotation should be kept
                    boolean keepAnnotation = variableDeclarations.getVariables().stream().noneMatch(elem -> acc.fields.contains(elem.getVariableType()));
                    if (!keepAnnotation) {
                        Optional<J.Annotation> annotation = variableDeclarations.getLeadingAnnotations().stream()
                                .filter(elem -> "VisibleForTesting".equals(elem.getSimpleName()))
                                .findFirst();
                        if (annotation.isPresent() && annotation.get().getType() instanceof JavaType.Class) {
                            JavaType.Class type = (JavaType.Class) annotation.get().getType();
                            return (J.VariableDeclarations) new RemoveAnnotation("@" + type.getFullyQualifiedName()).getVisitor().visitNonNull(variableDeclarations, executionContext, getCursor().getParentOrThrow());
                        }
                    }
                }
                return variableDeclarations;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
                J.MethodDeclaration methodDeclaration = super.visitMethodDeclaration(method, executionContext);
                if (acc.methods.contains(methodDeclaration.getMethodType())) {
                    Optional<J.Annotation> annotation = methodDeclaration.getLeadingAnnotations().stream()
                            .filter(elem -> "VisibleForTesting".equals(elem.getSimpleName()))
                            .findFirst();
                    if (annotation.isPresent() && annotation.get().getType() instanceof JavaType.Class) {
                        JavaType.Class type = (JavaType.Class) annotation.get().getType();
                        return (J.MethodDeclaration) new RemoveAnnotation("@" + type.getFullyQualifiedName()).getVisitor().visitNonNull(methodDeclaration, executionContext, getCursor().getParentOrThrow());
                    }
                }
                return methodDeclaration;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                J.ClassDeclaration classDeclaration = super.visitClassDeclaration(classDecl, executionContext);
                if (acc.classes.contains(classDeclaration.getType())) {
                    Optional<J.Annotation> annotation = classDeclaration.getLeadingAnnotations().stream()
                            .filter(elem -> "VisibleForTesting".equals(elem.getSimpleName()))
                            .findFirst();
                    if (annotation.isPresent() && annotation.get().getType() instanceof JavaType.Class) {
                        JavaType.Class type = (JavaType.Class) annotation.get().getType();
                        return (J.ClassDeclaration) new RemoveAnnotation("@" + type.getFullyQualifiedName()).getVisitor().visitNonNull(classDeclaration, executionContext, getCursor().getParentOrThrow());
                    }
                }
                return classDeclaration;
            }
        };
    }
}
