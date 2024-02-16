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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"UnnecessaryBoxing", "ExcessiveLambdaUsage"})
class JUnitAssertEqualsToAssertThatTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9"))
          .recipe(new JUnitAssertEqualsToAssertThat());
    }

    @DocumentExample
    @Test
    void singleStaticMethodNoMessage() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class MyTest {
                  @Test
                  public void test() {
                      assertEquals(1, notification());
                  }
                  private Integer notification() {
                      return 1;
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).isEqualTo(1);
                  }
                  private Integer notification() {
                      return 1;
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

              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class MyTest {
                  @Test
                  public void test() {
                      assertEquals("fred", notification(), () -> "These should be equal");
                  }
                  private String notification() {
                      return "fred";
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).as(() -> "These should be equal").isEqualTo("fred");
                  }
                  private String notification() {
                      return "fred";
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

              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class MyTest {
                  @Test
                  public void test() {
                      assertEquals(0.0d, notification(), 0.2d);
                  }
                  private Double notification() {
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
                      assertThat(notification()).isCloseTo(0.0d, within(0.2d));
                  }
                  private Double notification() {
                      return 0.1d;
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

              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class MyTest {
                  @Test
                  public void test() {
                      assertEquals(0.0d, notification(), 0.2d, "These should be close.");
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
                      assertThat(notification()).as("These should be close.").isCloseTo(0.0d, within(0.2d));
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

              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class MyTest {
                  @Test
                  public void test() {
                      assertEquals(Double.valueOf(0.0d), notification(), Double.valueOf(0.2d), () -> "These should be close.");
                  }
                  private double notification() {
                      return Double.valueOf(0.1d);
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
                      assertThat(notification()).as(() -> "These should be close.").isCloseTo(Double.valueOf(0.0d), within(Double.valueOf(0.2d)));
                  }
                  private double notification() {
                      return Double.valueOf(0.1d);
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
              
              import static org.junit.jupiter.api.Assertions.assertEquals;
              
              public class MyTest {
                  @Test
                  public void test() {
                      assertEquals(0.0f, notification(), 0.2f);
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
                      assertThat(notification()).isCloseTo(0.0f, within(0.2f));
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
              
              import static org.junit.jupiter.api.Assertions.assertEquals;
              
              public class MyTest {
                  @Test
                  public void test() {
                      assertEquals(0.0f, notification(), 0.2f, "These should be close.");
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
                      assertThat(notification()).as("These should be close.").isCloseTo(0.0f, within(0.2f));
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
                      org.junit.jupiter.api.Assertions.assertEquals(new File("someFile"), notification(), "These should be equal");
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
                      assertThat(notification()).as("These should be equal").isEqualTo(new File("someFile"));
                  }
                  private File notification() {
                      return new File("someFile");
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("479")
    void shouldImportWhenCustomClassIsUsed() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              package org.example;
              
              import org.junit.jupiter.api.Assertions;
              import org.junit.jupiter.api.Test;
              
              class ATest {
              
                @Test void testEquals() {
                  Assertions.assertEquals(new OwnClass(), new OwnClass());
                }
              
                public record OwnClass(String a) {
              
                  public OwnClass() {this("1");}
                }
              }
              """,
            """
              package org.example;
               
               import static org.assertj.core.api.Assertions.assertThat;
               
               import org.junit.jupiter.api.Test;
               
               class ATest {
               
                 @Test void testEquals() {
                   assertThat(new OwnClass()).isEqualTo(new OwnClass());
                 }
               
                 public record OwnClass(String a) {
               
                   public OwnClass() {this("1");}
                 }
              }
              """
          )
        );
    }
}
