/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.testing.easymock;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.marker.JavaVersion;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class EasyMockCaptureToMockitoArgumentCaptor extends Recipe {

    private static final String CAPTURE = "org.easymock.Capture";
    private static final String CAPTURE_TYPE = "org.easymock.CaptureType";
    private static final String ARGUMENT_CAPTOR = "org.mockito.ArgumentCaptor";
    private static final MethodMatcher NEW_CAPTURE = new MethodMatcher("org.easymock.EasyMock newCapture(..)");
    private static final MethodMatcher NEW_INSTANCE = new MethodMatcher(CAPTURE + " newInstance(..)");
    private static final MethodMatcher CAPTURE_ARGUMENT = new MethodMatcher("org.easymock.EasyMock capture*(" + CAPTURE + ")");
    private static final MethodMatcher HAS_CAPTURED = new MethodMatcher(CAPTURE + " hasCaptured()");
    private static final MethodMatcher MOCKITO_VERIFY = new MethodMatcher("org.mockito.Mockito verify(..)");

    @Getter
    final String displayName = "Replace EasyMock `Capture` with Mockito `ArgumentCaptor`";

    @Getter
    final String description = "Replace a new EasyMock `Capture<T>` with `ArgumentCaptor.forClass(T.class)`, `capture(captor)` " +
            "and `captureInt(captor)` and the like with `captor.capture()`, and `captor.hasCaptured()` with " +
            "`!captor.getAllValues().isEmpty()`. Where a `Mockito.verify` of a call has the same captor capture what the " +
            "stubbing of that call captures already, it matches the argument with `any()` instead, so that each argument " +
            "is captured once. The `Capture` type itself is left to `ChangeType`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(CAPTURE, false), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J j = super.visitNewClass(newClass, ctx);
                if (j instanceof J.NewClass && TypeUtils.isOfClassType(((J.NewClass) j).getType(), CAPTURE) &&
                        ((J.NewClass) j).getBody() == null && onlyCaptureTypeArguments(((J.NewClass) j).getArguments())) {
                    return newArgumentCaptor((J.NewClass) j, ctx);
                }
                return j;
            }

            @Override
            public J visitUnary(J.Unary unary, ExecutionContext ctx) {
                if (unary.getOperator() == J.Unary.Type.Not && unary.getExpression() instanceof J.MethodInvocation &&
                        HAS_CAPTURED.matches((J.MethodInvocation) unary.getExpression()) &&
                        ((J.MethodInvocation) unary.getExpression()).getSelect() != null) {
                    // `!captor.hasCaptured()` is `captor.getAllValues().isEmpty()`
                    return JavaTemplate.builder("#{any(" + ARGUMENT_CAPTOR + ")}.getAllValues().isEmpty()")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-5"))
                            .build()
                            .apply(getCursor(), unary.getCoordinates().replace(), ((J.MethodInvocation) unary.getExpression()).getSelect());
                }
                return super.visitUnary(unary, ctx);
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J j = super.visitMethodInvocation(method, ctx);
                if (!(j instanceof J.MethodInvocation)) {
                    return j;
                }
                J.MethodInvocation mi = (J.MethodInvocation) j;
                if ((NEW_CAPTURE.matches(mi) || NEW_INSTANCE.matches(mi)) && onlyCaptureTypeArguments(mi.getArguments())) {
                    return newArgumentCaptor(mi, ctx);
                }
                if (CAPTURE_ARGUMENT.matches(mi)) {
                    Expression captor = mi.getArguments().get(0);
                    if (isArgumentOfVerifiedCall() && isCapturedOutsideOfVerify(captor)) {
                        return anyArgument(mi, ctx);
                    }
                    return JavaTemplate.builder("#{any(" + ARGUMENT_CAPTOR + ")}.capture()")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-5"))
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), captor);
                }
                if (HAS_CAPTURED.matches(mi) && mi.getSelect() != null) {
                    return JavaTemplate.builder("!#{any(" + ARGUMENT_CAPTOR + ")}.getAllValues().isEmpty()")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-5"))
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), mi.getSelect());
                }
                return mi;
            }

            private boolean onlyCaptureTypeArguments(@Nullable List<Expression> arguments) {
                if (arguments == null) {
                    return true;
                }
                for (Expression argument : arguments) {
                    if (!(argument instanceof J.Empty) && !TypeUtils.isOfClassType(argument.getType(), CAPTURE_TYPE)) {
                        return false;
                    }
                }
                return true;
            }

            private J newArgumentCaptor(Expression newCapture, ExecutionContext ctx) {
                JavaType.Parameterized captureType = TypeUtils.asParameterized(newCapture.getType());
                if (captureType == null || captureType.getTypeParameters().size() != 1) {
                    return newCapture;
                }
                JavaType captured = captureType.getTypeParameters().get(0);
                String classLiteral;
                JavaType.FullyQualified importType = null;
                if (captured instanceof JavaType.Parameterized) {
                    importType = ((JavaType.Parameterized) captured).getType();
                    classLiteral = "(Class) " + importType.getClassName() + ".class";
                } else if (captured instanceof JavaType.FullyQualified) {
                    importType = (JavaType.FullyQualified) captured;
                    classLiteral = importType.getClassName() + ".class";
                } else if (captured instanceof JavaType.Array && ((JavaType.Array) captured).getElemType() instanceof JavaType.Primitive) {
                    classLiteral = ((JavaType.Primitive) ((JavaType.Array) captured).getElemType()).getKeyword() + "[].class";
                } else {
                    return newCapture;
                }
                maybeRemoveImport(CAPTURE_TYPE);
                maybeAddImport(ARGUMENT_CAPTOR);
                JavaTemplate.Builder template = JavaTemplate.builder("ArgumentCaptor.forClass(" + classLiteral + ")")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-5"));
                if (importType != null && !"java.lang".equals(importType.getPackageName())) {
                    template.imports(ARGUMENT_CAPTOR, TypeUtils.toFullyQualifiedName(importType.getFullyQualifiedName()));
                    maybeAddImport(importType.getFullyQualifiedName().replace('$', '.'), false);
                } else {
                    template.imports(ARGUMENT_CAPTOR);
                }
                return template.build().apply(getCursor(), newCapture.getCoordinates().replace());
            }

            private J anyArgument(J.MethodInvocation capture, ExecutionContext ctx) {
                J.MethodInvocation verifiedCall = getCursor().getParentTreeCursor().getValue();
                int index = verifiedCall.getArguments().indexOf(capture);
                JavaType.Method methodType = verifiedCall.getMethodType();
                JavaType parameterType = methodType != null && index >= 0 && index < methodType.getParameterTypes().size() ?
                        methodType.getParameterTypes().get(index) : null;
                String matcher = "any";
                if (parameterType instanceof JavaType.Primitive) {
                    String keyword = ((JavaType.Primitive) parameterType).getKeyword();
                    matcher = "any" + Character.toUpperCase(keyword.charAt(0)) + keyword.substring(1);
                } else if (parameterType instanceof JavaType.Class && compilesBelowJava8()) {
                    // Java 7 does not infer the type of `any()` from the parameter it is passed to
                    JavaType.FullyQualified type = (JavaType.FullyQualified) parameterType;
                    maybeAddImport("org.mockito.Mockito");
                    maybeAddImport(type.getFullyQualifiedName().replace('$', '.'), false);
                    return JavaTemplate.builder("Mockito.<" + type.getClassName() + ">any()")
                            .contextSensitive()
                            .imports("org.mockito.Mockito")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-5"))
                            .build()
                            .apply(getCursor(), capture.getCoordinates().replace());
                }
                if (!hasStaticStarImportOfMatchers()) {
                    maybeAddImport("org.mockito.Mockito", matcher);
                }
                return JavaTemplate.builder(matcher + "()")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-5"))
                        .staticImports("org.mockito.Mockito." + matcher)
                        .build()
                        .apply(getCursor(), capture.getCoordinates().replace());
            }

            private boolean compilesBelowJava8() {
                J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
                return cu != null && cu.getMarkers().findFirst(JavaVersion.class)
                        .map(javaVersion -> javaVersion.getMajorVersion() < 8)
                        .orElse(false);
            }

            /**
             * A static star import of `EasyMock` becomes one of `Mockito`, which then brings in the matchers already.
             */
            private boolean hasStaticStarImportOfMatchers() {
                J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
                if (cu == null) {
                    return false;
                }
                for (J.Import anImport : cu.getImports()) {
                    if (anImport.isStatic() && "*".equals(anImport.getQualid().getSimpleName()) &&
                            ("org.easymock.EasyMock".equals(anImport.getTypeName()) ||
                             "org.mockito.Mockito".equals(anImport.getTypeName()) ||
                             "org.mockito.ArgumentMatchers".equals(anImport.getTypeName()))) {
                        return true;
                    }
                }
                return false;
            }

            /**
             * Is this an argument of a call verified with `Mockito.verify(mock).call(..)`?
             */
            private boolean isArgumentOfVerifiedCall() {
                Object parent = getCursor().getParentTreeCursor().getValue();
                return parent instanceof J.MethodInvocation &&
                       ((J.MethodInvocation) parent).getSelect() instanceof J.MethodInvocation &&
                       MOCKITO_VERIFY.matches((J.MethodInvocation) ((J.MethodInvocation) parent).getSelect());
            }

            private boolean isCapturedOutsideOfVerify(Expression captor) {
                J.MethodDeclaration method = getCursor().firstEnclosing(J.MethodDeclaration.class);
                if (method == null || method.getBody() == null) {
                    return false;
                }
                AtomicBoolean captured = new AtomicBoolean();
                new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, AtomicBoolean found) {
                        if (MOCKITO_VERIFY.matches(mi.getSelect())) {
                            return mi;
                        }
                        if (CAPTURE_ARGUMENT.matches(mi) && SemanticallyEqual.areEqual(mi.getArguments().get(0), captor)) {
                            found.set(true);
                        }
                        return super.visitMethodInvocation(mi, found);
                    }
                }.visit(method.getBody(), captured, new Cursor(null, method));
                return captured.get();
            }
        });
    }
}
