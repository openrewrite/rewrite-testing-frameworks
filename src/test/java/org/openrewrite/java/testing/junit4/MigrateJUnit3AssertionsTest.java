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
package org.openrewrite.java.testing.junit4;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateJUnit3AssertionsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-4"))
          .recipe(new MigrateJUnit3Assertions());
    }

    @DocumentExample
    @Test
    void inheritedAssertionsBecomeStaticImports() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class FooTest extends TestCase {
                  public void testX() {
                      assertEquals(1, 2);
                      assertTrue("message", true);
                      fail("boom");
                  }
              }
              """,
            """
              import junit.framework.TestCase;

              import static org.junit.Assert.*;

              public class FooTest extends TestCase {
                  public void testX() {
                      assertEquals(1, 2);
                      assertTrue("message", true);
                      fail("boom");
                  }
              }
              """
          )
        );
    }

    @Test
    void qualifiedAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.Assert;
              import junit.framework.TestCase;

              public class FooTest extends TestCase {
                  public void testX() {
                      Assert.assertNull(null);
                      TestCase.assertNotNull("x");
                      FooTest.assertSame("a", "a");
                  }
              }
              """,
            """
              import junit.framework.TestCase;
              import org.junit.Assert;

              public class FooTest extends TestCase {
                  public void testX() {
                      Assert.assertNull(null);
                      Assert.assertNotNull("x");
                      Assert.assertSame("a", "a");
                  }
              }
              """
          )
        );
    }

    @Test
    void utilityClassWithStaticImport() {
        rewriteRun(
          //language=java
          java(
            """
              import static junit.framework.Assert.assertEquals;

              class Util {
                  void check(String s) {
                      assertEquals("a", s);
                  }
              }
              """,
            """
              import static org.junit.Assert.assertEquals;

              class Util {
                  void check(String s) {
                      assertEquals("a", s);
                  }
              }
              """
          )
        );
    }

    @Test
    void floatingPointComparisonGetsDelta() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class FooTest extends TestCase {
                  public void testX() {
                      assertEquals(1.5, 3.0 / 2);
                      assertEquals("msg", 1.5f, 3f / 2);
                      assertEquals(1.5, 3.0 / 2, 0.1);
                      assertEquals(Double.valueOf(1.5), Double.valueOf(1.5));
                  }
              }
              """,
            """
              import junit.framework.TestCase;

              import static org.junit.Assert.assertEquals;

              public class FooTest extends TestCase {
                  public void testX() {
                      assertEquals(1.5, 3.0 / 2, 0.0);
                      assertEquals("msg", 1.5f, 3f / 2, 0.0f);
                      assertEquals(1.5, 3.0 / 2, 0.1);
                      assertEquals(Double.valueOf(1.5), Double.valueOf(1.5));
                  }
              }
              """
          )
        );
    }

    @Test
    void ownAssertMethodHidesStaticImport() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class FooTest extends TestCase {
                  void assertEquals(Object o) {
                  }

                  public void testX() {
                      junit.framework.Assert.assertTrue(true);
                      assertEquals(1, 2);
                  }
              }
              """,
            """
              import junit.framework.TestCase;
              import org.junit.Assert;

              public class FooTest extends TestCase {
                  void assertEquals(Object o) {
                  }

                  public void testX() {
                      Assert.assertTrue(true);
                      Assert.assertEquals(1, 2);
                  }
              }
              """
          )
        );
    }

    @Test
    void leaveTestResultUsersUntouched() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;
              import junit.framework.TestResult;

              public class FooTest extends TestCase {
                  public void run(TestResult result) {
                      assertTrue(true);
                  }
              }
              """
          )
        );
    }

    @Test
    void primitiveComparedToBoxedFloatingPointStaysUnambiguous() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class FooTest extends TestCase {
                  public void testX() {
                      assertEquals(0.25, Double.valueOf(0.25));
                      assertEquals("m", Float.valueOf(1f), 1f);
                  }
              }
              """,
            """
              import junit.framework.TestCase;

              import static org.junit.Assert.assertEquals;

              public class FooTest extends TestCase {
                  public void testX() {
                      assertEquals((Object) 0.25, Double.valueOf(0.25));
                      assertEquals("m", Float.valueOf(1f), (Object) 1f);
                  }
              }
              """
          )
        );
    }

    @Test
    void negativeCastArgumentIsParenthesized() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class FooTest extends TestCase {
                  public void testX() {
                      assertEquals(-2.3, Double.valueOf(-2.3));
                  }
              }
              """,
            """
              import junit.framework.TestCase;

              import static org.junit.Assert.assertEquals;

              public class FooTest extends TestCase {
                  public void testX() {
                      assertEquals((Object) (-2.3), Double.valueOf(-2.3));
                  }
              }
              """
          )
        );
    }
}
