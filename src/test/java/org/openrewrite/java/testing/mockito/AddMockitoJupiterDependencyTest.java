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
package org.openrewrite.java.testing.mockito;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class AddMockitoJupiterDependencyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .beforeRecipe(withToolingApi())
          .recipe(new AddMockitoJupiterDependency())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api", "mockito-core", "mockito-junit-jupiter"));
    }

    @DocumentExample
    @Test
    void addsMockitoJupiterDependencyWhenMockUsedInJUnit5() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.mockito.Mock;

              class MyTest {
                  @Mock
                  Object myMock;

                  @Test
                  void someTest() {
                  }
              }
              """
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
                  testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
                  testImplementation("org.mockito:mockito-core:3.12.4")
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
                  testImplementation "org.mockito:mockito-junit-jupiter:3.12.4"
              }
              test {
                  useJUnitPlatform()
              }
              """
          )
        );
    }

    @Test
    void doesNotAddWhenMockitoExtensionAlreadyPresent() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myMock;

                  @Test
                  void someTest() {
                  }
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
                        <artifactId>mockito-core</artifactId>
                        <version>3.12.4</version>
                    </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }
}
