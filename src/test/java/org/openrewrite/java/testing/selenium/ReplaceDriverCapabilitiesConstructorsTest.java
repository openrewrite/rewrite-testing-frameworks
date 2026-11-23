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

class ReplaceDriverCapabilitiesConstructorsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceDriverCapabilitiesConstructors())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "selenium-api-3", "selenium-remote-driver-3", "selenium-chrome-driver-3",
              "selenium-firefox-driver-3", "selenium-ie-driver-3", "selenium-edge-driver-3"));
    }

    @DocumentExample
    @Test
    void capabilitiesMergedIntoOptions() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.chrome.ChromeDriver;
              import org.openqa.selenium.firefox.FirefoxDriver;
              import org.openqa.selenium.remote.DesiredCapabilities;

              class Drivers {
                  WebDriver chrome(DesiredCapabilities capabilities) {
                      return new ChromeDriver(capabilities);
                  }

                  WebDriver firefox(DesiredCapabilities capabilities) {
                      return new FirefoxDriver(capabilities);
                  }
              }
              """,
            """
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.chrome.ChromeDriver;
              import org.openqa.selenium.chrome.ChromeOptions;
              import org.openqa.selenium.firefox.FirefoxDriver;
              import org.openqa.selenium.firefox.FirefoxOptions;
              import org.openqa.selenium.remote.DesiredCapabilities;

              class Drivers {
                  WebDriver chrome(DesiredCapabilities capabilities) {
                      return new ChromeDriver(new ChromeOptions().merge(capabilities));
                  }

                  WebDriver firefox(DesiredCapabilities capabilities) {
                      return new FirefoxDriver(new FirefoxOptions().merge(capabilities));
                  }
              }
              """
          )
        );
    }

    @Test
    void withDriverService() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.Capabilities;
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.ie.InternetExplorerDriver;
              import org.openqa.selenium.ie.InternetExplorerDriverService;

              class Drivers {
                  WebDriver ie(InternetExplorerDriverService service, Capabilities capabilities) {
                      return new InternetExplorerDriver(service, capabilities);
                  }
              }
              """,
            """
              import org.openqa.selenium.Capabilities;
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.ie.InternetExplorerDriver;
              import org.openqa.selenium.ie.InternetExplorerDriverService;
              import org.openqa.selenium.ie.InternetExplorerOptions;

              class Drivers {
                  WebDriver ie(InternetExplorerDriverService service, Capabilities capabilities) {
                      return new InternetExplorerDriver(service, new InternetExplorerOptions().merge(capabilities));
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
              import org.openqa.selenium.Capabilities;
              import org.openqa.selenium.edge.EdgeDriver;

              class MyEdgeDriver extends EdgeDriver {
                  MyEdgeDriver(Capabilities capabilities) {
                      super(capabilities);
                  }
              }
              """,
            """
              import org.openqa.selenium.Capabilities;
              import org.openqa.selenium.edge.EdgeDriver;
              import org.openqa.selenium.edge.EdgeOptions;

              class MyEdgeDriver extends EdgeDriver {
                  MyEdgeDriver(Capabilities capabilities) {
                      super(new EdgeOptions().merge(capabilities));
                  }
              }
              """
          )
        );
    }

    @Test
    void seleniumTwoFirefoxProfile() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "selenium-api-2", "selenium-remote-driver-2", "selenium-firefox-driver-2")),
          //language=java
          java(
            """
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.firefox.FirefoxDriver;
              import org.openqa.selenium.firefox.FirefoxProfile;

              class Drivers {
                  WebDriver firefox() {
                      FirefoxProfile profile = new FirefoxProfile();
                      profile.setPreference("browser.download.folderList", 2);
                      return new FirefoxDriver(profile);
                  }
              }
              """,
            """
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.firefox.FirefoxDriver;
              import org.openqa.selenium.firefox.FirefoxOptions;
              import org.openqa.selenium.firefox.FirefoxProfile;

              class Drivers {
                  WebDriver firefox() {
                      FirefoxProfile profile = new FirefoxProfile();
                      profile.setPreference("browser.download.folderList", 2);
                      return new FirefoxDriver(new FirefoxOptions().setProfile(profile));
                  }
              }
              """
          )
        );
    }

    @Test
    void seleniumTwoFirefoxBinaryAndProfile() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "selenium-api-2", "selenium-remote-driver-2", "selenium-firefox-driver-2")),
          //language=java
          java(
            """
              import org.openqa.selenium.Capabilities;
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.firefox.FirefoxBinary;
              import org.openqa.selenium.firefox.FirefoxDriver;
              import org.openqa.selenium.firefox.FirefoxProfile;

              class Drivers {
                  WebDriver firefox(FirefoxBinary binary, FirefoxProfile profile) {
                      return new FirefoxDriver(binary, profile);
                  }

                  WebDriver firefox(FirefoxBinary binary, FirefoxProfile profile, Capabilities capabilities) {
                      return new FirefoxDriver(binary, profile, capabilities);
                  }

                  WebDriver firefox(Capabilities desired, Capabilities required) {
                      return new FirefoxDriver(desired, required);
                  }

                  WebDriver firefox(FirefoxBinary binary, FirefoxProfile profile, Capabilities desired, Capabilities required) {
                      return new FirefoxDriver(binary, profile, desired, required);
                  }

                  WebDriver firefoxWithoutCapabilities(FirefoxProfile profile) {
                      return new FirefoxDriver(new FirefoxBinary(), profile, null);
                  }
              }
              """,
            """
              import org.openqa.selenium.Capabilities;
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.firefox.FirefoxBinary;
              import org.openqa.selenium.firefox.FirefoxDriver;
              import org.openqa.selenium.firefox.FirefoxOptions;
              import org.openqa.selenium.firefox.FirefoxProfile;

              class Drivers {
                  WebDriver firefox(FirefoxBinary binary, FirefoxProfile profile) {
                      return new FirefoxDriver(new FirefoxOptions().setBinary(binary).setProfile(profile));
                  }

                  WebDriver firefox(FirefoxBinary binary, FirefoxProfile profile, Capabilities capabilities) {
                      return new FirefoxDriver(new FirefoxOptions().merge(capabilities).setBinary(binary).setProfile(profile));
                  }

                  WebDriver firefox(Capabilities desired, Capabilities required) {
                      return new FirefoxDriver(new FirefoxOptions().merge(desired).merge(required));
                  }

                  WebDriver firefox(FirefoxBinary binary, FirefoxProfile profile, Capabilities desired, Capabilities required) {
                      return new FirefoxDriver(new FirefoxOptions().merge(desired).merge(required).setBinary(binary).setProfile(profile));
                  }

                  WebDriver firefoxWithoutCapabilities(FirefoxProfile profile) {
                      return new FirefoxDriver(new FirefoxOptions().setBinary(new FirefoxBinary()).setProfile(profile));
                  }
              }
              """
          )
        );
    }

    @Test
    void optionsAreUnchanged() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.WebDriver;
              import org.openqa.selenium.chrome.ChromeDriver;
              import org.openqa.selenium.chrome.ChromeOptions;

              class Drivers {
                  WebDriver chrome(ChromeOptions options) {
                      return new ChromeDriver(options);
                  }
              }
              """
          )
        );
    }
}
