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
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.kotlin.Assertions.kotlin;

class MigrateSystemRulesKotlinTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.testing.junit5.JUnit4to5Migration")
          .parser(KotlinParser.builder().classpathFromResources(new InMemoryExecutionContext(),
            "junit-4", "system-rules-1"));
    }

    @Test
    void environmentVariables() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Rule
              import org.junit.Test
              import org.junit.contrib.java.lang.system.EnvironmentVariables

              class EnvironmentTest {
                  @Rule
                  @JvmField
                  val environment = EnvironmentVariables()

                  @Test
                  fun test() {
                      environment.set("KEY", "value")
                      environment.clear("KEY")
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test
              import org.junit.jupiter.api.extension.ExtendWith
              import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
              import uk.org.webcompere.systemstubs.jupiter.SystemStub
              import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension

              @ExtendWith(SystemStubsExtension::class)
              class EnvironmentTest {
                  @SystemStub
                  @JvmField
                  val environment = EnvironmentVariables()

                  @Test
                  fun test() {
                      environment.set("KEY", "value")
                      environment.remove("KEY")
                  }
              }
              """
          )
        );
    }

    @Test
    void systemProperties() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Rule
              import org.junit.Test
              import org.junit.Assert.assertEquals
              import org.junit.contrib.java.lang.system.ProvideSystemProperty

              class PropertiesTest {
                  @Rule
                  @JvmField
                  val property = ProvideSystemProperty("parity.property", "value")

                  @Test
                  fun test() {
                      assertEquals("value", System.getProperty("parity.property"))
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertEquals
              import org.junit.jupiter.api.Test
              import org.junitpioneer.jupiter.SetSystemProperty

              @SetSystemProperty(key = "parity.property", value = "value")
              class PropertiesTest {

                  @Test
                  fun test() {
                      assertEquals("value", System.getProperty("parity.property"))
                  }
              }
              """
          )
        );
    }

    @Test
    void systemOutput() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Rule
              import org.junit.Test
              import org.junit.Assert.assertEquals
              import org.junit.contrib.java.lang.system.SystemOutRule

              class OutputTest {
                  @Rule
                  @JvmField
                  val output = SystemOutRule().enableLog().mute()

                  @Test
                  fun test() {
                      print("value")
                      assertEquals("value", output.log)
                      output.clearLog()
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertEquals
              import org.junit.jupiter.api.Test
              import org.junit.jupiter.api.extension.ExtendWith
              import uk.org.webcompere.systemstubs.jupiter.SystemStub
              import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
              import uk.org.webcompere.systemstubs.stream.SystemOut

              @ExtendWith(SystemStubsExtension::class)
              class OutputTest {
                  @SystemStub
                  @JvmField
                  val output = SystemOut()

                  @Test
                  fun test() {
                      print("value")
                      assertEquals("value", output.getText())
                      output.clear()
                  }
              }
              """
          )
        );
    }

    @Test
    void expectedSystemExit() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Rule
              import org.junit.Test
              import org.junit.contrib.java.lang.system.ExpectedSystemExit

              class ExitTest {
                  @Rule
                  @JvmField
                  val exit = ExpectedSystemExit.none()

                  @Test
                  fun test() {
                      exit.expectSystemExitWithStatus(2)
                      System.exit(2)
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertEquals
              import org.junit.jupiter.api.Test
              import uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit

              class ExitTest {

                  @Test
                  fun test() {
                      val status = catchSystemExit {
                          System.exit(2)
                      }
                      assertEquals(2, status)
                  }
              }
              """
          )
        );
    }
}
