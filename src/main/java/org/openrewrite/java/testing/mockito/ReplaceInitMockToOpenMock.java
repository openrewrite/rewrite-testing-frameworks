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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.*;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.kotlin.KotlinTemplate;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.Markers;

import java.util.List;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER;
import static org.openrewrite.java.VariableNameUtils.generateVariableName;

public class ReplaceInitMockToOpenMock extends Recipe {

    @Getter
    final String displayName = "Replace `MockitoAnnotations.initMocks(this)` to `MockitoAnnotations.openMocks(this)`";

    @Getter
    final String description = "Replace `MockitoAnnotations.initMocks(this)` to `MockitoAnnotations.openMocks(this)` and generate `AutoCloseable` mocks.";

    private static final JavaType.FullyQualified AUTO_CLOSEABLE = JavaType.ShallowClass.build("java.lang.AutoCloseable");
    private static final String MOCKITO_EXTENSION = "org.mockito.junit.jupiter.MockitoExtension";
    private static final String MOCKITO_JUNIT_RUNNER = "org.mockito.junit.MockitoJUnitRunner";
    private static final String JUPITER_BEFORE_EACH = "org.junit.jupiter.api.BeforeEach";
    private static final AnnotationMatcher BEFORE_EACH_MATCHER = new AnnotationMatcher("@" + JUPITER_BEFORE_EACH);
    private static final AnnotationMatcher AFTER_EACH_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.AfterEach");
    private static final MethodMatcher INIT_MOCKS_MATCHER = new MethodMatcher("org.mockito.MockitoAnnotations initMocks(..)", false);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> preconditions = Preconditions.and(
                new UsesMethod<>(INIT_MOCKS_MATCHER),
                new UsesType<>(JUPITER_BEFORE_EACH, false),
                Preconditions.not(new UsesType<>(MOCKITO_EXTENSION, false)),
                Preconditions.not(new UsesType<>(MOCKITO_JUNIT_RUNNER, false))
        );
        return Preconditions.check(preconditions, new JavaIsoVisitor<ExecutionContext>() {
                    private String variableName = "mocks";

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                        if (getCursor().getMessage("initMocksFound", false)) {
                            variableName = generateVariableName("mocks", getCursor(), INCREMENT_NUMBER);
                            if (getCursor().firstEnclosing(K.CompilationUnit.class) != null) {
                                // Do not autoformat the whole class, as the Kotlin formatter would reformat unrelated code
                                return KotlinTemplate.apply("private lateinit var " + variableName + ": AutoCloseable",
                                        getCursor(), cd.getBody().getCoordinates().firstStatement());
                            }
                            J.ClassDeclaration after = JavaTemplate.apply("private AutoCloseable " + variableName + ";",
                                    getCursor(), cd.getBody().getCoordinates().firstStatement());
                            return maybeAutoFormat(cd, after, ctx);
                        }
                        return cd;
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        if (service(AnnotationService.class).matches(getCursor(), BEFORE_EACH_MATCHER)) {
                            return super.visitMethodDeclaration(method, ctx);
                        }
                        return method;
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (INIT_MOCKS_MATCHER.matches(mi)) {
                            getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, "initMocksFound", true);
                            doAfterVisit(updateJUnitLifecycleMethods);
                        }
                        return mi;
                    }

                    final TreeVisitor<J, ExecutionContext> updateJUnitLifecycleMethods = new JavaIsoVisitor<ExecutionContext>() {

                        private final String EXCEPTION_CLASS_NAME = "java.lang.Exception";

                        private boolean isAnnotatedMethodPresent(J.ClassDeclaration cd, AnnotationMatcher beforeEachMatcher) {
                            return cd.getBody().getStatements().stream().anyMatch(
                                    st -> st instanceof J.MethodDeclaration &&
                                            ((J.MethodDeclaration) st).getLeadingAnnotations().stream().anyMatch(beforeEachMatcher::matches)
                            );
                        }

                        @Override
                        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
                            if (!isAnnotatedMethodPresent(cd, AFTER_EACH_MATCHER) && isAnnotatedMethodPresent(cd, BEFORE_EACH_MATCHER)) {
                                maybeAddImport("org.junit.jupiter.api.AfterEach");
                                if (getCursor().firstEnclosing(K.CompilationUnit.class) != null) {
                                    cd = KotlinTemplate.builder("@AfterEach\nfun " + tearDownMethodName(cd) + "() {\n}")
                                            .parser(KotlinParser.builder().classpathFromResources(ctx, "junit-jupiter-api-5"))
                                            .imports("org.junit.jupiter.api.AfterEach")
                                            .build()
                                            .apply(getCursor(), cd.getBody().getCoordinates().lastStatement());
                                    // Blank line before the added method; the class is not autoformatted as a whole
                                    cd = cd.withBody(cd.getBody().withStatements(ListUtils.mapLast(cd.getBody().getStatements(),
                                            stmt -> stmt.getPrefix().getWhitespace().startsWith("\n\n") ? stmt :
                                                    stmt.withPrefix(stmt.getPrefix().withWhitespace("\n" + stmt.getPrefix().getWhitespace())))));
                                } else {
                                    cd = JavaTemplate.builder("@AfterEach\nvoid " + tearDownMethodName(cd) + "() throws Exception {\n}")
                                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5"))
                                            .imports("org.junit.jupiter.api.AfterEach")
                                            .build()
                                            .apply(getCursor(), cd.getBody().getCoordinates().lastStatement());
                                }
                            }

                            cd = super.visitClassDeclaration(cd, ctx);
                            if (getCursor().firstEnclosing(K.CompilationUnit.class) != null) {
                                // Do not autoformat the whole class, as the Kotlin formatter would reformat unrelated code
                                return cd;
                            }
                            return autoFormat(cd, ctx);
                        }

