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
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;
import org.openrewrite.xml.tree.Xml;

import java.util.List;

import static java.util.stream.Collectors.toList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class Selenium3to4MigrationTest implements RewriteTest {

    private static final String LATEST_SELENIUM_4 = "4\\.(1[4-9]|[2-9]\\d)\\.\\d+";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.testing.selenium.Selenium3to4Migration")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "selenium-api-3", "selenium-remote-driver-3", "selenium-support-3", "selenium-chrome-driver-3"));
    }

    private static String pom(String... dependencies) {
        StringBuilder deps = new StringBuilder();
        for (String dependency : dependencies) {
            String[] gav = dependency.split(":");
            deps.append("""
                      <dependency>
                          <groupId>%s</groupId>
                          <artifactId>%s</artifactId>
                          <version>%s</version>
                      </dependency>
              """.formatted(gav[0], gav[1], gav[2]));
        }
        return """
          <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>ui-tests</artifactId>
              <version>1.0.0</version>
              <dependencies>
          %s    </dependencies>
          </project>
          """.formatted(deps);
    }


    private static final String USES_SELENIUM = """
      import org.openqa.selenium.WebDriver;

      class LoginTest {
          WebDriver driver;
      }
      """;

    /**
     * Every Selenium artifact the pom resolves in test scope, as {@code artifactId:version}.
     */
    private static List<String> resolvedSelenium(Xml.Document pom) {
        return pom.getMarkers().findFirst(MavenResolutionResult.class).orElseThrow()
          .getDependencies().get(Scope.Test).stream()
          .filter(d -> "org.seleniumhq.selenium".equals(d.getGroupId()))
          .map(d -> d.getArtifactId() + ":" + d.getVersion())
          .distinct()
          .collect(toList());
    }

    private static void allSeleniumResolvedAt(Xml.Document pom, String versionPrefix) {
        List<String> resolved = resolvedSelenium(pom);
        assertThat(resolved).isNotEmpty();
        assertThat(resolved).allSatisfy(gav -> assertThat(gav).contains(":" + versionPrefix));
    }

    @DocumentExample
    @Test
    void javaEightMovesToTheLastSeleniumRunningOnIt() {
        rewriteRun(
          mavenProject("ui-tests",
            srcTestJava(
              //language=java
              java(
                """
                  import org.openqa.selenium.WebDriver;
                  import org.openqa.selenium.chrome.ChromeDriver;
                  import org.openqa.selenium.remote.DesiredCapabilities;
                  import org.openqa.selenium.support.ui.WebDriverWait;

                  import java.util.concurrent.TimeUnit;

                  class LoginTest {
                      void login() {
                          DesiredCapabilities capabilities = DesiredCapabilities.chrome();
                          capabilities.setJavascriptEnabled(true);
                          ChromeDriver driver = new ChromeDriver(capabilities);
                          driver.manage().timeouts().implicitlyWait(5, TimeUnit.SECONDS);
                          new WebDriverWait(driver, 10).until(d -> d.getTitle().startsWith("Welcome"));
                          driver.findElementById("logout").click();
                      }
                  }
                  """,
                """
                  import org.openqa.selenium.By;
                  import org.openqa.selenium.WebDriver;
                  import org.openqa.selenium.chrome.ChromeDriver;
                  import org.openqa.selenium.chrome.ChromeOptions;
                  import org.openqa.selenium.remote.DesiredCapabilities;
                  import org.openqa.selenium.support.ui.WebDriverWait;

                  import java.time.Duration;

                  class LoginTest {
                      void login() {
                          DesiredCapabilities capabilities = new DesiredCapabilities(new ChromeOptions());
                          ChromeDriver driver = new ChromeDriver(new ChromeOptions().merge(capabilities));
                          driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
                          new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> d.getTitle().startsWith("Welcome"));
                          driver.findElement(By.id("logout")).click();
                      }
                  }
                  """,
                spec -> spec.markers(javaVersion(8))
              )
            ),
            //language=xml
            pomXml(
              pom("org.seleniumhq.selenium:selenium-java:3.141.59"),
              pom("org.seleniumhq.selenium:selenium-java:4.13.0")
            )
          )
        );
    }

    @Test
    void javaElevenMovesToTheLatestSelenium() {
        rewriteRun(
          mavenProject("ui-tests",
            srcTestJava(
              //language=java
              java(
                """
                  class LoginTest {
                  }
                  """,
                spec -> spec.markers(javaVersion(17))
              )
            ),
            //language=xml
            pomXml(
              pom("org.seleniumhq.selenium:selenium-java:3.141.59",
                "org.seleniumhq.selenium:selenium-htmlunit-driver:2.52.0",
                "org.seleniumhq.selenium:selenium-opera-driver:3.141.59"),
              spec -> spec.after(after -> assertThat(after)
                .containsPattern("<artifactId>selenium-java</artifactId>\\s*<version>" + LATEST_SELENIUM_4 + "</version>")
                .containsPattern("<artifactId>htmlunit-driver</artifactId>\\s*<version>4\\.13\\.0</version>")
                .doesNotContain("selenium-opera-driver")
                .doesNotContain("selenium-htmlunit-driver")
                .actual())
            )
          )
        );
    }

    @Test
    void javaElevenUsingAnApiRemovedAfterSelenium413() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "selenium-api-3", "selenium-remote-driver-3", "selenium-support-3")),
          mavenProject("ui-tests",
            srcTestJava(
              //language=java
              java(
                """
                  import org.openqa.selenium.WebDriver;
                  import org.openqa.selenium.support.events.EventFiringWebDriver;

                  class LoginTest {
                      WebDriver listened(WebDriver driver) {
                          return new EventFiringWebDriver(driver);
                      }
                  }
                  """,
                spec -> spec.markers(javaVersion(17))
              )
            ),
            //language=xml
            pomXml(
              pom("org.seleniumhq.selenium:selenium-java:3.141.59"),
              pom("org.seleniumhq.selenium:selenium-java:4.13.0")
            )
          )
        );
    }

    @Test
    void versionManagedBySpringBootParent() {
        rewriteRun(
          mavenProject("ui-tests",
            srcTestJava(
              //language=java
              java(
                """
                  class LoginTest {
                  }
                  """,
                spec -> spec.markers(javaVersion(8))
              )
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>2.3.12.RELEASE</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>ui-tests</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.seleniumhq.selenium</groupId>
                            <artifactId>selenium-java</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>2.3.12.RELEASE</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>ui-tests</artifactId>
                    <version>1.0.0</version>
                    <properties>
                        <selenium.version>4.13.0</selenium.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.seleniumhq.selenium</groupId>
                            <artifactId>selenium-java</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void gradle() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          mavenProject("ui-tests",
            srcTestJava(
              //language=java
              java(
                """
                  class LoginTest {
                  }
                  """,
                spec -> spec.markers(javaVersion(8))
              )
            ),
            //language=groovy
            buildGradle(
              """
                plugins {
                    id 'java'
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    testImplementation 'org.seleniumhq.selenium:selenium-java:3.141.59'
                }
                """,
              """
                plugins {
                    id 'java'
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    testImplementation 'org.seleniumhq.selenium:selenium-java:4.13.0'
                }
                """
            )
          )
        );
    }

    @Test
    void appiumBeforeEightIsLeftAlone() {
        rewriteRun(
          mavenProject("ui-tests",
            srcTestJava(
              //language=java
              java(
                """
                  import org.openqa.selenium.WebDriver;

                  import java.util.concurrent.TimeUnit;

                  class AppTest {
                      void configure(WebDriver driver) {
                          driver.manage().timeouts().implicitlyWait(5, TimeUnit.SECONDS);
                      }
                  }
                  """,
                spec -> spec.markers(javaVersion(8))
              )
            ),
            //language=xml
            pomXml(
              pom("org.seleniumhq.selenium:selenium-java:3.141.59", "io.appium:java-client:7.6.0")
            )
          )
        );
    }

    @Test
    void arquillianDroneBeforeThreeIsLeftAlone() {
        rewriteRun(
          mavenProject("ui-tests",
            srcTestJava(
              //language=java
              java(
                """
                  import org.openqa.selenium.WebDriver;

                  import java.util.concurrent.TimeUnit;

                  class AppTest {
                      void configure(WebDriver driver) {
                          driver.manage().timeouts().implicitlyWait(5, TimeUnit.SECONDS);
                      }
                  }
                  """,
                spec -> spec.markers(javaVersion(8))
              )
            ),
            //language=xml
            pomXml(
              pom("org.seleniumhq.selenium:selenium-java:3.141.59",
                "org.jboss.arquillian.extension:arquillian-drone-webdriver:2.5.6")
            )
          )
        );
    }

    @Test
    void repositoryWithAModuleOnArquillianDroneBeforeThreeIsLeftAlone() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <modules>
                      <module>drone-tests</module>
                      <module>ui-tests</module>
                  </modules>
                  <properties>
                      <selenium.version>3.141.59</selenium.version>
                  </properties>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>org.seleniumhq.selenium</groupId>
                              <artifactId>selenium-java</artifactId>
                              <version>${selenium.version}</version>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
              </project>
              """,
            spec -> spec.path("pom.xml")
          ),
          mavenProject("drone-tests",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>parent</artifactId>
                        <version>1.0.0</version>
                    </parent>
                    <artifactId>drone-tests</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>org.seleniumhq.selenium</groupId>
                            <artifactId>selenium-java</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.jboss.arquillian.extension</groupId>
                            <artifactId>arquillian-drone-webdriver</artifactId>
                            <version>2.5.6</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          ),
          mavenProject("ui-tests",
            srcTestJava(
              //language=java
              java(
                """
                  import org.openqa.selenium.WebDriver;

                  import java.util.concurrent.TimeUnit;

                  class AppTest {
                      void configure(WebDriver driver) {
                          driver.manage().timeouts().implicitlyWait(5, TimeUnit.SECONDS);
                      }
                  }
                  """,
                spec -> spec.markers(javaVersion(8))
              )
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>parent</artifactId>
                        <version>1.0.0</version>
                    </parent>
                    <artifactId>ui-tests</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>org.seleniumhq.selenium</groupId>
                            <artifactId>selenium-java</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void vaadinTestBenchBeforeEightIsLeftAlone() {
        rewriteRun(
          mavenProject("ui-tests",
            srcTestJava(
              //language=java
              java(
                """
                  import org.openqa.selenium.WebDriver;

                  import java.util.concurrent.TimeUnit;

                  class AppTest {
                      void configure(WebDriver driver) {
                          driver.manage().timeouts().implicitlyWait(5, TimeUnit.SECONDS);
                      }
                  }
                  """,
                spec -> spec.markers(javaVersion(8))
              )
            ),
            //language=xml
            pomXml(
              pom("com.vaadin:vaadin-testbench-core:6.2.0")
            )
          )
        );
    }

    @Test
    void transitiveArtifactsPinnedBySpringBootParent() {
        rewriteRun(
          mavenProject("ui-tests",
            srcTestJava(
              //language=java
              java(USES_SELENIUM, spec -> spec.markers(javaVersion(8)))
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>1.5.9.RELEASE</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>ui-tests</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.seleniumhq.selenium</groupId>
                            <artifactId>selenium-java</artifactId>
                            <version>3.12.0</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>1.5.9.RELEASE</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>ui-tests</artifactId>
                    <version>1.0.0</version>
                    <properties>
                        <selenium.version>4.13.0</selenium.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.seleniumhq.selenium</groupId>
                            <artifactId>selenium-java</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.afterRecipe(pom -> allSeleniumResolvedAt(pom, "4.13."))
            )
          )
        );
    }

    @Test
    void htmlUnitDriverBesideAVersionPinnedBySpringBootParent() {
        rewriteRun(
          mavenProject("ui-tests",
            srcTestJava(
              //language=java
              java(USES_SELENIUM, spec -> spec.markers(javaVersion(8)))
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>1.5.9.RELEASE</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>ui-tests</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.seleniumhq.selenium</groupId>
                            <artifactId>selenium-java</artifactId>
                            <version>3.12.0</version>
                        </dependency>
                        <dependency>
                            <groupId>org.seleniumhq.selenium</groupId>
                            <artifactId>htmlunit-driver</artifactId>
                            <version>2.21</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>1.5.9.RELEASE</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>ui-tests</artifactId>
                    <version>1.0.0</version>
                    <properties>
                        <selenium.version>4.13.0</selenium.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.seleniumhq.selenium</groupId>
                            <artifactId>selenium-java</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.seleniumhq.selenium</groupId>
                            <artifactId>htmlunit-driver</artifactId>
                            <version>4.13.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.afterRecipe(pom -> allSeleniumResolvedAt(pom, "4.13."))
            )
          )
        );
    }

    @Test
    void seleniumOnlyThroughAnotherLibrary() {
        rewriteRun(
          mavenProject("ui-tests",
            srcTestJava(
              //language=java
              java(USES_SELENIUM, spec -> spec.markers(javaVersion(8)))
            ),
            //language=xml
            pomXml(
              pom("ru.yandex.qatools.htmlelements:htmlelements-java:1.20.0"),
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>ui-tests</artifactId>
                    <version>1.0.0</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.seleniumhq.selenium</groupId>
                                <artifactId>selenium-java</artifactId>
                                <version>4.13.0</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>ru.yandex.qatools.htmlelements</groupId>
                            <artifactId>htmlelements-java</artifactId>
                            <version>1.20.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.afterRecipe(pom -> allSeleniumResolvedAt(pom, "4.13."))
            )
          )
        );
    }

    @Test
    void seleniumServerUsedForTheClientApi() {
        rewriteRun(
          mavenProject("ui-tests",
            srcTestJava(
              //language=java
              java(USES_SELENIUM, spec -> spec.markers(javaVersion(8)))
            ),
            //language=xml
            pomXml(
              pom("org.seleniumhq.selenium:selenium-server:3.8.1"),
              pom("org.seleniumhq.selenium:selenium-java:4.13.0")
            )
          )
        );
    }

    @Test
    void seleniumServerBesideSeleniumJava() {
        rewriteRun(
          mavenProject("ui-tests",
            srcTestJava(
              //language=java
              java(USES_SELENIUM, spec -> spec.markers(javaVersion(8)))
            ),
            //language=xml
            pomXml(
              pom("org.seleniumhq.selenium:selenium-java:3.141.59", "org.seleniumhq.selenium:selenium-server:3.141.59"),
              pom("org.seleniumhq.selenium:selenium-java:4.13.0")
            )
          )
        );
    }

    @Test
    void moduleUsingTheSeleniumGridIsLeftAlone() {
        rewriteRun(
          mavenProject("grid",
            srcMainJava(
              //language=java
              java(
                """
                  package org.openqa.grid.selenium;

                  public class GridLauncherV3 {
                      public static void main(String[] args) {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  import org.openqa.grid.selenium.GridLauncherV3;
                  import org.openqa.selenium.WebDriver;

                  import java.util.concurrent.TimeUnit;

                  class Hub {
                      void start(WebDriver driver) {
                          GridLauncherV3.main(new String[0]);
                          driver.manage().timeouts().implicitlyWait(5, TimeUnit.SECONDS);
                      }
                  }
                  """,
                spec -> spec.markers(javaVersion(8))
              )
            ),
            //language=xml
            pomXml(
              pom("org.seleniumhq.selenium:selenium-server:3.141.59")
            )
          )
        );
    }

    @Test
    void phantomJsDriverKeepsTheSeleniumItBroughtAlong() {
        rewriteRun(
          mavenProject("ui-tests",
            srcTestJava(
              //language=java
              java(USES_SELENIUM, spec -> spec.markers(javaVersion(8)))
            ),
            //language=xml
            pomXml(
              pom("com.codeborne:phantomjsdriver:1.2.1"),
              spec -> spec.after(pom -> assertThat(pom)
                .containsPattern("<artifactId>phantomjsdriver</artifactId>\\s*<version>1\\.5\\.0</version>")
                .containsPattern("<artifactId>selenium-remote-driver</artifactId>\\s*<version>4\\.13\\.0</version>")
                .actual())
                .afterRecipe(pom -> allSeleniumResolvedAt(pom, "4.13."))
            )
          )
        );
    }

    @Test
    void dependenciesThatSeleniumJavaThreeBroughtAlong() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          mavenProject("ui-tests",
            srcTestJava(
              //language=java
              java(
                """
                  import org.apache.commons.io.FileUtils;
                  import org.openqa.selenium.OutputType;
                  import org.openqa.selenium.WebDriver;
                  import org.openqa.selenium.phantomjs.PhantomJSDriver;

                  import java.io.File;
                  import java.io.IOException;

                  class ScreenshotTest {
                      void screenshot() throws IOException {
                          PhantomJSDriver driver = new PhantomJSDriver();
                          File file = driver.getScreenshotAs(OutputType.FILE);
                          FileUtils.copyFile(file, new File("target/screenshot.png"));
                      }
                  }
                  """,
                spec -> spec.markers(javaVersion(8))
              )
            ),
            //language=xml
            pomXml(
              pom("org.seleniumhq.selenium:selenium-java:3.5.2"),
              spec -> spec.after(pom -> assertThat(pom)
                .containsPattern("<artifactId>selenium-java</artifactId>\\s*<version>4\\.13\\.0</version>")
                .containsPattern("<artifactId>phantomjsdriver</artifactId>\\s*<version>1\\.5\\.0</version>\\s*<scope>test</scope>")
                .containsPattern("<artifactId>commons-io</artifactId>\\s*<version>2\\.\\d+(\\.\\d+)?</version>\\s*<scope>test</scope>")
                .doesNotContain("htmlunit-driver", "selenium-remote-driver")
                .actual())
            )
          )
        );
    }

    @Test
    void httpClientThatSeleniumJavaThreeBroughtAlong() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          mavenProject("ui-tests",
            srcTestJava(
              //language=java
              java(
                """
                  import org.apache.commons.codec.binary.Base64;
                  import org.apache.http.client.methods.HttpGet;
                  import org.apache.http.impl.client.CloseableHttpClient;
                  import org.apache.http.impl.client.HttpClients;
                  import org.openqa.selenium.WebDriver;
                  import org.openqa.selenium.chrome.ChromeDriver;

                  import java.io.IOException;

                  class LoginTest {
                      WebDriver driver = new ChromeDriver();

                      void token() throws IOException {
                          try (CloseableHttpClient client = HttpClients.createDefault()) {
                              client.execute(new HttpGet("https://example.com/" + Base64.encodeBase64String(new byte[0])));
                          }
                      }
                  }
                  """,
                spec -> spec.markers(javaVersion(17))
              )
            ),
            //language=xml
            pomXml(
              pom("org.seleniumhq.selenium:selenium-java:3.14.0"),
              spec -> spec.after(pom -> assertThat(pom)
                .containsPattern("<artifactId>selenium-java</artifactId>\\s*<version>4\\.\\d+\\.\\d+</version>")
                .containsPattern("<artifactId>httpclient</artifactId>\\s*<version>4\\.5\\.\\d+</version>\\s*<scope>test</scope>")
                .doesNotContain("commons-codec", "commons-logging", "httpcore", "gson", "commons-exec")
                .actual())
            )
          )
        );
    }

    @Test
    void commonsLoggingThatSeleniumJavaThreeBroughtAlong() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          mavenProject("ui-tests",
            srcTestJava(
              //language=java
              java(
                """
                  import org.apache.commons.logging.Log;
                  import org.apache.commons.logging.LogFactory;
                  import org.openqa.selenium.WebDriver;

                  class LoginTest {
                      private static final Log LOG = LogFactory.getLog(LoginTest.class);
                      WebDriver driver;
                  }
                  """,
                spec -> spec.markers(javaVersion(17))
              )
            ),
            //language=xml
            pomXml(
              pom("org.seleniumhq.selenium:selenium-java:3.14.0"),
              spec -> spec.after(pom -> assertThat(pom)
                .containsPattern("<artifactId>commons-logging</artifactId>\\s*<version>1\\.\\d+(\\.\\d+)?</version>\\s*<scope>test</scope>")
                .actual())
            )
          )
        );
    }

    @Test
    void commonsLoggingApiThatSpringProvides() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          mavenProject("ui-tests",
            srcTestJava(
              //language=java
              java(
                """
                  import org.apache.commons.logging.Log;
                  import org.apache.commons.logging.LogFactory;
                  import org.openqa.selenium.WebDriver;

                  class LoginTest {
                      private static final Log LOG = LogFactory.getLog(LoginTest.class);
                      WebDriver driver;
                  }
                  """,
                spec -> spec.markers(javaVersion(17))
              )
            ),
            //language=xml
            pomXml(
              pom("org.seleniumhq.selenium:selenium-java:3.14.0", "org.springframework:spring-core:5.3.39"),
              spec -> spec.after(pom -> assertThat(pom)
                .containsPattern("<artifactId>selenium-java</artifactId>\\s*<version>4\\.\\d+\\.\\d+</version>")
                .doesNotContain("commons-logging")
                .actual())
            )
          )
        );
    }

    @Test
    void htmlUnitDriverThatSeleniumJavaThreeBroughtAlong() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          mavenProject("ui-tests",
            srcTestJava(
              //language=java
              java(
                """
                  import org.openqa.selenium.WebDriver;
                  import org.openqa.selenium.htmlunit.HtmlUnitDriver;

                  class HeadlessTest {
                      WebDriver driver = new HtmlUnitDriver();
                  }
                  """,
                spec -> spec.markers(javaVersion(8))
              )
            ),
            //language=xml
            pomXml(
              pom("org.seleniumhq.selenium:selenium-java:3.5.2"),
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>ui-tests</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.seleniumhq.selenium</groupId>
                            <artifactId>selenium-java</artifactId>
                            <version>4.13.0</version>
                        </dependency>
                        <dependency>
                            <groupId>org.seleniumhq.selenium</groupId>
                            <artifactId>htmlunit-driver</artifactId>
                            <version>4.13.0</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void arquillianSeleniumBomBecomesTheSeleniumBom() {
        rewriteRun(
          mavenProject("ui-tests",
            srcTestJava(
              //language=java
              java(USES_SELENIUM, spec -> spec.markers(javaVersion(8)))
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>ui-tests</artifactId>
                    <version>1.0.0</version>
                    <properties>
                        <selenium.version>3.2.0</selenium.version>
                    </properties>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.jboss.arquillian.selenium</groupId>
                                <artifactId>selenium-bom</artifactId>
                                <version>${selenium.version}</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.seleniumhq.selenium</groupId>
                            <artifactId>selenium-java</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>ui-tests</artifactId>
                    <version>1.0.0</version>
                    <properties>
                        <selenium.version>4.13.0</selenium.version>
                    </properties>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.seleniumhq.selenium</groupId>
                                <artifactId>selenium-bom</artifactId>
                                <version>${selenium.version}</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.seleniumhq.selenium</groupId>
                            <artifactId>selenium-java</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.afterRecipe(pom -> allSeleniumResolvedAt(pom, "4.13."))
            )
          )
        );
    }

    @Test
    void relocatedTypesAndRemovedConstants() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openqa.selenium.Capabilities;
              import org.openqa.selenium.ElementNotVisibleException;
              import org.openqa.selenium.WebElement;
              import org.openqa.selenium.internal.WrapsElement;
              import org.openqa.selenium.logging.LoggingPreferences;
              import org.openqa.selenium.remote.BrowserType;
              import org.openqa.selenium.remote.CapabilityType;
              import org.openqa.selenium.remote.DesiredCapabilities;

              class Grid {
                  DesiredCapabilities capabilities(String browser) {
                      DesiredCapabilities capabilities = new DesiredCapabilities();
                      switch (browser) {
                          case BrowserType.FIREFOX:
                              capabilities.setBrowserName(BrowserType.FIREFOX);
                              break;
                          default:
                              capabilities.setBrowserName(BrowserType.CHROME);
                      }
                      capabilities.setCapability(CapabilityType.PLATFORM, "LINUX");
                      capabilities.setCapability(CapabilityType.ACCEPT_SSL_CERTS, true);
                      capabilities.setCapability(CapabilityType.TAKES_SCREENSHOT, true);
                      return capabilities;
                  }

                  void logging(DesiredCapabilities capabilities, LoggingPreferences preferences) {
                      capabilities.setCapability(CapabilityType.LOGGING_PREFS, preferences);
                  }

                  String version(Capabilities capabilities) {
                      return capabilities.getVersion();
                  }

                  WebElement unwrap(WrapsElement element) {
                      try {
                          return element.getWrappedElement();
                      } catch (ElementNotVisibleException e) {
                          return null;
                      }
                  }
              }
              """,
            """
              import org.openqa.selenium.Capabilities;
              import org.openqa.selenium.ElementNotInteractableException;
              import org.openqa.selenium.WebElement;
              import org.openqa.selenium.WrapsElement;
              import org.openqa.selenium.logging.LoggingPreferences;
              import org.openqa.selenium.remote.CapabilityType;
              import org.openqa.selenium.remote.DesiredCapabilities;

              class Grid {
                  DesiredCapabilities capabilities(String browser) {
                      DesiredCapabilities capabilities = new DesiredCapabilities();
                      switch (browser) {
                          case "firefox":
                              capabilities.setBrowserName("firefox");
                              break;
                          default:
                              capabilities.setBrowserName("chrome");
                      }
                      capabilities.setCapability(CapabilityType.PLATFORM_NAME, "LINUX");
                      capabilities.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);
                      return capabilities;
                  }

                  void logging(DesiredCapabilities capabilities, LoggingPreferences preferences) {
                      capabilities.setCapability("goog:loggingPrefs", preferences);
                  }

                  String version(Capabilities capabilities) {
                      return capabilities.getBrowserVersion();
                  }

                  WebElement unwrap(WrapsElement element) {
                      try {
                          return element.getWrappedElement();
                      } catch (ElementNotInteractableException e) {
                          return null;
                      }
                  }
              }
              """
          )
        );
    }
}
