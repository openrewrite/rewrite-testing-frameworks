/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.testing.selenium;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateTimeoutsToDurationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateTimeoutsToDuration())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "selenium-api-3", "selenium-support-3"));
    }

    @DocumentExample
    @Test
    void timeouts() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.WebDriver;

              import java.util.concurrent.TimeUnit;

              class Setup {
                  void configure(WebDriver driver) {
                      driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
                      driver.manage().timeouts().pageLoadTimeout(2, TimeUnit.MINUTES);
                      driver.manage().timeouts().setScriptTimeout(500, TimeUnit.MILLISECONDS);
                  }
              }
              """,
            """
              import org.openqa.selenium.WebDriver;

              import java.time.Duration;

              class Setup {
                  void configure(WebDriver driver) {
                      driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
                      driver.manage().timeouts().pageLoadTimeout(Duration.ofMinutes(2));
                      driver.manage().timeouts().scriptTimeout(Duration.ofMillis(500));
                  }
              }
              """
          )
        );
    }

    @Test
    void staticallyImportedUnit() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.WebDriver;

              import static java.util.concurrent.TimeUnit.SECONDS;

              class Setup {
                  void configure(WebDriver driver, long timeout) {
                      driver.manage().timeouts().implicitlyWait(timeout, SECONDS);
                  }
              }
              """,
            """
              import org.openqa.selenium.WebDriver;

              import java.time.Duration;

              class Setup {
                  void configure(WebDriver driver, long timeout) {
                      driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(timeout));
                  }
              }
              """
          )
        );
    }

    @Test
    void unitOnlyKnownAtRuntime() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.WebDriver;

              import java.util.concurrent.TimeUnit;

              class Setup {
                  void configure(WebDriver driver, long timeout, TimeUnit unit) {
                      driver.manage().timeouts().implicitlyWait(timeout, unit);
                  }
              }
              """,
            """
              import org.openqa.selenium.WebDriver;

              import java.time.Duration;
              import java.util.concurrent.TimeUnit;

              class Setup {
                  void configure(WebDriver driver, long timeout, TimeUnit unit) {
                      driver.manage().timeouts().implicitlyWait(Duration.ofMillis(unit.toMillis(timeout)));
                  }
              }
              """
          )
        );
    }

    @Test
    void microseconds() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.WebDriver;

              import java.util.concurrent.TimeUnit;

              class Setup {
                  void configure(WebDriver driver) {
                      driver.manage().timeouts().implicitlyWait(1500, TimeUnit.MICROSECONDS);
                  }
              }
              """,
            """
              import org.openqa.selenium.WebDriver;

              import java.time.Duration;
              import java.time.temporal.ChronoUnit;

              class Setup {
                  void configure(WebDriver driver) {
                      driver.manage().timeouts().implicitlyWait(Duration.of(1500, ChronoUnit.MICROS));
                  }
              }
              """
          )
        );
    }

    @Test
    void fluentWait() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.NoSuchElementException;
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.support.ui.FluentWait;
              import org.openqa.selenium.support.ui.Wait;

              import java.util.concurrent.TimeUnit;

              class Waits {
                  Wait<WebDriver> wait(WebDriver driver) {
                      return new FluentWait<>(driver)
                              .withTimeout(30, TimeUnit.SECONDS)
                              .pollingEvery(5, TimeUnit.SECONDS)
                              .ignoring(NoSuchElementException.class);
                  }
              }
              """,
            """
              import org.openqa.selenium.NoSuchElementException;
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.support.ui.FluentWait;
              import org.openqa.selenium.support.ui.Wait;

              import java.time.Duration;

              class Waits {
                  Wait<WebDriver> wait(WebDriver driver) {
                      return new FluentWait<>(driver)
                              .withTimeout(Duration.ofSeconds(30))
                              .pollingEvery(Duration.ofSeconds(5))
                              .ignoring(NoSuchElementException.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void durationAlreadyUsed() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.support.ui.FluentWait;

              import java.time.Duration;

              class Waits {
                  FluentWait<WebDriver> wait(WebDriver driver) {
                      return new FluentWait<>(driver).withTimeout(Duration.ofSeconds(30));
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsTheLineBreakBeforeTheClosingParenthesis() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.WebDriver;

              import java.util.concurrent.TimeUnit;

              class Setup {
                  void configure(WebDriver driver, long timeout) {
                      driver.manage().timeouts().implicitlyWait(
                          timeout,
                          TimeUnit.SECONDS
                      );
                  }
              }
              """,
            """
              import org.openqa.selenium.WebDriver;

              import java.time.Duration;

              class Setup {
                  void configure(WebDriver driver, long timeout) {
                      driver.manage().timeouts().implicitlyWait(
                          Duration.ofSeconds(timeout)
                      );
                  }
              }
              """
          )
        );
    }
}
