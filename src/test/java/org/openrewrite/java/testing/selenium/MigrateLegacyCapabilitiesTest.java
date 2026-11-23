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

class MigrateLegacyCapabilitiesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateLegacyCapabilities())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "selenium-api-3", "selenium-remote-driver-3", "selenium-chrome-driver-3"));
    }

    @DocumentExample
    @Test
    void legacyNames() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.remote.DesiredCapabilities;

              class Grid {
                  DesiredCapabilities capabilities() {
                      DesiredCapabilities capabilities = new DesiredCapabilities();
                      capabilities.setBrowserName("chrome");
                      capabilities.setCapability("platform", "WINDOWS");
                      capabilities.setCapability("version", "92");
                      capabilities.setCapability("takesScreenshot", true);
                      capabilities.setJavascriptEnabled(true);
                      capabilities.setCapability("acceptSslCerts", true);
                      return capabilities;
                  }
              }
              """,
            """
              import org.openqa.selenium.remote.DesiredCapabilities;

              class Grid {
                  DesiredCapabilities capabilities() {
                      DesiredCapabilities capabilities = new DesiredCapabilities();
                      capabilities.setBrowserName("chrome");
                      capabilities.setCapability("platformName", "WINDOWS");
                      capabilities.setCapability("browserVersion", "92");
                      capabilities.setCapability("acceptInsecureCerts", true);
                      return capabilities;
                  }
              }
              """
          )
        );
    }

    @Test
    void javascriptTurnedOffForHtmlUnitIsKept() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.remote.DesiredCapabilities;

              class Headless {
                  DesiredCapabilities capabilities(boolean enabled) {
                      DesiredCapabilities capabilities = new DesiredCapabilities();
                      capabilities.setJavascriptEnabled(false);
                      capabilities.setJavascriptEnabled(enabled);
                      capabilities.setCapability("javascriptEnabled", false);
                      capabilities.setCapability("javascriptEnabled", true);
                      return capabilities;
                  }
              }
              """,
            """
              import org.openqa.selenium.remote.DesiredCapabilities;

              class Headless {
                  DesiredCapabilities capabilities(boolean enabled) {
                      DesiredCapabilities capabilities = new DesiredCapabilities();
                      capabilities.setCapability("javascriptEnabled", false);
                      capabilities.setCapability("javascriptEnabled", enabled);
                      capabilities.setCapability("javascriptEnabled", false);
                      return capabilities;
                  }
              }
              """
          )
        );
    }

    @Test
    void javascriptTurnedOffInASubclass() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.remote.DesiredCapabilities;

              class HeadlessCapabilities extends DesiredCapabilities {
                  HeadlessCapabilities() {
                      setJavascriptEnabled(false);
                  }
              }
              """,
            """
              import org.openqa.selenium.remote.DesiredCapabilities;

              class HeadlessCapabilities extends DesiredCapabilities {
                  HeadlessCapabilities() {
                      setCapability("javascriptEnabled", false);
                  }
              }
              """
          )
        );
    }

    @Test
    void seleniumTwoDesiredCapabilities() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "selenium-api-2", "selenium-remote-driver-2")),
          //language=java
          java(
            """
              import org.openqa.selenium.remote.DesiredCapabilities;

              class Grid {
                  DesiredCapabilities capabilities() {
                      DesiredCapabilities capabilities = DesiredCapabilities.firefox();
                      capabilities.setCapability("platform", "LINUX");
                      capabilities.setCapability("takesScreenshot", true);
                      capabilities.setJavascriptEnabled(true);
                      return capabilities;
                  }
              }
              """,
            """
              import org.openqa.selenium.remote.DesiredCapabilities;

              class Grid {
                  DesiredCapabilities capabilities() {
                      DesiredCapabilities capabilities = DesiredCapabilities.firefox();
                      capabilities.setCapability("platformName", "LINUX");
                      return capabilities;
                  }
              }
              """
          )
        );
    }

    @Test
    void chromeLoggingPreferences() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.chrome.ChromeOptions;
              import org.openqa.selenium.logging.LoggingPreferences;

              class Logs {
                  ChromeOptions options(LoggingPreferences preferences) {
                      ChromeOptions options = new ChromeOptions();
                      options.setCapability("loggingPrefs", preferences);
                      return options;
                  }
              }
              """,
            """
              import org.openqa.selenium.chrome.ChromeOptions;
              import org.openqa.selenium.logging.LoggingPreferences;

              class Logs {
                  ChromeOptions options(LoggingPreferences preferences) {
                      ChromeOptions options = new ChromeOptions();
                      options.setCapability("goog:loggingPrefs", preferences);
                      return options;
                  }
              }
              """
          )
        );
    }

    @Test
    void w3cAndVendorNamesAreUnchanged() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.remote.DesiredCapabilities;

              class Grid {
                  void configure(DesiredCapabilities capabilities, String name) {
                      capabilities.setCapability("platformName", "linux");
                      capabilities.setCapability("se:recordVideo", true);
                      capabilities.setCapability(name, true);
                  }
              }
              """
          )
        );
    }
}
