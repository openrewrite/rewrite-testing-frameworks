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
package org.openrewrite.java.testing.mockito;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

class Mockito1to5MigrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .beforeRecipe(withToolingApi())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-jupiter-api",
              "mockito-core-3",
              "mockito-junit-jupiter"))
          .recipeFromResources("org.openrewrite.java.testing.mockito.Mockito1to5Migration");
    }

    @DocumentExample
    @Test
    void modifyMockitoDependencies() {
        rewriteRun(
          buildGradle(
            //language=groovy
            """
              plugins {
                  id 'java-library'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  implementation("org.apache.commons:commons-lang3:3.17.0")
                  testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
                  testImplementation("org.mockito:mockito-core:3.12.4")
                  testImplementation("org.mockito:mockito-junit-jupiter:3.12.4")
              }
              test {
                 useJUnitPlatform()
              }
              """,
            spec -> spec.after(buildGradleAfter -> assertThat(buildGradleAfter)
              .containsPattern("implementation\\(\"org.apache.commons:commons-lang3:3.17.0\"")
              .containsPattern("testImplementation\\(\"org.junit.jupiter:junit-jupiter-api:5.11.4\"")
              .containsPattern("testImplementation\\(\"org\\.mockito:[\\D]+:(?<version>5\\.\\d+\\.\\d+)\"")
              .actual())),
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mockito;
              import org.mockito.junit.jupiter.MockitoExtension;
              import java.util.List;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Test
                  void test() {
                      List<String> list = Mockito.mock(List.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void upgradeMockitoAllVersionPropertyInParent() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <groupId>org.example</groupId>
                <artifactId>parent</artifactId>
                <version>1.0</version>
                <properties>
                  <version.mockito>1.9.5</version.mockito>
                </properties>
              </project>
              """,
            spec -> spec.after(after -> """
              <project>
                <groupId>org.example</groupId>
                <artifactId>parent</artifactId>
                <version>1.0</version>
                <properties>
                  <version.mockito>%s</version.mockito>
                </properties>
              </project>
              """.formatted(mockitoVersion(after)))
          ),
          mavenProject("child",
            //language=xml
            pomXml(
              """
                <project>
                  <parent>
                    <groupId>org.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0</version>
                  </parent>
                  <artifactId>child</artifactId>
                  <dependencies>
                    <dependency>
                      <groupId>org.mockito</groupId>
                      <artifactId>mockito-all</artifactId>
                      <version>${version.mockito}</version>
                    </dependency>
                  </dependencies>
                </project>
                """,
              """
                <project>
                  <parent>
                    <groupId>org.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0</version>
                  </parent>
                  <artifactId>child</artifactId>
                  <dependencies>
                    <dependency>
                      <groupId>org.mockito</groupId>
                      <artifactId>mockito-core</artifactId>
                      <version>${version.mockito}</version>
                    </dependency>
                  </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void upgradeMockitoAllVersionPropertyInSamePom() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <groupId>org.example</groupId>
                <artifactId>example</artifactId>
                <version>1.0</version>
                <properties>
                  <version.mockito>1.9.5</version.mockito>
                </properties>
                <dependencies>
                  <dependency>
                    <groupId>org.mockito</groupId>
                    <artifactId>mockito-all</artifactId>
                    <version>${version.mockito}</version>
                  </dependency>
                </dependencies>
              </project>
              """,
            spec -> spec.after(after -> """
              <project>
                <groupId>org.example</groupId>
                <artifactId>example</artifactId>
                <version>1.0</version>
                <properties>
                  <version.mockito>%s</version.mockito>
                </properties>
                <dependencies>
                  <dependency>
                    <groupId>org.mockito</groupId>
                    <artifactId>mockito-core</artifactId>
                    <version>${version.mockito}</version>
                  </dependency>
                </dependencies>
              </project>
              """.formatted(mockitoVersion(after)))
          )
        );
    }

    private static String mockitoVersion(String pom) {
        return Pattern.compile("<version.mockito>(5\\.[^<]+)</version.mockito>").matcher(pom).results().findFirst().orElseThrow().group(1);
    }
}
