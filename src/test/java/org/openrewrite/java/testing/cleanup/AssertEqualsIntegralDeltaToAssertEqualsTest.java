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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AssertEqualsIntegralDeltaToAssertEqualsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5"))
          .recipe(new AssertEqualsIntegralDeltaToAssertEquals());
    }

    @DocumentExample
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/869")
    @Test
    void removeIntDelta() {
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class Test {
                  void test() {
                      assertEquals(1, 2, 0);
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class Test {
                  void test() {
                      assertEquals(1, 2);
                  }
              }
              """
          )
        );
    }

    @Test
    void removeLongDeltaWithDoublePrecision() {
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class Test {
                  long getValue() { return 1L; }
                  void test() {
                      assertEquals(1L, getValue(), 0.0);
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class Test {
                  long getValue() { return 1L; }
                  void test() {
                      assertEquals(1L, getValue());
                  }
              }
              """
          )
        );
    }

    @Test
    void removeDeltaWithMessage() {
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class Test {
                  void test() {
                      assertEquals(1, 2, 0, "message");
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class Test {
                  void test() {
                      assertEquals(1, 2, "message");
                  }
              }
              """
          )
        );
    }

    @Test
    void removeDeltaWithSupplierMessage() {
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class Test {
                  void test() {
                      assertEquals(1L, 2L, 0.0, () -> "message");
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class Test {
                  void test() {
                      assertEquals(1L, 2L, () -> "message");
                  }
              }
              """
          )
        );
    }

    @Test
    void qualifiedAssertEquals() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Assertions;

              public class Test {
                  void test() {
                      Assertions.assertEquals(1, 2, 0);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions;

              public class Test {
                  void test() {
                      Assertions.assertEquals(1, 2);
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForFloatingPoint() {
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class Test {
                  void test() {
                      assertEquals(1.0, 2.0, 0.1);
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForMessageArg() {
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class Test {
                  void test() {
                      assertEquals(1, 2, "expected to be equal");
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForTwoArgs() {
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class Test {
                  void test() {
                      assertEquals(1, 2);
                  }
              }
              """
          )
        );
    }
}
