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

class ReplaceSetHeadlessTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceSetHeadless())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "selenium-api-3", "selenium-remote-driver-3", "selenium-chrome-driver-3", "selenium-firefox-driver-3"));
    }

    @DocumentExample
    @Test
    void headless() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.chrome.ChromeOptions;
              import org.openqa.selenium.firefox.FirefoxOptions;

              class Browsers {
                  ChromeOptions chrome() {
                      ChromeOptions options = new ChromeOptions();
                      options.setHeadless(true);
                      return options;
                  }

                  FirefoxOptions firefox() {
                      return new FirefoxOptions().setHeadless(true).addArguments("-private");
                  }
              }
              """,
            """
              import org.openqa.selenium.chrome.ChromeOptions;
              import org.openqa.selenium.firefox.FirefoxOptions;

              class Browsers {
                  ChromeOptions chrome() {
                      ChromeOptions options = new ChromeOptions();
                      options.addArguments("--headless", "--disable-gpu");
                      return options;
                  }

                  FirefoxOptions firefox() {
                      return new FirefoxOptions().addArguments("-headless").addArguments("-private");
                  }
              }
              """
          )
        );
    }

    @Test
    void notHeadlessAddedNothing() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.chrome.ChromeOptions;

              class Browsers {
                  ChromeOptions chrome() {
                      ChromeOptions options = new ChromeOptions().setHeadless(false);
                      options.setHeadless(false);
                      return options;
                  }
              }
              """,
            """
              import org.openqa.selenium.chrome.ChromeOptions;

              class Browsers {
                  ChromeOptions chrome() {
                      ChromeOptions options = new ChromeOptions();
                      return options;
                  }
              }
              """
          )
        );
    }

    @Test
    void notHeadlessOutsideABlockIsLeftAlone() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.chrome.ChromeOptions;

              import java.util.function.Consumer;

              class Browsers {
                  Consumer<ChromeOptions> options(String mode, boolean debug, ChromeOptions options) {
                      switch (mode) {
                          case "local":
                              options.setHeadless(false);
                              break;
                          default:
                              options.addArguments("--incognito");
                      }
                      if (debug) options.setHeadless(false);
                      return o -> o.setHeadless(false);
                  }
              }
              """
          )
        );
    }

    @Test
    void flagOnlyKnownAtRuntimeIsLeftAlone() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.chrome.ChromeOptions;

              class Browsers {
                  ChromeOptions chrome(boolean headless) {
                      return new ChromeOptions().setHeadless(headless);
                  }
              }
              """
          )
        );
    }
}
