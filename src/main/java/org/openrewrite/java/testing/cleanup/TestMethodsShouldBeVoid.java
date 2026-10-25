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
package org.openrewrite.java.testing.cleanup;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;

public class TestMethodsShouldBeVoid extends Recipe {

    @Getter
    final String displayName = "Test methods should have void return type";

    @Getter
    final String description = "Test methods annotated with `@Test`, `@ParameterizedTest`, `@RepeatedTest`, `@TestTemplate` " +
            "should have `void` return type. Non-void return types can cause test discovery issues, " +
            "and warnings as of JUnit 5.13+. This recipe changes the return type to `void` and drops the returned values. Test methods that override a method or are called elsewhere in the same file are left unchanged.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.and(
                new JavaFileChecker<>(),
                Preconditions.or(
                        new UsesType<>("org.junit.jupiter.api.Test", false),
                        new UsesType<>("org.junit.jupiter.api.RepeatedTest", false),
                        new UsesType<>("org.junit.jupiter.params.ParameterizedTest", false),
                        new UsesType<>("org.junit.jupiter.api.TestTemplate", false))
        ), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                J.Block body = m.getBody();
                TypeTree returnType = m.getReturnTypeExpression();
                if (body == null || returnType == null || TypeUtils.isOfType(returnType.getType(), JavaType.Primitive.Void) ||
                        !hasJUnit5MethodAnnotation(m) || TypeUtils.isOverride(m.getMethodType()) ||
                        isInvoked(getCursor().firstEnclosingOrThrow(J.CompilationUnit.class), m.getSimpleName())) {
                    return m;
                }

                AtomicBoolean earlyReturnHasSideEffects = new AtomicBoolean();
                J.Block withoutLastReturn = body.withStatements(ListUtils.mapLast(body.getStatements(), TestMethodsShouldBeVoid::removeLastReturn));
                J.Block newBody = (J.Block) new RemoveEarlyReturnValues().visitNonNull(withoutLastReturn, earlyReturnHasSideEffects, getCursor());
                if (earlyReturnHasSideEffects.get()) {
                    return m;
                }

                m = m.withReturnTypeExpression(new J.Primitive(returnType.getId(), returnType.getPrefix(), returnType.getMarkers(), JavaType.Primitive.Void));
                if (m.getMethodType() != null) {
                    m = m.withMethodType(m.getMethodType().withReturnType(JavaType.Primitive.Void));
                }
                return m.withBody(newBody);
            }
        });
    }

    private static @Nullable Statement removeLastReturn(Statement statement) {
        if (!(statement instanceof J.Return) || ((J.Return) statement).getExpression() == null) {
            return statement;
        }
        Expression expression = requireNonNull(((J.Return) statement).getExpression()).unwrap();
        return isExpressionStatement(expression) ? expression.withPrefix(statement.getPrefix()) : null;
    }

    private static class RemoveEarlyReturnValues extends JavaVisitor<AtomicBoolean> {
        @Override
        public J visitLambda(J.Lambda lambda, AtomicBoolean earlyReturnHasSideEffects) {
            return lambda;
        }

        @Override
        public J visitNewClass(J.NewClass newClass, AtomicBoolean earlyReturnHasSideEffects) {
            return newClass;
        }

        @Override
        public J visitClassDeclaration(J.ClassDeclaration classDecl, AtomicBoolean earlyReturnHasSideEffects) {
            return classDecl;
        }

        @Override
        public J visitReturn(J.Return retrn, AtomicBoolean earlyReturnHasSideEffects) {
            Expression expression = retrn.getExpression();
            if (expression == null) {
                return retrn;
            }
            if (hasSideEffects(expression)) {
                earlyReturnHasSideEffects.set(true);
            }
            return retrn.withExpression(null);
        }
    }

    private static boolean isExpressionStatement(Expression expression) {
        return expression instanceof J.MethodInvocation || expression instanceof J.NewClass ||
                expression instanceof J.Assignment || expression instanceof J.AssignmentOperation ||
                expression instanceof J.Unary && ((J.Unary) expression).getOperator().isModifying();
    }

    private static boolean hasSideEffects(Expression expression) {
        return new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public @Nullable J preVisit(J tree, AtomicBoolean found) {
                if (tree instanceof Expression && isExpressionStatement((Expression) tree)) {
                    found.set(true);
                    stopAfterPreVisit();
                }
                return tree;
            }
        }.reduce(expression, new AtomicBoolean()).get();
    }

    private static boolean isInvoked(J.CompilationUnit cu, String methodName) {
        return new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean found) {
                if (method.getSimpleName().equals(methodName)) {
                    found.set(true);
                }
                return super.visitMethodInvocation(method, found);
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, AtomicBoolean found) {
                if (memberRef.getReference().getSimpleName().equals(methodName)) {
                    found.set(true);
                }
                return super.visitMemberReference(memberRef, found);
            }
        }.reduce(cu, new AtomicBoolean()).get();
    }

    private static boolean hasJUnit5MethodAnnotation(J.MethodDeclaration method) {
        for (J.Annotation a : method.getLeadingAnnotations()) {
            if (TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.Test") ||
                    TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.RepeatedTest") ||
                    TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.params.ParameterizedTest") ||
                    TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.TestTemplate")) {
                return true;
            }
        }
        return false;
    }
}
