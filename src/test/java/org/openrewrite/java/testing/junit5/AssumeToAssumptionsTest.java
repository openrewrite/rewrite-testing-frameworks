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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AssumeToAssumptionsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "hamcrest-3"));
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/54")
    @Test
    void assumeToAssumptions() {
        rewriteRun(
          spec -> spec.recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing")
            .build()
            .activateRecipes("org.openrewrite.java.testing.junit5.JUnit5BestPractices")),
          //language=java
          java(
            """
              import org.junit.Assume;

              class Test {
                  void test() {
                      Assume.assumeTrue("One is one", true);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assumptions;

              class Test {
                  void test() {
                      Assumptions.assumeTrue(true, "One is one");
                  }
              }
              """
          )
        );
    }
}
