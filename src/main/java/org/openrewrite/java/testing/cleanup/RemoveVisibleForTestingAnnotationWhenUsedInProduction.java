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
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotation;
import org.openrewrite.java.search.IsLikelyNotTest;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RemoveVisibleForTestingAnnotationWhenUsedInProduction extends ScanningRecipe<RemoveVisibleForTestingAnnotationWhenUsedInProduction.VisibleForTesting> {

    public static class VisibleForTesting {
        Set<String> methodPatterns = new HashSet<>();
        Set<String> fieldPatterns = new HashSet<>();
        Set<String> classPatterns = new HashSet<>();
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
    public VisibleForTesting getInitialValue(ExecutionContext ctx) {
        return new VisibleForTesting();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(VisibleForTesting acc) {
        JavaIsoVisitor<ExecutionContext> scanningVisitor = new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fa, ExecutionContext ctx) {
                if (fa.getTarget().getType() instanceof JavaType.Class) {
                    checkAndRegister(acc.classPatterns, fa.getTarget().getType());
                }
                if (fa.getName().getFieldType() != null) {
                    checkAndRegister(acc.fieldPatterns, fa.getName().getFieldType());
                }
                return super.visitFieldAccess(fa, ctx);
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference mr, ExecutionContext ctx) {
                if (mr.getMethodType() != null) {
                    checkAndRegister(acc.methodPatterns, mr.getMethodType());
                }
                return super.visitMemberReference(mr, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
                if (mi.getMethodType() != null) {
                    checkAndRegister(acc.methodPatterns, mi.getMethodType());
                }
                return super.visitMethodInvocation(mi, ctx);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass nc, ExecutionContext ctx) {
                if (nc.getConstructorType() != null) {
                    checkAndRegister(acc.methodPatterns, nc.getConstructorType());
                }
                if (nc.getClazz() != null && nc.getClazz().getType() instanceof JavaType.Class) {
                    checkAndRegister(acc.classPatterns, nc.getClazz().getType());
                }
                return super.visitNewClass(nc, ctx);
            }

            private void checkAndRegister(Set<String> target, JavaType type) {
                String typeString = TypeUtils.toString(type);
                if (!target.contains(typeString)) {
                    for (JavaType.FullyQualified annotation : getAnnotations(type)) {
                        if ("VisibleForTesting".equals(annotation.getClassName())) {
                            target.add(typeString);
                        }
                    }
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
        return Preconditions.check(new IsLikelyNotTest().getVisitor(), scanningVisitor);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(VisibleForTesting acc) {
        JavaIsoVisitor<ExecutionContext> visitor = new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations vd, ExecutionContext ctx) {
                J.VariableDeclarations variableDeclarations = super.visitVariableDeclarations(vd, ctx);
                if (!variableDeclarations.getVariables().isEmpty()) {
                    // if none of the variables in the declaration are used from production code, the annotation should be kept
                    for (J.VariableDeclarations.NamedVariable elem : variableDeclarations.getVariables()) {
                        if (elem.getVariableType() != null) {
                            if (acc.fieldPatterns.contains(TypeUtils.toString(elem.getVariableType()))) {
                                return (J.VariableDeclarations) new RemoveAnnotation("@*..VisibleForTesting").getVisitor()
                                        .visitNonNull(variableDeclarations, ctx, getCursor().getParentOrThrow());
                            }
                        }
                    }
                }
                return variableDeclarations;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext ctx) {
                J.MethodDeclaration methodDeclaration = super.visitMethodDeclaration(md, ctx);
                if (methodDeclaration.getMethodType() != null && acc.methodPatterns.contains(TypeUtils.toString(methodDeclaration.getMethodType()))) {
                    return (J.MethodDeclaration) new RemoveAnnotation("@*..VisibleForTesting").getVisitor()
                            .visitNonNull(methodDeclaration, ctx, getCursor().getParentOrThrow());
                }
                return methodDeclaration;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
                J.ClassDeclaration classDeclaration = super.visitClassDeclaration(cd, ctx);
                if (classDeclaration.getType() != null && acc.classPatterns.contains(TypeUtils.toString(classDeclaration.getType()))) {
                    return (J.ClassDeclaration) new RemoveAnnotation("@*..VisibleForTesting").getVisitor()
                            .visitNonNull(classDeclaration, ctx, getCursor().getParentOrThrow());
                }
                return classDeclaration;
            }
        };
        return Preconditions.check(new IsLikelyNotTest().getVisitor(), visitor);
    }
}
