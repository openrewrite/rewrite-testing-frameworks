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
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.java.VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER;
import static org.openrewrite.java.VariableNameUtils.generateVariableName;
import static org.openrewrite.java.tree.Flag.Static;

public class MockitoWhenOnStaticToMockStatic extends Recipe {

    private static final AnnotationMatcher JUNIT_4_ANNOTATION = new AnnotationMatcher("org.junit.*");
    private static final AnnotationMatcher JUNIT_5_ANNOTATION = new AnnotationMatcher("org.junit.jupiter.api.*");
    private static final AnnotationMatcher TESTNG_ANNOTATION = new AnnotationMatcher("org.testng.annotations.*");

    private static final AnnotationMatcher BEFORE = new AnnotationMatcher("org..Before*");
    private static final AnnotationMatcher BEFORE_CLASS = new AnnotationMatcher("org..BeforeClass");
    private static final AnnotationMatcher BEFORE_ALL = new AnnotationMatcher("org..BeforeAll");
    private static final AnnotationMatcher BEFORE_PARAM_CLASS_INV = new AnnotationMatcher("org..BeforeParameterizedClassInvocation");
    private static final AnnotationMatcher AFTER = new AnnotationMatcher("org..After*");

    private static final MethodMatcher MOCKITO_WHEN = new MethodMatcher("org.mockito.Mockito when(..)");
    private static final TypeMatcher MOCKED_STATIC = new TypeMatcher("org.mockito.MockedStatic");

    private int varCounter = 0;

    @Override
    public String getDisplayName() {
        return "Replace `Mockito.when` on static (non mock) with try-with-resource with MockedStatic";
    }

