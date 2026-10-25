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
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.*;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.KotlinVisitor;
import org.openrewrite.kotlin.marker.SingleExpressionBlock;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.kotlin.tree.KotlinTypeUtils;
import org.openrewrite.marker.Markers;

import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;

public class KotlinTestMethodsShouldReturnUnit extends Recipe {

    private static final String TEST_ANNOTATION_PATTERN = "org..* *Test*";
    // `TEST_ANNOTATION_PATTERN`'s `*Test*` segment matches any simple name containing "Test", which
    // incidentally also matches `@TestFactory`. Unlike `@Test`/`@ParameterizedTest`/`@RepeatedTest`/
    // `@TestTemplate`, `@TestFactory` methods are required by JUnit Jupiter to return a value (e.g.
    // `Collection<DynamicTest>`), so they must be excluded here.
    private static final String TEST_FACTORY_ANNOTATION_PATTERN = "org.junit.jupiter.api.TestFactory";
    // `UsesType` matches against fully qualified type names, so it needs a single-token glob rather
    // than the two-token (`<owner> <name>`) form `AnnotationMatcher` uses above.
    private static final String TEST_ANNOTATION_TYPE_PATTERN = "org.junit..*Test*";
    private static final JavaType.Class KOTLIN_UNIT = (JavaType.Class) JavaType.buildType("kotlin.Unit");

    @Getter
    final String displayName = "Kotlin test methods should have return type `Unit`";

    @Getter
    final String description = "Kotlin test methods annotated with `@Test`, `@ParameterizedTest`, `@RepeatedTest`, `@TestTemplate` " +
            "should have `Unit` return type. Other return types can cause test discovery issues, " +
            "and warnings as of JUnit 5.13+. This recipe changes the return type to `Unit` and drops the returned values. Test methods that override a method or are called elsewhere in the same file are left unchanged.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new UsesType<>(TEST_ANNOTATION_TYPE_PATTERN, true), new UsesType<>("org.testng.annotations.Test", true)), new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

                // Skip invalid signatures or already-correct return types
                J.Block body = m.getBody();
                JavaType.Method methodType = m.getMethodType();
                if (body == null || methodType == null || KotlinTypeUtils.isKotlinUnit(methodType.getReturnType())) {
                    return m;
                }

                // Only consider test methods, excluding `@TestFactory` (see comment on
                // TEST_FACTORY_ANNOTATION_PATTERN above).
                AnnotationService annotationService = service(AnnotationService.class);
                if (!annotationService.matches(getCursor(), new AnnotationMatcher(TEST_ANNOTATION_PATTERN, true)) ||
                        annotationService.matches(getCursor(), new AnnotationMatcher(TEST_FACTORY_ANNOTATION_PATTERN, true))) {
                    return m;
                }

                if (TypeUtils.isOverride(methodType) || isInvoked(getCursor().firstEnclosingOrThrow(K.CompilationUnit.class), m.getSimpleName())) {
                    return m;
                }

                JavaType.Method unitMethodType = methodType.withReturnType(KOTLIN_UNIT);
                J.MethodDeclaration unitMethod = m.withMethodType(unitMethodType).withName(m.getName().withType(unitMethodType));

                // Add an explicit Unit return type
                if (body.getMarkers().findFirst(SingleExpressionBlock.class).isPresent()) {
                    return unitMethod.withReturnTypeExpression(new J.Identifier(
                            Tree.randomId(),
                            Space.SINGLE_SPACE,
                            Markers.EMPTY,
                            emptyList(),
                            KOTLIN_UNIT.getClassName(),
                            KOTLIN_UNIT,
                            null));
                }

                AtomicBoolean unsafeReturn = new AtomicBoolean();
                J.Block withoutLastReturn = body.withStatements(ListUtils.mapLast(body.getStatements(), KotlinTestMethodsShouldReturnUnit::removeLastReturn));
                J.Block newBody = (J.Block) new RemoveEarlyReturnValues(m.getSimpleName()).visitNonNull(withoutLastReturn, unsafeReturn, getCursor());
                if (unsafeReturn.get()) {
                    return m;
                }
                return unitMethod.withReturnTypeExpression(null).withBody(newBody);
            }
        });
    }

    private static @Nullable Statement removeLastReturn(Statement statement) {
        if (!(statement instanceof K.Return)) {
            return statement;
        }
        Expression expression = ((K.Return) statement).getExpression().getExpression();
        return expression instanceof Statement ? expression.withPrefix(statement.getPrefix()) : null;
    }

    @RequiredArgsConstructor
    private static class RemoveEarlyReturnValues extends KotlinVisitor<AtomicBoolean> {
        private final String methodName;

        @Override
        public J visitClassDeclaration(J.ClassDeclaration classDeclaration, AtomicBoolean unsafeReturn) {
            return classDeclaration;
        }

        @Override
        public J visitMethodDeclaration(J.MethodDeclaration method, AtomicBoolean unsafeReturn) {
            return method;
        }

        @Override
        public J visitNewClass(J.NewClass newClass, AtomicBoolean unsafeReturn) {
            return newClass;
        }

        @Override
        public J visitReturn(K.Return return_, AtomicBoolean unsafeReturn) {
            if (getCursor().dropParentUntil(t -> t instanceof J.Lambda || t instanceof J.MethodDeclaration).getValue() instanceof J.Lambda) {
                J.Identifier label = return_.getLabel();
                if (label == null || methodName.equals(label.getSimpleName())) {
                    unsafeReturn.set(true);
                }
                return return_;
            }
            Expression expression = return_.getExpression().getExpression();
            if (expression == null) {
                return return_;
            }
            if (hasSideEffects(expression)) {
                unsafeReturn.set(true);
            }
            return return_.withExpression(return_.getExpression().withExpression(null));
        }
    }

    private static boolean hasSideEffects(Expression expression) {
        return new KotlinIsoVisitor<AtomicBoolean>() {
            @Override
            public @Nullable J preVisit(J tree, AtomicBoolean found) {
                if (tree instanceof J.MethodInvocation || tree instanceof J.NewClass ||
                        tree instanceof J.Assignment || tree instanceof J.AssignmentOperation ||
                        tree instanceof J.Unary && ((J.Unary) tree).getOperator().isModifying()) {
                    found.set(true);
                    stopAfterPreVisit();
                }
                return tree;
            }
        }.reduce(expression, new AtomicBoolean()).get();
    }

    private static boolean isInvoked(K.CompilationUnit cu, String methodName) {
        return new KotlinIsoVisitor<AtomicBoolean>() {
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
}
