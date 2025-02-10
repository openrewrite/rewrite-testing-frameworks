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
package org.openrewrite.java.testing.assertj;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"NewClassNamingConvention", "java:S2699"})
class JUnit4FailToAssertJFailTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4.13.2"))
          .recipe(new JUnitFailToAssertJFail());
    }

    @DocumentExample
    @Test
    void singleStaticMethodNoMessage() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;

              import static org.junit.Assert.fail;

              public class MyTest {
                  @Test
                  public void test() {
                      fail();
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.assertj.core.api.Assertions.fail;

              public class MyTest {
                  @Test
                  public void test() {
                      fail("");
                  }
              }
              """
          )
        );
    }

    @Test
    void singleStaticMethodWithMessage() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;

              import static org.junit.Assert.fail;

              public class MyTest {
                  @Test
                  public void test() {
                      fail("This should fail");
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.assertj.core.api.Assertions.fail;

              public class MyTest {
                  @Test
                  public void test() {
                      fail("This should fail");
                  }
              }
              """
          )
        );
    }

    @Test
    void inlineReference() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;

              public class MyTest {
                  @Test
                  public void test() {
                      org.junit.Assert.fail();
                      org.junit.Assert.fail("This should fail");
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.assertj.core.api.Assertions.fail;

              public class MyTest {
                  @Test
                  public void test() {
                      fail("");
                      fail("This should fail");
                  }
              }
              """
          )
        );
    }

    @Test
    void mixedReferences() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;

              import static org.junit.Assert.fail;

              public class MyTest {
                  @Test
                  public void test() {
                      fail();
                      org.junit.Assert.fail("This should fail");
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.assertj.core.api.Assertions.fail;

              public class MyTest {
                  @Test
                  public void test() {
                      fail("");
                      fail("This should fail");
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/504")
    void stringVariableArgument() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Assert;
              import org.junit.Test;

              public class OpenrewriteTest {
                  @Test
                  public void smokeTest() {
                      String failMessage = "OpenrewriteTest.smokeTest() is not implemented yet";
                      Assert.fail(failMessage);
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.assertj.core.api.Assertions.fail;

              public class OpenrewriteTest {
                  @Test
                  public void smokeTest() {
                      String failMessage = "OpenrewriteTest.smokeTest() is not implemented yet";
                      fail(failMessage);
                  }
              }
              """
          )
        );
    }
}
