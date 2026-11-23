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

class ExtendAbstractWebDriverEventListenerTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ExtendAbstractWebDriverEventListener())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "selenium-api-3", "selenium-remote-driver-3", "selenium-support-3"));
    }

    @DocumentExample
    @Test
    void listenerWrittenForAnEarlierSelenium() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.By;
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.WebElement;
              import org.openqa.selenium.support.events.WebDriverEventListener;

              import java.io.Serializable;

              class LoggingListener implements WebDriverEventListener, Serializable {
                  @Override
                  public void beforeClickOn(WebElement element, WebDriver driver) {
                      System.out.println("Clicking " + element);
                  }

                  @Override
                  public void afterFindBy(By by, WebElement element, WebDriver driver) {
                      System.out.println("Found " + by);
                  }
              }
              """,
            """
              import org.openqa.selenium.By;
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.WebElement;
              import org.openqa.selenium.support.events.AbstractWebDriverEventListener;

              import java.io.Serializable;

              class LoggingListener extends AbstractWebDriverEventListener implements Serializable {
                  @Override
                  public void beforeClickOn(WebElement element, WebDriver driver) {
                      System.out.println("Clicking " + element);
                  }

                  @Override
                  public void afterFindBy(By by, WebElement element, WebDriver driver) {
                      System.out.println("Found " + by);
                  }
              }
              """
          )
        );
    }

    @Test
    void onlyInterface() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.support.events.WebDriverEventListener;

              class NavigationListener implements WebDriverEventListener {
                  @Override
                  public void afterNavigateTo(String url, WebDriver driver) {
                      System.out.println("At " + url);
                  }
              }
              """,
            """
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.support.events.AbstractWebDriverEventListener;

              class NavigationListener extends AbstractWebDriverEventListener {
                  @Override
                  public void afterNavigateTo(String url, WebDriver driver) {
                      System.out.println("At " + url);
                  }
              }
              """
          )
        );
    }

    @Test
    void anonymousListener() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.support.events.EventFiringWebDriver;
              import org.openqa.selenium.support.events.WebDriverEventListener;

              class Drivers {
                  EventFiringWebDriver listened(WebDriver driver) {
                      return new EventFiringWebDriver(driver).register(new WebDriverEventListener() {
                          @Override
                          public void afterNavigateTo(String url, WebDriver driver) {
                              System.out.println("At " + url);
                          }
                      });
                  }
              }
              """,
            """
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.support.events.AbstractWebDriverEventListener;
              import org.openqa.selenium.support.events.EventFiringWebDriver;

              class Drivers {
                  EventFiringWebDriver listened(WebDriver driver) {
                      return new EventFiringWebDriver(driver).register(new AbstractWebDriverEventListener() {
                          @Override
                          public void afterNavigateTo(String url, WebDriver driver) {
                              System.out.println("At " + url);
                          }
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void subclassIsLeftAlone() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.support.events.WebDriverEventListener;

              abstract class BaseListener {
              }

              class NavigationListener extends BaseListener implements WebDriverEventListener {
                  @Override
                  public void afterNavigateTo(String url, WebDriver driver) {
                      System.out.println("At " + url);
                  }
              }
              """
          )
        );
    }
}
