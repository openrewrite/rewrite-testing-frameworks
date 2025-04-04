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
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;

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
        return "The `@VisibleForTesting` annotation marks a method or field that is intentionally made more accessible (e.g., changing its visibility from private or package-private to public or protected) solely for testing purposes. " +
                "The annotation serves as an indicator that the increased visibility is not part of the intended public API but exists only to support testability. " +
                "This recipe removes the annotation where such an element is used from production classes. It identifies production classes as classes in `src/main` and test classes as classes in `src/test`. " +
                "It will remove the `@VisibleForTesting` from methods, fields (both member fields and constants), constructors and inner classes. " +
                "It does not support generic methods (e.g. `<T> T method(T);`. " +
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
            public J.FieldAccess visitFieldAccess(J.FieldAccess fa, ExecutionContext ctx) {
                if (fa.getTarget().getType() instanceof JavaType.Class) {
                    checkAndRegister(acc.classes, fa.getTarget().getType());
                }
                if (fa.getName().getFieldType() != null) {
                    checkAndRegister(acc.fields, fa.getName().getFieldType());
                }
                return super.visitFieldAccess(fa, ctx);
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference mr, ExecutionContext ctx) {
                if (mr.getMethodType() != null) {
                    checkAndRegister(acc.methods, mr.getMethodType());
                }
                return super.visitMemberReference(mr, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
                if (mi.getMethodType() != null) {
                    checkAndRegister(acc.methods, mi.getMethodType());
                }
                return super.visitMethodInvocation(mi, ctx);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass nc, ExecutionContext ctx) {
                if (nc.getConstructorType() != null) {
                    checkAndRegister(acc.methods, nc.getConstructorType());
                }
                if (nc.getClazz() != null && nc.getClazz().getType() instanceof JavaType.Class) {
                    checkAndRegister(acc.classes, nc.getClazz().getType());
                }
                return super.visitNewClass(nc, ctx);
            }

            private void checkAndRegister(List<String> target, JavaType type) {
                if (!target.contains(TypeUtils.toString(type))) {
                    getAnnotations(type).forEach(annotation -> {
                        if ("VisibleForTesting".equals(annotation.getClassName())) {
                            J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
                            if (cu != null &&
                                    cu.getMarkers().findFirst(JavaSourceSet.class).filter(s -> "main".equals(s.getName().toLowerCase(Locale.ROOT))).isPresent()){
                                target.add(TypeUtils.toString(type));
                            }
                        }
                    });
                }
            }

            private List<JavaType.FullyQualified> getAnnotations(JavaType type) {
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
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations vd, ExecutionContext ctx) {
                J.VariableDeclarations variableDeclarations = super.visitVariableDeclarations(vd, ctx);
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
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext ctx) {
                J.MethodDeclaration methodDeclaration = super.visitMethodDeclaration(md, ctx);
                if (methodDeclaration.getMethodType() != null && acc.methods.contains(TypeUtils.toString(methodDeclaration.getMethodType()))) {
                    return (J.MethodDeclaration) getElement(ctx, methodDeclaration.getLeadingAnnotations(), methodDeclaration);
                }
                return methodDeclaration;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
                J.ClassDeclaration classDeclaration = super.visitClassDeclaration(cd, ctx);
                if (classDeclaration.getType() != null && acc.classes.contains(TypeUtils.toString(classDeclaration.getType()))) {
                    return (J.ClassDeclaration) getElement(ctx, classDeclaration.getLeadingAnnotations(), classDeclaration);
                }
                return classDeclaration;
            }

            private <T extends J> J getElement(ExecutionContext ctx, List<J.Annotation> leadingAnnotations, T target) {
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
