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

@SuppressWarnings({"ExcessiveLambdaUsage"})
class JUnitAssertNotNullToAssertThatTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5"))
          .recipe(new JUnitAssertNotNullToAssertThat());
    }

    @DocumentExample
    @Test
    void singleStaticMethodNoMessage() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertNotNull;

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
              import org.junit.jupiter.api.Test;

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
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertNotNull;

              public class MyTest {
                  @Test
                  public void test() {
                      assertNotNull(notification(), "Should not be null");
                  }
                  private String notification() {
                      return "";
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

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
    void singleStaticMethodWithMessageSupplier() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertNotNull;

              public class MyTest {
                  @Test
                  public void test() {
                      assertNotNull(notification(), () -> "Should not be null");
                  }
                  private String notification() {
                      return "";
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).as(() -> "Should not be null").isNotNull();
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
              import org.junit.jupiter.api.Test;

              public class MyTest {
                  @Test
                  public void test() {
                      org.junit.jupiter.api.Assertions.assertNotNull(notification());
                      org.junit.jupiter.api.Assertions.assertNotNull(notification(), "Should not be null");
                      org.junit.jupiter.api.Assertions.assertNotNull(notification(), () -> "Should not be null");
                  }
                  private String notification() {
                      return "";
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).isNotNull();
                      assertThat(notification()).as("Should not be null").isNotNull();
                      assertThat(notification()).as(() -> "Should not be null").isNotNull();
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
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.*;
              import static org.junit.jupiter.api.Assertions.assertNotNull;

              public class MyTest {
                  @Test
                  public void test() {
                      assertNotNull(notification());
                      org.junit.jupiter.api.Assertions.assertNotNull(notification(), "Should not be null");
                      assertNotNull(notification(), () -> "Should not be null");
                  }
                  private String notification() {
                      return "";
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.*;

              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).isNotNull();
                      assertThat(notification()).as("Should not be null").isNotNull();
                      assertThat(notification()).as(() -> "Should not be null").isNotNull();
                  }
                  private String notification() {
                      return "";
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/491")
    @Test
    void importAddedForCustomArguments() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertNotNull;

              class TTest {

                  class A {}

                  @Test
                  public void testClass() {
                      assertNotNull(new A());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              class TTest {

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
