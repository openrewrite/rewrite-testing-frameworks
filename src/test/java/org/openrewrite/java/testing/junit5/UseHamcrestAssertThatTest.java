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
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UseHamcrestAssertThatTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4.13", "mockito-all-1.10", "hamcrest-2.2"))
          .recipe(Environment.builder()
        .scanRuntimeClasspath("org.openrewrite.java.testing.junit5")
        .build()
        .activateRecipes("org.openrewrite.java.testing.junit5.UseHamcrestAssertThat"));
    }

    @DocumentExample
    @Test
    void assertAssertThatToHamcrestMatcherAssert() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.hamcrest.CoreMatchers.is;
              import static org.junit.Assert.assertThat;
              
              class Test {
                  void test() {
                      assertThat(1 + 1, is(2));
                  }
              }
              """,
            """
              import static org.hamcrest.CoreMatchers.is;
              import static org.hamcrest.MatcherAssert.assertThat;
              
              class Test {
                  void test() {
                      assertThat(1 + 1, is(2));
                  }
              }
              """
          )
        );
    }
}
