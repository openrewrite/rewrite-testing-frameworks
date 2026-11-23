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

class ReplaceFindElementByMethodsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceFindElementByMethods())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "selenium-api-3", "selenium-remote-driver-3", "selenium-chrome-driver-3"));
    }

    @DocumentExample
    @Test
    void remoteWebDriver() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.WebElement;
              import org.openqa.selenium.chrome.ChromeDriver;

              import java.util.List;

              class Page {
                  void login(ChromeDriver driver, String user) {
                      driver.findElementById("user").sendKeys(user);
                      driver.findElementByXPath("//button[@type='submit']").click();
                      List<WebElement> errors = driver.findElementsByCssSelector(".error");
                      driver.findElementByLinkText("Home").click();
                  }
              }
              """,
            """
              import org.openqa.selenium.By;
              import org.openqa.selenium.WebElement;
              import org.openqa.selenium.chrome.ChromeDriver;

              import java.util.List;

              class Page {
                  void login(ChromeDriver driver, String user) {
                      driver.findElement(By.id("user")).sendKeys(user);
                      driver.findElement(By.xpath("//button[@type='submit']")).click();
                      List<WebElement> errors = driver.findElements(By.cssSelector(".error"));
                      driver.findElement(By.linkText("Home")).click();
                  }
              }
              """
          )
        );
    }

    @Test
    void remoteWebElement() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.remote.RemoteWebElement;

              class Row {
                  String cell(RemoteWebElement row, String name) {
                      return row.findElementByName(name).getText() + row.findElementsByTagName("td").size();
                  }
              }
              """,
            """
              import org.openqa.selenium.By;
              import org.openqa.selenium.remote.RemoteWebElement;

              class Row {
                  String cell(RemoteWebElement row, String name) {
                      return row.findElement(By.name(name)).getText() + row.findElements(By.tagName("td")).size();
                  }
              }
              """
          )
        );
    }

    @Test
    void castToFindsByInterface() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.SearchContext;
              import org.openqa.selenium.WebElement;
              import org.openqa.selenium.internal.FindsById;

              import java.util.List;

              class Selector {
                  WebElement find(SearchContext context, String id) {
                      return ((FindsById) context).findElementById(id);
                  }

                  List<WebElement> findAll(SearchContext context, String id) {
                      return ((FindsById) context).findElementsById(id);
                  }
              }
              """,
            """
              import org.openqa.selenium.By;
              import org.openqa.selenium.SearchContext;
              import org.openqa.selenium.WebElement;

              import java.util.List;

              class Selector {
                  WebElement find(SearchContext context, String id) {
                      return context.findElement(By.id(id));
                  }

                  List<WebElement> findAll(SearchContext context, String id) {
                      return context.findElements(By.id(id));
                  }
              }
              """
          )
        );
    }

    @Test
    void subclassCallingInheritedMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.WebElement;
              import org.openqa.selenium.chrome.ChromeDriver;

              class MyDriver extends ChromeDriver {
                  WebElement header() {
                      return findElementByClassName("header");
                  }
              }
              """,
            """
              import org.openqa.selenium.By;
              import org.openqa.selenium.WebElement;
              import org.openqa.selenium.chrome.ChromeDriver;

              class MyDriver extends ChromeDriver {
                  WebElement header() {
                      return findElement(By.className("header"));
                  }
              }
              """
          )
        );
    }

    @Test
    void findElementWithByIsUnchanged() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.By;
              import org.openqa.selenium.WebDriver;

              class Page {
                  void click(WebDriver driver) {
                      driver.findElement(By.id("go")).click();
                  }
              }
              """
          )
        );
    }
}
