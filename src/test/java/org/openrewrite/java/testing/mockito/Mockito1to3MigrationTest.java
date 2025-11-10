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
package org.openrewrite.java.testing.mockito;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class Mockito1to3MigrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .beforeRecipe(withToolingApi())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api", "mockito-all", "mockito-junit-jupiter")
            //language=java
            .dependsOn(
              """
                import java.util.List;

                public class A {
                    public boolean someMethod(Object o, String s, List<String> l) {
                        return true;
                    }
                }
                """
            )
          )
          .recipeFromResources("org.openrewrite.java.testing.mockito.Mockito1to3Migration");
    }

    @DocumentExample
    @Test
    void migrateSomeMethodsAndDependencies() {
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
                  testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
                  testImplementation("org.mockito:mockito-all:1.10.19")
                  testImplementation("org.mockito:mockito-junit-jupiter:2.28.2")
              }
              test {
                  useJUnitPlatform()
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
                  testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
                  testImplementation("org.mockito:mockito-core:3.12.4")
                  testImplementation("org.mockito:mockito-junit-jupiter:3.12.4")
              }
              test {
                  useJUnitPlatform()
              }
              """
          ),
          //language=xml
          pomXml(
            """
              <project>
                <groupId>org.example</groupId>
                <artifactId>some-project</artifactId>
                <version>1.0-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>org.junit.jupiter</groupId>
                        <artifactId>junit-jupiter-api</artifactId>
                        <version>5.11.4</version>
                    </dependency>
                    <dependency>
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-all</artifactId>
                        <version>1.10.19</version>
                    </dependency>
                    <dependency>
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-junit-jupiter</artifactId>
                        <version>2.28.2</version>
                    </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <groupId>org.example</groupId>
                <artifactId>some-project</artifactId>
                <version>1.0-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>org.junit.jupiter</groupId>
                        <artifactId>junit-jupiter-api</artifactId>
                        <version>5.11.4</version>
                    </dependency>
                    <dependency>
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-core</artifactId>
                        <version>3.12.4</version>
                    </dependency>
                    <dependency>
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-junit-jupiter</artifactId>
                        <version>3.12.4</version>
                    </dependency>
                </dependencies>
              </project>
              """
          ),
          //language=java
          java(
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;
              import org.mockito.Mock;

              import static org.mockito.Matchers.anyListOf;
              import static org.mockito.Matchers.anyObject;
              import static org.mockito.Matchers.anyString;
              import static org.mockito.Mockito.when;

              class MyTest {
                  @Mock
                  Object objectMock;

                  private A subject;

                  @BeforeEach
                  void setup() {
                      subject = new A();
                  }

                  @Test
                  void someTest() {
                      when(subject.someMethod(anyObject(), anyString(), anyListOf(String.class))).thenReturn(false);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;
              import org.mockito.Mock;

              import static org.mockito.ArgumentMatchers.anyList;
              import static org.mockito.ArgumentMatchers.any;
              import static org.mockito.ArgumentMatchers.anyString;
              import static org.mockito.Mockito.when;

              class MyTest {
                  @Mock
                  Object objectMock;

                  private A subject;

                  @BeforeEach
                  void setup() {
                      subject = new A();
                  }

                  @Test
                  void someTest() {
                      when(subject.someMethod(any(), anyString(), anyList())).thenReturn(false);
                  }
              }
              """
          )
        );
    }

    @Test
    void migratesCorrectlyWhenManagedDependency() {
        rewriteRun(
          pomXml(
            //language=xml
            """
              <project>
                <groupId>org.example</groupId>
                <artifactId>some-project-parent</artifactId>
                <version>1.0-SNAPSHOT</version>
                <properties>
                    <mockito.version>1.10.19</mockito.version>
                    <mockito-jupiter.version>2.28.2</mockito-jupiter.version>
                </properties>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter-api</artifactId>
                            <version>5.11.4</version>
                        </dependency>
                        <dependency>
                            <groupId>org.mockito</groupId>
                            <artifactId>mockito-all</artifactId>
                            <version>${mockito.version}</version>
                        </dependency>
                        <dependency>
                            <groupId>org.mockito</groupId>
                            <artifactId>mockito-junit-jupiter</artifactId>
                            <version>${mockito-jupiter.version}</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
              </project>
              """,
            spec -> spec
              .path("pom.xml")
              .after(pom -> assertThat(pom)
                .contains("<artifactId>mockito-core</artifactId>")
                .doesNotContain("<artifactId>mockito-all</artifactId>")
                .containsPattern("<mockito.version>3.\\d+.\\d+</mockito.version>")
                .containsPattern("<mockito-jupiter.version>3.\\d+.\\d+</mockito-jupiter.version>")
                .actual())
          ),
          pomXml(
            //language=xml
            """
              <project>
                <groupId>org.example</groupId>
                <artifactId>some-project</artifactId>
                <version>1.0-SNAPSHOT</version>
                <parent>
                    <groupId>org.example</groupId>
                    <artifactId>some-project-parent</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <relativePath>../pom.xml</relativePath>
                </parent>
                <dependencies>
                    <dependency>
                        <groupId>org.junit.jupiter</groupId>
                        <artifactId>junit-jupiter-api</artifactId>
                    </dependency>
                    <dependency>
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-all</artifactId>
                    </dependency>
                    <dependency>
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-junit-jupiter</artifactId>
                    </dependency>
                </dependencies>
              </project>
              """,
            spec -> spec
              .path("child/pom.xml")
              .after(pom -> assertThat(pom)
                .contains("<artifactId>mockito-core</artifactId>")
                .doesNotContain("<artifactId>mockito-all</artifactId>")
                .actual())
          )
        );
    }

    @Test
    void handlesAnyObjectFromMockitoWildCardImport() {
        rewriteRun(
          java(
            """
            package com.yourorg;
            import static org.mockito.Mockito.*;

            public class MyTest {
                void test() {
                    Object o = anyObject();
                }
            }
            """,
            """
            package com.yourorg;
            import static org.mockito.Mockito.any;

            public class MyTest {
                void test() {
                    Object o = any();
                }
            }
            """
          )
        );
    }

    @Test
    void handlesAnyObjectFromMockitoWildCardImport_keepsWildCardIfManyUsages() {
        rewriteRun(
          java(
            """
            package com.yourorg;
            import java.util.List;
            import static org.mockito.Mockito.*;

            public class MyTest {
                void test() {
                    Object o1 = anyObject();
                    Object o2 = doNothing().when(new Object());
                    verify(o2, never());
                    mock(List.class);
                    spy(new Object());
                }
            }
            """,
            """
            package com.yourorg;
            import java.util.List;
            import static org.mockito.Mockito.*;

            public class MyTest {
                void test() {
                    Object o1 = any();
                    Object o2 = doNothing().when(new Object());
                    verify(o2, never());
                    mock(List.class);
                    spy(new Object());
                }
            }
            """
          )
        );
    }

}
