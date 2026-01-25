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
import org.openrewrite.*;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.*;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.KotlinVisitor;
import org.openrewrite.kotlin.marker.KObject;
import org.openrewrite.kotlin.marker.SingleExpressionBlock;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.Markers;

import static java.util.Collections.emptyList;

public class KotlinTestMethodsShouldReturnUnit extends Recipe {

    private static final String TEST_ANNOTATION_PATTERN = "org..* *Test*";
    private static final JavaType.Class KOTLIN_UNIT = (JavaType.Class) JavaType.buildType("kotlin.Unit");

    @Getter
    final String displayName = "Kotlin test methods should have return type `Unit`";

    @Getter
    final String description = "Kotlin test methods annotated with `@Test`, `@ParameterizedTest`, `@RepeatedTest`, `@TestTemplate` " +
            "should have `Unit` return type. Other return types can cause test discovery issues, " +
            "and warnings as of JUnit 5.13+. This recipe changes the return type to `Unit` and removes `return` statements.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(TEST_ANNOTATION_PATTERN, true), new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

                // Skip invalid signatures or already-correct return types
                JavaType.Method methodType = m.getMethodType();
                if (m.getBody() == null || methodType == null || TypeUtils.isOfType(methodType.getReturnType(), KOTLIN_UNIT)) {
                    return m;
                }

                // Only consider test methods
                if (!service(AnnotationService.class).matches(getCursor(), new AnnotationMatcher(TEST_ANNOTATION_PATTERN, true))) {
                    return m;
                }

                // Update method and method identifier type.
                JavaType.Method newMethodType = methodType.withReturnType(KOTLIN_UNIT);
                m = m.withMethodType(newMethodType).withName(m.getName().withType(newMethodType));

                // Add an explicit Unit return type
                if (m.getBody().getMarkers().findFirst(SingleExpressionBlock.class).isPresent()) {
                    return m.withReturnTypeExpression(new J.Identifier(
                            Tree.randomId(),
                            Space.SINGLE_SPACE,
                            Markers.EMPTY,
                            emptyList(),
                            KOTLIN_UNIT.getClassName(),
                            KOTLIN_UNIT,
                            null));
                }

                // Otherwise, there's no need for a return type expression at all.
                return m.withReturnTypeExpression(null)
                        // Remove return statements that are not in nested classes, objects, or lambdas.
                        .withBody((J.Block) new RemoveDirectReturns().visit(m.getBody(), ctx));
            }
        });
    }

    private static class RemoveDirectReturns extends KotlinVisitor<ExecutionContext> {
        @Override
        public J visitClassDeclaration(J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
            return classDeclaration.getMarkers().findFirst(KObject.class).isPresent() ?
                    classDeclaration : // Retain nested object expressions
                    super.visitClassDeclaration(classDeclaration, ctx);
        }

        @Override
        public J.Lambda visitLambda(J.Lambda lambda, ExecutionContext ctx) {
            return lambda; // Retain nested returns
        }

        @Override
        public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
            return newClass; // Retain nested returns
        }

        @Override
        public @Nullable J visitReturn(K.Return retrn, ExecutionContext ctx) {
            Expression returnExpr = retrn.getExpression().getExpression();
            return returnExpr instanceof Statement ?
                    // Retain any side effects from statements in return expressions
                    returnExpr.withPrefix(retrn.getPrefix()) :
                    // Remove any other return statements entirely
                    null;
        }
    }
}
