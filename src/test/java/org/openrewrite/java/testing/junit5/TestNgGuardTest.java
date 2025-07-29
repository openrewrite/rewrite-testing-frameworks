/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class TestNgGuardTest implements RewriteTest {
    private static final String MavenMarker = "<!--~~>-->";
    private static final String GradleMarker = "/*~~>*/";
    private static final String JavaMarker = GradleMarker;

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5", "testng"))
          .beforeRecipe(withToolingApi())
          .recipe(new TestNgGuard());
    }

    @Test
    void whenTestNgDependencyDoesNotMark() {
        rewriteRun(
          mavenProject("project-gradle",
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
                    testImplementation 'org.junit.jupiter:junit-jupiter-api:5.13.3'
                    testImplementation 'org.testng:testng:7.8.0'
                }
                """
            ),
            srcTestJava(
              //language=java
              java(
                """
                  import org.junit.jupiter.api.Test;

                  class ExampleClassGradleTest {
                      @Test
                      public void testMethod() {}
                  }
                  """
              )
            )
          ),
          mavenProject("project-maven",
            //language=xml
            pomXml(
              """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>project-maven</artifactId>
                    <version>0.0.1</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter-api</artifactId>
                            <version>5.13.3</version>
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
                  import org.junit.jupiter.api.Test;

                  class ExampleClassMavenTest {
                      @Test
                      public void testMethod() {}
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void whenTestNgTypeUsageDoesNotMark() {
        rewriteRun(
          mavenProject("project-gradle",
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
                    testImplementation 'org.junit.jupiter:junit-jupiter-api:5.13.3'
                }
                """
            ),
            srcTestJava(
              //language=java
              java(
                """
                  import org.testng.annotations.Test;

                  class ExampleClassGradleTest {
                      @Test
                      public void testMethod() {}
                  }
                  """
              )
            )
          ),
          mavenProject("project-maven",
            //language=xml
            pomXml(
              """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>project-maven</artifactId>
                    <version>0.0.1</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter-api</artifactId>
                            <version>5.13.3</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            srcTestJava(
              //language=java
              java(
                """
                  import org.testng.annotations.Test;

                  class ExampleClassMavenTest {
                      @Test
                      public void testMethod() {}
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void whenNoTestNgDependencyNorTypeUsageMarks() {
        rewriteRun(
          mavenProject("project-gradle",
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
                    testImplementation 'org.junit.jupiter:junit-jupiter-api:5.13.3'
                }
                """,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(GradleMarker)
                  .actual()
              )
            ),
            srcTestJava(
              //language=java
              java(
                """
                  import org.junit.jupiter.api.Test;

                  class ExampleClassGradleTest {
                      @Test
                      public void testMethod() {}
                  }
                  """,
                spec -> spec.after(actual ->
                  assertThat(actual)
                    .startsWith(JavaMarker)
                    .actual()
                )
              )
            )
          ),
          mavenProject("project-maven",
            //language=xml
            pomXml(
              """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>project-maven</artifactId>
                    <version>0.0.1</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter-api</artifactId>
                            <version>5.13.3</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(MavenMarker)
                  .actual()
              )
            ),
            srcTestJava(
              //language=java
              java(
                """
                  import org.junit.jupiter.api.Test;

                  class ExampleClassMavenTest {
                      @Test
                      public void testMethod() {}
                  }
                  """,
                spec -> spec.after(actual ->
                  assertThat(actual)
                    .startsWith(JavaMarker)
                    .actual()
                )
              )
            )
          )
        );
    }

    @Test
    void whenTestNgLooseFilesDoesNotMark() {
        rewriteRun(
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
                  testImplementation 'org.junit.jupiter:junit-jupiter-api:5.13.3'
                  testImplementation 'org.testng:testng:7.8.0'
              }
              """
          ),
          //language=xml
          pomXml(
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>project-maven</artifactId>
                  <version>0.0.1</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.junit.jupiter</groupId>
                          <artifactId>junit-jupiter-api</artifactId>
                          <version>5.13.3</version>
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
          //language=java
          java(
            """
              import org.testng.annotations.Test;

              class ExampleClassTest {
                  @Test
                  public void testMethod() {}
              }
              """
          )
        );
    }

    @Test
    void whenNoTestNgLooseFilesMarks() {
        rewriteRun(
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
                  testImplementation 'org.junit.jupiter:junit-jupiter-api:5.13.3'
              }
              """,
            spec -> spec.after(actual ->
              assertThat(actual)
                .startsWith(GradleMarker)
                .actual()
            )
          ),
          //language=xml
          pomXml(
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>project-maven</artifactId>
                  <version>0.0.1</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.junit.jupiter</groupId>
                          <artifactId>junit-jupiter-api</artifactId>
                          <version>5.13.3</version>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(actual ->
              assertThat(actual)
                .startsWith(MavenMarker)
                .actual()
            )
          ),
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;

              class ExampleClassTest {
                  @Test
                  public void testMethod() {}
              }
              """,
            spec -> spec.after(actual ->
              assertThat(actual)
                .startsWith(JavaMarker)
                .actual()
            )
          )
        );
    }
}
