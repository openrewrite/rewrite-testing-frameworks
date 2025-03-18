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
import org.openrewrite.*;
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
            /*@Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = (J.MethodDeclaration) super.visitMethodDeclaration(method, ctx);
                if (md.getBody() == null) {
                    return md;
                }

                List<Statement> newStatements = isMethodDeclarationWithAnnotation(md, BEFORE) ?
                        maybeStatementsToMockedStatic(md, md.getBody().getStatements(), ctx) :
                        maybeWrapStatementsInTryWithResourcesMockedStatic(md, md.getBody().getStatements(), ctx);

                return maybeAutoFormat(md, md.withBody(md.getBody().withStatements(newStatements)), ctx);
            }*/

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);

                J.MethodInvocation whenArg = getWhenArg(mi);
                if (whenArg != null) {
                    Cursor currCursor = getCursor().dropParentUntil(is -> is instanceof J.Block);
                    String className = getClassName(whenArg);
                    if (className != null) {
                        return tryWithMockedStatic2(currCursor, mi, className, whenArg, ctx);
                    }
                }
                return mi;
            }

            private List<Statement> maybeStatementsToMockedStatic(J.MethodDeclaration m, List<Statement> statements, ExecutionContext ctx) {
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

            private List<Statement> maybeWrapStatementsInTryWithResourcesMockedStatic(J.MethodDeclaration m, List<Statement> statements, ExecutionContext ctx) {
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
                            return tryWithMockedStatic(m, statements, index, (J.MethodInvocation) statement, className, whenArg, ctx);
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

            private J.Try tryWithMockedStatic(J.MethodDeclaration m, List<Statement> statements, Integer index,
                    J.MethodInvocation statement, String className, J.MethodInvocation whenArg, ExecutionContext ctx) {
                Expression thenReturnArg = statement.getArguments().get(0);

                J.MethodDeclaration md = javaTemplateMockStatic(String.format(
                        "try(MockedStatic<%1$s> %2$s = mockStatic(%1$s.class)) {\n" +
                        "    %2$s.when(() -> #{any()}).thenReturn(#{any()});\n" +
                        "}", className, generateVariableName("mock" + className, updateCursor(m), INCREMENT_NUMBER)), ctx)
                        .apply(getCursor(), m.getCoordinates().replaceBody(), whenArg, thenReturnArg);
                J.Try try_ = (J.Try) md.getBody().getStatements().get(0);

                List<Statement> precedingStatements = statements.subList(0, index);
                List<Statement> handledStatements = ListUtils.concat(precedingStatements, try_);
                List<Statement> remainingStatements = statements.subList(index + 1, statements.size());

                List<Statement> newStatements = ListUtils.concatAll(
                        try_.getBody().getStatements(),
                        maybeWrapStatementsInTryWithResourcesMockedStatic(m.withBody(m.getBody().withStatements(handledStatements)), remainingStatements, ctx));

                return try_.withBody(try_.getBody().withStatements(newStatements))
                        .withPrefix(statement.getPrefix());
            }

            private J.Try tryWithMockedStatic2(Cursor blockCursor, J.MethodInvocation mi, String className, J.MethodInvocation whenArg, ExecutionContext ctx) {
                J.Block block = blockCursor.getValue();
                Expression thenReturnArg = mi.getArguments().get(0);

                J.Block temp = javaTemplateMockStatic(String.format(
                        "try(MockedStatic<%1$s> %2$s = mockStatic(%1$s.class)) {\n" +
                                "    %2$s.when(() -> #{any()}).thenReturn(#{any()});\n" +
                                "}", className, generateVariableName("mock" + className, getCursor(), INCREMENT_NUMBER)), ctx)
                        .apply(blockCursor, block.getCoordinates().firstStatement(), whenArg, thenReturnArg);
                J.Try try_ = (J.Try) temp.getStatements().get(0);

                boolean hasMatched = false;
                List<Statement> newStatements = new ArrayList<>();
                for (Statement s : block.getStatements()) {
                    if (mi.equals(s)) {
                        newStatements.addAll(try_.getBody().getStatements());
                        hasMatched = true;
                    } else if (hasMatched) {
                        newStatements.add(s);
                    }
                }

                return try_.withBody(try_.getBody().withStatements(newStatements))
                        .withPrefix(mi.getPrefix());
            }


            private List<Statement> mockedStatic(J.MethodDeclaration m, J.MethodInvocation statement,  String className, J.MethodInvocation whenArg, ExecutionContext ctx) {
                String variableName = generateVariableName("mock" + className, updateCursor(m), INCREMENT_NUMBER);
                Expression thenReturnArg = statement.getArguments().get(0);

                J.MethodDeclaration md = javaTemplateMockStatic(String.format(
                        "%2$s = mockStatic(%1$s.class);\n" +
                        "%2$s.when(() -> #{any()}).thenReturn(#{any()});", className, variableName), ctx)
                        .apply(getCursor(), m.getCoordinates().replaceBody(), whenArg, thenReturnArg);

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

                return md.getBody().getStatements();
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

    private static boolean isMethodDeclarationWithAnnotation(Statement statement, AnnotationMatcher matcher) {
        if (statement instanceof J.MethodDeclaration) {
            return ((J.MethodDeclaration) statement).getLeadingAnnotations().stream().anyMatch(matcher::matches);
        }
        return false;
    }
}
