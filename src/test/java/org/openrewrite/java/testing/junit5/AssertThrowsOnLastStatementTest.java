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
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AssertThrowsOnLastStatementTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            //.logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5"))
          .recipe(new AssertThrowsOnLastStatement());
    }

    @DocumentExample
    @Test
    void applyToLastStatementWithDeclaringVariableThreeLines() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.junit.jupiter.api.Assertions.assertThrows;

              class MyTest {

                  @Test
                  public void test() {
                      Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
                          foo();
                          System.out.println("foo");
                          foo();
                      });
                      assertEquals("Error message", exception.getMessage());
                  }
                  void foo() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.junit.jupiter.api.Assertions.assertThrows;

              class MyTest {

                  @Test
                  public void test() {
                      foo();
                      System.out.println("foo");
                      Throwable exception = assertThrows(IllegalArgumentException.class, () ->
                          foo());
                      assertEquals("Error message", exception.getMessage());
                  }
                  void foo() {
                  }
              }
              """
          )
        );
    }

    @Test
    void applyToLastStatementWithDeclaringVariableThreeLinesHasLineBefore() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.junit.jupiter.api.Assertions.assertThrows;

              class MyTest {

                  @Test
                  public void test() {
                      System.out.println("bla");
                      Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
                          foo();
                          System.out.println("foo");
                          foo();
                      });
                      assertEquals("Error message", exception.getMessage());
                  }
                  void foo() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.junit.jupiter.api.Assertions.assertThrows;

              class MyTest {

                  @Test
                  public void test() {
                      System.out.println("bla");
                      foo();
                      System.out.println("foo");
                      Throwable exception = assertThrows(IllegalArgumentException.class, () ->
                          foo());
                      assertEquals("Error message", exception.getMessage());
                  }
                  void foo() {
                  }
              }
              """
          )
        );
    }

    @Test
    void applyToLastStatementNoDeclaringVariableTwoLinesNoLinesAfter() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertThrows;

              class MyTest {

                  @Test
                  public void test() {
                      assertThrows(IllegalArgumentException.class, () -> {
                          System.out.println("foo");
                          foo();
                      });
                  }
                  void foo() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertThrows;

              class MyTest {

                  @Test
                  public void test() {
                      System.out.println("foo");
                      assertThrows(IllegalArgumentException.class, () ->
                          foo());
                  }
                  void foo() {
                  }
              }
              """
          )
        );
    }

    @Test
    void applyToLastStatementHasMessage() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertThrows;

              class MyTest {

                  @Test
                  public void test() {
                      assertThrows(IllegalArgumentException.class, () -> {
                          System.out.println("foo");
                          foo();
                      }, "message");
                  }
                  void foo() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertThrows;

              class MyTest {

                  @Test
                  public void test() {
                      System.out.println("foo");
                      assertThrows(IllegalArgumentException.class, () -> {
                          foo();
                      }, "message");
                  }
                  void foo() {
                  }
              }
              """
          )
        );
    }

    @Test
    void makeNoChangesAsOneLine() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.junit.jupiter.api.Assertions.assertThrows;

              class MyTest {

                  @Test
                  public void test() {
                      Throwable exception = assertThrows(IllegalArgumentException.class, () -> foo());
                      assertEquals("Error message", exception.getMessage());
                  }
                  void foo() {
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/618")
    void bodyNull() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.*;

              class MyTest {

                  @Test
                  void test() {
                      assertThrows(IllegalStateException.class, () -> System.out.println("foo"));
                  }

                  interface InnerInterface {
                      String createParser(String input);
                  }
              }
              """
          )
        );
    }

    @Test
    void lastStatementHasArgumentWhichIsMethodCall() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.*;

              class MyTest {

                  @Test
                  void test() {
                      assertThrows(Exception.class, () -> {
                          doA();
                          doB();
                          testThing(getC());
                      });
                  }

                  void doA();
                  void doB();
                  String getC();
                  void testThing(String c);
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.*;

              class MyTest {

                  @Test
                  void test() {
                      doA();
                      doB();
                      String c = getC();
                      assertThrows(Exception.class, () ->
                          testThing(c));
                  }

                  void doA();
                  void doB();
                  String getC();
                  void testThing(String c);
              }
              """
          )
        );
    }

    @Test
    void lastStatementHasArgumentWhichIsChainedMethodCall() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.lang.StringBuilder;

              import static org.junit.jupiter.api.Assertions.*;

              class MyTest {

                  @Test
                  void test() {
                      assertThrows(Exception.class, () -> {
                          doA();
                          doB();
                          testThing(getC().toString());
                      });
                  }

                  void doA();
                  void doB();
                  StringBuilder getC();
                  void testThing(String c);
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.lang.StringBuilder;

              import static org.junit.jupiter.api.Assertions.*;

              class MyTest {

                  @Test
                  void test() {
                      doA();
                      doB();
                      String toString = getC().toString();
                      assertThrows(Exception.class, () ->
                          testThing(toString));
                  }

                  void doA();
                  void doB();
                  StringBuilder getC();
                  void testThing(String c);
              }
              """
          )
        );
    }

    @Test
    void lastStatementHasArgumentWhichIsExpression() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.*;

              class MyTest {

                  @Test
                  void test() {
                      assertThrows(Exception.class, () -> {
                          doA();
                          doB();
                          testThing(getC() == 1);
                      });
                  }

                  void doA();
                  void doB();
                  int getC();
                  void testThing(boolean c);
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.*;

              class MyTest {

                  @Test
                  void test() {
                      doA();
                      doB();
                      boolean x = getC() == 1;
                      assertThrows(Exception.class, () ->
                          testThing(x));
                  }

                  void doA();
                  void doB();
                  int getC();
                  void testThing(boolean c);
              }
              """
          )
        );
    }

    @Test
    void lastStatementHasArgumentWhichNeedImport() {
        //language=java
        rewriteRun(
          java(
            """
             import java.nio.file.Path;

             class Tester {
                 public static void testThing(Path path) {}
             }
             """
          ),
          java(
            """
              import org.junit.jupiter.api.Test;

              import java.nio.file.Paths;

              import static org.junit.jupiter.api.Assertions.*;

              class MyTest {

                  @Test
                  void test() {
                      assertThrows(Exception.class, () -> {
                          doA();
                          doB();
                          Tester.testThing(Paths.get("file.txt"));
                      });
                  }

                  void doA();
                  void doB();
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import java.nio.file.Path;
              import java.nio.file.Paths;

              import static org.junit.jupiter.api.Assertions.*;

              class MyTest {

                  @Test
                  void test() {
                      doA();
                      doB();
                      Path x = Paths.get("file.txt");
                      assertThrows(Exception.class, () ->
                          Tester.testThing(x));
                  }

                  void doA();
                  void doB();
              }
              """
          )
        );
    }

    @Test
    void lastStatementHasArgumentWhichNeedImportFromSource() {
        //language=java
        rewriteRun(
          java(
            """
              package org.test.other;
              public class SomeObject {}
              public class SomeObjectProvider {
                  public static SomeObject getSomeObject() {
                      return null;
                  }
                  public static void testThing(SomeObject someObject) {}
              }
              """
          ),
          java(
            """
              package org.test;

              import org.junit.jupiter.api.Test;
              import org.test.other.SomeObjectProvider;

              import static org.junit.jupiter.api.Assertions.*;

              class MyTest {

                  @Test
                  void test() {
                      assertThrows(Exception.class, () -> {
                          doA();
                          doB();
                          SomeObjectProvider.testThing(SomeObjectProvider.getSomeObject());
                      });
                  }

                  void doA();
                  void doB();
              }
              """,
            """
              package org.test;

              import org.junit.jupiter.api.Test;
              import org.test.other.SomeObject;
              import org.test.other.SomeObjectProvider;

              import static org.junit.jupiter.api.Assertions.*;

              class MyTest {

                  @Test
                  void test() {
                      doA();
                      doB();
                      SomeObject someObject = SomeObjectProvider.getSomeObject();
                      assertThrows(Exception.class, () ->
                          SomeObjectProvider.testThing(someObject));
                  }

                  void doA();
                  void doB();
              }
              """
          )
        );
    }

    @Test
    void uniqueVariableNames() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.*;

              class MyTest {

                  @Test
                  void test() {
                      assertThrows(Exception.class, () -> {
                          getA();
                          testThing(getB(), getC());
                      });
                  }

                  String getA() { return "A"; }
                  String getB() { return "B"; }
                  String getC() { return "C"; }
                  void testThing(String one, String two) {}
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.*;

              class MyTest {

                  @Test
                  void test() {
                      getA();
                      String b = getB();
                      String c = getC();
                      assertThrows(Exception.class, () ->
                          testThing(b, c));
                  }

                  String getA() { return "A"; }
                  String getB() { return "B"; }
                  String getC() { return "C"; }
                  void testThing(String one, String two) {}
              }
              """
          )
        );
    }

    @Disabled("Creates duplicate variables `b`")
    @Test
    void duplicateVariableNames() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.*;

              class MyTest {

                  @Test
                  void test() {
                      assertThrows(Exception.class, () -> {
                          getA();
                          testThing(getB(), getB());
                      });
                  }

                  String getA() { return "A"; }
                  String getB() { return "B"; }
                  void testThing(String one, String two) {}
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.*;

              class MyTest {

                  @Test
                  void test() {
                      getA();
                      String b = getB();
                      String b1 = getB();
                      assertThrows(Exception.class, () ->
                          testThing(b, c));
                  }

                  String getA() { return "A"; }
                  String getB() { return "B"; }
                  void testThing(String one, String two) {}
              }
              """
          )
        );
    }

    @Disabled("Not implemented yet")
    @Test
    void lambdaWithSingleStatementStillExtractsVariable() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.*;

              class MyTest {

                  @Test
                  void test() {
                      assertThrows(Exception.class, () -> {
                          testThing(getA());
                      });
                  }

                  String getA() { return "A"; }
                  void testThing(String one) {}
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.*;

              class MyTest {

                  @Test
                  void test() {
                      String a = getA();
                      assertThrows(Exception.class, () ->
                          testThing(a));
                  }

                  String getA() { return "A"; }
                  void testThing(String one) {}
              }
              """
          )
        );
    }
}
