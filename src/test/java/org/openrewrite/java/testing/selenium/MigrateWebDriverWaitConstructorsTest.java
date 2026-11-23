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
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

class MigrateWebDriverWaitConstructorsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateWebDriverWaitConstructors())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "selenium-api-3", "selenium-support-3"));
    }

    @DocumentExample
    @Test
    void timeoutInSeconds() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.By;
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.WebElement;
              import org.openqa.selenium.support.ui.WebDriverWait;

              class Page {
                  WebElement waitForLogin(WebDriver driver) {
                      return new WebDriverWait(driver, 10).until(d -> d.findElement(By.id("login")));
                  }
              }
              """,
            """
              import org.openqa.selenium.By;
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.WebElement;
              import org.openqa.selenium.support.ui.WebDriverWait;

              import java.time.Duration;

              class Page {
                  WebElement waitForLogin(WebDriver driver) {
                      return new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> d.findElement(By.id("login")));
                  }
              }
              """
          )
        );
    }

    @Test
    void timeoutAndSleep() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.support.ui.WebDriverWait;

              class Page {
                  static final int TIMEOUT = 30;

                  WebDriverWait wait(WebDriver driver) {
                      return new WebDriverWait(driver, TIMEOUT, 250);
                  }
              }
              """,
            """
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.support.ui.WebDriverWait;

              import java.time.Duration;

              class Page {
                  static final int TIMEOUT = 30;

                  WebDriverWait wait(WebDriver driver) {
                      return new WebDriverWait(driver, Duration.ofSeconds(TIMEOUT), Duration.ofMillis(250));
                  }
              }
              """
          )
        );
    }

    @Test
    void clockAndSleeperMoveBehindTheDurations() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.support.ui.Sleeper;
              import org.openqa.selenium.support.ui.WebDriverWait;

              import java.time.Clock;

              class Page {
                  WebDriverWait wait(WebDriver driver, Clock clock, Sleeper sleeper) {
                      return new WebDriverWait(driver, clock, sleeper, 30, 250);
                  }
              }
              """,
            """
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.support.ui.Sleeper;
              import org.openqa.selenium.support.ui.WebDriverWait;

              import java.time.Clock;
              import java.time.Duration;

              class Page {
                  WebDriverWait wait(WebDriver driver, Clock clock, Sleeper sleeper) {
                      return new WebDriverWait(driver, Duration.ofSeconds(30), Duration.ofMillis(250), clock, sleeper);
                  }
              }
              """
          )
        );
    }

    @Test
    void subclass() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.support.ui.WebDriverWait;

              class LongWait extends WebDriverWait {
                  LongWait(WebDriver driver) {
                      super(driver, 60);
                  }
              }
              """,
            """
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.support.ui.WebDriverWait;

              import java.time.Duration;

              class LongWait extends WebDriverWait {
                  LongWait(WebDriver driver) {
                      super(driver, Duration.ofSeconds(60));
                  }
              }
              """
          )
        );
    }

    @Test
    void anonymousSubclassKeepsItsBody() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.support.ui.WebDriverWait;

              class Page {
                  WebDriverWait wait(WebDriver driver) {
                      return new WebDriverWait(driver, 5) {
                          @Override
                          public String toString() {
                              return "short wait";
                          }
                      };
                  }
              }
              """,
            """
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.support.ui.WebDriverWait;

              import java.time.Duration;

              class Page {
                  WebDriverWait wait(WebDriver driver) {
                      return new WebDriverWait(driver, Duration.ofSeconds(5)) {
                          @Override
                          public String toString() {
                              return "short wait";
                          }
                      };
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
              import org.openqa.selenium.support.ui.WebDriverWait;

              class Page {
                  WebDriverWait await(WebDriver driver, long timeout, long precision) {
                      return new WebDriverWait(
                          driver,
                          timeout,
                          precision
                      );
                  }
              }
              """,
            """
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.support.ui.WebDriverWait;

              import java.time.Duration;

              class Page {
                  WebDriverWait await(WebDriver driver, long timeout, long precision) {
                      return new WebDriverWait(
                          driver,
                          Duration.ofSeconds(timeout),
                          Duration.ofMillis(precision)
                      );
                  }
              }
              """
          )
        );
    }

    @Test
    void kotlinSource() {
        rewriteRun(
          spec -> spec.parser(KotlinParser.builder()
            .classpathFromResources(new InMemoryExecutionContext(), "selenium-api-3", "selenium-support-3")),
          //language=kotlin
          kotlin(
            """
              import org.openqa.selenium.WebDriver
              import org.openqa.selenium.support.ui.WebDriverWait

              fun WebDriver.await(timeout: Long, precision: Long): WebDriverWait {
                  return WebDriverWait(
                      this,
                      timeout,
                      precision
                  )
              }
              """,
            """
              import org.openqa.selenium.WebDriver
              import org.openqa.selenium.support.ui.WebDriverWait

              import java.time.Duration

              fun WebDriver.await(timeout: Long, precision: Long): WebDriverWait {
                  return WebDriverWait(
                      this,
                      Duration.ofSeconds(timeout),
                      Duration.ofMillis(precision)
                  )
              }
              """
          )
        );
    }
}
