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
package org.openrewrite.java.testing.assertj;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"UnnecessaryBoxing", "ExcessiveLambdaUsage"})
class JUnitAssertNotEqualsToAssertThatTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9.3"))
          .recipe(new JUnitAssertNotEqualsToAssertThat());
    }

    @DocumentExample
    @Test
    void singleStaticMethodNoMessage() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertNotEquals;

              public class MyTest {
                  @Test
                  public void test() {
                      assertNotEquals(1, notification());
                  }
                  private Integer notification() {
                      return 2;
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).isNotEqualTo(1);
                  }
                  private Integer notification() {
                      return 2;
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
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertNotEquals;

              public class MyTest {
                  @Test
                  public void test() {
                      assertNotEquals("fred", notification(), () -> "These should not be equal");
                  }
                  private String notification() {
                      return "joe";
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).as(() -> "These should not be equal").isNotEqualTo("fred");
                  }
                  private String notification() {
                      return "joe";
                  }
              }
              """
          )
        );
    }

    @Test
    void doubleCloseToWithNoMessage() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertNotEquals;

              public class MyTest {
                  @Test
                  public void test() {
                      assertNotEquals(0.0d, notification(), 0.2d);
                  }
                  private Double notification() {
                      return 1.1d;
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.assertj.core.api.Assertions.within;

              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).isNotCloseTo(0.0d, within(0.2d));
                  }
                  private Double notification() {
                      return 1.1d;
                  }
              }
              """
          )
        );
    }

    @Test
    void doubleCloseToWithMessage() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertNotEquals;

              public class MyTest {
                  @Test
                  public void test() {
                      assertNotEquals(2.0d, notification(), 0.2d, "These should not be close.");
                  }
                  private double notification() {
                      return 0.1d;
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.assertj.core.api.Assertions.within;

              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).as("These should not be close.").isNotCloseTo(2.0d, within(0.2d));
                  }
                  private double notification() {
                      return 0.1d;
                  }
              }
              """
          )
        );
    }


    @Test
    void doubleObjectsCloseToWithMessage() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertNotEquals;

              public class MyTest {
                  @Test
                  public void test() {
                      assertNotEquals(Double.valueOf(0.0d), notification(), Double.valueOf(0.2d), () -> "These should not be close.");
                  }
                  private double notification() {
                      return Double.valueOf(1.1d);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.assertj.core.api.Assertions.within;

              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).as(() -> "These should not be close.").isNotCloseTo(Double.valueOf(0.0d), within(Double.valueOf(0.2d)));
                  }
                  private double notification() {
                      return Double.valueOf(1.1d);
                  }
              }
              """
          )
        );
    }

    @Test
    void floatCloseToWithNoMessage() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertNotEquals;
              
              public class MyTest {
                  @Test
                  public void test() {
                      assertNotEquals(2.0f, notification(), 0.2f);
                  }
                  private Float notification() {
                      return 0.1f;
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              import static org.assertj.core.api.Assertions.within;
              
              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).isNotCloseTo(2.0f, within(0.2f));
                  }
                  private Float notification() {
                      return 0.1f;
                  }
              }
              """
          )
        );
    }

    @Test
    void floatCloseToWithMessage() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertNotEquals;

              public class MyTest {
                  @Test
                  public void test() {
                      assertNotEquals(2.0f, notification(), 0.2f, "These should not be close.");
                  }
                  private float notification() {
                      return 0.1f;
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.assertj.core.api.Assertions.within;

              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).as("These should not be close.").isNotCloseTo(2.0f, within(0.2f));
                  }
                  private float notification() {
                      return 0.1f;
                  }
              }
              """
          )
        );
    }

    @Test
    void fullyQualifiedMethodWithMessage() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.io.File;

              public class MyTest {
                  @Test
                  public void test() {
                      org.junit.jupiter.api.Assertions.assertNotEquals(new File("otherFile"), notification(), "These should not be equal");
                  }
                  private File notification() {
                      return new File("someFile");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.io.File;

              import static org.assertj.core.api.Assertions.assertThat;

              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).as("These should not be equal").isNotEqualTo(new File("otherFile"));
                  }
                  private File notification() {
                      return new File("someFile");
                  }
              }
              """
          )
        );
    }
}
