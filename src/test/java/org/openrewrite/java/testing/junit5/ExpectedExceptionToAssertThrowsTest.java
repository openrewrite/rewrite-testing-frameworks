/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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

class ExpectedExceptionToAssertThrowsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4.13", "hamcrest-2.2"))
          .recipe(new ExpectedExceptionToAssertThrows());
    }

    @DocumentExample
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
              
              import static org.junit.jupiter.api.Assertions.assertThrows;
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              class MyTest {
              
                  @Test
                  public void testEmptyPath() {
                      Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
                          foo();
                      });
                      assertTrue(exception.getMessage().contains("Invalid location: gs://"));
                  }
                  void foo() {
                  }
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

    @SuppressWarnings("ConstantConditions")
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/77")
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
                      thrown.expectMessage("Index 1 out of bounds for length 1");
                      int b = a[1];
                  }
              }
              """,
            """
              package org.openrewrite.java.testing.junit5;
              
              import static org.junit.jupiter.api.Assertions.assertThrows;
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              public class SimpleExpectedExceptionTest {
              
                  public void statementsBeforeExpected() {
                      Throwable exception = assertThrows(IndexOutOfBoundsException.class, () -> {
                          int[] a = new int[]{1};
                          int b = a[1];
                      });
                      assertTrue(exception.getMessage().contains("Index 1 out of bounds for length 1"));
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
}
