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
package org.openrewrite.java.testing.gatling;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;

class RemoveGatlingGradleLogOptionsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveGatlingGradleLogOptions());
    }

    @DocumentExample
    @Test
    void removeLogOptions() {
        rewriteRun(
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java'
                  id 'io.gatling.gradle' version '3.10.5.1'
              }

              gatling {
                  // WARNING: options below only work when logback config file isn't provided
                  logLevel = 'WARN' // logback root level
                  logHttp = 'NONE' // set to 'ALL' for all HTTP traffic
                  enterprise {
                      // Enterprise Cloud configuration
                  }
              }
              """,
            """
              plugins {
                  id 'java'
                  id 'io.gatling.gradle' version '3.10.5.1'
              }

              gatling {
                  enterprise {
                      // Enterprise Cloud configuration
                  }
              }
              """
          )
        );
    }

    @Test
    void removeCommandSyntax() {
        rewriteRun(
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java'
                  id 'io.gatling.gradle' version '3.10.5.1'
              }

              gatling {
                  includeMainOutput = false
                  logLevel 'WARN' // root level
              }
              """,
            """
              plugins {
                  id 'java'
                  id 'io.gatling.gradle' version '3.10.5.1'
              }

              gatling {
                  includeMainOutput = false
              }
              """
          )
        );
    }

    @Test
    void leaveOtherBlocksAlone() {
        rewriteRun(
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java'
              }

              ext {
                  logLevel = 'WARN'
              }
              """
          )
        );
    }
}
