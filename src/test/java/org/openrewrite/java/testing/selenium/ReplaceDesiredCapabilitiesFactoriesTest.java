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

class ReplaceDesiredCapabilitiesFactoriesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceDesiredCapabilitiesFactories())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "selenium-api-3", "selenium-remote-driver-3"));
    }

    @DocumentExample
    @Test
    void browsersWithOptions() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.remote.DesiredCapabilities;

              class Browsers {
                  DesiredCapabilities capabilities(String browser) {
                      switch (browser) {
                          case "firefox":
                              return DesiredCapabilities.firefox();
                          case "ie":
                              return DesiredCapabilities.internetExplorer();
                          default:
                              DesiredCapabilities capabilities = DesiredCapabilities.chrome();
                              capabilities.setCapability("build", "42");
                              return capabilities;
                      }
                  }
              }
              """,
            """
              import org.openqa.selenium.chrome.ChromeOptions;
              import org.openqa.selenium.firefox.FirefoxOptions;
              import org.openqa.selenium.ie.InternetExplorerOptions;
              import org.openqa.selenium.remote.DesiredCapabilities;

              class Browsers {
                  DesiredCapabilities capabilities(String browser) {
                      switch (browser) {
                          case "firefox":
                              return new DesiredCapabilities(new FirefoxOptions());
                          case "ie":
                              return new DesiredCapabilities(new InternetExplorerOptions());
                          default:
                              DesiredCapabilities capabilities = new DesiredCapabilities(new ChromeOptions());
                              capabilities.setCapability("build", "42");
                              return capabilities;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void browsersWithoutOptions() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.remote.DesiredCapabilities;

              class Browsers {
                  DesiredCapabilities headless() {
                      return DesiredCapabilities.phantomjs();
                  }

                  DesiredCapabilities htmlUnit() {
                      return DesiredCapabilities.htmlUnit();
                  }
              }
              """,
            """
              import org.openqa.selenium.Platform;
              import org.openqa.selenium.remote.DesiredCapabilities;

              class Browsers {
                  DesiredCapabilities headless() {
                      return new DesiredCapabilities("phantomjs", "", Platform.ANY);
                  }

                  DesiredCapabilities htmlUnit() {
                      return new DesiredCapabilities("htmlunit", "", Platform.ANY);
                  }
              }
              """
          )
        );
    }

    @Test
    void staticImport() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.Capabilities;

              import static org.openqa.selenium.remote.DesiredCapabilities.safari;

              class Browsers {
                  Capabilities capabilities() {
                      return safari();
                  }
              }
              """,
            """
              import org.openqa.selenium.Capabilities;
              import org.openqa.selenium.remote.DesiredCapabilities;
              import org.openqa.selenium.safari.SafariOptions;

              class Browsers {
                  Capabilities capabilities() {
                      return new DesiredCapabilities(new SafariOptions());
                  }
              }
              """
          )
        );
    }

    @Test
    void instanceMethodsAreUnchanged() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.remote.DesiredCapabilities;

              class Browsers {
                  boolean insecure(DesiredCapabilities capabilities) {
                      return capabilities.acceptInsecureCerts();
                  }
              }
              """
          )
        );
    }
}
