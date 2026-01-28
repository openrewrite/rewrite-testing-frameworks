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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"deprecation", "JUnitMalformedDeclaration", "JUnit3StyleTestMethodInJUnit4Class", "Convert2MethodRef"})
class ExpectedExceptionToAssertThrowsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "hamcrest-3"))
          .recipe(new ExpectedExceptionToAssertThrows());
    }

    @DocumentExample
    @Test
    void expectedExceptionRule() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.Test;
              import org.junit.rules.ExpectedException;

              class MyTest {

                  @Rule
                  ExpectedException thrown = ExpectedException.none();

                  @Test
                  public void testEmptyPath() {
                      this.thrown.expect(IllegalArgumentException.class);
                      this.thrown.expectMessage("Invalid location: gs://");
                      foo();
                  }
                  void foo() {
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.hamcrest.CoreMatchers.containsString;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.junit.jupiter.api.Assertions.assertThrows;

              class MyTest {

                  @Test
                  public void testEmptyPath() {
                      Throwable exception = assertThrows(IllegalArgumentException.class, () ->
                          foo());
                      assertThat(exception.getMessage(), containsString("Invalid location: gs://"));
                  }
                  void foo() {
                  }
              }
              """
          )
        );
    }

    @Test
    void leavesOtherRulesAlone() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.rules.TemporaryFolder;
              import org.junit.rules.ExpectedException;

              class MyTest {

                  @Rule
                  TemporaryFolder tempDir = new TemporaryFolder();

                  @Rule
                  ExpectedException thrown = ExpectedException.none();
              }
              """,
            """
              import org.junit.Rule;
              import org.junit.rules.TemporaryFolder;

              class MyTest {

                  @Rule
                  TemporaryFolder tempDir = new TemporaryFolder();
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/72")
    @Test
    void removeExpectedExceptionAndLeaveMethodAlone() {
        //language=java
        rewriteRun(
          java(
            """
              package org.openrewrite.java.testing.junit5;

              import org.junit.Rule;
              import org.junit.rules.ExpectedException;

              public class SimpleExpectedExceptionTest {
                  @Rule
                  public ExpectedException thrown = ExpectedException.none();

                  public void doNotChange() {
                      final String noChanges = "atAll";
                  }
              }
              """,
            """
              package org.openrewrite.java.testing.junit5;

              public class SimpleExpectedExceptionTest {

                  public void doNotChange() {
                      final String noChanges = "atAll";
                  }
              }
              """
          )
        );
    }

    @Test
    void refactorExceptClass() {
        //language=java
        rewriteRun(
          java(
            """
              package org.openrewrite.java.testing.junit5;

              import org.junit.Rule;
              import org.junit.rules.ExpectedException;

              public class SimpleExpectedExceptionTest {
                  @Rule
                  public ExpectedException thrown = ExpectedException.none();

                  public void throwsExceptionWithSpecificType() {
                      thrown.expect(NullPointerException.class);
                      throw new NullPointerException();
                  }
              }
              """,
            """
              package org.openrewrite.java.testing.junit5;

              import static org.junit.jupiter.api.Assertions.assertThrows;

              public class SimpleExpectedExceptionTest {

                  public void throwsExceptionWithSpecificType() {
                      assertThrows(NullPointerException.class, () -> {
                          throw new NullPointerException();
                      });
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/72")
    @Test
    void refactorExceptWithMatcher() {
        //language=java
        rewriteRun(
          java(
            """
              package org.openrewrite.java.testing.junit5;

              import org.junit.Rule;
              import org.junit.rules.ExpectedException;

              import static org.hamcrest.Matchers.isA;

              public class SimpleExpectedExceptionTest {
                  @Rule
                  public ExpectedException thrown = ExpectedException.none();

                  public void throwsExceptionWithSpecificType() {
                      thrown.expect(isA(NullPointerException.class));
                      throw new NullPointerException();
                  }
              }
              """,
            """
              package org.openrewrite.java.testing.junit5;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.isA;
              import static org.junit.jupiter.api.Assertions.assertThrows;

              public class SimpleExpectedExceptionTest {

                  public void throwsExceptionWithSpecificType() {
                      Throwable exception = assertThrows(Exception.class, () -> {
                          throw new NullPointerException();
                      });
                      assertThat(exception, isA(NullPointerException.class));
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/77")
    @SuppressWarnings("ConstantConditions")
    @Test
    void refactorExpectMessageString() {
        //language=java
        rewriteRun(
          java(
            """
              package org.openrewrite.java.testing.junit5;

              import org.junit.Rule;
              import org.junit.rules.ExpectedException;

              public class SimpleExpectedExceptionTest {
                  @Rule
                  public ExpectedException thrown = ExpectedException.none();

                  public void statementsBeforeExpected() {
                      int[] a = new int[] { 1 };
                      thrown.expect(IndexOutOfBoundsException.class);
                      thrown.expectMessage("Index 1 out of bounds for length " + a.length);
                      int b = a[1];
                  }
              }
              """,
            """
              package org.openrewrite.java.testing.junit5;

              import static org.hamcrest.CoreMatchers.containsString;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.junit.jupiter.api.Assertions.assertThrows;

              public class SimpleExpectedExceptionTest {

                  public void statementsBeforeExpected() {
                      int[] a = new int[] { 1 };
                      Throwable exception = assertThrows(IndexOutOfBoundsException.class, () -> {
                          int b = a[1];
                      });
                      assertThat(exception.getMessage(), containsString("Index 1 out of bounds for length " + a.length));
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/72")
    @Test
    void refactorExpectMessageWithMatcher() {
        //language=java
        rewriteRun(
          java(
            """
              package org.openrewrite.java.testing.junit5;

              import org.junit.Rule;
              import org.junit.rules.ExpectedException;

              import static org.hamcrest.Matchers.containsString;

              public class ExampleTests {
                  @Rule
                  public ExpectedException thrown = ExpectedException.none();

                  public void expectMessageWithMatcher() {
                      this.thrown.expectMessage(containsString("rewrite expectMessage"));
                      throw new NullPointerException("rewrite expectMessage with hamcrest matcher.");
                  }
              }
              """,
            """
              package org.openrewrite.java.testing.junit5;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.containsString;
              import static org.junit.jupiter.api.Assertions.assertThrows;

              public class ExampleTests {

                  public void expectMessageWithMatcher() {
                      Throwable exception = assertThrows(Exception.class, () -> {
                          throw new NullPointerException("rewrite expectMessage with hamcrest matcher.");
                      });
                      assertThat(exception.getMessage(), containsString("rewrite expectMessage"));
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/72")
    @Test
    void refactorExpectCauseWithMatchers() {
        //language=java
        rewriteRun(
          java(
            """
              package org.openrewrite.java.testing.junit5;

              import org.junit.Rule;
              import org.junit.rules.ExpectedException;

              import static org.hamcrest.Matchers.nullValue;

              public class ExampleTests {
                  @Rule
                  public ExpectedException thrown = ExpectedException.none();

                  public void expectCause() {
                      this.thrown.expectCause(nullValue());
                      throw new NullPointerException("rewrite expectMessage with hamcrest matcher.");
                  }
              }
              """,
            """
              package org.openrewrite.java.testing.junit5;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.nullValue;
              import static org.junit.jupiter.api.Assertions.assertThrows;

              public class ExampleTests {

                  public void expectCause() {
                      Throwable exception = assertThrows(Exception.class, () -> {
                          throw new NullPointerException("rewrite expectMessage with hamcrest matcher.");
                      });
                      assertThat(exception.getCause(), nullValue());
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/72")
    @Test
    void refactorExpectException() {
        //language=java
        rewriteRun(
          java(
            """
              package org.openrewrite.java.testing.junit5;

              import org.junit.Rule;
              import org.junit.rules.ExpectedException;

              import static org.hamcrest.Matchers.*;

              public class ExampleTests {
                  @Rule
                  public ExpectedException thrown = ExpectedException.none();

                  public void expectExceptionUseCases() {
                      this.thrown.expect(isA(NullPointerException.class));
                      this.thrown.expectMessage(containsString("rewrite expectMessage"));
                      this.thrown.expectCause(nullValue());
                      throw new NullPointerException("rewrite expectMessage with hamcrest matcher.");
                  }
              }
              """,
            """
              package org.openrewrite.java.testing.junit5;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.*;
              import static org.junit.jupiter.api.Assertions.assertThrows;

              public class ExampleTests {

                  public void expectExceptionUseCases() {
                      Throwable exception = assertThrows(Exception.class, () -> {
                          throw new NullPointerException("rewrite expectMessage with hamcrest matcher.");
                      });
                      assertThat(exception, isA(NullPointerException.class));
                      assertThat(exception.getMessage(), containsString("rewrite expectMessage"));
                      assertThat(exception.getCause(), nullValue());
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/55")
    @Test
    void preserveThrowsWhenCodeBeforeExpectThrowsCheckedException() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.Test;
              import org.junit.rules.ExpectedException;

              class MyTest {

                  @Rule
                  ExpectedException thrown = ExpectedException.none();

                  @Test
                  public void testMethod() throws InterruptedException {
                      setup();
                      this.thrown.expect(IllegalArgumentException.class);
                      doSomething();
                  }

                  void setup() throws InterruptedException {
                      Thread.sleep(100);
                  }

                  void doSomething() {
                      throw new IllegalArgumentException();
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.junit.jupiter.api.Assertions.assertThrows;

              class MyTest {

                  @Test
                  public void testMethod() throws InterruptedException {
                      setup();
                      assertThrows(IllegalArgumentException.class, () ->
                          doSomething());
                  }

                  void setup() throws InterruptedException {
                      Thread.sleep(100);
                  }

                  void doSomething() {
                      throw new IllegalArgumentException();
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/563")
    @Test
    void expectedCheckedExceptionThrowsRemoved() {
        //language=java
        rewriteRun(
          java(
            """
              import java.io.IOException;

              import org.junit.Rule;
              import org.junit.Test;
              import org.junit.rules.ExpectedException;

              class MyTest {

                  @Rule
                  ExpectedException thrown = ExpectedException.none();

                  @Test
                  public void testEmptyPath() throws IOException{
                      this.thrown.expect(IOException.class);
                      foo();
                  }
                  void foo() throws IOException {
                      throw new IOException();
                  }
              }
              """,
            """
              import java.io.IOException;

              import static org.junit.jupiter.api.Assertions.assertThrows;
              import org.junit.Test;

              class MyTest {

                  @Test
                  public void testEmptyPath() {
                      assertThrows(IOException.class, () ->
                          foo());
                  }
                  void foo() throws IOException {
                      throw new IOException();
                  }
              }
              """
          )
        );
    }

    @Test
    void refactorExpectExceptionWithConditionalStatement() {
        //language=java
        rewriteRun(
          java(
            """
              package org.openrewrite.java.testing.junit5;

              import org.junit.Rule;
              import org.junit.Test;
              import org.junit.rules.ExpectedException;

              public class BranchingExpectedExceptionTest {
                  @Rule
                  public ExpectedException thrown = ExpectedException.none();

                  @Test
                  public void testWithBranch() {
                      boolean condition = true;
                      this.thrown.expect(IllegalArgumentException.class);
                      this.thrown.expectMessage("Error message");

                      if (condition) {
                          throw new IllegalArgumentException("Error message");
                      } else {
                          throw new IllegalArgumentException("Different message");
                      }
                  }
              }
              """,
            """
              package org.openrewrite.java.testing.junit5;

              import org.junit.Test;

              import static org.hamcrest.CoreMatchers.containsString;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.junit.jupiter.api.Assertions.assertThrows;

              public class BranchingExpectedExceptionTest {

                  @Test
                  public void testWithBranch() {
                      boolean condition = true;
                      Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

                          if (condition) {
                              throw new IllegalArgumentException("Error message");
                          } else {
                              throw new IllegalArgumentException("Different message");
                          }
                      });
                      assertThat(exception.getMessage(), containsString("Error message"));
                  }
              }
              """
          )
        );
    }

    @Test
    void refactorExpectExceptionWithConditionalThrow() {
        //language=java
        rewriteRun(
          java(
            """
              package org.openrewrite.java.testing.junit5;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.equalTo;

              import org.junit.Rule;
              import org.junit.Test;
              import org.junit.rules.ExpectedException;

              public class ConditionalThrowTest {
                  @Rule
                  public ExpectedException thrown = ExpectedException.none();

                  @Test
                  public void testConditionalThrow() {
                      boolean shouldThrow = true;
                      if (shouldThrow) {
                          this.thrown.expect(IllegalArgumentException.class);
                          this.thrown.expectMessage("input must be greater than or equal to zero");
                          foo(-1);
                      } else {
                          assertThat(foo(2), equalTo(4));
                      }
                  }
                  public int foo(int x) {
                      if (x < 0) {
                          throw new IllegalArgumentException("input must be greater than or equal to zero");
                      } else {
                          return 2 * x;
                      }
                  }
              }
              """,
            """
              package org.openrewrite.java.testing.junit5;

              import static org.hamcrest.CoreMatchers.containsString;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.equalTo;
              import static org.junit.jupiter.api.Assertions.assertThrows;

              import org.junit.Test;

              public class ConditionalThrowTest {

                  @Test
                  public void testConditionalThrow() {
                      boolean shouldThrow = true;
                      if (shouldThrow) {
                          Throwable exception = assertThrows(IllegalArgumentException.class, () ->
                              foo(-1));
                          assertThat(exception.getMessage(), containsString("input must be greater than or equal to zero"));
                      } else {
                          assertThat(foo(2), equalTo(4));
                      }
                  }
                  public int foo(int x) {
                      if (x < 0) {
                          throw new IllegalArgumentException("input must be greater than or equal to zero");
                      } else {
                          return 2 * x;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void refactorExpectExceptionWithConditionalThrowNoElse() {
        //language=java
        rewriteRun(
          java(
            """
              package org.openrewrite.java.testing.junit5;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.equalTo;

              import org.junit.Rule;
              import org.junit.Test;
              import org.junit.rules.ExpectedException;

              public class ConditionalThrowTest {
                  @Rule
                  public ExpectedException thrown = ExpectedException.none();

                  @Test
                  public void testConditionalThrow() {
                      int x = 2;
                      if (x < 0) {
                          this.thrown.expect(IllegalArgumentException.class);
                          this.thrown.expectMessage("input must be greater than or equal to zero");
                      }
                      int y = foo(x);
                      assertThat(y, equalTo(4));
                  }
                  public int foo(int x) {
                      if (x < 0) {
                          throw new IllegalArgumentException("input must be greater than or equal to zero");
                      } else {
                          return 2 * x;
                      }
                  }
              }
              """,
            """
              package org.openrewrite.java.testing.junit5;

              import static org.hamcrest.CoreMatchers.containsString;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.equalTo;
              import static org.junit.jupiter.api.Assertions.assertThrows;

              import org.junit.Test;

              public class ConditionalThrowTest {

                  @Test
                  public void testConditionalThrow() {
                      int x = 2;
                      if (x < 0) {
                          Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
                              int y = foo(x);
                              assertThat(y, equalTo(4));
                          });
                          assertThat(exception.getMessage(), containsString("input must be greater than or equal to zero"));
                          return;
                      }
                      int y = foo(x);
                      assertThat(y, equalTo(4));
                  }
                  public int foo(int x) {
                      if (x < 0) {
                          throw new IllegalArgumentException("input must be greater than or equal to zero");
                      } else {
                          return 2 * x;
                      }
                  }
              }
              """
          )
        );
    }
}
