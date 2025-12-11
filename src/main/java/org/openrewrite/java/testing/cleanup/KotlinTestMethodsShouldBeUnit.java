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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite.Description;
import org.openrewrite.NlsRewrite.DisplayName;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.KotlinVisitor;
import org.openrewrite.kotlin.marker.KObject;
import org.openrewrite.kotlin.marker.SingleExpressionBlock;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.Markers;

import java.util.Collections;

import static java.util.Objects.requireNonNull;
import static org.openrewrite.java.testing.cleanup.TestMethodsShouldBeVoid.isIntendedTestMethod;

public class KotlinTestMethodsShouldBeUnit extends Recipe {

  private static JavaType.Class KOTLIN_UNIT = (JavaType.Class) JavaType.buildType("kotlin.Unit");

  @Override
  public @DisplayName String getDisplayName() {
    return "Kotlin test methods should have unit return type";
  }

  @Override
  public @Description String getDescription() {
    return "Kotlin test methods annotated with `@Test`, `@ParameterizedTest`, `@RepeatedTest`, `@TestTemplate` " +
        "should have `Unit` return type. Non-void return types can cause test discovery issues, " +
        "and warnings as of JUnit 5.13+. This recipe changes the return type to `Unit` and removes `return` statements.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new KotlinIsoVisitor<ExecutionContext>() {
      @Override
      public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
          ExecutionContext ctx) {
        J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

        // If the method is not intended to the be a test method, do nothing.
        if (!isIntendedTestMethod(m)) {
          return m;
        }

        // If return type is already Unit, do nothing.
        JavaType.Method methodType = m.getMethodType();
        if (methodType != null && TypeUtils.isOfType(methodType.getReturnType(), KOTLIN_UNIT)) {
          return m;
        }

        // If the function is a single expression function, add an explicit Unit return type
        // Otherwise, there's no need for a return type expression at all.
        J.Block body = requireNonNull(m.getBody());
        boolean singleExprFunc = hasMarker(body, SingleExpressionBlock.class);
        TypeTree returnTypeExpr = m.getReturnTypeExpression();
        if (singleExprFunc) {
          returnTypeExpr = new J.Identifier(
              returnTypeExpr == null ? Tree.randomId() : returnTypeExpr.getId(),
              returnTypeExpr == null ? Space.SINGLE_SPACE : returnTypeExpr.getPrefix(),
              returnTypeExpr == null ? Markers.EMPTY : returnTypeExpr.getMarkers(),
              Collections.emptyList(),
              KOTLIN_UNIT.getClassName(),
              KOTLIN_UNIT,
              null);
        } else {
          returnTypeExpr = null;
        }
        m = m.withReturnTypeExpression(returnTypeExpr);

        // Update method and method identifier type.
        if (methodType != null) {
          JavaType.Method newMethodType = methodType.withReturnType(KOTLIN_UNIT);
          m = m.withMethodType(newMethodType).withName(m.getName().withType(newMethodType));
        }

        // Remove return statements that are not in nested classes, objects, or lambdas.
        return singleExprFunc ? m : m.withBody(new RemoveDirectReturns().visitBlock(body, ctx));
      }
    };
  }

  private static boolean hasMarker(J tree, Class<? extends Marker> marker) {
    return tree.getMarkers().findFirst(marker).isPresent();
  }

  private static class RemoveDirectReturns extends KotlinVisitor<ExecutionContext> {
    @Override
    public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
      return (J.Block) super.visitBlock(block, ctx);
    }

    @Override
    public @Nullable J visitReturn(K.Return retrn, ExecutionContext ctx) {
      Expression returnExpr = retrn.getExpression().getExpression();
      return (returnExpr instanceof Statement) ?
          // Retain any side effects from statements in return expressions
          returnExpr.withPrefix(retrn.getPrefix()) :
          // Remove any other return statements entirely
          null;
    }

    @Override
    public J visitLambda(J.Lambda lambda, ExecutionContext ctx) {
      return lambda; // Retain nested returns
    }

    @Override
    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
      return newClass; // Retain nested returns
    }

    @Override
    public J visitClassDeclaration(J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
      return hasMarker(classDeclaration, KObject.class) ?
          classDeclaration : // Retain nested object expressions
          super.visitClassDeclaration(classDeclaration, ctx);
    }
  }
}
