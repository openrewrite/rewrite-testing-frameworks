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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AssertTrueInstanceofToAssertInstanceOfTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5", "junit-4"))
          .recipe(new AssertTrueInstanceofToAssertInstanceOf());
    }

    @DocumentExample
    @Test
    void jUnit5() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;

              import static org.junit.jupiter.api.Assertions.assertTrue;

              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertTrue(list instanceof List);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;

              import static org.junit.jupiter.api.Assertions.assertInstanceOf;

              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertInstanceOf(List.class, list);
                  }
              }
              """
          ));
    }

    @Test
    void jUnit5WithReason() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;

              import static org.junit.jupiter.api.Assertions.assertTrue;

              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertTrue(list instanceof Iterable, "Not instance of Iterable");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;

              import static org.junit.jupiter.api.Assertions.assertInstanceOf;

              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertInstanceOf(Iterable.class, list, "Not instance of Iterable");
                  }
              }
              """
          ));
    }

    @Test
    void jUnit4() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;

              import static org.junit.Assert.assertTrue;

              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertTrue(list instanceof Iterable);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;

              import static org.junit.jupiter.api.Assertions.assertInstanceOf;

              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertInstanceOf(Iterable.class, list);
                  }
              }
              """
          ));
    }

    @Test
    void jUnit4WithReason() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;

              import static org.junit.Assert.assertTrue;

              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertTrue("Not instance of Iterable", list instanceof Iterable);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;

              import static org.junit.jupiter.api.Assertions.assertInstanceOf;

              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertInstanceOf(Iterable.class, list, "Not instance of Iterable");
                  }
              }
              """
          ));
    }

    @Test
    void jUnit4GenericInstanceOf() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;

              import static org.junit.Assert.assertTrue;

              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertTrue(list instanceof List<?>);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;

              import static org.junit.jupiter.api.Assertions.assertInstanceOf;

              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertInstanceOf(List.class, list);
                  }
              }
              """
          ));
    }

    @Test
    void customType() {
        //language=java
        rewriteRun(
          java(
            """
              class Foo {}
              """
          ),
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertTrue;

              class ATest {
                  @Test
                  void test() {
                      Object obj = new Foo();
                      assertTrue(obj instanceof Foo);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertInstanceOf;

              class ATest {
                  @Test
                  void test() {
                      Object obj = new Foo();
                      assertInstanceOf(Foo.class, obj);
                  }
              }
              """
          ));
    }
}
