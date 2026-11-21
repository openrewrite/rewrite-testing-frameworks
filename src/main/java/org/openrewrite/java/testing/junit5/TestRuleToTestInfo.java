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
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;

public class TestRuleToTestInfo extends Recipe {

    @Getter
    final String displayName = "JUnit `TestName` `@Rule` to JUnit Jupiter `TestInfo`";

    @Getter
    final String description = "Replace usages of JUnit 4's `@Rule TestName` with JUnit 5's `TestInfo`.";

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
                        return mi.getSelect().withPrefix(mi.getPrefix());
                    }
                    return mi;
                }
            });
            doAfterVisit(new ChangeType("org.junit.rules.TestName", "java.lang.String", true).getVisitor());
            return compilationUnit;
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
            J.VariableDeclarations varDecls = super.visitVariableDeclarations(multiVariable, ctx);
            if (varDecls.getType() != null && TypeUtils.isOfClassType(varDecls.getType(), "org.junit.rules.TestName")) {
                varDecls = varDecls.withLeadingAnnotations(ListUtils.filter(varDecls.getLeadingAnnotations(), anno ->
                        !RULE_ANNOTATION_MATCHER.matches(anno)));
                varDecls = removeFinalModifier(varDecls);
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
                    cd = typeThisFieldAccess(cd, varDecls.getVariables().get(0).getSimpleName(), cd.getType());
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

    /**
     * The template cannot see the field being assigned, so the {@code this.name} it produces has an unknown type.
     */
    private static <T extends J> T typeThisFieldAccess(T tree, String fieldName, JavaType.@Nullable FullyQualified owner) {
        JavaType.Variable fieldType = new JavaType.Variable(null, Flag.Public.getBitMask(), fieldName, owner,
                JavaType.ShallowClass.build("java.lang.String"), emptyList());
        //noinspection unchecked
        return (T) new JavaIsoVisitor<Integer>() {
            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, Integer p) {
                J.FieldAccess fa = super.visitFieldAccess(fieldAccess, p);
                if ((fa.getType() == null || fa.getType() instanceof JavaType.Unknown) && fieldName.equals(fa.getSimpleName()) &&
                    fa.getTarget() instanceof J.Identifier && "this".equals(((J.Identifier) fa.getTarget()).getSimpleName())) {
                    return fa.withName(fa.getName().withType(fieldType.getType()).withFieldType(fieldType))
                            .withType(fieldType.getType());
                }
                return fa;
            }
        }.visitNonNull(tree, 0);
    }

    @RequiredArgsConstructor
    private static class BeforeMethodToTestInfoVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final AnnotationMatcher JUNIT_BEFORE_MATCHER = new AnnotationMatcher("@org.junit.Before");
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
                String testInfoName = "testInfo";
                J.VariableDeclarations existing = null;
                List<Statement> params = md.getParameters();
                for (Statement param : params) {
                    if (param instanceof J.VariableDeclarations &&
                        TypeUtils.isOfClassType(((J.VariableDeclarations) param).getType(), "org.junit.jupiter.api.TestInfo")) {
                        existing = (J.VariableDeclarations) param;
                    }
                }
                if (existing != null) {
                    testInfoName = existing.getVariables().get(0).getSimpleName();
                } else {
                    J.MethodDeclaration withTestInfo = JavaTemplate.builder("TestInfo testInfo")
                            .contextSensitive()
                            .javaParser(javaParser(ctx))
                            .imports("org.junit.jupiter.api.TestInfo")
                            .build()
                            .apply(updateCursor(md), md.getCoordinates().replaceParameters());
                    if (params.isEmpty() || params.get(0) instanceof J.Empty) {
                        md = withTestInfo;
                    } else {
                        Statement testInfoParam = withTestInfo.getParameters().get(0).withPrefix(Space.SINGLE_SPACE);
                        md = md.withParameters(ListUtils.concat(params, testInfoParam));
                        updateCursor(md);
                    }
                }
                md = renameBeforeToBeforeEach(md);

                String finalTestInfoName = testInfoName;
                md = maybeAutoFormat(
                        md,
                        JavaTemplate.builder(testMethodStatement.replace("testInfo.", finalTestInfoName + "."))
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

                J.ClassDeclaration enclosingClass = getCursor().firstEnclosing(J.ClassDeclaration.class);
                md = typeThisFieldAccess(md, varDecls.getVariables().get(0).getSimpleName(),
                        enclosingClass == null ? null : enclosingClass.getType());

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

        private J.MethodDeclaration renameBeforeToBeforeEach(J.MethodDeclaration md) {
            return md.withLeadingAnnotations(ListUtils.map(md.getLeadingAnnotations(), anno -> {
                if (!JUNIT_BEFORE_MATCHER.matches(anno)) {
                    return anno;
                }
                maybeRemoveImport("org.junit.Before");
                maybeAddImport("org.junit.jupiter.api.BeforeEach");
                JavaType.ShallowClass beforeEach = JavaType.ShallowClass.build("org.junit.jupiter.api.BeforeEach");
                J.Identifier name = new J.Identifier(Tree.randomId(), anno.getAnnotationType().getPrefix(), Markers.EMPTY,
                        emptyList(), "BeforeEach", beforeEach, null);
                return anno.withType(beforeEach).withAnnotationType(name);
            }));
        }
    }
}
