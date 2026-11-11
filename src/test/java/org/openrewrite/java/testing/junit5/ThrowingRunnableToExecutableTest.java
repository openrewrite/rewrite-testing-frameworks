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
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ThrowingRunnableToExecutableTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "junit-jupiter-api-5"))
          .recipeFromResources("org.openrewrite.java.testing.junit5.ThrowingRunnableToExecutable");
    }

    @Test
    void runIsExecute() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.function.ThrowingRunnable;

              class A {
                  void check(ThrowingRunnable codeToRun) throws Throwable {
                      codeToRun.run();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.function.Executable;

              class A {
                  void check(Executable codeToRun) throws Throwable {
                      codeToRun.execute();
                  }
              }
              """
          )
        );
    }
}
