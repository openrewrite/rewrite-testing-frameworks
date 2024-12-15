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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"NewClassNamingConvention", "ExcessiveLambdaUsage"})
class JUnitAssertNullToAssertThatTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9"))
          .recipe(new JUnitAssertNullToAssertThat());
    }

    @DocumentExample
    @Test
    void singleStaticMethodNoMessage() {
        //language=java
        rewriteRun(
          java(
            """
                  import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertNull;

              public class MyTest {
                  @Test
                  public void test() {
                      assertNull(notification());
                  }
                  private String notification() {
                      return null;
                  }
              }
              """,
            """
                  import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).isNull();
                  }
                  private String notification() {
                      return null;
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
                            
              import static org.junit.jupiter.api.Assertions.assertNull;
                            
              public class MyTest {
                  @Test
                  public void test() {
                      assertNull(notification(), "Should be null");
                  }
                  private String notification() {
                      return null;
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
                            
              import static org.assertj.core.api.Assertions.assertThat;
                            
              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).as("Should be null").isNull();
                  }
                  private String notification() {
                      return null;
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

              import static org.junit.jupiter.api.Assertions.assertNull;

              public class MyTest {
                  @Test
                  public void test() {
                      assertNull(notification(), () -> "Should be null");
                  }
                  private String notification() {
                      return null;
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).as(() -> "Should be null").isNull();
                  }
                  private String notification() {
                      return null;
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
                      org.junit.jupiter.api.Assertions.assertNull(notification());
                      org.junit.jupiter.api.Assertions.assertNull(notification(), "Should be null");
                      org.junit.jupiter.api.Assertions.assertNull(notification(), () -> "Should be null");
                  }
                  private String notification() {
                      return null;
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
                            
              import static org.assertj.core.api.Assertions.assertThat;
                            
              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).isNull();
                      assertThat(notification()).as("Should be null").isNull();
                      assertThat(notification()).as(() -> "Should be null").isNull();
                  }
                  private String notification() {
                      return null;
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
              import static org.junit.jupiter.api.Assertions.assertNull;
                            
              public class MyTest {
                  @Test
                  public void test() {
                      assertNull(notification());
                      org.junit.jupiter.api.Assertions.assertNull(notification(), "Should be null");
                      assertNull(notification(), () -> "Should be null");
                  }
                  private String notification() {
                      return null;
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
                            
              import static org.assertj.core.api.Assertions.*;
                            
              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).isNull();
                      assertThat(notification()).as("Should be null").isNull();
                      assertThat(notification()).as(() -> "Should be null").isNull();
                  }
                  private String notification() {
                      return null;
                  }
              }
              """
          )
        );
    }
}