                        @Override
                        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                            J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);

                            if (service(AnnotationService.class).matches(getCursor(), BEFORE_EACH_MATCHER) && md.getBody() != null) {
                                maybeRemoveImport("org.mockito.MockitoAnnotations.initMocks");
                                maybeAddImport("org.mockito.MockitoAnnotations");
                                return (J.MethodDeclaration) new JavaVisitor<ExecutionContext>() {
                                    @Override
                                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                                        J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                                        if (INIT_MOCKS_MATCHER.matches(mi)) {
                                            if (getCursor().firstEnclosing(K.CompilationUnit.class) != null) {
                                                J.Assignment assignment = KotlinTemplate.builder(variableName + " = MockitoAnnotations.openMocks(this)")
                                                        .parser(KotlinParser.builder().classpathFromResources(ctx, "mockito-core"))
                                                        .imports("org.mockito.MockitoAnnotations")
                                                        .build()
                                                        .apply(getCursor(), mi.getCoordinates().replace());
                                                return assignment.withVariable(((J.Identifier) assignment.getVariable()).withType(AUTO_CLOSEABLE));
                                            }
                                            return JavaTemplate.builder(variableName + " = MockitoAnnotations.openMocks(this);")
                                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core"))
                                                    .imports("org.mockito.MockitoAnnotations")
                                                    .contextSensitive()
                                                    .build()
                                                    .apply(getCursor(), mi.getCoordinates().replace());
                                        }
                                        return mi;
                                    }
                                }.visitNonNull(md, ctx, getCursor().getParentOrThrow());
                            }
                            if (service(AnnotationService.class).matches(getCursor(), AFTER_EACH_MATCHER) && md.getBody() != null) {
                                for (Statement st : md.getBody().getStatements()) {
                                    if (st instanceof J.MethodInvocation &&
                                            ((J.MethodInvocation) st).getSelect() instanceof J.Identifier &&
                                            ((J.Identifier) ((J.MethodInvocation) st).getSelect()).getSimpleName().equals(variableName)) {
                                        return md;
                                    }
                                }

                                if (getCursor().firstEnclosing(K.CompilationUnit.class) != null) {
                                    md = KotlinTemplate.builder(variableName + ".close()")
                                            .build()
                                            .apply(getCursor(), md.getBody().getCoordinates().lastStatement());
                                    JavaType.Method closeType = new JavaType.Method(
                                            null,
                                            Flag.Public.getBitMask() | Flag.Abstract.getBitMask(),
                                            AUTO_CLOSEABLE,
                                            "close",
                                            JavaType.Primitive.Void,
                                            (List<String>) null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null
                                    );
                                    md = md.withBody(md.getBody().withStatements(ListUtils.mapLast(md.getBody().getStatements(), stmt -> {
                                        J.MethodInvocation close = (J.MethodInvocation) stmt;
                                        return close.withMethodType(closeType)
                                                .withName(close.getName().withType(closeType))
                                                .withSelect(((J.Identifier) close.getSelect()).withType(AUTO_CLOSEABLE));
                                    })));
                                } else {
                                    md = JavaTemplate.builder(variableName + ".close();")
                                            .contextSensitive()
                                            .build()
                                            .apply(getCursor(), md.getBody().getCoordinates().lastStatement());
                                    md = addThrowsIfAbsent(md);
                                }

                                return maybeAutoFormat(method, md, ctx);
                            }

                            return md;
                        }

                        private J.MethodDeclaration addThrowsIfAbsent(J.MethodDeclaration md) {
                            if (md.getThrows() != null && md.getThrows().stream().anyMatch(j -> TypeUtils.isOfClassType(j.getType(), EXCEPTION_CLASS_NAME))) {
                                return md;
                            }
                            JavaType.Class exceptionType = JavaType.ShallowClass.build(EXCEPTION_CLASS_NAME);
                            return md.withThrows(ListUtils.concat(md.getThrows(), new J.Identifier(randomId(), Space.SINGLE_SPACE, Markers.EMPTY, emptyList(), exceptionType.getClassName(), exceptionType, null)));
                        }

                        private String tearDownMethodName(J.ClassDeclaration cd) {
                            String methodName = "tearDown";
                            int suffix = 0;
                            String updatedMethodName = methodName;
                            for (Statement st : cd.getBody().getStatements()) {
                                if (st instanceof J.MethodDeclaration && ((J.MethodDeclaration) st).getSimpleName().equals(updatedMethodName)) {
                                    updatedMethodName = methodName + suffix++;
                                }
                            }
                            return updatedMethodName;
                        }
                    };
                }
        );
    }
}
