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
package org.openrewrite.java.testing.mockito;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.openrewrite.java.VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER;
import static org.openrewrite.java.VariableNameUtils.generateVariableName;
import static org.openrewrite.java.tree.Flag.Static;

public class MockitoWhenOnStaticToMockStatic extends Recipe {

    private static final AnnotationMatcher BEFORE = new AnnotationMatcher("org.junit.Before");
    private static final AnnotationMatcher AFTER = new AnnotationMatcher("org.junit.After");
    private static final MethodMatcher MOCKITO_WHEN = new MethodMatcher("org.mockito.Mockito when(..)");

    private int varCounter = 0;

    @Override
    public String getDisplayName() {
        return "Replace `Mockito.when` on static (non mock) with try-with-resource with MockedStatic";
    }

    @Override
    public String getDescription() {
        return "Replace `Mockito.when` on static (non mock) with try-with-resource with MockedStatic as Mockito4 no longer allows this." +
                "This change moves away from implicit bytecode manipulation for static method stubbing, making mocking behavior more explicit and scoped to avoid unintended side effects.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(MOCKITO_WHEN), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitBlock(J.Block block, ExecutionContext ctx) {
                List<Statement> newStatements = isMethodDeclarationWithAnnotation(getCursor().firstEnclosing(J.MethodDeclaration.class), BEFORE) ?
                        maybeStatementsToMockedStatic(block, block.getStatements(), ctx) :
                        maybeWrapStatementsInTryWithResourcesMockedStatic(block, block.getStatements(), ctx);

                J.Block b = (J.Block) super.visitBlock(block.withStatements(newStatements), ctx);

                return maybeAutoFormat(block, b, ctx);
            }

            private List<Statement> maybeStatementsToMockedStatic(J.Block m, List<Statement> statements, ExecutionContext ctx) {
                List<Statement> list = new ArrayList<>();
                for (Statement statement : statements) {
                    J.MethodInvocation whenArg = getWhenArg(statement);
                    if (whenArg != null) {
                        String className = getClassName(whenArg);
                        if (className != null) {
                            list.addAll(mockedStatic(m, (J.MethodInvocation) statement, className, whenArg, ctx));
                        }
                    } else {
                        list.add(statement);
                    }
                }
                return list;
            }

            private List<Statement> maybeWrapStatementsInTryWithResourcesMockedStatic(J.Block block, List<Statement> statements, ExecutionContext ctx) {
                AtomicBoolean restInTry = new AtomicBoolean(false);
                return ListUtils.map(statements, (index, statement) -> {
                    if (restInTry.get()) {
                        // Rest of the statements have ended up in the try block
                        return null;
                    }

                    J.MethodInvocation whenArg = getWhenArg(statement);
                    if (whenArg != null) {
                        String className = getClassName(whenArg);
                        if (className != null) {
                            restInTry.set(true);
                            return tryWithMockedStatic(block, statements, index, (J.MethodInvocation) statement, className, whenArg, ctx);
                        }
                    }
                    return statement;
                });
            }

            private J.@Nullable MethodInvocation getWhenArg(Statement statement) {
                if (statement instanceof J.MethodInvocation && MOCKITO_WHEN.matches(((J.MethodInvocation) statement).getSelect())) {
                    J.MethodInvocation when = (J.MethodInvocation) ((J.MethodInvocation) statement).getSelect();
                    if (when != null && when.getArguments().get(0) instanceof J.MethodInvocation) {
                        J.MethodInvocation whenArg = (J.MethodInvocation) when.getArguments().get(0);
                        if (whenArg.getMethodType() != null && whenArg.getMethodType().hasFlags(Static)) {
                            return whenArg;
                        }
                    }
                }
                return null;
            }

            private @Nullable String getClassName(J.MethodInvocation whenArg) {
                J.Identifier clazz = null;
                if (whenArg.getSelect() instanceof J.Identifier) {
                    clazz = (J.Identifier) whenArg.getSelect();
                } else if (whenArg.getSelect() instanceof J.FieldAccess && ((J.FieldAccess) whenArg.getSelect()).getTarget() instanceof J.Identifier) {
                    clazz = (J.Identifier) ((J.FieldAccess) whenArg.getSelect()).getTarget();
                }
                return clazz != null && clazz.getType() != null ? clazz.getSimpleName() : null;
            }


