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
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;

class Mockito1to4MigrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .beforeRecipe(withToolingApi())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api", "mockito-core", "mockito-junit-jupiter"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing")
            .build()
            .activateRecipes("org.openrewrite.java.testing.mockito.Mockito1to4Migration"));
    }

    @Test
    void modifyMockitoDependencies() {
        //language=java
        rewriteRun(
          buildGradle(
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
                   testImplementation("org.mockito:mockito-core:4.11.0")
                   testImplementation("org.mockito:mockito-junit-jupiter:4.11.0")
               }
               test {
                  useJUnitPlatform()
               }
               """
          ),
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mockito;
              import org.mockito.junit.jupiter.MockitoExtension;
              import java.util.List;

              @ExtendWith(MockitoExtension.class)
              public class MyTest {
                  @Test
                  public void test() {
                      List<String> list = Mockito.mock(List.class);
                  }
              }
              """
          )
        );
    }
}
