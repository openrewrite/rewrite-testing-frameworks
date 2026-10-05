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
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.RecipeRun;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteRunner;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class SystemRulesMigrationTest implements RewriteTest {

    // Runs only as many cycles as the Moderne CLI and platform would, rather than as many as the test expects
    private static final RewriteRunner PRODUCTION_CYCLES = new RewriteRunner() {
        @Override
        public RecipeRun run(Recipe recipe, Context context) {
            return recipe.run(context.getSources(), context.getExecutionContext(), 3, 1);
        }
    };

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-4",
              "junit-jupiter-api-5",
              "system-rules-1",
              "system-stubs-core-2",
              "system-stubs-jupiter-2",
              "junit-pioneer-2"))
          .recipeFromResources("org.openrewrite.java.testing.junit5.JUnit4to5Migration");
    }

    //language=xml
    private static final String POM_BEFORE = """
      <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.example</groupId>
          <artifactId>example</artifactId>
          <version>1.0.0</version>
          <dependencies>
              <dependency>
                  <groupId>com.github.stefanbirkner</groupId>
                  <artifactId>system-rules</artifactId>
                  <version>1.19.0</version>
                  <scope>test</scope>
              </dependency>
              <dependency>
                  <groupId>org.junit.jupiter</groupId>
                  <artifactId>junit-jupiter</artifactId>
                  <version>5.14.0</version>
                  <scope>test</scope>
              </dependency>
          </dependencies>
      </project>
      """;

    @DocumentExample
    @Test
    void migrateRulesAndDependencies() {
        rewriteRun(
          spec -> spec.runner(PRODUCTION_CYCLES),
          mavenProject("example",
            srcTestJava(
              //language=java
              java(
                """
                  import org.junit.Rule;
                  import org.junit.contrib.java.lang.system.EnvironmentVariables;
                  import org.junit.contrib.java.lang.system.ExpectedSystemExit;
                  import org.junit.contrib.java.lang.system.ProvideSystemProperty;
                  import org.junit.contrib.java.lang.system.SystemOutRule;
                  import org.junit.jupiter.api.Test;

                  import static org.junit.jupiter.api.Assertions.assertEquals;

                  class CliTest {
                      @Rule
                      public final ProvideSystemProperty properties = new ProvideSystemProperty("mode", "test");

                      @Rule
                      public final EnvironmentVariables environment = new EnvironmentVariables();

                      @Rule
                      public final SystemOutRule out = new SystemOutRule().enableLog().mute();

                      @Rule
                      public final ExpectedSystemExit exit = ExpectedSystemExit.none();

                      @Test
                      void printsUsageAndExits() {
                          environment.set("HOME", "/tmp");
                          exit.expectSystemExitWithStatus(1);
                          exit.checkAssertionAfterwards(() -> assertEquals("usage", out.getLog()));
                          System.out.print("usage");
                          System.exit(1);
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.extension.ExtendWith;
                  import org.junitpioneer.jupiter.SetSystemProperty;
                  import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
                  import uk.org.webcompere.systemstubs.jupiter.SystemStub;
                  import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
                  import uk.org.webcompere.systemstubs.stream.SystemOut;

                  import static org.junit.jupiter.api.Assertions.assertEquals;
                  import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

                  @ExtendWith(SystemStubsExtension.class)
                  @SetSystemProperty(key = "mode", value = "test")
                  class CliTest {

                      @SystemStub
                      public final EnvironmentVariables environment = new EnvironmentVariables();

                      @SystemStub
                      public final SystemOut out = new SystemOut();

                      @Test
                      void printsUsageAndExits() throws Exception {
                          environment.set("HOME", "/tmp");
                          int status = catchSystemExit(() -> {
                              System.out.print("usage");
                              System.exit(1);
                          });
                          assertEquals(1, status);
                          assertEquals("usage", out.getText());
                      }
                  }
                  """
              )
            ),
            pomXml(
              POM_BEFORE,
              spec -> spec.after(actual -> assertThat(actual)
                .doesNotContain("system-rules")
                .containsPattern("<artifactId>system-stubs-jupiter</artifactId>\\s*<version>2\\.\\d+\\.\\d+</version>\\s*<scope>test</scope>")
                .containsPattern("<artifactId>junit-pioneer</artifactId>\\s*<version>2\\.\\d+\\.\\d+</version>\\s*<scope>test</scope>")
                .actual())
            )
          )
        );
    }

    @Test
    void keepSystemRulesWhileStillUsed() {
        rewriteRun(
          spec -> spec.runner(PRODUCTION_CYCLES),
          mavenProject("example",
            srcTestJava(
              //language=java
              java(
                """
                  import org.junit.Rule;
                  import org.junit.contrib.java.lang.system.SystemOutRule;
                  import org.junit.jupiter.api.Test;

                  class GreeterTest {
                      @Rule
                      public final SystemOutRule out = new SystemOutRule().enableLog();

                      @Test
                      void greets() {
                          out.mute();
                      }
                  }
                  """,
                """
                  import org.junit.Rule;
                  import org.junit.contrib.java.lang.system.SystemOutRule;
                  import org.junit.jupiter.api.Test;

                  class GreeterTest {
                      // TODO Migrate by hand to System Stubs: this rule is used in a way that has no direct System Stubs equivalent.
                      @Rule
                      public final SystemOutRule out = new SystemOutRule().enableLog();

                      @Test
                      void greets() {
                          out.mute();
                      }
                  }
                  """
              )
            ),
            pomXml(POM_BEFORE)
          )
        );
    }

    @Test
    void junit4Test() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.Test;
              import org.junit.contrib.java.lang.system.ExpectedSystemExit;
              import org.junit.contrib.java.lang.system.SystemOutRule;

              import static org.junit.Assert.assertEquals;

              public class CliTest {
                  @Rule
                  public final SystemOutRule out = new SystemOutRule().enableLog().mute();

                  @Rule
                  public final ExpectedSystemExit exit = ExpectedSystemExit.none();

                  @Test
                  public void printsUsageAndExits() {
                      exit.expectSystemExitWithStatus(1);
                      exit.checkAssertionAfterwards(() -> assertEquals("usage", out.getLog()));
                      System.out.print("usage");
                      System.exit(1);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import uk.org.webcompere.systemstubs.jupiter.SystemStub;
              import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
              import uk.org.webcompere.systemstubs.stream.SystemOut;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

              @ExtendWith(SystemStubsExtension.class)
              public class CliTest {
                  @SystemStub
                  public final SystemOut out = new SystemOut();

                  @Test
                  public void printsUsageAndExits() throws Exception {
                      int status = catchSystemExit(() -> {
                          System.out.print("usage");
                          System.exit(1);
                      });
                      assertEquals(1, status);
                      assertEquals("usage", out.getText());
                  }
              }
              """
          )
        );
    }

    @Test
    void upgradeJUnitPioneerForRestoreSystemProperties() {
        rewriteRun(
          spec -> spec.runner(PRODUCTION_CYCLES),
          mavenProject("example",
            srcTestJava(
              //language=java
              java(
                """
                  import org.junit.Rule;
                  import org.junit.contrib.java.lang.system.RestoreSystemProperties;
                  import org.junit.jupiter.api.Test;

                  class ConfigTest {
                      @Rule
                      public final RestoreSystemProperties restore = new RestoreSystemProperties();

                      @Test
                      void changesProperties() {
                          System.setProperty("mode", "test");
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                  import org.junitpioneer.jupiter.RestoreSystemProperties;

                  @RestoreSystemProperties
                  class ConfigTest {

                      @Test
                      void changesProperties() {
                          System.setProperty("mode", "test");
                      }
                  }
                  """
              )
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>com.github.stefanbirkner</groupId>
                            <artifactId>system-rules</artifactId>
                            <version>1.19.0</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.junit-pioneer</groupId>
                            <artifactId>junit-pioneer</artifactId>
                            <version>1.7.1</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(actual -> assertThat(actual)
                .doesNotContain("system-rules")
                .containsPattern("<artifactId>junit-pioneer</artifactId>\\s*<version>2\\.\\d+\\.\\d+</version>")
                .actual())
            )
          )
        );
    }

    @Test
    void addDependencyToTheSourceSetThatUsesIt() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()).runner(PRODUCTION_CYCLES),
          mavenProject("example",
            srcSmokeTestJava(
              //language=java
              java(
                """
                  import org.junit.Rule;
                  import org.junit.contrib.java.lang.system.EnvironmentVariables;
                  import org.junit.jupiter.api.Test;

                  class EnvironmentTest {
                      @Rule
                      public final EnvironmentVariables environment = new EnvironmentVariables();

                      @Test
                      void setsHome() {
                          environment.set("HOME", "/tmp");
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.extension.ExtendWith;
                  import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
                  import uk.org.webcompere.systemstubs.jupiter.SystemStub;
                  import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

                  @ExtendWith(SystemStubsExtension.class)
                  class EnvironmentTest {
                      @SystemStub
                      public final EnvironmentVariables environment = new EnvironmentVariables();

                      @Test
                      void setsHome() {
                          environment.set("HOME", "/tmp");
                      }
                  }
                  """
              )
            ),
            //language=groovy
            buildGradle(
              """
                plugins {
                    id "java"
                }
                repositories {
                    mavenCentral()
                }
                sourceSets {
                    smokeTest
                }
                dependencies {
                    smokeTestImplementation "org.junit.jupiter:junit-jupiter:5.14.0"
                    smokeTestImplementation "com.github.stefanbirkner:system-rules:1.19.0"
                }
                """,
              spec -> spec.after(actual -> assertThat(actual)
                .doesNotContain("system-rules")
                .containsPattern("smokeTestImplementation \"uk\\.org\\.webcompere:system-stubs-jupiter:2\\.\\d+\\.\\d+\"")
                .actual())
            )
          )
        );
    }

    @Test
    void addSystemStubsForCatchSystemExitOnly() {
        rewriteRun(
          spec -> spec.runner(PRODUCTION_CYCLES),
          mavenProject("example",
            srcTestJava(
              //language=java
              java(
                """
                  import org.junit.Rule;
                  import org.junit.contrib.java.lang.system.ExpectedSystemExit;
                  import org.junit.jupiter.api.Test;

                  class CliTest {
                      @Rule
                      public final ExpectedSystemExit exit = ExpectedSystemExit.none();

                      @Test
                      void exits() {
                          exit.expectSystemExitWithStatus(1);
                          System.exit(1);
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;

                  import static org.junit.jupiter.api.Assertions.assertEquals;
                  import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

                  class CliTest {

                      @Test
                      void exits() throws Exception {
                          int status = catchSystemExit(() -> System.exit(1));
                          assertEquals(1, status);
                      }
                  }
                  """
              )
            ),
            pomXml(
              POM_BEFORE,
              spec -> spec.after(actual -> assertThat(actual)
                .doesNotContain("system-rules")
                .contains("<artifactId>system-stubs-jupiter</artifactId>")
                .doesNotContain("junit-pioneer")
                .actual())
            )
          )
        );
    }
}
