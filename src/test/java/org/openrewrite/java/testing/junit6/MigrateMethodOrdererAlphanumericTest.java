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
package org.openrewrite.java.testing.junit6;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateMethodOrdererAlphanumericTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5"))
          .recipe(new MigrateMethodOrdererAlphanumeric());
    }

    @DocumentExample
    @Test
    void migrateAlphanumericInTestMethodOrder() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.MethodOrderer;
              import org.junit.jupiter.api.TestMethodOrder;
              import org.junit.jupiter.api.Test;

              @TestMethodOrder(MethodOrderer.Alphanumeric.class)
              class MyTest {
                  @Test
                  void test1() {
                  }

                  @Test
                  void test2() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.MethodOrderer;
              import org.junit.jupiter.api.TestMethodOrder;
              import org.junit.jupiter.api.Test;

              @TestMethodOrder(MethodOrderer.MethodName.class)
              class MyTest {
                  @Test
                  void test1() {
                  }

                  @Test
                  void test2() {
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateImportedAlphanumeric() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.MethodOrderer.Alphanumeric;
              import org.junit.jupiter.api.TestMethodOrder;
              import org.junit.jupiter.api.Test;

              @TestMethodOrder(Alphanumeric.class)
              class MyTest {
                  @Test
                  void test1() {
                  }

                  @Test
                  void test2() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.MethodOrderer.MethodName;
              import org.junit.jupiter.api.TestMethodOrder;
              import org.junit.jupiter.api.Test;

              @TestMethodOrder(MethodName.class)
              class MyTest {
                  @Test
                  void test1() {
                  }

                  @Test
                  void test2() {
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeOtherOrderers() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.MethodOrderer;
              import org.junit.jupiter.api.TestMethodOrder;
              import org.junit.jupiter.api.Test;

              @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
              class MyTest {
                  @Test
                  void test1() {
                  }

                  @Test
                  void test2() {
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateStaticImportedAlphanumeric() {
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.MethodOrderer.Alphanumeric;
              import org.junit.jupiter.api.TestMethodOrder;
              import org.junit.jupiter.api.Test;

              @TestMethodOrder(Alphanumeric.class)
              class MyTest {
                  @Test
                  void test1() {
                  }

                  @Test
                  void test2() {
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.MethodOrderer.MethodName;
              import org.junit.jupiter.api.TestMethodOrder;
              import org.junit.jupiter.api.Test;

              @TestMethodOrder(MethodName.class)
              class MyTest {
                  @Test
                  void test1() {
                  }

                  @Test
                  void test2() {
                  }
              }
              """
          )
        );
    }
}
