/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestRuleToTestInfo extends Recipe {

    private static final String testNameType = "org.junit.rules.TestName";
    private static final MethodMatcher TEST_NAME_GET_NAME = new MethodMatcher(testNameType + " getMethodName()");

    private static final ThreadLocal<JavaParser> TEST_INFO_PARSER = ThreadLocal.withInitial(() ->
            JavaParser.fromJavaVersion().dependsOn(
                    Stream.of(
                            Parser.Input.fromString(
                                    "package org.junit.jupiter.api;\n" +
                                            "import java.lang.reflect.Method;\n" +
                                            "import java.util.Optional;\n" +
                                            "import java.util.Set;\n" +
                                            "public interface TestInfo {\n" +
                                            "  String getDisplayName();\n" +
                                            "  Set<String> getTags();\n" +
                                            "  Optional<Class<?>> getTestClass();\n" +
                                            "  Optional<Method> getTestMethod();" +
                                            "}")).collect(Collectors.toList())
            ).build());

    @Override
    public String getDisplayName() {
        return "JUnit TestName @Rule to JUnit Jupiter TestInfo";
    }

    @Override
    public String getDescription() {
        return "Replace usages of JUnit 4's `@Rule TestName` with JUnit 5's TestInfo.";
    }

    @Override
    protected @Nullable TreeVisitor<?, ExecutionContext> getApplicableTest() {
        return new UsesType<>(testNameType);
    }

    @Override
    protected @Nullable TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>(testNameType);
    }

    @Override
    protected JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                J.CompilationUnit compilationUnit = super.visitCompilationUnit(cu, executionContext);
                maybeRemoveImport("org.junit.Rule");
                maybeRemoveImport(testNameType);
                maybeAddImport("org.junit.jupiter.api.TestInfo");
                doAfterVisit(new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                        J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, executionContext);
                        if (TEST_NAME_GET_NAME.matches(mi) && mi.getSelect() != null) {
                            return mi.getSelect().withPrefix(Space.format(" "));
                        }
                        return mi;
                    }
                });
                doAfterVisit(new ChangeType(testNameType, "String"));
                return compilationUnit;
            }

            @Override
            public J.Import visitImport(J.Import _import, ExecutionContext executionContext) {
                J.Import imp = super.visitImport(_import, executionContext);
                if (imp.getTypeName().equals(testNameType)) {
                    //noinspection ConstantConditions
                    return null;
                }
                return imp;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext executionContext) {
                J.VariableDeclarations varDecls = super.visitVariableDeclarations(multiVariable, executionContext);
                if (varDecls.getType() != null && TypeUtils.isOfClassType(varDecls.getType(), testNameType)) {
                    varDecls = varDecls.withLeadingAnnotations(new ArrayList<>());
                    //noinspection ConstantConditions
                    doAfterVisit(new AddBeforeEachMethod(varDecls, getCursor().firstEnclosing(J.ClassDeclaration.class)));
                }
                return varDecls;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext executionContext) {
                J.NewClass nc = super.visitNewClass(newClass, executionContext);
                if (TypeUtils.isOfClassType(nc.getType(), testNameType)) {
                    //noinspection ConstantConditions
                    return null;
                }
                return nc;
            }

            //FIXME. add TestMethod statements.
//            @Override
//            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
//                J.MethodDeclaration md = super.visitMethodDeclaration(method, executionContext);
//                return md;
//            }

            private boolean isBeforeAnnotation(J.Annotation annotation) {
                return TypeUtils.isOfClassType(annotation.getType(), "org.junit.Before") || TypeUtils.isOfClassType(annotation.getType(), "org.junit.jupiter.api.BeforeEach");
            }
        };
    }

    private static class AddBeforeEachMethod extends JavaIsoVisitor<ExecutionContext> {
        private final J.VariableDeclarations varDecls;
        private final J.ClassDeclaration enclosingClass;

        public AddBeforeEachMethod(J.VariableDeclarations varDecls, J.ClassDeclaration enclosingClass) {
            this.varDecls = varDecls;
            this.enclosingClass = enclosingClass;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);
            if (enclosingClass.getId().equals(cd.getId())) {
                String t = "@BeforeEach\n" +
                        "public void setup(TestInfo testInfo) {\n" +
                        "   Optional<Method> testMethod = testInfo.getTestMethod();\n" +
                        "   if (testMethod.isPresent()) {\n" +
                        "        this.#{} = testMethod.get().getName();\n" +
                        "   }\n" +
                        "}";
                cd = cd.withTemplate(JavaTemplate.builder(this::getCursor, t).javaParser(TEST_INFO_PARSER::get)
                                .imports("org.junit.jupiter.api.TestInfo", "java.util.Optional", "java.lang.reflect.Method")
                                .build(),
                        cd.getBody().getCoordinates().lastStatement(),
                        varDecls.getVariables().get(0).getName().getSimpleName());
            }
            return cd;
        }
    }
}
