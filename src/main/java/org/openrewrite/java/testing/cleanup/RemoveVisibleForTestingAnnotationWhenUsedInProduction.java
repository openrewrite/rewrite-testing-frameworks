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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotation;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class RemoveVisibleForTestingAnnotationWhenUsedInProduction extends ScanningRecipe<RemoveVisibleForTestingAnnotationWhenUsedInProduction.Scanned> {

    public static class Scanned {
        List<String> methods = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        List<String> classes = new ArrayList<>();
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
                    checkAndRegister(acc.classes, fieldAccess.getTarget().getType());
                }
                // Mark fields
                if (fieldAccess.getName().getFieldType() != null) {
                    checkAndRegister(acc.fields, fieldAccess.getName().getFieldType());
                }
                return super.visitFieldAccess(fieldAccess, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (method.getMethodType() != null) {
                    checkAndRegister(acc.methods, method.getMethodType());
                }
                return super.visitMethodInvocation(method, ctx);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                // Mark constructors
                if (newClass.getConstructorType() != null) {
                    checkAndRegister(acc.methods, newClass.getConstructorType());
                }
                // Mark classes
                if (newClass.getClazz() != null && newClass.getClazz().getType() != null && newClass.getClazz().getType() instanceof JavaType.Class) {
                    checkAndRegister(acc.classes, newClass.getClazz().getType());
                }
                return super.visitNewClass(newClass, ctx);
            }

            private void checkAndRegister(List<String> target, JavaType type) {
                if (!target.contains(TypeUtils.toString(type))) {
                    getAnnotations(type).forEach(annotation -> {
                        if ("VisibleForTesting".equals(annotation.getClassName())) {
                            J.CompilationUnit compilationUnit = getCursor().firstEnclosing(J.CompilationUnit.class);
                            if (compilationUnit != null) {
                                compilationUnit
                                        .getMarkers()
                                        .findFirst(JavaSourceSet.class)
                                        .filter(elem -> "main".equals(elem.getName()))
                                        .ifPresent(sourceSet -> target.add(TypeUtils.toString(type)));
                            }
                        }
                    });
                }
            }

            private @NotNull List<JavaType.FullyQualified> getAnnotations(JavaType type) {
                if (type instanceof JavaType.Class) {
                    return ((JavaType.Class) type).getAnnotations();
                } else if (type instanceof JavaType.Variable) {
                    return ((JavaType.Variable) type).getAnnotations();
                } else if (type instanceof JavaType.Method) {
                    return ((JavaType.Method) type).getAnnotations();
                }
                return Collections.emptyList();
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Scanned acc) {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations variableDeclarations = super.visitVariableDeclarations(multiVariable, ctx);
                if (!variableDeclarations.getVariables().isEmpty()) {
                    // if none of the variables in the declaration are used from production code, the annotation should be kept
                    boolean keepAnnotation = variableDeclarations.getVariables().stream()
                            .filter(elem -> elem.getVariableType() != null)
                            .noneMatch(elem -> acc.fields.contains(TypeUtils.toString(elem.getVariableType())));
                    if (!keepAnnotation) {
                        return (J.VariableDeclarations) getElement(ctx, variableDeclarations.getLeadingAnnotations(), variableDeclarations);
                    }
                }
                return variableDeclarations;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration methodDeclaration = super.visitMethodDeclaration(method, ctx);
                if (methodDeclaration.getMethodType() != null && acc.methods.contains(TypeUtils.toString(methodDeclaration.getMethodType()))) {
                    return (J.MethodDeclaration) getElement(ctx, methodDeclaration.getLeadingAnnotations(), methodDeclaration);
                }
                return methodDeclaration;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration classDeclaration = super.visitClassDeclaration(classDecl, ctx);
                if (classDeclaration.getType() != null && acc.classes.contains(TypeUtils.toString(classDeclaration.getType()))) {
                    return (J.ClassDeclaration) getElement(ctx, classDeclaration.getLeadingAnnotations(), classDeclaration);
                }
                return classDeclaration;
            }

            private <@Nullable T extends J> J getElement(ExecutionContext ctx, List<J.Annotation> leadingAnnotations, T target) {
                Optional<J.Annotation> annotation = leadingAnnotations.stream()
                        .filter(elem -> "VisibleForTesting".equals(elem.getSimpleName()))
                        .findFirst();
                if (annotation.isPresent() && annotation.get().getType() instanceof JavaType.Class) {
                    JavaType.Class type = (JavaType.Class) annotation.get().getType();
                    return new RemoveAnnotation("@" + type.getFullyQualifiedName()).getVisitor().visitNonNull(target, ctx, getCursor().getParentOrThrow());
                }
                return target;
            }
        };
    }
}
