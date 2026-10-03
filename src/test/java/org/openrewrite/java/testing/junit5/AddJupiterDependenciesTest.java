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

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class AddJupiterDependenciesTest implements RewriteTest {

    @Language("java")
    private static final String SOME_TEST = """
      import org.junit.jupiter.api.Test;
      
      class FooTest {
          @Test
          void bar() {
          }
      }
      """;

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddJupiterDependencies())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api"));
    }

    @Test
    void declaresApiUsedThroughAggregateWhenDependencyAnalysisIsEnabled() {
        rewriteRun(
          mavenProject("project",
            srcTestJava(java(SOME_TEST)),
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>6.0.0</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <artifactId>maven-dependency-plugin</artifactId>
                                <version>3.8.1</version>
                                <executions>
                                    <execution>
                                        <goals><goal>analyze-only</goal></goals>
                                        <configuration><failOnWarning>true</failOnWarning></configuration>
                                    </execution>
                                </executions>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """,
              spec -> spec.after(pom -> assertThat(pom)
                .contains("<artifactId>junit-jupiter-api</artifactId>")
                .contains("<artifactId>junit-jupiter-engine</artifactId>")
                .doesNotContain("<artifactId>junit-jupiter</artifactId>", "<version>5.")
                .doesNotContain("<artifactId>junit-jupiter-params</artifactId>")
                .contains("<version>6.0.0</version>")
                .actual())
            )
          )
        );
    }

    @Test
    void inheritsDependencyAnalysisAndDeclaresParameterizedApi() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5", "junit-jupiter-params-5")),
          mavenProject("parent",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0</version>
                    <packaging>pom</packaging>
                    <modules><module>child</module></modules>
                    <build>
                        <plugins>
                            <plugin>
                                <artifactId>maven-dependency-plugin</artifactId>
                                <version>3.8.1</version>
                                <executions>
                                    <execution>
                                        <goals><goal>analyze-only</goal></goals>
                                        <configuration><failOnWarning>true</failOnWarning></configuration>
                                    </execution>
                                </executions>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """
            ),
            mavenProject("child",
              srcTestJava(java(
                """
                  import org.junit.jupiter.params.ParameterizedTest;
                  import org.junit.jupiter.params.provider.ValueSource;
                  import static org.junit.jupiter.api.Assertions.assertNotNull;

                  class ExampleTest {
                      @ParameterizedTest
                      @ValueSource(strings = "value")
                      void test(String value) {
                          assertNotNull(value);
                      }
                  }
                  """
              )),
              pomXml(
                """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <parent>
                          <groupId>org.example</groupId>
                          <artifactId>parent</artifactId>
                          <version>1.0</version>
                      </parent>
                      <artifactId>child</artifactId>
                      <dependencies>
                          <dependency>
                              <groupId>org.junit.jupiter</groupId>
                              <artifactId>junit-jupiter</artifactId>
                              <version>5.10.2</version>
                              <scope>test</scope>
                          </dependency>
                      </dependencies>
                  </project>
                  """,
                spec -> spec.after(pom -> assertThat(pom)
                  .contains("<artifactId>junit-jupiter-api</artifactId>",
                    "<artifactId>junit-jupiter-engine</artifactId>",
                    "<artifactId>junit-jupiter-params</artifactId>")
                  .doesNotContain("<artifactId>junit-jupiter</artifactId>")
                  .contains("<version>5.10.2</version>")
                  .actual())
              )
            )
          )
        );
    }

    @Test
    void retainsAggregateWithoutDependencyAnalysis() {
        rewriteRun(
          mavenProject("project",
            srcTestJava(java(SOME_TEST)),
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.2</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <artifactId>maven-dependency-plugin</artifactId>
                                <version>3.8.1</version>
                                <executions>
                                    <execution>
                                        <goals><goal>copy-dependencies</goal></goals>
                                    </execution>
                                </executions>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """
            )
          )
        );
    }

    @Test
    void declaresDirectApiDuringJUnit4Migration() {
        rewriteRun(
          spec -> spec.cycles(1).expectedCyclesThatMakeChanges(1).recipeFromResources("org.openrewrite.java.testing.junit5.JUnit4to5Migration")
            .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-4")),
          mavenProject("project",
            srcTestJava(java(
              """
                import org.junit.Test;

                class ExampleTest {
                    @Test public void test() {}
                }
                """,
              """
                import org.junit.jupiter.api.Test;

                class ExampleTest {
                    @Test public void test() {}
                }
                """
            )),
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>junit</groupId>
                            <artifactId>junit</artifactId>
                            <version>4.13.2</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <artifactId>maven-dependency-plugin</artifactId>
                                <version>3.8.1</version>
                                <executions>
                                    <execution>
                                        <goals><goal>analyze-only</goal></goals>
                                        <configuration><failOnWarning>true</failOnWarning></configuration>
                                    </execution>
                                </executions>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """,
              spec -> spec.after(pom -> assertThat(pom)
                .contains("<artifactId>junit-jupiter-api</artifactId>", "<artifactId>junit-jupiter-engine</artifactId>")
                .doesNotContain("<artifactId>junit-jupiter</artifactId>", "<groupId>junit</groupId>")
                .actual())
            )
          )
        );
    }

    @Test
    void declaresParametersGeneratedByJUnitParamsInOneCycle() {
        rewriteRun(
          spec -> spec.cycles(1).expectedCyclesThatMakeChanges(1).recipeFromResources("org.openrewrite.java.testing.junit5.JUnit4to5Migration")
            .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-4", "JUnitParams-1.1")),
          mavenProject("project",
            srcTestJava(java(
              """
                import org.junit.Test;
                import org.junit.runner.RunWith;
                import junitparams.JUnitParamsRunner;
                import junitparams.Parameters;

                @RunWith(JUnitParamsRunner.class)
                class ExampleTest {
                    @Test
                    @Parameters({"1", "2"})
                    public void test(int value) {}
                }
                """,
              spec -> spec.after(source -> assertThat(source)
                .contains("@ParameterizedTest")
                .doesNotContain("@RunWith")
                .actual())
            )),
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>junit</groupId>
                            <artifactId>junit</artifactId>
                            <version>4.13.2</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <artifactId>maven-dependency-plugin</artifactId>
                                <version>3.8.1</version>
                                <executions>
                                    <execution>
                                        <goals><goal>analyze-only</goal></goals>
                                        <configuration><failOnWarning>true</failOnWarning></configuration>
                                    </execution>
                                </executions>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """,
              spec -> spec.after(pom -> assertThat(pom)
                .contains("<artifactId>junit-jupiter-api</artifactId>", "<artifactId>junit-jupiter-engine</artifactId>",
                  "<artifactId>junit-jupiter-params</artifactId>")
                .doesNotContain("<artifactId>junit-jupiter</artifactId>", "<groupId>junit</groupId>")
                .actual())
            )
          )
        );
    }

    @DocumentExample
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/585")
    @Test
    void addToTestScope() {
        rewriteRun(
          mavenProject("project",
            srcTestJava(java(SOME_TEST)),
            pomXml(
              //language=xml
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>project</artifactId>
                    <version>0.0.1</version>
                </project>
                """,
              spec -> spec.after(pom -> {
                  return assertThat(pom)
                          .contains("junit-jupiter")
                          .contains("<scope>test</scope>").actual();
              })
            )
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/585")
    @Test
    void addToCompileScope() {
        rewriteRun(
          mavenProject("project",
            srcMainJava(java(SOME_TEST)),
            pomXml(
              //language=xml
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>project</artifactId>
                    <version>0.0.1</version>
                </project>
                """,
              spec -> spec.after(pom -> {
                  return assertThat(pom)
                          .contains("junit-jupiter")
                          .doesNotContain("<scope>test</scope>").actual();
              })
            )
          )
        );
    }

    @Test
    void doNotAddWithoutJUnit() {
        rewriteRun(
          mavenProject("project",
            srcTestJava(
              //language=java
              java(
                """
                  public class NotATest {
                      public void addition() {
                          assert 4 == 2 + 2;
                      }
                  }
                  """
              )
            ),
            pomXml(
              //language=xml
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>project</artifactId>
                    <version>0.0.1</version>
                </project>
                """
            )
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/1113")
    @Test
    void addWhenOnlyJUnit3TestCaseIsUsed() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-4")),
          mavenProject("project",
            srcTestJava(
              //language=java
              java(
                """
                  import junit.framework.TestCase;

                  public class LegacyTest extends TestCase {
                      public void testAddition() {
                          assertEquals(4, 2 + 2);
                      }
                  }
                  """
              )
            ),
            pomXml(
              //language=xml
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>project</artifactId>
                    <version>0.0.1</version>
                </project>
                """,
              spec -> spec.after(pom -> {
                  return assertThat(pom)
                          .contains("junit-jupiter")
                          .contains("<scope>test</scope>").actual();
              })
            )
          )
        );
    }
}