    @Override
    public String getDescription() {
        return "Replace `Mockito.when` on static (non mock) with try-with-resource with MockedStatic as Mockito4 no longer allows this. " +
                "For JUnit 4: When `@Before` or `@BeforeClass` is used, a `close` call is added to either the `@After` or `@AfterClass` method. " +
                "For JUnit 5: When `@BeforeEach`, `@BeforeAll` or `@BeforeParameterizedClassInvocation` is used, " +
                "a `close` call is added to a corresponding `@AfterEach`, `@AfterAll` or `@AfterParameterizedClassInvocation` method. " +
                "For TestNG: When `@BeforeMethod` or `@BeforeClass` is used, a `close` call is added to either the `@AfterMethod` or `@AfterClass` method. " +
                "This change moves away from implicit bytecode manipulation for static method stubbing, making mocking behavior more explicit and scoped to avoid unintended side effects.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(MOCKITO_WHEN), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                List<Statement> newStatements = isMethodDeclarationWithAnnotation(getCursor().firstEnclosing(J.MethodDeclaration.class), BEFORE) ?
                        maybeStatementsToMockedStatic(block, block.getStatements(), ctx) :
                        maybeWrapStatementsInTryWithResourcesMockedStatic(block, block.getStatements(), ctx);

                J.Block b = super.visitBlock(block.withStatements(newStatements), ctx);
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
                            Optional<String> nameOfWrappingMockedStatic = tryGetMatchedWrappingResourceName(getCursor(), className);
                            if (nameOfWrappingMockedStatic.isPresent()) {
                                return reuseMockedStatic(block, (J.MethodInvocation) statement, nameOfWrappingMockedStatic.get(), whenArg, ctx);
                            }
                            J.Identifier staticMockedVariable = findMockedStaticVariable(getCursor(), className);
                            if (staticMockedVariable != null) {
                                return reuseMockedStatic(block, (J.MethodInvocation) statement, staticMockedVariable, whenArg, ctx);
                            }
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
                // Having a fieldType implies that something is a field rather than a class itself
                if (whenArg.getSelect() instanceof J.Identifier && ((J.Identifier) whenArg.getSelect()).getFieldType() == null) {
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

            private Statement reuseMockedStatic(J.Block block, J.MethodInvocation statement, Object variable, J.MethodInvocation whenArg, ExecutionContext ctx) {
                String mockedStaticVariableTemplate = variable instanceof J ? "#{any()}" : "#{}";
                return javaTemplateMockStatic(mockedStaticVariableTemplate + ".when(() -> #{any()}).thenReturn(#{any()});", ctx)
                        .<J.Block>apply(getCursor(), block.getCoordinates().firstStatement(), variable, whenArg, statement.getArguments().get(0))
                        .getStatements().get(0);
            }

            private List<Statement> mockedStatic(J.Block block, J.MethodInvocation statement, String className, J.MethodInvocation whenArg, ExecutionContext ctx) {
                boolean staticSetup = isMethodDeclarationWithAnnotation(getCursor().firstEnclosing(J.MethodDeclaration.class), BEFORE_CLASS, BEFORE_ALL, BEFORE_PARAM_CLASS_INV);
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
                        J.ClassDeclaration after = JavaTemplate.builder(
                                        String.format("private%s MockedStatic<%s> %s;", staticSetup ? " static" : "", className, variableName))
                                .contextSensitive()
                                .build()
                                .apply(updateCursor(classDecl), classDecl.getBody().getCoordinates().firstStatement());

                        if (classDecl.getBody().getStatements().stream().noneMatch(it -> isMethodDeclarationWithAnnotation(it, AFTER))) {
                            Optional<Statement> beforeMethodJunit4 = after.getBody().getStatements().stream()
                                    .filter(it -> isMethodDeclarationWithAnnotation(it, JUNIT_4_ANNOTATION))
                                    .findFirst();
                            Optional<Statement> beforeMethodJunit5 = after.getBody().getStatements().stream()
                                    .filter(it -> isMethodDeclarationWithAnnotation(it, JUNIT_5_ANNOTATION))
                                    .findFirst();
                            Optional<Statement> beforeMethodTestng = after.getBody().getStatements().stream()
                                    .filter(it -> isMethodDeclarationWithAnnotation(it, TESTNG_ANNOTATION))
                                    .findFirst();
                            if (beforeMethodJunit4.isPresent()) {
                                maybeAddImport("org.junit.AfterClass");
                                maybeAddImport("org.junit.After");
                                after = JavaTemplate.builder(String.format(
                                                "%s void tearDown() {}", staticSetup ? "@AfterClass public static" : "@After public"
                                        ))
                                        .imports("org.junit.AfterClass", "org.junit.After")
                                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-4"))
                                        .build()
                                        .apply(updateCursor(after), beforeMethodJunit4.get().getCoordinates().after());
                            } else if (beforeMethodJunit5.isPresent()) {
                                boolean staticForParameterized = isMethodDeclarationWithAnnotation(getCursor().firstEnclosing(J.MethodDeclaration.class), BEFORE_PARAM_CLASS_INV);
                                maybeAddImport("org.junit.jupiter.api.AfterParameterizedClassInvocation");
                                maybeAddImport("org.junit.jupiter.api.AfterAll");
                                maybeAddImport("org.junit.jupiter.api.AfterEach");
                                String annotatedQualifiers = staticSetup ?
                                        (staticForParameterized ?
                                                "@AfterParameterizedClassInvocation public static" :
                                                "@AfterAll public static") :
                                        "@AfterEach public";
                                after = JavaTemplate.builder(String.format("%s void tearDown() {}", annotatedQualifiers))
                                        .imports(
                                                "org.junit.jupiter.api.AfterParameterizedClassInvocation",
                                                "org.junit.jupiter.api.AfterAll",
                                                "org.junit.jupiter.api.AfterEach"
                                        )
                                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5"))
                                        .build()
                                        .apply(updateCursor(after), beforeMethodJunit5.get().getCoordinates().after());
                            } else if (beforeMethodTestng.isPresent()) {
                                maybeAddImport("org.testng.annotations.AfterClass");
                                maybeAddImport("org.testng.annotations.AfterMethod");
                                after = JavaTemplate.builder(String.format(
                                                "%s void tearDown() {}", staticSetup ? "@AfterClass public static" : "@AfterMethod public"
                                        ))
                                        .imports("org.testng.annotations.AfterClass", "org.testng.annotations.AfterMethod")
                                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "testng"))
                                        .build()
                                        .apply(updateCursor(after), beforeMethodTestng.get().getCoordinates().after());
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

    private static List<J.Try.Resource> getMatchingFilteredResources(@Nullable List<J.Try.Resource> resources, String className) {
        if (resources != null) {
            return resources.stream().filter(res -> {
                J.VariableDeclarations vds = (J.VariableDeclarations) res.getVariableDeclarations();
                return TypeUtils.isAssignableTo("org.mockito.MockedStatic<" + className + ">", vds.getTypeAsFullyQualified());
            }).collect(toList());
        }
        return emptyList();
    }

    private static Optional<String> tryGetMatchedWrappingResourceName(Cursor cursor, String className) {
        try {
            Cursor foundParentCursor = cursor.dropParentUntil(val -> {
                if (val instanceof J.Try) {
                    List<J.Try.Resource> filteredResources = getMatchingFilteredResources(((J.Try) val).getResources(), className);
                    return !filteredResources.isEmpty();
                }
                return false;
            });
            return getMatchingFilteredResources(((J.Try) foundParentCursor.getValue()).getResources(), className)
                    .stream()
                    .findFirst()
                    .map(res -> ((J.VariableDeclarations) res.getVariableDeclarations()).getVariables().get(0).getSimpleName());
        } catch (IllegalStateException e) {
            return Optional.empty();
        }
    }

    private static boolean isMethodDeclarationWithAnnotation(@Nullable Statement statement, AnnotationMatcher... matchers) {
        if (statement instanceof J.MethodDeclaration) {
            return ((J.MethodDeclaration) statement).getLeadingAnnotations().stream()
                    .anyMatch(it -> Arrays.stream(matchers).anyMatch(m -> m.matches(it)));
        }
        return false;
    }

    private static J.@Nullable Identifier findMockedStaticVariable(Cursor scope, String className) {
        JavaSourceFile compilationUnit = scope.firstEnclosing(JavaSourceFile.class);
        if (compilationUnit == null) {
            return null;
        }

        return new JavaIsoVisitor<AtomicReference<J.Identifier>>() {
            @Override
            public J.Block visitBlock(J.Block block, AtomicReference<J.Identifier> mockedStaticVar) {
                if (scope.isScopeInPath(block)) {
                    return super.visitBlock(block, mockedStaticVar);
                }
                return block;
            }

            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, AtomicReference<J.Identifier> mockedStaticVar) {
                J.Identifier identifier = variable.getName();
                if (MOCKED_STATIC.matches(identifier) && identifier.getType() instanceof JavaType.Parameterized) {
                    JavaType.Parameterized parameterizedType = (JavaType.Parameterized) identifier.getType();
                    if (parameterizedType.getTypeParameters().size() == 1 && TypeUtils.isAssignableTo(className, parameterizedType.getTypeParameters().get(0))) {
                        mockedStaticVar.set(identifier);
                    }
                }

                return super.visitVariable(variable, mockedStaticVar);
            }
        }.reduce(compilationUnit, new AtomicReference<>()).get();
    }
}
