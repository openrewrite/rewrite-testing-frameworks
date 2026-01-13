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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AssumeNotNullToAssumptionsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-4"))
          .recipe(new AssumeNotNullToAssumptionsRecipes());
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/886")
    @Test
    void singleArg() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Assume;

              class Test {
                  void test(Object object) {
                      Assume.assumeNotNull(object);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assumptions;

              class Test {
                  void test(Object object) {
                      Assumptions.assumeFalse(object == null);
                  }
              }
              """
          )
        );
    }

    @Disabled("Refaster fails to match multiple arguments into varargs for now")
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/886")
    @Test
    void twoArgs() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Assume;

              class Test {
                  void test(Object object1, Object object2) {
                      Assume.assumeNotNull(new Object(), new Object());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assumptions;

              import java.util.stream.Stream;

              class Test {
                  void test(Object object1, Object object2) {
                      Stream.of(object1, object2).forEach((o) -> Assumptions.assumeFalse(o == null));
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/886")
    @Test
    void varargs() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Assume;

              class Test {
                  void test(Object... objects) {
                      Assume.assumeNotNull(objects);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assumptions;

              import java.util.stream.Stream;

              class Test {
                  void test(Object... objects) {
                      Stream.of(objects).forEach((o) -> Assumptions.assumeFalse(o == null));
                  }
              }
              """
          )
        );
    }
}
