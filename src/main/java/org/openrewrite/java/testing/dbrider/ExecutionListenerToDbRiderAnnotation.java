/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.testing.dbrider;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Comparator;
import java.util.List;

public class ExecutionListenerToDbRiderAnnotation extends ScanningRecipe<ExecutionListenerToDbRiderAnnotation.DbRiderExecutionListenerContext> {

    private static final AnnotationMatcher executionListenerAnnotationMatcher = new AnnotationMatcher("@org.springframework.test.context.TestExecutionListeners");
    private static final AnnotationMatcher dbriderAnnotationMatcher = new AnnotationMatcher("@com.github.database.rider.junit5.api.DBRider");

    @Override
    public String getDisplayName() {
        return "Migrate the `DBRiderTestExecutionListener` to the `@DBRider` annotation";
    }

    @Override
    public String getDescription() {
        return "Migrate the `DBRiderTestExecutionListener` to the `@DBRider` annotation.\n" +
                "This recipe is useful when migrating from junit4 dbrider-spring to junit5 dbrider-junit5.";
    }

    @Override
    public DbRiderExecutionListenerContext getInitialValue(final ExecutionContext ctx) {
        return new DbRiderExecutionListenerContext();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(final DbRiderExecutionListenerContext acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(final J.Annotation annotation, final ExecutionContext ctx) {
                if (executionListenerAnnotationMatcher.matches(annotation)) {
                    acc.testExecutionListenersFound(annotation);
                } else if (dbriderAnnotationMatcher.matches(annotation)) {
                    acc.dbriderFound = true;
                }
                return super.visitAnnotation(annotation, ctx);
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(final DbRiderExecutionListenerContext acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(final J.CompilationUnit cu, final ExecutionContext ctx) {
                if (acc.shouldMigrate()) {
                    maybeRemoveImport("org.springframework.test.context.TestExecutionListeners");
                    maybeRemoveImport("org.springframework.test.context.TestExecutionListeners.MergeMode");
                    maybeRemoveImport("com.github.database.rider.spring.DBRiderTestExecutionListener");
                    maybeAddImport("com.github.database.rider.junit5.api.DBRider");
                    return super.visitCompilationUnit(cu, ctx);
                }
                return cu;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(final J.ClassDeclaration classDeclaration, final ExecutionContext ctx) {
                J.ClassDeclaration c = classDeclaration;
                if (acc.shouldAddDbRiderAnnotation()) {
                    c = JavaTemplate.builder("@DBRider")
                            .imports("com.github.database.rider.junit5.api.DBRider")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "rider-junit5-1.44"))
                            .build()
                            .apply(getCursor(), c.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                }
                return c.withLeadingAnnotations(ListUtils.map(c.getLeadingAnnotations(), annotation -> {
                    if (annotation != null && executionListenerAnnotationMatcher.matches(annotation)) {
                        return acc.getExecutionListenerAnnotation();
                    }
                    return annotation;
                }));
            }
        };
    }

    public static class DbRiderExecutionListenerContext {
        private J.@Nullable Annotation testExecutionListenerAnnotation;
        private boolean dbriderFound = false;
        private J.@Nullable NewArray listeners;
        private J.@Nullable FieldAccess listener;
        private @Nullable Expression inheritListeners;
        private @Nullable Expression mergeMode;


        public void testExecutionListenersFound(final J.Annotation annotation) {
            testExecutionListenerAnnotation = annotation;
            if (annotation.getArguments() != null) {
                annotation.getArguments().forEach(arg -> {
                    if (arg instanceof J.Assignment) {
                        J.Assignment assignment = (J.Assignment) arg;
                        switch (((J.Identifier) assignment.getVariable()).getSimpleName()) {
                            case "value":
                            case "listeners":
                                if (assignment.getAssignment() instanceof J.NewArray) {
                                    listeners = (J.NewArray) assignment.getAssignment();
                                }
                                break;
                            case "inheritListeners":
                                inheritListeners = assignment.getAssignment();
                                break;
                            case "mergeMode":
                                mergeMode = assignment.getAssignment();
                                break;
                        }
                    } else if (arg instanceof J.NewArray) {
                        listeners = (J.NewArray) arg;
                    } else if (arg instanceof J.FieldAccess) {
                        listener = (J.FieldAccess) arg;
                    }
                });
            }
        }

        public boolean shouldMigrate() {
            return isTestExecutionListenerForDbRider() && !dbriderFound;
        }

        public boolean shouldAddDbRiderAnnotation() {
            if (dbriderFound) {
                return false;
            }

            return isTestExecutionListenerForDbRider();
        }

        public J.@Nullable Annotation getExecutionListenerAnnotation() {
            if (isTestExecutionListenerForDbRider()) {
                if (canTestExecutionListenerBeRemoved()) {
                    return null;
                }
                if (testExecutionListenerAnnotation != null && testExecutionListenerAnnotation.getArguments() != null) {
                    return testExecutionListenerAnnotation.withArguments(ListUtils.map(testExecutionListenerAnnotation.getArguments(), arg -> {
                        if (arg instanceof J.Assignment) {
                            J.Assignment assignment = (J.Assignment) arg;
                            Expression newValue = assignment.getAssignment();
                            switch (((J.Identifier) assignment.getVariable()).getSimpleName()) {
                                case "value":
                                case "listeners":
                                    if (assignment.getAssignment() instanceof J.NewArray) {
                                        newValue = getMigratedListeners();
                                    }
                                    break;
                                case "inheritListeners":
                                    newValue = getMigratedInheritListeners();
                                    break;
                                case "mergeMode":
                                    newValue = getMigratedMergeMode();
                                    break;
                            }
                            if (newValue == null) {
                                return null;
                            }
                            return assignment.withAssignment(newValue);
                        } else if (arg instanceof J.NewArray) {
                            return getMigratedListeners();
                        }
                        if (arg instanceof J.FieldAccess && isTypeReference(arg, "com.github.database.rider.spring.DBRiderTestExecutionListener")) {
                            return null;
                        }
                        return arg;
                    }));
                }
            }

            return testExecutionListenerAnnotation;
        }

        // We can only remove an execution listener annotation if:
        // - InheritListeners was null or true
        // - MergeMode was TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
        // By default, the TestExecutionListeners.MergeMode is REPLACE_DEFAULTS so if we remove the annotation, other defaults would kick in.
        private boolean canTestExecutionListenerBeRemoved() {
            if (listener == null && listeners != null && listeners.getInitializer() != null && listeners.getInitializer().stream().allMatch(listener -> isTypeReference(listener, "com.github.database.rider.spring.DBRiderTestExecutionListener"))) {
                return (getMigratedInheritListeners() == null && getMigratedMergeMode() != null);
            }
            return false;
        }

        private @Nullable Expression getMigratedMergeMode() {
            if (mergeMode != null && mergeMode instanceof J.FieldAccess && "REPLACE_DEFAULTS".equals(((J.FieldAccess) mergeMode).getName().getSimpleName())) {
                return null;
            }
            return mergeMode;
        }

        private @Nullable Expression getMigratedInheritListeners() {
            if (inheritListeners != null && (inheritListeners instanceof J.Literal && Boolean.TRUE.equals(((J.Literal) inheritListeners).getValue()))) {
                return null;
            }
            return inheritListeners;
        }

        // Remove the DBRiderTestExecutionListener from the listeners array
        // If the listeners array is empty after removing the DBRiderTestExecutionListener, return null so that the array itself can be removed
        private J.@Nullable NewArray getMigratedListeners() {
            if (listeners != null && listeners.getInitializer() != null) {
                List<Expression> newListeners = ListUtils.map(listeners.getInitializer(), listener -> {
                    if (listener instanceof J.FieldAccess && isTypeReference(listener, "com.github.database.rider.spring.DBRiderTestExecutionListener")) {
                        return null;
                    }
                    return listener;
                });
                if (newListeners.isEmpty()) {
                    return null;
                }
                return listeners.withInitializer(newListeners);
            }
            return listeners;
        }

        private boolean isTestExecutionListenerForDbRider() {
            if (listener != null) {
                return isTypeReference(listener, "com.github.database.rider.spring.DBRiderTestExecutionListener");
            }
            if (listeners != null && listeners.getInitializer() != null) {
                return listeners.getInitializer().stream().anyMatch(listener -> isTypeReference(listener, "com.github.database.rider.spring.DBRiderTestExecutionListener"));
            }
            return false;
        }

        private static boolean isTypeReference(Expression expression, String type) {
            return expression.getType() instanceof JavaType.Parameterized &&
                    ((JavaType.Parameterized) expression.getType()).getFullyQualifiedName().equals("java.lang.Class") &&
                    ((JavaType.Parameterized) expression.getType()).getTypeParameters().size() == 1 &&
                    ((JavaType.Parameterized) expression.getType()).getTypeParameters().get(0) instanceof JavaType.Class &&
                    ((JavaType.Class) ((JavaType.Parameterized) expression.getType()).getTypeParameters().get(0)).getFullyQualifiedName().equals(type);
        }
    }
}
