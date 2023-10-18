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
    @Override
    public J visitNewClass(J.NewClass newClass, ExecutionContext executionContext) {
      J.NewClass nc = (J.NewClass) super.visitNewClass(newClass, executionContext);
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

      // apply the template and replace the `new Expectations()` statement coordinates
      // TODO: handle exception results with another template
      J.MethodInvocation newMethod = JavaTemplate.builder("when(#{any()}).thenReturn(#{});")
          .javaParser(JavaParser.fromJavaVersion().classpathFromResources(executionContext, "mockito-core-3.12"))
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
  }
}
