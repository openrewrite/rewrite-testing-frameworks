/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;

public class GradleUseJunitJupiterTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new GradleUseJunitJupiter())
          .beforeRecipe(withToolingApi());
    }

    @Test
    void addWhenMissing() {
        rewriteRun(
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java-library'
              }
              tasks.named('classes') { }
              tasks.withType(JavaCompile) { }
              tasks.withType(JavaCompile).configureEach { }
              """,
            """
              plugins {
                  id 'java-library'
              }
              tasks.named('classes') { }
              tasks.withType(JavaCompile) { }
              tasks.withType(JavaCompile).configureEach { }
              tasks.withType(Test).configureEach {
                  useJUnitPlatform()
              }
              """
          )
        );
    }

    @Test
    void testDsl() {
        rewriteRun(
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java'
              }
              test {
              }
              """,
            """
              plugins {
                  id 'java'
              }
              test {
                  useJUnitPlatform()
              }
              """
          )
        );
    }

    @Test
    void testDslAlreadyExists() {
        rewriteRun(
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java'
              }
              test {
                  useJUnit()
              }
              """,
            """
              plugins {
                  id 'java'
              }
              test {
                  useJUnitPlatform()
              }
              """
          )
        );
    }

    @Test
    void tasksWithTypeTest() {
        rewriteRun(
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java'
              }
              tasks.withType(Test) {
              }
              """,
            """
              plugins {
                  id 'java'
              }
              tasks.withType(Test) {
                  useJUnitPlatform()
              }
              """
          )
        );
    }

    @Test
    void tasksWithTypeTestConfigureEach() {
        rewriteRun(
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java'
              }
              tasks.withType(Test).configureEach {
              }
              """,
            """
              plugins {
                  id 'java'
              }
              tasks.withType(Test).configureEach {
                  useJUnitPlatform()
              }
              """
          )
        );
    }

    @Test
    void tasksNamedTest() {
        rewriteRun(
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java'
              }
              tasks.named('test', Test) {
              }
              """,
            """
              plugins {
                  id 'java'
              }
              tasks.named('test', Test) {
                  useJUnitPlatform()
              }
              """
          )
        );
    }

    @Test
    void tasksNamedTestNoType() {
        rewriteRun(
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java'
              }
              tasks.named('test') {
              }
              """,
            """
              plugins {
                  id 'java'
              }
              tasks.named('test') {
                  useJUnitPlatform()
              }
              """
          )
        );
    }

    @Test
    void leaveOtherTestDslAlone() {
        rewriteRun(
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java'
              }
              sourceSets {
                  test {
                      java {
                          srcDir 'src/test/java'
                      }
                  }
              }
              """,
            """
              plugins {
                  id 'java'
              }
              sourceSets {
                  test {
                      java {
                          srcDir 'src/test/java'
                      }
                  }
              }
              tasks.withType(Test).configureEach {
                  useJUnitPlatform()
              }
              """
          )
        );
    }
}
