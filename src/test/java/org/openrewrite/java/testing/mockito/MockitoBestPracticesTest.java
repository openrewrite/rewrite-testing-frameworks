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

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;

public class MockitoBestPracticesTest implements RewriteTest {

        @Override
        public void defaults(RecipeSpec spec) {
            spec
              .beforeRecipe(withToolingApi())
              .parser(JavaParser.fromJavaVersion()
                .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api", "mockito-core", "mockito-junit-jupiter"))
              .recipeFromResources("org.openrewrite.java.testing.mockito.Mockito1to5Migration");
        }

    @DocumentExample
    @Test
    void mockito5() {
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
                  testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
                  testImplementation("org.mockito:mockito-core:3.12.4")
                  testImplementation("org.mockito:mockito-junit-jupiter:3.12.4")
              }
              test {
                 useJUnitPlatform()
              }
              """,
            spec -> spec.after(buildGradleAfter -> assertThat(buildGradleAfter)
              .containsPattern("testImplementation\\(\"org\\.mockito:[\\D]+:(?<version>5\\.\\d+\\.\\d+)\"")
              .actual())),
          //language=java
          java(
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.Mockito;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;
              import java.sql.Connection;
              import java.util.List;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Connection conn;

                  AutoCloseable mocks;

                  @BeforeEach
                  void beforeEach() {
                    mocks = MockitoAnnotations.openMocks(this);
                  }

                  @Test
                  void test() {
                      List<String> list = Mockito.mock(List.class);
                  }

                  @AfterEach
                  void afterEach() {
                      if (mocks != null) {
                          mocks.close();
                      }
                  }

                  @Nested
                  class NestedClass {
                      @Test
                      void test() {
                          List<String> list = Mockito.mock(List.class);
                      }

                      @AfterEach
                      void afterEach() {
                          if (mocks != null) {
                              mocks.close();
                          }
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.Mockito;
              import org.mockito.junit.jupiter.MockitoExtension;
              import java.sql.Connection;
              import java.util.List;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Connection conn;

                  @Test
                  void test() {
                      List<String> list = Mockito.mock(List.class);
                  }

                  @Nested
                  class NestedClass {
                      @Test
                      void test() {
                          List<String> list = Mockito.mock(List.class);
                      }
                  }
              }
              """
          )
        );
    }
}
