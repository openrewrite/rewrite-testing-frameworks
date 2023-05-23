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

@SuppressWarnings({"NewClassNamingConvention", "ExcessiveLambdaUsage"})
class JUnitAssertSameToAssertThatTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9"))
          .recipe(new JUnitAssertSameToAssertThat());
    }

    @DocumentExample
    @Test
    void singleStaticMethodNoMessage() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
                            
              import static org.junit.jupiter.api.Assertions.assertSame;

              public class MyTest {
                  @Test
                  public void test() {
                      String str = "String";
                      assertSame(notification(), str);
                  }
                  private String notification() {
                      return "String";
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              public class MyTest {
                  @Test
                  public void test() {
                      String str = "String";
                      assertThat(str).isSameAs(notification());
                  }
                  private String notification() {
                      return "String";
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

              import static org.junit.jupiter.api.Assertions.assertSame;

              public class MyTest {
                  @Test
                  public void test() {
                      String str = "string";
                      assertSame(notification(), str, "Should be the same");
                  }
                  private String notification() {
                      return "String";
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              public class MyTest {
                  @Test
                  public void test() {
                      String str = "string";
                      assertThat(str).as("Should be the same").isSameAs(notification());
                  }
                  private String notification() {
                      return "String";
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

              import static org.junit.jupiter.api.Assertions.assertSame;

              public class MyTest {
                  @Test
                  public void test() {
                      String str = "string";
                      assertSame(notification(), str, () -> "Should be the same");
                  }
                  private String notification() {
                      return "String";
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              public class MyTest {
                  @Test
                  public void test() {
                      String str = "string";
                      assertThat(str).as(() -> "Should be the same").isSameAs(notification());
                  }
                  private String notification() {
                      return "String";
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
                      String str = "string";
                      org.junit.jupiter.api.Assertions.assertSame(notification(), str);
                      org.junit.jupiter.api.Assertions.assertSame(notification(), str, "Should be the same");
                      org.junit.jupiter.api.Assertions.assertSame(notification(), str, () -> "Should be the same");
                  }
                  private String notification() {
                      return "String";
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
                            
              import static org.assertj.core.api.Assertions.assertThat;
                            
              public class MyTest {
                  @Test
                  public void test() {
                      String str = "string";
                      assertThat(str).isSameAs(notification());
                      assertThat(str).as("Should be the same").isSameAs(notification());
                      assertThat(str).as(() -> "Should be the same").isSameAs(notification());
                  }
                  private String notification() {
                      return "String";
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
              import static org.junit.jupiter.api.Assertions.assertSame;
              
              public class MyTest {
                  @Test
                  public void test() {
                      String str = "string";
                      assertSame(notification(), str);
                      org.junit.jupiter.api.Assertions.assertSame(notification(), str, "Should be the same");
                      assertSame(notification(), str, () -> "Should be the same");
                  }
                  private String notification() {
                      return "String";
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.*;
              
              public class MyTest {
                  @Test
                  public void test() {
                      String str = "string";
                      assertThat(str).isSameAs(notification());
                      assertThat(str).as("Should be the same").isSameAs(notification());
                      assertThat(str).as(() -> "Should be the same").isSameAs(notification());
                  }
                  private String notification() {
                      return "String";
                  }
              }
              """
          )
        );
    }
}
