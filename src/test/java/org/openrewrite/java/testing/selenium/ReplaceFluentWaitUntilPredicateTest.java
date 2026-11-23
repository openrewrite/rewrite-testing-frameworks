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

class ReplaceFluentWaitUntilPredicateTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceFluentWaitUntilPredicate())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "selenium-api-2", "selenium-remote-driver-2", "selenium-support-2", "guava"));
    }

    @DocumentExample
    @Test
    void anonymousPredicateBecomesALambda() {
        rewriteRun(
          //language=java
          java(
            """
              import com.google.common.base.Predicate;
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.support.ui.WebDriverWait;

              class Waits {
                  void waitForTitle(WebDriverWait wait, final String title) {
                      wait.until(new Predicate<WebDriver>() {
                          @Override
                          public boolean apply(WebDriver driver) {
                              return driver.getTitle().equals(title);
                          }
                      });
                  }
              }
              """,
            """
              import org.openqa.selenium.support.ui.WebDriverWait;

              class Waits {
                  void waitForTitle(WebDriverWait wait, final String title) {
                      wait.until(driver -> driver.getTitle().equals(title));
                  }
              }
              """
          )
        );
    }

    @Test
    void anonymousPredicateWithSeveralStatements() {
        rewriteRun(
          //language=java
          java(
            """
              import com.google.common.base.Predicate;
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.support.ui.WebDriverWait;

              class Waits {
                  void waitForTitle(WebDriverWait wait, final String title) {
                      wait.until(new Predicate<WebDriver>() {
                          @Override
                          public boolean apply(WebDriver driver) {
                              driver.navigate().refresh();
                              return driver.getTitle().equals(title);
                          }
                      });
                  }
              }
              """,
            """
              import org.openqa.selenium.support.ui.WebDriverWait;

              class Waits {
                  void waitForTitle(WebDriverWait wait, final String title) {
                      wait.until(driver -> {
                          driver.navigate().refresh();
                          return driver.getTitle().equals(title);
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void lambdaCastToPredicate() {
        rewriteRun(
          //language=java
          java(
            """
              import com.google.common.base.Predicate;
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.support.ui.WebDriverWait;

              class Waits {
                  void waitForTitle(WebDriverWait wait, String title) {
                      wait.until((Predicate<WebDriver>) input -> title.equals(input.getTitle()));
                  }
              }
              """,
            """
              import org.openqa.selenium.support.ui.WebDriverWait;

              class Waits {
                  void waitForTitle(WebDriverWait wait, String title) {
                      wait.until(input -> title.equals(input.getTitle()));
                  }
              }
              """
          )
        );
    }

    @Test
    void predicateFromALibrary() {
        rewriteRun(
          //language=java
          java(
            """
              import com.google.common.base.Predicate;
              import org.openqa.selenium.WebDriver;

              class Conditions {
                  static Predicate<WebDriver> ajaxCallsCompleted() {
                      return driver -> true;
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.support.ui.FluentWait;

              class Waits {
                  void waitForAjax(FluentWait<WebDriver> wait) {
                      wait.until(Conditions.ajaxCallsCompleted());
                  }
              }
              """,
            """
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.support.ui.FluentWait;

              class Waits {
                  void waitForAjax(FluentWait<WebDriver> wait) {
                      wait.until(Conditions.ajaxCallsCompleted()::apply);
                  }
              }
              """
          )
        );
    }

    @Test
    void anonymousPredicateReferringToItselfBecomesAMethodReference() {
        rewriteRun(
          //language=java
          java(
            """
              import com.google.common.base.Predicate;
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.support.ui.WebDriverWait;

              class Waits {
                  void waitForTitle(WebDriverWait wait) {
                      wait.until(new Predicate<WebDriver>() {
                          int polls;

                          @Override
                          public boolean apply(WebDriver driver) {
                              return ++polls > 3;
                          }
                      });
                  }
              }
              """,
            """
              import com.google.common.base.Predicate;
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.support.ui.WebDriverWait;

              class Waits {
                  void waitForTitle(WebDriverWait wait) {
                      wait.until(new Predicate<WebDriver>() {
                          int polls;

                          @Override
                          public boolean apply(WebDriver driver) {
                              return ++polls > 3;
                          }
                      }::apply);
                  }
              }
              """
          )
        );
    }

    @Test
    void functionIsUnchanged() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.By;
              import org.openqa.selenium.support.ui.ExpectedConditions;
              import org.openqa.selenium.support.ui.WebDriverWait;

              class Waits {
                  void waitForLogout(WebDriverWait wait) {
                      wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("logout")));
                  }
              }
              """
          )
        );
    }
}
