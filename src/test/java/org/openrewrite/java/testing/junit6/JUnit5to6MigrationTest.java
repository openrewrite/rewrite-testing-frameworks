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

    //language=java
    private static final String JUNIT5_EXTENSION =
      """
        import org.junit.jupiter.api.extension.ExtensionContext;

        class FooExtension {
            void store(ExtensionContext.Store store) {
                store.getOrComputeIfAbsent(StringBuilder.class);
            }
        }
        """;

    //language=java
    private static final String TESTNG_TEST =
      """
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
