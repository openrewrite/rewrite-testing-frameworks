/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.jmockit;

import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;

@Value
@EqualsAndHashCode(callSuper = false)
public class JMockitExpectationsToMockitoWhen extends Recipe {
  @Override
  public String getDisplayName() {
    return "Rewrite JMockit Expectations";
  }

  @Override
  public String getDescription() {
    return "Rewrites JMockit `Expectations` to `Mockito.when`.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return Preconditions.check(new UsesType<>("mockit.*", false),
        new RewriteExpectationsVisitor());
  }

  private static class RewriteExpectationsVisitor extends JavaVisitor<ExecutionContext> {

    private static final String PRIMITIVE_RESULT_TEMPLATE = "when(#{any()}).thenReturn(#{});";
    private static final String OBJECT_RESULT_TEMPLATE = "when(#{any()}).thenReturn(#{any(java.lang.String)});";
    private static final String EXCEPTION_RESULT_TEMPLATE = "when(#{any()}).thenThrow(#{any()});";

    @Override
    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
      J.NewClass nc = (J.NewClass) super.visitNewClass(newClass, ctx);
      if (!(nc.getClazz() instanceof J.Identifier)) {
        return nc;
      }
      J.Identifier clazz = (J.Identifier) nc.getClazz();
      if (!clazz.getSimpleName().equals("Expectations")) {
        return nc;
      }

      // empty Expectations block is considered invalid
      assert nc.getBody() != null : "Expectations block is empty";

      // prepare the statements for moving
      J.Block innerBlock = (J.Block) nc.getBody().getStatements().get(0);

      // TODO: handle multiple mock statements
      Statement mockInvocation = innerBlock.getStatements().get(0);
      Expression result = ((J.Assignment) innerBlock.getStatements().get(1)).getAssignment();
      String template = getTemplate(result);

      // apply the template and replace the `new Expectations()` statement coordinates
      J.MethodInvocation newMethod = JavaTemplate.builder(template)
          .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
          .staticImports("org.mockito.Mockito.when")
          .build()
          .apply(
              getCursor(),
              nc.getCoordinates().replace(),
              mockInvocation,
              result
          );

      // handle import changes
      maybeAddImport("org.mockito.Mockito", "when");
      maybeRemoveImport("mockit.Expectations");

      return newMethod.withPrefix(nc.getPrefix());
    }

    /*
     * Based on the result type, we need to use a different template.
     */
    private static String getTemplate(Expression result) {
      String template;
      JavaType resultType = Objects.requireNonNull(result.getType());
      if (resultType instanceof JavaType.Primitive) {
        template = PRIMITIVE_RESULT_TEMPLATE;
      } else if (resultType instanceof JavaType.Class) {
        Class<?> resultClass;
        try {
          resultClass = Class.forName(((JavaType.Class) resultType).getFullyQualifiedName());
        } catch (ClassNotFoundException e) {
          throw new RuntimeException(e);
        }
        template = Throwable.class.isAssignableFrom(resultClass) ? EXCEPTION_RESULT_TEMPLATE : OBJECT_RESULT_TEMPLATE;
      } else {
        throw new IllegalStateException("Unexpected value: " + result.getType());
      }
      return template;
    }
  }
}
