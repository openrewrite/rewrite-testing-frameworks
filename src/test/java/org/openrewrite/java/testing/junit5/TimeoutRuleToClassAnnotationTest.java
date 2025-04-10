/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class TimeoutRuleToClassAnnotationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "hamcrest-3"))
          .recipe(new TimeoutRuleToClassAnnotation());
    }

    @DocumentExample
    @Test
    void defaultTimeUnit() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.rules.Timeout;
              import java.util.concurrent.TimeUnit;

              class MyTest {

                  @Rule
                  public Timeout timeout = new Timeout(30);

                  void testMethod() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Timeout;

              import java.util.concurrent.TimeUnit;

              @Timeout(value = 30, unit = TimeUnit.MILLISECONDS)
              class MyTest {

                  void testMethod() {
                  }
              }
              """
          )
        );
    }

    @Test
    void withSecondsTimeUnit() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.rules.Timeout;
              import java.util.concurrent.TimeUnit;

              class MyTest {

                  @Rule
                  public Timeout timeout = new Timeout(30, TimeUnit.SECONDS);

                  void testMethod() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Timeout;

              import java.util.concurrent.TimeUnit;

              @Timeout(value = 30, unit = TimeUnit.SECONDS)
              class MyTest {

                  void testMethod() {
                  }
              }
              """
          )
        );
    }

    @Test
    void withMinutesTimeUnit() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.rules.Timeout;
              import java.util.concurrent.TimeUnit;

              class MyTest {

                  @Rule
                  public Timeout timeout = new Timeout(2, TimeUnit.MINUTES);

                  void testMethod() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Timeout;

              import java.util.concurrent.TimeUnit;

              @Timeout(value = 2, unit = TimeUnit.MINUTES)
              class MyTest {

                  void testMethod() {
                  }
              }
              """
          )
        );
    }

    @Test
    void millisBuilder() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.rules.Timeout;
              import java.util.concurrent.TimeUnit;

              class MyTest {

                  @Rule
                  public Timeout timeout = Timeout.millis(30);

                  void testMethod() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Timeout;

              import java.util.concurrent.TimeUnit;

              @Timeout(value = 30, unit = TimeUnit.MILLISECONDS)
              class MyTest {

                  void testMethod() {
                  }
              }
              """
          )
        );
    }

    @Test
    void secondsBuilder() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.rules.Timeout;
              import java.util.concurrent.TimeUnit;

              class MyTest {

                  @Rule
                  public Timeout timeout = Timeout.seconds(30);

                  void testMethod() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Timeout;

              import java.util.concurrent.TimeUnit;

              @Timeout(value = 30, unit = TimeUnit.SECONDS)
              class MyTest {

                  void testMethod() {
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
              import org.junit.rules.Timeout;
              import org.junit.rules.TemporaryFolder;

              import java.util.concurrent.TimeUnit;

              class MyTest {

                  @Rule
                  public Timeout timeout = new Timeout(30, TimeUnit.SECONDS);

                  @Rule
                  TemporaryFolder tempDir = new TemporaryFolder();
              }
              """,
            """
              import org.junit.Rule;
              import org.junit.jupiter.api.Timeout;
              import org.junit.rules.TemporaryFolder;

              import java.util.concurrent.TimeUnit;

              @Timeout(value = 30, unit = TimeUnit.SECONDS)
              class MyTest {

                  @Rule
                  TemporaryFolder tempDir = new TemporaryFolder();
              }
              """
          )
        );
    }

    @Test
    void notRemoveRulesIfNotInitialized() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.rules.Timeout;
              import java.util.concurrent.TimeUnit;

              class MyTest {

                  @Rule
                  public Timeout timeout = null;

                  void testMethod() {
                  }
              }
              """
          )
        );
    }

    @Test
    void notRemoveRulesWithBuilder() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.rules.Timeout;
              import java.util.concurrent.TimeUnit;

              class MyTest {

                  @Rule
                  public Timeout timeout = Timeout.builder()
                                          .withTimeout(2, TimeUnit.SECONDS)
                                          .withLookingForStuckThread(true)
                                          .build();

                  void testMethod() {
                  }
              }
              """
          )
        );
    }
}
