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
package org.openrewrite.java.testing.mockito;

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.List;
import java.util.Objects;

public class ReplaceInitMockToOpenMock extends Recipe {

    @Override
    public String getDisplayName() {
        return "Replace `MockitoAnnotations.initMocks(this)` to `MockitoAnnotations.openMocks(this)`";
    }

    @Override
    public String getDescription() {
        return "Replace `MockitoAnnotations.initMocks(this)` to `MockitoAnnotations.openMocks(this)` and generate `AutoCloseable` mocks.";
    }

    private static final String MOCKITO_EXTENSION = "org.mockito.junit.jupiter.MockitoExtension";
    private static final String MOCKITO_JUNIT_RUNNER = "org.mockito.junit.MockitoJUnitRunner";
    private static final AnnotationMatcher BEFORE_EACH_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.BeforeEach");
    private static final AnnotationMatcher AFTER_EACH_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.AfterEach");
    private static final MethodMatcher INIT_MOCKS_MATCHER = new MethodMatcher("org.mockito.MockitoAnnotations initMocks(..)", false);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new UsesMethod<>(INIT_MOCKS_MATCHER),
                        Preconditions.not(new UsesType<>(MOCKITO_EXTENSION, false)),
                        Preconditions.not(new UsesType<>(MOCKITO_JUNIT_RUNNER, false))
                ),
                new JavaIsoVisitor<ExecutionContext>() {

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, executionContext);
                        if (INIT_MOCKS_MATCHER.matches(mi)) {
                            doAfterVisit(
                                    tmp
                            );
                        }
                        return mi;
                    }

                    TreeVisitor<J, ExecutionContext> tmp = new JavaIsoVisitor<ExecutionContext>() {

                        @Override
                        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
                            J.ClassDeclaration after = JavaTemplate.builder("private AutoCloseable mocks;")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx))
                                    //.contextSensitive()
                                    .build()
                                    .apply(updateCursor(cd), cd.getBody().getCoordinates().firstStatement());


                            maybeAddImport("org.junit.AfterEach");
                            after = JavaTemplate.builder("    @AfterEach\n" +
                                            "    void tearDown() throws Exception {\n" +
                                            "        mocks.close();\n" +
                                            "    }")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5"))
                                    .imports("org.junit.AfterEach")
                                    .build()
                                    .apply(updateCursor(after), after.getBody().getCoordinates().lastStatement());

                            after = super.visitClassDeclaration(after, ctx);
                            return after;
                        }

                        @Override
                        public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
//                            List<Statement> newStatements = isMethodDeclarationWithAnnotation(getCursor().firstEnclosing(J.MethodDeclaration.class), BEFORE, BEFORE_CLASS) ?
//                                    maybeStatementsToMockedStatic(block, block.getStatements(), ctx) :
//                                    maybeWrapStatementsInTryWithResourcesMockedStatic(block, block.getStatements(), ctx);
//
//                            J.Block b = super.visitBlock(block.withStatements(newStatements), ctx);
//                            return maybeAutoFormat(block, b, ctx);
                            return super.visitBlock(block, ctx);
                        }

                        @Override
                        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                            J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                            if (service(AnnotationService.class).matches(updateCursor(md), BEFORE_EACH_MATCHER) && md.getBody() != null) {
                                for (Statement st : md.getBody().getStatements()) {
                                    if (st instanceof J.MethodInvocation && INIT_MOCKS_MATCHER.matches((J.MethodInvocation) st)) {
                                        return JavaTemplate.builder("mocks = MockitoAnnotations.openMocks(this);")
                                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core"))
                                                .imports("org.mockito.MockitoAnnotations")
                                                .build()
                                                .apply(getCursor(), st.getCoordinates().replace());
                                    }
                                }
                            }
                            return md;
                        }


//                    @Override
//                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
//                        J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
//                        if (service(AnnotationService.class).matches(updateCursor(md), BEFORE_EACH_MATCHER)) {
//                            md = md.withBody(md.getBody().withStatements(Objects.requireNonNull(ListUtils.map(md.getBody().getStatements(),
//                                    st -> {
//                                        if (st instanceof J.MethodInvocation && INIT_MOCKS_MATCHER.matches((J.MethodInvocation) st)) {
//                                            Cursor cursor = new Cursor(getCursor(), st);
//                                            return JavaTemplate.builder("mocks = MockitoAnnotations.openMocks(this);")
//                                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core"))
//                                                    .imports("org.mockito.MockitoAnnotations")
//                                                    .build()
//                                                    .apply(cursor, st.getCoordinates().replace());
//                                        }
//                                        return st;
//                                    }))));
//
//                            getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, "AddAutoCloseable", "mocks");
//                            return autoFormat(md, ctx);
//                        }
//                        return md;
//                    }
//
//                    @Override
//                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
//                        cd = super.visitClassDeclaration(cd, ctx);
//                        String autoCloseable = getCursor().pollMessage("AddAutoCloseable");
//                        if (autoCloseable != null) {
//                            cd = JavaTemplate.builder("private AutoCloseable mocks;")
//                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx))
//                                    .build()
//                                    .apply(updateCursor(cd), cd.getBody().getCoordinates().firstStatement());
//                        }
//
//                        return cd;
//                    }

                    };
                }
        );
    }
}
