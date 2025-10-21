/*
 * Copyright 2024 the original author or authors.
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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.Tree;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.marker.BuildTool;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

@SuppressWarnings({"NewClassNamingConvention", "EqualsWithItself", "deprecation", "LanguageMismatch"})
class JUnit5MigrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing.junit5")
            .build()
            .activateRecipes("org.openrewrite.java.testing.junit5.JUnit4to5Migration"));
    }

    @DocumentExample
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/145")
    @Test
    void assertThatReceiver() {
        rewriteRun(
          spec -> spec
            .parser(JavaParser.fromJavaVersion()
              .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "hamcrest-3")),
          //language=java
          java(
            """
              import org.junit.Assert;
              import org.junit.Test;

              import static java.util.Arrays.asList;
              import static org.hamcrest.Matchers.containsInAnyOrder;

              public class SampleTest {
                  @SuppressWarnings("ALL")
                  @Test
                  public void filterShouldRemoveUnusedConfig() {
                      Assert.assertThat(asList("1", "2", "3"),
                              containsInAnyOrder("3", "2", "1"));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static java.util.Arrays.asList;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.containsInAnyOrder;

              public class SampleTest {
                  @SuppressWarnings("ALL")
                  @Test
                  public void filterShouldRemoveUnusedConfig() {
                      assertThat(asList("1", "2", "3"),
                              containsInAnyOrder("3", "2", "1"));
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/284")
    @Test
    void classReference() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Test;

              public class Sample {
                  void method() {
                      Class<Test> c = Test.class;
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              public class Sample {
                  void method() {
                      Class<Test> c = Test.class;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/279")
    @Test
    void upgradeMavenPluginVersions() {
        rewriteRun(
          pomXml(
            //language=xml
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example.jackson</groupId>
                <artifactId>test-plugins</artifactId>
                <version>1.0.0</version>
                <build>
                  <plugins>
                    <plugin>
                      <groupId>org.apache.maven.plugins</groupId>
                      <artifactId>maven-surefire-plugin</artifactId>
                      <version>2.20.1</version>
                    </plugin>
                    <plugin>
                      <groupId>org.apache.maven.plugins</groupId>
                      <artifactId>maven-failsafe-plugin</artifactId>
                      <version>2.20.1</version>
                    </plugin>
                  </plugins>
                </build>
              </project>
              """,
            spec -> spec.after(actual -> {
                assertThat(Pattern.compile("<version>3\\.(.*)</version>").matcher(actual).results().toList()).hasSize(2);
                return actual;
            })
          )
        );
    }

    @Test
    void excludeJunit4Dependency() {
        // Just using play-test_2.13 as an example because it appears to still depend on junit.
        // In practice, this would probably just break it, I assume.
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-parent</artifactId>
                      <version>3.2.1</version>
                      <relativePath/> <!-- lookup parent from repository -->
                  </parent>
                  <groupId>dev.ted</groupId>
                  <artifactId>needs-exclusion</artifactId>
                  <version>0.0.1</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.springframework.boot</groupId>
                          <artifactId>spring-boot-starter</artifactId>
                      </dependency>
                      <dependency>
                          <groupId>com.typesafe.play</groupId>
                          <artifactId>play-test_2.13</artifactId>
                          <version>2.9.6</version>
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
                      <version>3.2.1</version>
                      <relativePath/> <!-- lookup parent from repository -->
                  </parent>
                  <groupId>dev.ted</groupId>
                  <artifactId>needs-exclusion</artifactId>
                  <version>0.0.1</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.springframework.boot</groupId>
                          <artifactId>spring-boot-starter</artifactId>
                      </dependency>
                      <dependency>
                          <groupId>com.typesafe.play</groupId>
                          <artifactId>play-test_2.13</artifactId>
                          <version>2.9.6</version>
                          <scope>test</scope>
                          <exclusions>
                              <exclusion>
                                  <groupId>junit</groupId>
                                  <artifactId>junit</artifactId>
                              </exclusion>
                          </exclusions>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/429")
    @Test
    void dontExcludeJunit4DependencyFromTestcontainers() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example.jackson</groupId>
                  <artifactId>test-plugins</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.testcontainers</groupId>
                          <artifactId>testcontainers</artifactId>
                          <version>1.18.3</version>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example.jackson</groupId>
                  <artifactId>test-plugins</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.testcontainers</groupId>
                          <artifactId>testcontainers</artifactId>
                          <version>2.0.1</version>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/429")
    @Test
    void dontExcludeJunit4DependencyFromTestcontainersJupiter() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example.jackson</groupId>
                  <artifactId>test-plugins</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.testcontainers</groupId>
                          <artifactId>junit-jupiter</artifactId>
                          <version>1.18.3</version>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example.jackson</groupId>
                  <artifactId>test-plugins</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.testcontainers</groupId>
                          <artifactId>testcontainers-junit-jupiter</artifactId>
                          <version>2.0.1</version>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/477")
    @Test
    void dontExcludeJunit4DependencyFromSpringBootTestcontainers() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-parent</artifactId>
                      <version>3.2.1</version>
                      <relativePath/> <!-- lookup parent from repository -->
                  </parent>
                  <groupId>dev.ted</groupId>
                  <artifactId>testcontainer-migrate</artifactId>
                  <version>0.0.1</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.springframework.boot</groupId>
                          <artifactId>spring-boot-starter</artifactId>
                      </dependency>
                      <dependency>
                          <groupId>org.springframework.boot</groupId>
                          <artifactId>spring-boot-starter-test</artifactId>
                          <scope>test</scope>
                      </dependency>
                      <dependency>
                          <groupId>org.springframework.boot</groupId>
                          <artifactId>spring-boot-testcontainers</artifactId>
                          <scope>test</scope>
                      </dependency>
                      <dependency>
                          <groupId>org.testcontainers</groupId>
                          <artifactId>junit-jupiter</artifactId>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    // edge case for deprecated use of assertEquals
    // https://junit.org/junit4/javadoc/4.13/org/junit/Assert.html#assertEquals(java.lang.Object%5B%5D,%20java.lang.Object%5B%5D)
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/pull/384")
    @Test
    void assertEqualsWithArrayArgumentToAssertArrayEquals() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Assert;

              class MyTest {
                  void test() {
                       Assert.assertEquals(new Object[1], new Object[1]);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions;

              class MyTest {
                  void test() {
                       Assertions.assertArrayEquals(new Object[1], new Object[1]);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/443")
    @Test
    void migrateInheritedTestBeforeAfterAnnotations() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.After;
              import org.junit.Before;
              import org.junit.Test;

              public class AbstractTest {
                  @Before
                  public void before() {
                  }

                  @After
                  public void after() {
                  }

                  @Test
                  public void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;

              public class AbstractTest {
                  @BeforeEach
                  public void before() {
                  }

                  @AfterEach
                  public void after() {
                  }

                  @Test
                  public void test() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              public class A extends AbstractTest {
                  public void before() {
                  }

                  public void after() {
                  }

                  public void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;

              public class A extends AbstractTest {
                  @BeforeEach
                  public void before() {
                  }

                  @AfterEach
                  public void after() {
                  }

                  @Test
                  public void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void noJunitDependencyIfApiAlreadyPresent() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
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
                  testImplementation 'org.junit.jupiter:junit-jupiter-api:5.7.2'
              }
              tasks.withType(Test).configureEach {
                  useJUnitPlatform()
              }
              """
          ),
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>dev.ted</groupId>
                  <artifactId>testcontainer-migrate</artifactId>
                  <version>0.0.1</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.junit.jupiter</groupId>
                          <artifactId>junit-jupiter-api</artifactId>
                          <version>5.7.2</version>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void removeJunitVintageEngineFromGradleBuild() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
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
                  testImplementation 'junit:junit:4.12'
                  testRuntimeOnly 'org.junit.vintage:junit-vintage-engine:5.7.2'
              }
              """,
            """
              plugins {
                  id 'java-library'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
              }
              tasks.withType(Test).configureEach {
                  useJUnitPlatform()
              }
              """
          ),
          srcTestJava(java("""
              import org.junit.Test;

              public class MyTest {
                  @Test
                  public void hello() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              public class MyTest {
                  @Test
                  public void hello() {
                  }
              }
              """
          ))
        );
    }

    @Test
    void bumpSurefireOnOlderMavenVersions() {
        rewriteRun(
          spec -> spec.recipeFromResource("/META-INF/rewrite/junit5.yml", "org.openrewrite.java.testing.junit5.UpgradeSurefirePlugin"),
          pomXml(
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>dev.ted</groupId>
                  <artifactId>testcontainer-migrate</artifactId>
                  <version>0.0.1</version>
              </project>
              """,
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>dev.ted</groupId>
                  <artifactId>testcontainer-migrate</artifactId>
                  <version>0.0.1</version>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.apache.maven.plugins</groupId>
                              <artifactId>maven-surefire-plugin</artifactId>
                              <version>3.2.5</version>
                              <dependencies>
                                  <dependency>
                                      <groupId>org.junit.platform</groupId>
                                      <artifactId>junit-platform-surefire-provider</artifactId>
                                      <version>1.1.0</version>
                                  </dependency>
                              </dependencies>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """,
            spec -> spec.markers(new BuildTool(Tree.randomId(), BuildTool.Type.Maven, "3.5.4"))
          )
        );
    }

    @Test
    void addMockitoJupiterDependencyIfExtendWithPresent() {
        rewriteRun(
          spec -> spec
            .parser(JavaParser.fromJavaVersion()
              .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "mockito-all-1.10"))
            .recipe(Environment.builder()
              .scanRuntimeClasspath("org.openrewrite.java.testing.junit5")
              .build()
              .activateRecipes("org.openrewrite.java.testing.junit5.UseMockitoExtension")),
          mavenProject("sample",
            srcMainJava(
              //language=java
              java(
                """
                  import org.junit.runner.RunWith;
                  import org.mockito.runners.MockitoJUnitRunner;

                  @RunWith(MockitoJUnitRunner.class)
                  public class MyClassTest {}
                  """,
                """
                  import org.junit.jupiter.api.extension.ExtendWith;
                  import org.mockito.junit.jupiter.MockitoExtension;
                  import org.mockito.junit.jupiter.MockitoSettings;
                  import org.mockito.quality.Strictness;

                  @MockitoSettings(strictness = Strictness.WARN)
                  @ExtendWith(MockitoExtension.class)
                  public class MyClassTest {
                  }
                  """
              )
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>project</artifactId>
                    <version>0.0.1</version>
                    <dependencies>
                        <dependency>
                            <groupId>junit</groupId>
                            <artifactId>junit</artifactId>
                            <version>4.12</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.mockito</groupId>
                            <artifactId>mockito-core</artifactId>
                            <version>2.23.4</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>project</artifactId>
                    <version>0.0.1</version>
                    <dependencies>
                        <dependency>
                            <groupId>junit</groupId>
                            <artifactId>junit</artifactId>
                            <version>4.12</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.mockito</groupId>
                            <artifactId>mockito-core</artifactId>
                            <version>4.11.0</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.mockito</groupId>
                            <artifactId>mockito-junit-jupiter</artifactId>
                            <version>4.11.0</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Nested
    class TestngExclude {
        @Test
        void noChangesIfTestNgGradleDependencyIncluded() {
            rewriteRun(
              spec -> spec.beforeRecipe(withToolingApi()),
              mavenProject("project",
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
                        testImplementation 'junit:junit:4.12'
                        testImplementation 'org.testng:testng:7.8.0'
                    }
                    tasks.withType(Test).configureEach {
                        useJUnitPlatform()
                    }
                    """
                ),
                srcTestJava(
                  //language=java
                  java(
                    """
                      import org.junit.Ignore;

                      class ExampleClass {
                          @Ignore
                          public void testMethod() {}
                      }
                      """
                  )
                )
              )
            );
        }

        @Test
        void noChangesIfTestNgMavenDependencyIncluded() {
            rewriteRun(
              mavenProject("project",
                //language=xml
                pomXml(
                  """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>dev.ted</groupId>
                        <artifactId>testcontainer-migrate</artifactId>
                        <version>0.0.1</version>
                        <dependencies>
                            <dependency>
                                <groupId>junit</groupId>
                                <artifactId>junit</artifactId>
                                <version>4.12</version>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>org.testng</groupId>
                                <artifactId>testng</artifactId>
                                <version>7.8.0</version>
                            </dependency>
                        </dependencies>
                    </project>
                    """
                ),
                srcTestJava(
                  //language=java
                  java(
                    """
                      import org.junit.Ignore;

                      class ExampleClass {
                          @Ignore
                          public void testMethod() {}
                      }
                      """
                  )
                )
              )
            );
        }
    }

    /**
     * The bug this test is reproducing is likely not specific to the JUnit5 upgrade, it's just where it was first
     * encountered and isolated. It would probably be reproducible using other recipes that reorder method parameters,
     * provided that a Javadoc link refers to an affected method, and the link has a newline in between the parameters.
     * If that proves to be true, this test should be generalized and moved to the tests of {@code rewrite-java}.
     */
    @Issue("https://github.com/openrewrite/rewrite/issues/6001")
    @Test
    void correctlyHandleALineBreakInAJavadocLinkThatReferencesAMethodWhoseParametersGetReordered() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          //language=java
          java(
            """
              import org.junit.Assert;
              import org.junit.Test;

              public class UltimateQuestionTest {

                  /**
                   * Checks that <i>The Answer to the Ultimate Question of Life, the Universe, and Everything</i>
                   * is indeed {@code 42}. The test relies on JUnit's {@link Assert#assertEquals(String, long,
                   * long)} method to verify this.
                   */
                  @Test
                  public void testUltimateAnswer() {
                      int answerToTheUltimateQuestion = 2 * 3 * 7;
                      Assert.assertEquals("The Ultimate Answer", 42, answerToTheUltimateQuestion);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions;
              import org.junit.jupiter.api.Test;

              public class UltimateQuestionTest {

                  /**
                   * Checks that <i>The Answer to the Ultimate Question of Life, the Universe, and Everything</i>
                   * is indeed {@code 42}. The test relies on JUnit's {@link Assertions#assertEquals(long, long,
                   * String)} method to verify this.
                   */
                  @Test
                  public void testUltimateAnswer() {
                      int answerToTheUltimateQuestion = 2 * 3 * 7;
                      Assertions.assertEquals(42, answerToTheUltimateQuestion, "The Ultimate Answer");
                  }
              }
              """
          )
        );
    }
}
