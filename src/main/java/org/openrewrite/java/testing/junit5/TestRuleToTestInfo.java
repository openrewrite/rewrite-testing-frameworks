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
package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class TestRuleToTestInfo extends Recipe {

    @Override
    public String getDisplayName() {
        return "JUnit TestName @Rule to JUnit Jupiter TestInfo";
    }

    @Override
    public String getDescription() {
        return "Replace usages of JUnit 4's `@Rule TestName` with JUnit 5's TestInfo.";
    }

    @Override
    protected UsesType<ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.junit.rules.TestName", false);
    }

    @Override
    protected TestRuleToTestInfoVisitor getVisitor() {
        return new TestRuleToTestInfoVisitor();
    }

    private static class TestRuleToTestInfoVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final MethodMatcher TEST_NAME_GET_NAME = new MethodMatcher("org.junit.rules.TestName getMethodName()");
        private static final AnnotationMatcher RULE_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.Rule");
        private static final AnnotationMatcher JUNIT_BEFORE_MATCHER = new AnnotationMatcher("@org.junit.Before");
        private static final AnnotationMatcher JUPITER_BEFORE_EACH_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.BeforeEach");

        @Nullable
        private Supplier<JavaParser> javaParser;
        private Supplier<JavaParser> javaParser(ExecutionContext ctx) {
            if(javaParser == null) {
                javaParser = () -> JavaParser.fromJavaVersion()
                        .classpathFromResources(ctx, "junit-jupiter-api-5.9.3")
                        .build();
            }
            return javaParser;
        }

        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            J.CompilationUnit compilationUnit = super.visitCompilationUnit(cu, ctx);
            maybeRemoveImport("org.junit.Rule");
            maybeRemoveImport("org.junit.rules.TestName");
            maybeAddImport("org.junit.jupiter.api.TestInfo");
            doAfterVisit(new JavaVisitor<ExecutionContext>() {
                @Override
                public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                    J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                    if (TEST_NAME_GET_NAME.matches(mi) && mi.getSelect() != null) {
                        return mi.getSelect().withPrefix(Space.format(" "));
                    }
                    return mi;
                }
            });
            doAfterVisit(new ChangeType("org.junit.rules.TestName", "java.lang.String", true));
            doAfterVisit(new ChangeType("org.junit.Before", "org.junit.jupiter.api.BeforeEach", true));
            return compilationUnit;
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
            J.VariableDeclarations varDecls = super.visitVariableDeclarations(multiVariable, ctx);
            if (varDecls.getType() != null && TypeUtils.isOfClassType(varDecls.getType(), "org.junit.rules.TestName")) {
                varDecls = varDecls.withLeadingAnnotations(ListUtils.map(varDecls.getLeadingAnnotations(), anno -> {
                    if (RULE_ANNOTATION_MATCHER.matches(anno)) {
                        return null;
                    }
                    return anno;
                }));
                getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance).putMessage("has-testName-rule", varDecls);
            }
            return varDecls;
        }

        @Override
        public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
            J.NewClass nc = super.visitNewClass(newClass, ctx);
            if (TypeUtils.isOfClassType(nc.getType(), "org.junit.rules.TestName")) {
                //noinspection ConstantConditions
                return null;
            }
            return nc;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
            if (md.getLeadingAnnotations().stream().anyMatch(anno -> JUNIT_BEFORE_MATCHER.matches(anno) || JUPITER_BEFORE_EACH_MATCHER.matches(anno))) {
                getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance).putMessage("before-method", md);
            }
            return md;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
            J.VariableDeclarations varDecls = getCursor().pollMessage("has-testName-rule");
            J.MethodDeclaration beforeMethod = getCursor().pollMessage("before-method");
            if (varDecls != null) {
                String testMethodStatement = "Optional<Method> testMethod = testInfo.getTestMethod();\n" +
                        "if (testMethod.isPresent()) {\n" +
                        "    this.#{} = testMethod.get().getName();\n" +
                        "}";
                if (beforeMethod == null) {
                    String t = "@BeforeEach\n" +
                            "public void setup(TestInfo testInfo) {" + testMethodStatement + "}";
                    cd = cd.withTemplate(JavaTemplate.builder(this::getCursor, t)
                                    .javaParser(javaParser(ctx))
                                    .imports("org.junit.jupiter.api.TestInfo",
                                            "org.junit.jupiter.api.BeforeEach",
                                            "java.util.Optional",
                                            "java.lang.reflect.Method")
                                    .build(),
                            cd.getBody().getCoordinates().lastStatement(),
                            varDecls.getVariables().get(0).getName().getSimpleName());
                    maybeAddImport("java.lang.reflect.Method");
                    maybeAddImport("java.util.Optional");
                } else {
                    doAfterVisit(new BeforeMethodToTestInfoVisitor(beforeMethod, varDecls, testMethodStatement));
                }
            }
            return cd;
        }
    }

    private static class BeforeMethodToTestInfoVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final J.MethodDeclaration beforeMethod;
        private final J.VariableDeclarations varDecls;
        private final String testMethodStatement;

        @Nullable
        private Supplier<JavaParser> javaParser;
        private Supplier<JavaParser> javaParser(ExecutionContext ctx) {
            if(javaParser == null) {
                javaParser = () -> JavaParser.fromJavaVersion()
                        .classpathFromResources(ctx, "junit-jupiter-api-5.9.3")
                        .build();
            }
            return javaParser;
        }

        public BeforeMethodToTestInfoVisitor(J.MethodDeclaration beforeMethod, J.VariableDeclarations varDecls, String testMethodStatement) {
            this.beforeMethod = beforeMethod;
            this.varDecls = varDecls;
            this.testMethodStatement = testMethodStatement;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
            if (md.getId().equals(beforeMethod.getId())) {
                md = md.withTemplate(JavaTemplate.builder(this::getCursor, "TestInfo testInfo")
                                .javaParser(javaParser(ctx))
                                .imports("org.junit.jupiter.api.TestInfo",
                                        "org.junit.jupiter.api.BeforeEach",
                                        "java.util.Optional",
                                        "java.lang.reflect.Method")
                                .build(),
                        md.getCoordinates().replaceParameters());

                //noinspection ConstantConditions
                md = maybeAutoFormat(md, md.withTemplate(JavaTemplate.builder(this::getCursor, testMethodStatement)
                                .javaParser(javaParser(ctx))
                                .imports("org.junit.jupiter.api.TestInfo",
                                        "java.util.Optional",
                                        "java.lang.reflect.Method")
                                .build(),
                        md.getBody().getCoordinates().lastStatement(), varDecls.getVariables().get(0).getName().getSimpleName()), ctx, getCursor().getParent());

                // Make sure the testName is initialized first in case any other piece of the method body references it
                assert md.getBody() != null;
                if (md.getBody().getStatements().size() > 2) {
                    List<Statement> statements = md.getBody().getStatements();
                    List<Statement> reorderedStatements = new ArrayList<>(statements.size());
                    reorderedStatements.addAll(statements.subList(statements.size() - 2, statements.size()));
                    reorderedStatements.addAll(statements.subList(0, statements.size() - 2));
                    md = md.withBody(md.getBody().withStatements(reorderedStatements));
                }
                maybeAddImport("java.lang.reflect.Method");
                maybeAddImport("java.util.Optional");
            }
            return md;
        }
    }

  @Override
  public Duration getEstimatedEffortPerOccurrence() {
    return Duration.ofMinutes(5);
  }
}
