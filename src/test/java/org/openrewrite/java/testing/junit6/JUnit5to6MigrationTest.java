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
package org.openrewrite.java.testing.junit6;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class JUnit5to6MigrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-jupiter-api-5",
              "testng-7"))
          .recipeFromResources("org.openrewrite.java.testing.junit6.JUnit5to6Migration");
    }

    @DocumentExample
    @Test
    void renamesStoreGetOrComputeIfAbsent() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.extension.ExtensionContext;

              class FooExtension {
                  void store(ExtensionContext.Store store) {
                      store.getOrComputeIfAbsent(StringBuilder.class);
                      store.getOrComputeIfAbsent("key", k -> new StringBuilder());
                      store.getOrComputeIfAbsent("key", k -> new StringBuilder(), StringBuilder.class);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtensionContext;

              class FooExtension {
                  void store(ExtensionContext.Store store) {
                      store.computeIfAbsent(StringBuilder.class);
                      store.computeIfAbsent("key", k -> new StringBuilder());
                      store.computeIfAbsent("key", k -> new StringBuilder(), StringBuilder.class);
                  }
              }
              """,
            spec -> spec.markers(javaVersion(17))
          )
        );
    }

    @Test
    void upgradesJunitBomViaVersionProperty() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              class FooTest {
                  @Test
                  void bar() {
                  }
              }
              """,
            spec -> spec.markers(javaVersion(17))
          ),
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>example</artifactId>
                  <version>1.0.0</version>
                  <properties>
                      <junit-jupiter.version>5.14.4</junit-jupiter.version>
                  </properties>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>org.junit</groupId>
                              <artifactId>junit-bom</artifactId>
                              <version>${junit-jupiter.version}</version>
                              <type>pom</type>
                              <scope>import</scope>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
              </project>
              """,
            spec -> spec.after(actual -> assertThat(actual)
              .as("junit-jupiter.version property should be upgraded to 6.x")
              .containsPattern("<junit-jupiter\\.version>6\\.\\d+\\.\\d+</junit-jupiter\\.version>")
              .actual())
          )
        );
    }

    @Test
    void upgradesExplicitPlatformAndVintageVersionsInMaven() {
        rewriteRun(
          mavenProject("project",
            srcTestJava(java(
              """
                import org.junit.jupiter.api.Test;
                class ExampleTest {
                    @Test
                    void test() {}
                }
                """,
              spec -> spec.markers(javaVersion(17))
            )),
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.14.1</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.junit.platform</groupId>
                            <artifactId>junit-platform-launcher</artifactId>
                            <version>1.14.1</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.junit.vintage</groupId>
                            <artifactId>junit-vintage-engine</artifactId>
                            <version>5.14.1</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(actual -> assertThat(actual)
                .containsPattern("<artifactId>junit-platform-launcher</artifactId>\\s*<version>6\\.\\d+\\.\\d+</version>")
                .containsPattern("<artifactId>junit-vintage-engine</artifactId>\\s*<version>6\\.\\d+\\.\\d+</version>")
                .actual())
            )
          )
        );
    }

    @Test
    void upgradesExistingGradleLauncherInsteadOfOnlyAddingIfMissing() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          mavenProject("project",
            srcTestJava(java(
              """
                import org.junit.jupiter.api.Test;
                class ExampleTest {
                    @Test
                    void test() {}
                }
                """,
              spec -> spec.markers(javaVersion(17))
            )),
            buildGradle(
              """
                plugins {
                    id 'java-library'
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    testImplementation 'org.junit.jupiter:junit-jupiter:5.14.1'
                    testRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.14.1'
                    testRuntimeOnly 'org.junit.vintage:junit-vintage-engine:5.14.1'
                }
                test {
                    useJUnitPlatform()
                }
                """,
              spec -> spec.after(actual -> assertThat(actual)
                .containsPattern("org.junit.platform:junit-platform-launcher:6\\.\\d+\\.\\d+")
                .containsPattern("org.junit.vintage:junit-vintage-engine:6\\.\\d+\\.\\d+")
                .actual())
            )
          )
        );
    }

    @Test
    void leavesMavenModuleUsingTestNgAlone() {
        rewriteRun(
          mavenProject("project",
            srcTestJava(
              java(JUNIT5_EXTENSION, spec -> spec.markers(javaVersion(17))),
              java(TESTNG_TEST, spec -> spec.markers(javaVersion(17)))
            ),
            pomXml(pomWithTestNg("project"))
          )
        );
    }

    @Test
    void leavesGradleModuleUsingTestNgAlone() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          mavenProject("project",
            srcTestJava(
              java(JUNIT5_EXTENSION, spec -> spec.markers(javaVersion(17))),
              java(TESTNG_TEST, spec -> spec.markers(javaVersion(17)))
            ),
            //language=groovy
            buildGradle(
              """
                plugins {
                    id 'java-library'
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    testImplementation 'org.junit.jupiter:junit-jupiter:5.14.1'
                    testImplementation 'org.testng:testng:7.11.0'
                }
                tasks.withType(Test).configureEach {
                    useJUnitPlatform()
                }
                """
            )
          )
        );
    }

    @Test
    void migratesOnlyModuleWithoutTestNg() {
        rewriteRun(
          mavenProject("testng-module",
            srcTestJava(
              java(JUNIT5_EXTENSION, spec -> spec.markers(javaVersion(17))),
              java(TESTNG_TEST, spec -> spec.markers(javaVersion(17)))
            ),
            pomXml(pomWithTestNg("testng-module"))
          ),
          mavenProject("junit-module",
            srcTestJava(
              //language=java
              java(
                """
                  import org.junit.jupiter.api.extension.ExtensionContext;

                  class BazExtension {
                      void store(ExtensionContext.Store store) {
                          store.getOrComputeIfAbsent(StringBuilder.class);
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.extension.ExtensionContext;

                  class BazExtension {
                      void store(ExtensionContext.Store store) {
                          store.computeIfAbsent(StringBuilder.class);
                      }
                  }
                  """,
                spec -> spec.markers(javaVersion(17))
              )
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>junit-module</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.14.1</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(actual -> assertThat(actual)
                .containsPattern("<version>6\\.\\d+\\.\\d+</version>")
                .actual())
            )
          )
        );
    }

    @Test
    void keepsMigrationSupportWhileUsed() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-jupiter-api-5",
              "junit-jupiter-migrationsupport-5")),
          mavenProject("project",
            srcTestJava(
              //language=java
              java(
                """
                  import org.junit.jupiter.api.extension.ExtendWith;
                  import org.junit.jupiter.migrationsupport.rules.ExternalResourceSupport;

                  @ExtendWith(ExternalResourceSupport.class)
                  class FooTest {
                  }
                  """,
                spec -> spec.markers(javaVersion(17))
              )
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter-migrationsupport</artifactId>
                            <version>5.14.1</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(actual -> assertThat(actual)
                .containsPattern("<artifactId>junit-jupiter-migrationsupport</artifactId>\\s*<version>6\\.\\d+\\.\\d+</version>")
                .actual())
            )
          )
        );
    }

    @Test
    void removesUnusedMigrationSupport() {
        rewriteRun(
          mavenProject("project",
            srcTestJava(
              //language=java
              java(
                """
                  import org.junit.jupiter.api.Test;

                  class FooTest {
                      @Test
                      void foo() {
                      }
                  }
                  """,
                spec -> spec.markers(javaVersion(17))
              )
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter-api</artifactId>
                            <version>5.14.1</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter-migrationsupport</artifactId>
                            <version>5.14.1</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(actual -> assertThat(actual)
                .containsPattern("<artifactId>junit-jupiter-api</artifactId>\\s*<version>6\\.\\d+\\.\\d+</version>")
                .doesNotContain("junit-jupiter-migrationsupport")
                .actual())
            )
          )
        );
    }

    @Test
    void upgradesCucumber() {
        rewriteRun(
          mavenProject("project",
            srcTestJava(java(
              """
                import org.junit.jupiter.api.Test;
                class ExampleTest {
                    @Test
                    void test() {}
                }
                """,
              spec -> spec.markers(javaVersion(17))
            )),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.14.1</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.junit.platform</groupId>
                            <artifactId>junit-platform-suite</artifactId>
                            <version>1.14.1</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>io.cucumber</groupId>
                            <artifactId>cucumber-junit-platform-engine</artifactId>
                            <version>7.34.3</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(actual -> assertThat(actual)
                .containsPattern("<artifactId>junit-platform-suite</artifactId>\\s*<version>6\\.\\d+\\.\\d+</version>")
                .containsPattern("<artifactId>cucumber-junit-platform-engine</artifactId>\\s*<version>8\\.\\d+\\.\\d+</version>")
                .actual())
            )
          )
        );
    }

    @Test
    void migratesJUnitPioneer() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5", "junit-pioneer-2")),
          mavenProject("project",
            //language=java
            srcTestJava(java(
              """
                import org.junitpioneer.jupiter.DefaultTimeZone;
                import org.junitpioneer.jupiter.RetryingTest;

                class MyTest {
                    @RetryingTest(3)
                    @DefaultTimeZone("UTC")
                    void flaky() {
                    }
                }
                """,
              """
                import org.junit.jupiter.api.util.DefaultTimeZone;
                import org.junitpioneer.jupiter.RetryingTest;

                class MyTest {
                    @RetryingTest(3)
                    @DefaultTimeZone("UTC")
                    void flaky() {
                    }
                }
                """,
              spec -> spec.markers(javaVersion(17))
            )),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.14.1</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.junit-pioneer</groupId>
                            <artifactId>junit-pioneer</artifactId>
                            <version>2.3.0</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
              // junit-pioneer only moves once 3.0.0 is released, as 3.x does not select release candidates
              spec -> spec.after(actual -> assertThat(actual)
                .containsPattern("<artifactId>junit-jupiter</artifactId>\\s*<version>6\\.\\d+\\.\\d+</version>")
                .containsPattern("<artifactId>junit-pioneer</artifactId>\\s*<version>(2\\.3\\.0|3\\.\\d+\\.\\d+)</version>")
                .actual())
            )
          )
        );
    }

    //language=java
    private static final String JUNIT5_EXTENSION = """
      import org.junit.jupiter.api.extension.ExtensionContext;

      class FooExtension {
          void store(ExtensionContext.Store store) {
              store.getOrComputeIfAbsent(StringBuilder.class);
          }
      }
      """;

    //language=java
    private static final String TESTNG_TEST = """
      import org.testng.annotations.Test;

      class BarTest {
          @Test
          void bar() {
          }
      }
      """;

    //language=xml
    private static String pomWithTestNg(String artifactId) {
        return """
          <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>%s</artifactId>
              <version>1.0.0</version>
              <dependencies>
                  <dependency>
                      <groupId>org.junit.jupiter</groupId>
                      <artifactId>junit-jupiter</artifactId>
                      <version>5.14.1</version>
                      <scope>test</scope>
                  </dependency>
                  <dependency>
                      <groupId>org.testng</groupId>
                      <artifactId>testng</artifactId>
                      <version>7.11.0</version>
                      <scope>test</scope>
                  </dependency>
              </dependencies>
          </project>
          """.formatted(artifactId);
    }
}
