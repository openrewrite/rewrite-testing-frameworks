/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.testing.assertj;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AssertJBestPracticesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3.24"))
          .recipeFromResources("org.openrewrite.java.testing.assertj.Assertj");
    }

    @DocumentExample
    @Test
    void convertsIsEqualToEmptyString() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;
              class Test {
                  void test() {
                      assertThat("test").isEqualTo("");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;
              class Test {
                  void test() {
                      assertThat("test").isEmpty();
                  }
              }
              """
          )
        );
    }
}
