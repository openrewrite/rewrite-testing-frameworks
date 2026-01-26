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
package org.openrewrite.java.testing.jmockit;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcTestJava;
import static org.openrewrite.java.testing.jmockit.JMockitTestUtils.setDefaultParserSettings;
import static org.openrewrite.maven.Assertions.pomXml;

class JMockitToMockitoDependencyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        setDefaultParserSettings(spec);
    }

    @Test
    void mockitoMavenDependencyAddedWithTestScope() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(2),
          mavenProject("project",
            srcTestJava(
              //language=java
              java(
                """
                  import mockit.Mocked;
                  import mockit.integration.junit5.JMockitExtension;
                  import org.junit.jupiter.api.extension.ExtendWith;

                  @ExtendWith(JMockitExtension.class)
                  class MyTest {
                      @Mocked
                      Object myObject;

                      void test() {
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.extension.ExtendWith;
                  import org.mockito.Mock;
                  import org.mockito.junit.jupiter.MockitoExtension;

                  @ExtendWith(MockitoExtension.class)
                  class MyTest {
                      @Mock
                      Object myObject;

                      void test() {
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
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.jmockit</groupId>
                            <artifactId>jmockit</artifactId>
                            <version>1.49</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
              sourceSpecs -> sourceSpecs.after(after -> """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.mockito</groupId>
                            <artifactId>mockito-core</artifactId>
                            <version>%s</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """.formatted(Pattern.compile("<version>(5\\..*)</version>").matcher(requireNonNull(after)).results().findFirst().orElseThrow().group(1)))
            )
          )
        );
    }

    @Test
    void mockitoGradleDependencyAddedWithTestScope() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(2).beforeRecipe(withToolingApi()),
          mavenProject("project",
            srcTestJava(
              //language=java
              java(
                """
                  import mockit.Mocked;
                  import mockit.integration.junit5.JMockitExtension;
                  import org.junit.jupiter.api.extension.ExtendWith;

                  @ExtendWith(JMockitExtension.class)
                  class MyTest {
                      @Mocked
                      Object myObject;

                      void test() {
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.extension.ExtendWith;
                  import org.mockito.Mock;
                  import org.mockito.junit.jupiter.MockitoExtension;

                  @ExtendWith(MockitoExtension.class)
                  class MyTest {
                      @Mock
                      Object myObject;

                      void test() {
                      }
                  }
                  """
              )
            ),
            //language=groovy
            buildGradle(
              """
                plugins {
                    id "java-library"
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    testImplementation "org.jmockit:jmockit:1.49"
                }
                """,
              sourceSpecs -> sourceSpecs.after(after -> """
                plugins {
                    id "java-library"
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    testImplementation "org.mockito:%s"
                }
                """.formatted(Pattern.compile("(mockito-core:[^\"]*)").matcher(requireNonNull(after)).results().findFirst().orElseThrow().group(1))
              )
            )
          )
        );
    }
}
