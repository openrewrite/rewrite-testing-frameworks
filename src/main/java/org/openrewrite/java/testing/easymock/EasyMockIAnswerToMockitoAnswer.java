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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.HashSet;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

public class EasyMockIAnswerToMockitoAnswer extends Recipe {

    private static final String IANSWER = "org.easymock.IAnswer";
    private static final String INVOCATION_ON_MOCK = "org.mockito.invocation.InvocationOnMock";
    private static final MethodMatcher ANSWER = new MethodMatcher(IANSWER + " answer()", true);
    private static final MethodMatcher GET_CURRENT_ARGUMENTS = new MethodMatcher("org.easymock.EasyMock getCurrentArguments()");
    private static final MethodMatcher GET_CURRENT_ARGUMENT = new MethodMatcher("org.easymock.EasyMock getCurrentArgument(int)");

    @Getter
    final String displayName = "Replace EasyMock `IAnswer` implementations with Mockito `Answer` implementations";

    @Getter
    final String description = "Give lambdas, anonymous classes and classes implementing `org.easymock.IAnswer` the " +
            "`InvocationOnMock` parameter of Mockito's `Answer.answer(InvocationOnMock)`, and replace " +
            "`EasyMock.getCurrentArguments()` and `EasyMock.getCurrentArgument(int)` within them with " +
            "`invocation.getArguments()` and `invocation.getArgument(int)`. The `IAnswer` type itself is left to `ChangeType`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                new UsesType<>(IANSWER, true),
                new UsesMethod<>(GET_CURRENT_ARGUMENTS),
                new UsesMethod<>(GET_CURRENT_ARGUMENT)), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitLambda(J.Lambda lambda, ExecutionContext ctx) {
                J.Lambda l = (J.Lambda) super.visitLambda(lambda, ctx);
                if (!TypeUtils.isAssignableTo(IANSWER, l.getType()) ||
                        l.getParameters().getParameters().size() != 1 ||
                        !(l.getParameters().getParameters().get(0) instanceof J.Empty)) {
                    return l;
                }
                String name = parameterName(l);
                l = l.withParameters(lambdaParameters(name));
                return l.withBody(useInvocation(l.getBody(), name, new Cursor(getCursor().getParent(), l), ctx));
            }

            /**
             * `paramNames::elements` becomes `invocation -> paramNames.elements()`, as a method reference without
             * parameters does not match `Answer.answer(InvocationOnMock)`.
             */
            @Override
            public J visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                J j = super.visitMemberReference(memberRef, ctx);
                if (!(j instanceof J.MemberReference)) {
                    return j;
                }
                J.MemberReference mr = (J.MemberReference) j;
                JavaType.Method methodType = mr.getMethodType();
                if (!TypeUtils.isAssignableTo(IANSWER, mr.getType()) || methodType == null ||
                        !methodType.getParameterTypes().isEmpty() || "new".equals(mr.getReference().getSimpleName())) {
                    return mr;
                }
                J.MethodInvocation call = new J.MethodInvocation(
                        randomId(), Space.SINGLE_SPACE, Markers.EMPTY,
                        JRightPadded.build(mr.getContaining().withPrefix(Space.EMPTY)),
                        null,
                        mr.getReference().withPrefix(Space.EMPTY).withType(methodType),
                        JContainer.build(Space.EMPTY, singletonList(JRightPadded.build(new J.Empty(randomId(), Space.EMPTY, Markers.EMPTY))), Markers.EMPTY),
                        methodType);
                return new J.Lambda(randomId(), mr.getPrefix(), mr.getMarkers(), lambdaParameters(parameterName(mr)),
                        Space.SINGLE_SPACE, call, mr.getType());
            }

            private J.Lambda.Parameters lambdaParameters(String name) {
                JavaType.FullyQualified invocationType = JavaType.ShallowClass.build(INVOCATION_ON_MOCK);
                JavaType.Variable variableType = new JavaType.Variable(null, 0, name, null, invocationType, emptyList());
                J.VariableDeclarations parameter = new J.VariableDeclarations(
                        randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), emptyList(), null, null,
                        singletonList(JRightPadded.build(new J.VariableDeclarations.NamedVariable(
                                randomId(), Space.EMPTY, Markers.EMPTY,
                                new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), name, invocationType, variableType),
                                emptyList(), null, variableType))));
                return new J.Lambda.Parameters(randomId(), Space.EMPTY, Markers.EMPTY, false, singletonList(JRightPadded.build(parameter)));
            }

            @Override
            public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = (J.MethodDeclaration) super.visitMethodDeclaration(method, ctx);
                if (m.getBody() == null || !ANSWER.matches(m.getMethodType()) ||
                        m.getParameters().size() != 1 || !(m.getParameters().get(0) instanceof J.Empty)) {
                    return m;
                }
                String name = parameterName(m);
                m = JavaTemplate.builder("InvocationOnMock " + name)
                        .imports(INVOCATION_ON_MOCK)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-5"))
                        .build()
                        .apply(updateCursor(m), m.getCoordinates().replaceParameters());
                maybeAddImport(INVOCATION_ON_MOCK);
                return m.withBody((J.Block) useInvocation(m.getBody(), name, new Cursor(getCursor().getParent(), m), ctx));
            }

            /**
             * A star import is left to the type change that follows, so it is not unfolded here.
             */
            private void removeExplicitStaticImport(String member) {
                J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
                if (cu != null) {
                    for (J.Import anImport : cu.getImports()) {
                        if (anImport.isStatic() && member.equals(anImport.getQualid().getSimpleName()) &&
                                "org.easymock.EasyMock".equals(anImport.getTypeName())) {
                            maybeRemoveImport("org.easymock.EasyMock." + member);
                            return;
                        }
                    }
                }
            }

            /**
             * The name `invocation` unless the answer or the code around it already uses it.
             */
            private String parameterName(J answer) {
                Object scope = getCursor().dropParentUntil(p -> p instanceof J.MethodDeclaration ||
                                                                p instanceof J.ClassDeclaration ||
                                                                p == Cursor.ROOT_VALUE).getValue();
                Set<String> names = new HashSet<>();
                new JavaIsoVisitor<Set<String>>() {
                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, Set<String> n) {
                        n.add(identifier.getSimpleName());
                        return identifier;
                    }
                }.visit(scope instanceof Tree ? (Tree) scope : answer, names);
                for (String candidate : new String[]{"invocation", "invocationOnMock", "mockInvocation"}) {
                    if (!names.contains(candidate)) {
                        return candidate;
                    }
                }
                int i = 2;
                while (names.contains("invocation" + i)) {
                    i++;
                }
                return "invocation" + i;
            }

            private J useInvocation(J body, String name, Cursor parent, ExecutionContext ctx) {
                removeExplicitStaticImport("getCurrentArguments");
                removeExplicitStaticImport("getCurrentArgument");
                JavaType.FullyQualified invocationType = JavaType.ShallowClass.build(INVOCATION_ON_MOCK);
                J.Identifier invocation = new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), name, invocationType, null);
                return new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                        if (GET_CURRENT_ARGUMENTS.matches(mi)) {
                            return JavaTemplate.builder("#{any(" + INVOCATION_ON_MOCK + ")}.getArguments()")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-5"))
                                    .build()
                                    .apply(getCursor(), mi.getCoordinates().replace(), invocation);
                        }
                        if (GET_CURRENT_ARGUMENT.matches(mi)) {
                            return JavaTemplate.builder("#{any(" + INVOCATION_ON_MOCK + ")}.getArgument(#{any(int)})")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-5"))
                                    .build()
                                    .apply(getCursor(), mi.getCoordinates().replace(), invocation, mi.getArguments().get(0));
                        }
                        return mi;
                    }
                }.visitNonNull(body, ctx, parent);
            }
        });
    }
}
