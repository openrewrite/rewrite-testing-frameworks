/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.testing.hamcrest;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AssertThatBooleanToAssertJTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new AssertThatBooleanToAssertJ())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-jupiter-api-5",
              "hamcrest-3",
              "assertj-core-3"));
    }

    @DocumentExample
    @Test
    void assertThatBoolean() {
        rewriteRun(
          //language=java
          java(
                """
              import org.junit.jupiter.api.Test;

              import static org.hamcrest.MatcherAssert.assertThat;

              class ATest {
                  @Test
                  void test() {
                      assertThat("Reason", 1 != 2);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              class ATest {
                  @Test
                  void test() {
                      assertThat(1 != 2).as("Reason").isTrue();
                  }
              }
              """
          )
        );
    }
}
