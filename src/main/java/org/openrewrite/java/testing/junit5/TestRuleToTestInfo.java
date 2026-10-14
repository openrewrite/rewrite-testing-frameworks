/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.testing.junit5;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.kotlin.KotlinTemplate;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

public class TestRuleToTestInfo extends Recipe {

    @Getter
    final String displayName = "JUnit TestName @Rule to JUnit Jupiter TestInfo";

    @Getter
    final String description = "Replace usages of JUnit 4's `@Rule TestName` with JUnit 5's TestInfo.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.junit.rules.TestName", false), new TestRuleToTestInfoVisitor());
    }

    private static class TestRuleToTestInfoVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final MethodMatcher TEST_NAME_GET_NAME = new MethodMatcher("org.junit.rules.TestName getMethodName()");
        private static final AnnotationMatcher RULE_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.Rule");
        private static final AnnotationMatcher JUNIT_BEFORE_MATCHER = new AnnotationMatcher("@org.junit.Before");
        private static final AnnotationMatcher JUPITER_BEFORE_EACH_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.BeforeEach");

        @Override
        public J preVisit(J tree, ExecutionContext ctx) {
            if (KotlinJUnit5Support.isKotlin(tree)) {
                scheduleUpdates();
            }
            return tree;
        }

        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            J.CompilationUnit compilationUnit = super.visitCompilationUnit(cu, ctx);
            scheduleUpdates();
            return compilationUnit;
        }

        private void scheduleUpdates() {
            maybeRemoveImport("org.junit.Rule");
            maybeRemoveImport("org.junit.rules.TestName");
            maybeAddImport("org.junit.jupiter.api.TestInfo");
            doAfterVisit(new JavaVisitor<ExecutionContext>() {
                @Override
                public J visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                    J.FieldAccess access = (J.FieldAccess) super.visitFieldAccess(fieldAccess, ctx);
                    if (KotlinJUnit5Support.isKotlin(getCursor()) &&
                            "methodName".equals(access.getSimpleName()) &&
                            TypeUtils.isOfClassType(access.getTarget().getType(), "org.junit.rules.TestName")) {
                        return access.getTarget().withPrefix(access.getPrefix());
                    }
                    return access;
                }

                @Override
                public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                    J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                    if (TEST_NAME_GET_NAME.matches(mi) && mi.getSelect() != null) {
                        return mi.getSelect().withPrefix(Space.format(" "));
                    }
                    return mi;
                }
            });
            doAfterVisit(new ChangeType("org.junit.rules.TestName", "java.lang.String", true).getVisitor());
            doAfterVisit(new ChangeType("org.junit.Before", "org.junit.jupiter.api.BeforeEach", true).getVisitor());
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
            J.VariableDeclarations varDecls = super.visitVariableDeclarations(multiVariable, ctx);
            if (varDecls.getVariables().stream().anyMatch(variable ->
                    TypeUtils.isOfClassType(variable.getType(), "org.junit.rules.TestName"))) {
                varDecls = varDecls.withLeadingAnnotations(ListUtils.map(varDecls.getLeadingAnnotations(), anno -> {
                    if (RULE_ANNOTATION_MATCHER.matches(anno)) {
                        return null;
                    }
                    return anno;
                }));
                if (KotlinJUnit5Support.isKotlin(getCursor())) {
                    varDecls = varDecls.withLeadingAnnotations(ListUtils.map(varDecls.getLeadingAnnotations(),
                                    anno -> "JvmField".equals(anno.getSimpleName()) ? null : anno))
                            .withModifiers(ListUtils.map(varDecls.getModifiers(), modifier ->
                                    modifier.getType() == J.Modifier.Type.Final ?
                                            modifier.withType(J.Modifier.Type.LanguageExtension).withKeyword("var") : modifier))
                            .withTypeExpression(TypeTree.build("String").withType(JavaType.Primitive.String).withPrefix(Space.format(" ")));
                    varDecls = varDecls.withModifiers(ListUtils.concat(new J.Modifier(randomId(), Space.EMPTY, Markers.EMPTY,
                            "lateinit", J.Modifier.Type.LanguageExtension, emptyList()),
                            ListUtils.map(varDecls.getModifiers(), modifier -> modifier.withPrefix(Space.SINGLE_SPACE))));
                } else {
                    varDecls = removeFinalModifier(varDecls);
                }
                getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance).putMessage("has-testName-rule", varDecls);
            }
            return varDecls;
        }

        private static J.VariableDeclarations removeFinalModifier(J.VariableDeclarations varDecls) {
            List<J.Modifier> modifiers = varDecls.getModifiers();
            List<J.Modifier> retained = ListUtils.filter(modifiers, mod -> mod.getType() != J.Modifier.Type.Final);
            if (retained == modifiers) {
                return varDecls;
            }
            Space prefix = modifiers.get(0).getPrefix();
            if (retained.isEmpty()) {
                return varDecls.withModifiers(retained).withTypeExpression(
                        varDecls.getTypeExpression() == null ? null : varDecls.getTypeExpression().withPrefix(prefix));
            }
            return varDecls.withModifiers(ListUtils.mapFirst(retained, mod -> mod.withPrefix(prefix)));
        }

        @Override
        public  J.@Nullable NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
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
                if (KotlinJUnit5Support.isKotlin(getCursor())) {
                    if (beforeMethod == null) {
                        cd = KotlinTemplate.builder("@BeforeEach\nfun setup(testInfo: TestInfo) {}")
                                .parser(KotlinParser.builder().classpathFromResources(ctx, "junit-jupiter-api-5"))
                                .imports("org.junit.jupiter.api.BeforeEach", "org.junit.jupiter.api.TestInfo")
                                .build()
                                .apply(updateCursor(cd), cd.getBody().getCoordinates().lastStatement());
                        beforeMethod = (J.MethodDeclaration) cd.getBody().getStatements().get(cd.getBody().getStatements().size() - 1);
                        maybeAddImport("org.junit.jupiter.api.BeforeEach");
                    }
                    doAfterVisit(new BeforeMethodToTestInfoVisitor(beforeMethod, varDecls, ""));
                    return maybeAutoFormat(classDecl, cd, ctx);
                }
                String testMethodStatement = "Optional<Method> testMethod = testInfo.getTestMethod();\n" +
                                             "if (testMethod.isPresent()) {\n" +
                                             "    this.#{} = testMethod.get().getName();\n" +
                                             "}";
                if (beforeMethod == null) {
                    String t = "@BeforeEach\n" +
                               "public void setup(TestInfo testInfo) {" + testMethodStatement + "}";
                    cd = JavaTemplate.builder(t)
                            .contextSensitive()
                            .javaParser(JavaParser.fromJavaVersion()
                                    .classpathFromResources(ctx, "junit-jupiter-api-5"))
                            .imports("org.junit.jupiter.api.TestInfo",
                                    "org.junit.jupiter.api.BeforeEach",
                                    "java.util.Optional",
                                    "java.lang.reflect.Method")
                            .build()
                            .apply(
                                    updateCursor(cd),
                                    cd.getBody().getCoordinates().lastStatement(),
                                    varDecls.getVariables().get(0).getName().getSimpleName()
                            );
                    maybeAddImport("java.lang.reflect.Method");
                    maybeAddImport("java.util.Optional");
                    maybeAddImport("org.junit.jupiter.api.BeforeEach");
                } else {
                    doAfterVisit(new BeforeMethodToTestInfoVisitor(beforeMethod, varDecls, testMethodStatement));
                }
            }
            return cd;
        }
    }

    @RequiredArgsConstructor
    private static class BeforeMethodToTestInfoVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final J.MethodDeclaration beforeMethod;
        private final J.VariableDeclarations varDecls;
        private final String testMethodStatement;

        private JavaParser.Builder<?, ?> javaParser(ExecutionContext ctx) {
                return JavaParser.fromJavaVersion()
                        .classpathFromResources(ctx, "junit-jupiter-api-5");
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
            if (md.getId().equals(beforeMethod.getId())) {
                if (KotlinJUnit5Support.isKotlin(getCursor())) {
                    if (md.getParameters().stream().noneMatch(parameter -> parameter instanceof J.VariableDeclarations &&
                            TypeUtils.isOfClassType(((J.VariableDeclarations) parameter).getType(), "org.junit.jupiter.api.TestInfo"))) {
                        md = KotlinTemplate.builder("testInfo: TestInfo").imports("org.junit.jupiter.api.TestInfo")
                                .parser(KotlinParser.builder().classpathFromResources(ctx, "junit-jupiter-api-5"))
                                .build().apply(updateCursor(md), md.getCoordinates().replaceParameters());
                        if (md.getMethodType() != null) {
                            md = md.withMethodType(md.getMethodType().withParameterNames(singletonList("testInfo"))
                                    .withParameterTypes(singletonList(JavaType.ShallowClass.build("org.junit.jupiter.api.TestInfo"))));
                            md = md.withName(md.getName().withType(md.getMethodType()));
                        }
                    }
                    assert md.getBody() != null;
                    J.Identifier info = ((J.VariableDeclarations) md.getParameters().get(0)).getVariables().get(0)
                            .getName().withPrefix(Space.EMPTY);
                    J.Block body = KotlinTemplate.builder("#{any(org.junit.jupiter.api.TestInfo)}.testMethod.get().name")
                            .parser(KotlinParser.builder().classpathFromResources(ctx, "junit-jupiter-api-5"))
                            .build().apply(new org.openrewrite.Cursor(updateCursor(md), md.getBody()),
                                    md.getBody().getCoordinates().firstStatement(), info);
                    Expression name = (Expression) body.getStatements().get(0);
                    J.Identifier field = varDecls.getVariables().get(0).getName().withPrefix(Space.EMPTY)
                            .withType(JavaType.Primitive.String);
                    J.FieldAccess target = new J.FieldAccess(randomId(), Space.EMPTY, Markers.EMPTY,
                            new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), "this", null, null),
                            new JLeftPadded<>(Space.EMPTY, field, Markers.EMPTY), JavaType.Primitive.String);
                    J.Assignment assignment = new J.Assignment(randomId(), name.getPrefix(), Markers.EMPTY, target,
                            new JLeftPadded<>(Space.format(" "), name.withPrefix(Space.format(" ")), Markers.EMPTY),
                            JavaType.Primitive.String);
                    body = body.withStatements(ListUtils.mapFirst(body.getStatements(), statement -> assignment));
                    return maybeAutoFormat(method, md.withBody(body), ctx);
                }
                md = JavaTemplate.builder("TestInfo testInfo")
                        .contextSensitive()
                        .javaParser(javaParser(ctx))
                        .imports("org.junit.jupiter.api.TestInfo",
                                "org.junit.jupiter.api.BeforeEach",
                                "java.util.Optional",
                                "java.lang.reflect.Method")
                        .build()
                        .apply(updateCursor(md), md.getCoordinates().replaceParameters());

                //noinspection ConstantConditions
                md = maybeAutoFormat(
                        md,
                        JavaTemplate.builder(testMethodStatement)
                                .contextSensitive()
                                .javaParser(javaParser(ctx))
                                .imports("org.junit.jupiter.api.TestInfo",
                                        "java.util.Optional",
                                        "java.lang.reflect.Method")
                                .build()
                                .apply(
                                        updateCursor(md),
                                        md.getBody().getCoordinates().lastStatement(),
                                        varDecls.getVariables().get(0).getName().getSimpleName()),
                        ctx,
                        getCursor().getParent()
                );

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
}
