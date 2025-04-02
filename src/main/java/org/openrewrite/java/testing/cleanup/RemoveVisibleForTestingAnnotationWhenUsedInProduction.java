/*
 * Copyright 2025 the original author or authors.
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
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
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

                return super.visitFieldAccess(fieldAccess, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
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
                return super.visitMethodInvocation(method, ctx);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
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
                return super.visitNewClass(newClass, ctx);
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Scanned acc) {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations variableDeclarations = super.visitVariableDeclarations(multiVariable, ctx);
                if(!variableDeclarations.getVariables().isEmpty()) {
                    // if none of the variables in the declaration are used from production code, the annotation should be kept
                    boolean keepAnnotation = variableDeclarations.getVariables().stream().noneMatch(elem -> acc.fields.contains(elem.getVariableType()));
                    if (!keepAnnotation) {
                        Optional<J.Annotation> annotation = variableDeclarations.getLeadingAnnotations().stream()
                                .filter(elem -> "VisibleForTesting".equals(elem.getSimpleName()))
                                .findFirst();
                        if (annotation.isPresent() && annotation.get().getType() instanceof JavaType.Class) {
                            JavaType.Class type = (JavaType.Class) annotation.get().getType();
                            return (J.VariableDeclarations) new RemoveAnnotation("@" + type.getFullyQualifiedName()).getVisitor().visitNonNull(variableDeclarations, ctx, getCursor().getParentOrThrow());
                        }
                    }
                }
                return variableDeclarations;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration methodDeclaration = super.visitMethodDeclaration(method, ctx);
                if (acc.methods.contains(methodDeclaration.getMethodType())) {
                    Optional<J.Annotation> annotation = methodDeclaration.getLeadingAnnotations().stream()
                            .filter(elem -> "VisibleForTesting".equals(elem.getSimpleName()))
                            .findFirst();
                    if (annotation.isPresent() && annotation.get().getType() instanceof JavaType.Class) {
                        JavaType.Class type = (JavaType.Class) annotation.get().getType();
                        return (J.MethodDeclaration) new RemoveAnnotation("@" + type.getFullyQualifiedName()).getVisitor().visitNonNull(methodDeclaration, ctx, getCursor().getParentOrThrow());
                    }
                }
                return methodDeclaration;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration classDeclaration = super.visitClassDeclaration(classDecl, ctx);
                if (acc.classes.contains(classDeclaration.getType())) {
                    Optional<J.Annotation> annotation = classDeclaration.getLeadingAnnotations().stream()
                            .filter(elem -> "VisibleForTesting".equals(elem.getSimpleName()))
                            .findFirst();
                    if (annotation.isPresent() && annotation.get().getType() instanceof JavaType.Class) {
                        JavaType.Class type = (JavaType.Class) annotation.get().getType();
                        return (J.ClassDeclaration) new RemoveAnnotation("@" + type.getFullyQualifiedName()).getVisitor().visitNonNull(classDeclaration, ctx, getCursor().getParentOrThrow());
                    }
                }
                return classDeclaration;
            }
        };
    }
}
