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
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"ExcessiveLambdaUsage", "java:S2699"})
class JUnit4AssertNotNullToAssertThatTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4.13.2"))
          .recipe(new JUnitAssertNotNullToAssertThat());
    }

    @DocumentExample
    @Test
    void singleStaticMethodNoMessage() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;

              import static org.junit.Assert.assertNotNull;

              public class MyTest {
                  @Test
                  public void test() {
                      assertNotNull(notification());
                  }
                  private String notification() {
                      return "";
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).isNotNull();
                  }
                  private String notification() {
                      return "";
                  }
              }
              """
          )
        );
    }

    @Test
    void singleStaticMethodWithMessageString() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.junit.Test;

              import static org.junit.Assert.assertNotNull;

              public class MyTest {
                  @Test
                  public void test() {
                      assertNotNull("Should not be null", notification());
                  }
                  private String notification() {
                      return "";
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).as("Should not be null").isNotNull();
                  }
                  private String notification() {
                      return "";
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
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.junit.Test;

              public class MyTest {
                  @Test
                  public void test() {
                      org.junit.Assert.assertNotNull(notification());
                      org.junit.Assert.assertNotNull("Should not be null", notification());
                  }
                  private String notification() {
                      return "";
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).isNotNull();
                      assertThat(notification()).as("Should not be null").isNotNull();
                  }
                  private String notification() {
                      return "";
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
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.junit.Test;

              import static org.assertj.core.api.Assertions.*;
              import static org.junit.Assert.assertNotNull;

              public class MyTest {
                  @Test
                  public void test() {
                      assertNotNull(notification());
                      org.junit.Assert.assertNotNull("Should not be null", notification());
                  }
                  private String notification() {
                      return "";
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.assertj.core.api.Assertions.*;

              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).isNotNull();
                      assertThat(notification()).as("Should not be null").isNotNull();
                  }
                  private String notification() {
                      return "";
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/491")
    void importAddedForCustomArguments() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.junit.Test;

              import static org.junit.Assert.assertNotNull;

              public class MyTest {

                  class A {}

                  @Test
                  public void testClass() {
                      assertNotNull(new A());
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              public class MyTest {

                  class A {}

                  @Test
                  public void testClass() {
                      assertThat(new A()).isNotNull();
                  }
              }
              """
          )
        );
    }
}