            private J.Try tryWithMockedStatic(J.Block block, List<Statement> statements, Integer index,
                                              J.MethodInvocation statement, String className, J.MethodInvocation whenArg, ExecutionContext ctx) {
                String variableName = generateVariableName("mock" + className + ++varCounter, updateCursor(block), INCREMENT_NUMBER);
                Expression thenReturnArg = statement.getArguments().get(0);

                J.Try try_ = (J.Try) javaTemplateMockStatic(String.format(
                        "try(MockedStatic<%1$s> %2$s = mockStatic(%1$s.class)) {\n" +
                                "    %2$s.when(() -> #{any()}).thenReturn(#{any()});\n" +
                                "}", className, variableName), ctx)
                        .<J.Block>apply(getCursor(), block.getCoordinates().firstStatement(), whenArg, thenReturnArg)
                        .getStatements().get(0);

                List<Statement> precedingStatements = statements.subList(0, index);
                List<Statement> handledStatements = ListUtils.concat(precedingStatements, try_);
                List<Statement> remainingStatements = statements.subList(index + 1, statements.size());

                List<Statement> newStatements = ListUtils.concatAll(
                        try_.getBody().getStatements(),
                        maybeWrapStatementsInTryWithResourcesMockedStatic(block.withStatements(handledStatements), remainingStatements, ctx));

                return try_.withBody(try_.getBody().withStatements(newStatements))
                        .withPrefix(statement.getPrefix());
            }

            private List<Statement> mockedStatic(J.Block block, J.MethodInvocation statement,  String className, J.MethodInvocation whenArg, ExecutionContext ctx) {
                String variableName = generateVariableName("mock" + className + ++varCounter, updateCursor(block), INCREMENT_NUMBER);
                Expression thenReturnArg = statement.getArguments().get(0);

                List<Statement> statements = javaTemplateMockStatic(String.format(
                        "%2$s = mockStatic(%1$s.class);\n" +
                                "%2$s.when(() -> #{any()}).thenReturn(#{any()});", className, variableName), ctx)
                        .<J.Block>apply(getCursor(), block.getCoordinates().firstStatement(), whenArg, thenReturnArg)
                        .getStatements().subList(0, 2);

                doAfterVisit(new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration after = JavaTemplate.builder(String.format("private MockedStatic<%1$s> %2$s;", className, variableName))
                                .contextSensitive()
                                .build()
                                .apply(updateCursor(classDecl), classDecl.getBody().getCoordinates().firstStatement());

                        if (classDecl.getBody().getStatements().stream().noneMatch(it -> isMethodDeclarationWithAnnotation(it, AFTER))) {
                            Optional<Statement> beforeMethod = after.getBody().getStatements().stream()
                                    .filter(it -> isMethodDeclarationWithAnnotation(it, BEFORE))
                                    .findFirst();
                            if (beforeMethod.isPresent()) {
                                maybeAddImport("org.junit.After");
                                after = JavaTemplate.builder("@After public void tearDown() {}")
                                        .imports("org.junit.After")
                                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-4"))
                                        .build()
                                        .apply(updateCursor(after), beforeMethod.get().getCoordinates().after());
                            }
                        }

                        J.ClassDeclaration cd = super.visitClassDeclaration(after, ctx);
                        return maybeAutoFormat(classDecl, cd, ctx);
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDecl, ExecutionContext ctx) {
                        J.MethodDeclaration md = super.visitMethodDeclaration(methodDecl, ctx);

                        if (isMethodDeclarationWithAnnotation(md, AFTER)) {
                            return JavaTemplate.builder(variableName + ".close();")
                                    .contextSensitive()
                                    .build()
                                    .apply(getCursor(), md.getBody().getCoordinates().lastStatement());
                        }

                        return md;
                    }
                });

                return statements;
            }

            private JavaTemplate javaTemplateMockStatic(String code, ExecutionContext ctx) {
                maybeAddImport("org.mockito.MockedStatic", false);
                maybeAddImport("org.mockito.Mockito", "mockStatic");
                return JavaTemplate.builder(code)
                        .contextSensitive()
                        .imports("org.mockito.MockedStatic")
                        .staticImports("org.mockito.Mockito.mockStatic")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-5"))
                        .build();
            }
        });
    }

    private static boolean isMethodDeclarationWithAnnotation(@Nullable Statement statement, AnnotationMatcher matcher) {
        if (statement instanceof J.MethodDeclaration) {
            return ((J.MethodDeclaration) statement).getLeadingAnnotations().stream().anyMatch(matcher::matches);
        }
        return false;
    }
}
