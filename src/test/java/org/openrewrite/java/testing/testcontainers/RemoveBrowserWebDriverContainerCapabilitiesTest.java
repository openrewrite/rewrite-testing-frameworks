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
package org.openrewrite.java.testing.testcontainers;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveBrowserWebDriverContainerCapabilitiesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new RemoveBrowserWebDriverContainerCapabilities())
          .parser(JavaParser.fromJavaVersion()
            //language=java
            .dependsOn(
              """
                package org.openqa.selenium;
                public interface Capabilities {}
                """,
              """
                package org.openqa.selenium.chrome;
                public class ChromeOptions implements org.openqa.selenium.Capabilities {}
                """,
              """
                package org.openqa.selenium.remote;
                public class DesiredCapabilities implements org.openqa.selenium.Capabilities {}
                """,
              """
                package org.testcontainers.containers;
                import org.openqa.selenium.Capabilities;
                import org.openqa.selenium.remote.DesiredCapabilities;
                public class BrowserWebDriverContainer<SELF extends BrowserWebDriverContainer<SELF>> {
                    public BrowserWebDriverContainer() {}
                    public BrowserWebDriverContainer(String dockerImageName) {}
                    public SELF withCapabilities(Capabilities capabilities) { return null; }
                    public SELF withDesiredCapabilities(DesiredCapabilities capabilities) { return null; }
                    public SELF withAccessToHost(boolean value) { return null; }
                }
                """
            ));
    }

    @DocumentExample
    @Test
    void removeLastInChain() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.chrome.ChromeOptions;
              import org.testcontainers.containers.BrowserWebDriverContainer;

              class SeleniumTest {
                  static BrowserWebDriverContainer<?> chrome = new BrowserWebDriverContainer<>("selenium/standalone-chrome:4.8.3")
                          .withAccessToHost(true)
                          .withCapabilities(new ChromeOptions());
              }
              """,
            """
              import org.testcontainers.containers.BrowserWebDriverContainer;

              class SeleniumTest {
                  static BrowserWebDriverContainer<?> chrome = new BrowserWebDriverContainer<>("selenium/standalone-chrome:4.8.3")
                          .withAccessToHost(true);
              }
              """
          )
        );
    }

    @Test
    void testcontainers2Migration() {
        rewriteRun(
          spec -> spec.recipeFromResource("/META-INF/rewrite/testcontainers.yml",
            "org.openrewrite.java.testing.testcontainers.Testcontainers2Migration"),
          //language=java
          java(
            """
              import org.openqa.selenium.chrome.ChromeOptions;
              import org.testcontainers.containers.BrowserWebDriverContainer;

              class SeleniumTest {
                  static BrowserWebDriverContainer<?> chrome = new BrowserWebDriverContainer<>("selenium/standalone-chrome:4.8.3")
                          .withAccessToHost(true)
                          .withCapabilities(new ChromeOptions());
              }
              """,
            """
              import org.testcontainers.selenium.BrowserWebDriverContainer;

              class SeleniumTest {
                  static BrowserWebDriverContainer chrome = new BrowserWebDriverContainer("selenium/standalone-chrome:4.8.3")
                          .withAccessToHost(true);
              }
              """
          )
        );
    }

    @Test
    void removeInMiddleOfChain() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.remote.DesiredCapabilities;
              import org.testcontainers.containers.BrowserWebDriverContainer;

              class SeleniumTest {
                  void start(DesiredCapabilities capabilities) {
                      BrowserWebDriverContainer<?> chrome = new BrowserWebDriverContainer<>("selenium/standalone-chrome:4.8.3")
                              .withDesiredCapabilities(capabilities)
                              .withAccessToHost(true);
                  }
              }
              """,
            """
              import org.openqa.selenium.remote.DesiredCapabilities;
              import org.testcontainers.containers.BrowserWebDriverContainer;

              class SeleniumTest {
                  void start(DesiredCapabilities capabilities) {
                      BrowserWebDriverContainer<?> chrome = new BrowserWebDriverContainer<>("selenium/standalone-chrome:4.8.3")
                              .withAccessToHost(true);
                  }
              }
              """
          )
        );
    }

    @Test
    void keepWithoutImage() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.chrome.ChromeOptions;
              import org.testcontainers.containers.BrowserWebDriverContainer;

              class SeleniumTest {
                  BrowserWebDriverContainer<?> chrome = new BrowserWebDriverContainer<>()
                          .withCapabilities(new ChromeOptions());

                  void configure(BrowserWebDriverContainer<?> container) {
                      container.withCapabilities(new ChromeOptions());
                  }
              }
              """
          )
        );
    }
}
