/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.testing.mockito;

import java.util.List;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.tree.*;

public class PowerMockitoMockStaticToMockito extends Recipe {

    public static final String POWER_MOCK_CONFIG = "org.powermock.configuration.PowerMockConfiguration";
    public static final String MOCKED_STATIC = "org.mockito.MockedStatic";

    @Override
    public String getDisplayName() {
        return "Replace PowerMock mockStatic with Mockito mockStatic";
    }

    @Override
    public String getDescription() {
        return "Replaces `PowerMockito.mockStatic()` by `Mockito.mockStatic()`. Removes superclasses extending " +
                "`PowerMockConfiguration` and the `@PrepareForTest` annotation.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            public static final String PREPARE_FOR_TEST = "org.powermock.core.classloader.annotations.PrepareForTest";

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext executionContext) {
                // Remove the @PrepareForTest annotation
                if (annotation.getType() != null && TypeUtils.isOfClassType(annotation.getType(), PREPARE_FOR_TEST)) {
                    maybeRemoveImport(PREPARE_FOR_TEST);
                    // Pass the annotation to the class declaration, in order to add their arguments as fields
                    getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, "PrepareForTestAnnotation", annotation);
                    //noinspection DataFlowIssue
                    return null;
                }
                return super.visitAnnotation(annotation, executionContext);
            }

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                System.out.println("------visitCompilationUnit LST Tree Start------");
                System.out.println(TreeVisitingPrinter.printTree(cu));
                System.out.println("------visitCompilationUnit LST Tree End------");
                return super.visitCompilationUnit(cu, executionContext);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                // Remove the extension of class PowerMockConfiguration
                if (cd.getExtends() != null && TypeUtils.isAssignableTo(POWER_MOCK_CONFIG, cd.getExtends().getType())) {
                    cd = cd.withExtends(null);
                    maybeRemoveImport(POWER_MOCK_CONFIG);
                }

                // Add the classes of the arguments in the annotation @PrepareForTest as fields
                // e.g. @PrepareForTest(Calendar.class)
                // becomes
                // private MockStatic mockedCalendar = mockStatic(Calendar.class)
                J.Annotation prepareForTest = getCursor().pollMessage("PrepareForTestAnnotation");
                if (prepareForTest != null && prepareForTest.getArguments() != null) {
                    List<Expression> mockTypes = ListUtils.flatMap(prepareForTest.getArguments(), a -> {
                        if (a instanceof J.NewArray && ((J.NewArray) a).getInitializer() != null) {
                            return ((J.NewArray) a).getInitializer();
                        } else if (a instanceof J.Assignment && ((J.NewArray) ((J.Assignment) a).getAssignment()).getInitializer() != null) {
                            return ((J.NewArray) ((J.Assignment) a).getAssignment()).getInitializer();

                        }
                        return null;
                    });
                    for (Expression mockType : mockTypes) {
                        String typeName = mockType.toString();
                        String classlessTypeName = removeDotClass(typeName);
                        cd = cd.withBody(cd.getBody()
                                .withTemplate(
                                        JavaTemplate.builder(() -> getCursor().getParentTreeCursor(),
                                                        "private MockedStatic<#{}> mocked#{} = mockStatic(#{});")
                                                .javaParser(() -> JavaParser.fromJavaVersion()
                                                        .classpathFromResources(ctx, "mockito-core-3.12.4")
                                                        .build())
                                                .staticImports("org.mockito.Mockito.mockStatic")
                                                .imports(MOCKED_STATIC)
                                                .build(),
                                        cd.getBody().getCoordinates().firstStatement(),
                                        classlessTypeName,
                                        classlessTypeName,
                                        typeName
                                )
                        );
                    }
                    cd = maybeAutoFormat(classDecl, cd.withPrefix(cd.getPrefix().withWhitespace("")), cd.getName(), ctx, getCursor());
                    maybeAddImport(MOCKED_STATIC);
                    maybeAddImport("org.mockito.Mockito", "mockStatic");
                }
                return cd;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, executionContext);
                if (TypeUtils.isOfClassType(mi.getType(), MOCKED_STATIC)) {
                    Cursor declaringScope = getCursor().dropParentUntil(it -> it instanceof J.ClassDeclaration
                            || it instanceof J.MethodDeclaration || it == Cursor.ROOT_VALUE);
                    if (declaringScope.getValue() instanceof J.MethodDeclaration) {
                        //noinspection DataFlowIssue
                        return null;
                    }
                }
                return mi;
            }
        };
    }

    private static String removeDotClass(String s) {
        return s.endsWith(".class") ? s.substring(0, s.length() - 6) : s;
    }
}
