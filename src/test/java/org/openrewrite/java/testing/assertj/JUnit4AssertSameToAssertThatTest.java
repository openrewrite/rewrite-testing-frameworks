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

@SuppressWarnings({"NewClassNamingConvention", "ExcessiveLambdaUsage", "java:S2699"})
class JUnit4AssertSameToAssertThatTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4.13.2"))
          .recipe(new JUnitAssertSameToAssertThat());
    }

    @DocumentExample
    @Test
    void singleStaticMethodNoMessage() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;

              import static org.junit.Assert.assertSame;

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
              import org.junit.Test;

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
              import org.junit.Test;

              import static org.junit.Assert.assertSame;

              public class MyTest {
                  @Test
                  public void test() {
                      String str = "string";
                      assertSame("Should be the same", notification(), str);
                  }
                  private String notification() {
                      return "String";
                  }
              }
              """,
            """
              import org.junit.Test;

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
                      String str = "string";
                      org.junit.Assert.assertSame(notification(), str);
                      org.junit.Assert.assertSame("Should be the same", notification(), str);
                  }
                  private String notification() {
                      return "String";
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              public class MyTest {
                  @Test
                  public void test() {
                      String str = "string";
                      assertThat(str).isSameAs(notification());
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
    void mixedReferences() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.junit.Test;

              import static org.assertj.core.api.Assertions.*;
              import static org.junit.Assert.assertSame;

              public class MyTest {
                  @Test
                  public void test() {
                      String str = "string";
                      assertSame(notification(), str);
                      org.junit.Assert.assertSame("Should be the same", notification(), str);
                  }
                  private String notification() {
                      return "String";
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.assertj.core.api.Assertions.*;

              public class MyTest {
                  @Test
                  public void test() {
                      String str = "string";
                      assertThat(str).isSameAs(notification());
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
}
