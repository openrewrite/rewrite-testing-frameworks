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
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.kotlin.Assertions.kotlin;

class JUnit4to5KotlinExtensionsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.testing.junit5.JUnit4to5Migration")
          .parser(KotlinParser.builder().classpathFromResources(new InMemoryExecutionContext(),
            "junit-4", "hamcrest-3", "wiremock-jre8-2.35", "mockwebserver-3.14", "JUnitParams-1.1", "assertj-core-3",
            "mockito-core-3.12"));
    }

    @Test
    void mockitoRule() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Rule
              import org.junit.Test
              import org.mockito.junit.MockitoJUnit

              class MockitoTest {
                  @Rule
                  @JvmField
                  val rule = MockitoJUnit.rule()

                  @Test
                  fun test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test
              import org.junit.jupiter.api.extension.ExtendWith
              import org.mockito.junit.jupiter.MockitoExtension
              import org.mockito.junit.jupiter.MockitoSettings
              import org.mockito.quality.Strictness

              @ExtendWith(MockitoExtension::class)
              @MockitoSettings(strictness = Strictness.WARN)
              class MockitoTest {

                  @Test
                  fun test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void softAssertionsRule() {
        rewriteRun(
          kotlin(
            """
              import org.assertj.core.api.JUnitSoftAssertions
              import org.junit.Rule
              import org.junit.Test

              class SoftAssertionsTest {
                  @Rule
                  @JvmField
                  val softly = JUnitSoftAssertions()

                  @Test
                  fun test() {
                      softly.assertThat(1).isEqualTo(1)
                  }
              }
              """,
            """
              import org.assertj.core.api.SoftAssertions
              import org.assertj.core.api.junit.jupiter.InjectSoftAssertions
              import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension
              import org.junit.jupiter.api.Test
              import org.junit.jupiter.api.extension.ExtendWith

              @ExtendWith(SoftAssertionsExtension::class)
              class SoftAssertionsTest {
                  @InjectSoftAssertions
                  lateinit var softly: SoftAssertions

                  @Test
                  fun test() {
                      softly.assertThat(1).isEqualTo(1)
                  }
              }
              """
          )
        );
    }

    @Test
    void wireMockClassRuleLifecycle() {
        rewriteRun(
          kotlin(
            """
              import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
              import com.github.tomakehurst.wiremock.junit.WireMockRule
              import org.junit.ClassRule
              import org.junit.Test
              import org.junit.Assert.assertTrue

              class WireMockClassTest {
                  companion object {
                      @ClassRule
                      @JvmField
                      val server = WireMockRule(options().bindAddress("127.0.0.1").dynamicPort())
                  }

                  @Test
                  fun test() {
                      assertTrue(server.isRunning())
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.WireMockServer
              import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
              import org.junit.jupiter.api.AfterAll
              import org.junit.jupiter.api.Assertions.assertTrue
              import org.junit.jupiter.api.BeforeAll
              import org.junit.jupiter.api.Test

              class WireMockClassTest {
                  companion object {
                      @JvmField
                      val server = WireMockServer(options().bindAddress("127.0.0.1").dynamicPort())
                      @BeforeAll
                      @JvmStatic
                      fun startWireMock() {
                          server.start()
                      }
                      @AfterAll
                      @JvmStatic
                      fun stopWireMock() {
                          server.stop()
                      }
                  }

                  @Test
                  fun test() {
                      assertTrue(server.isRunning())
                  }
              }
              """
          )
        );
    }

    @Test
    void junitParamsNamedAndConventionalProviders() {
        rewriteRun(
          kotlin(
            """
              import junitparams.JUnitParamsRunner
              import junitparams.NamedParameters
              import junitparams.Parameters
              import org.junit.Assert.assertEquals
              import org.junit.Test
              import org.junit.runner.RunWith

              @RunWith(JUnitParamsRunner::class)
              class ProvidersTest {
                  @Test
                  @Parameters
                  fun doubled(value: Int, expected: Int) {
                      assertEquals(expected, value * 2)
                  }

                  @Test
                  @Parameters(named = "rows")
                  fun named(value: Int, expected: Int) {
                      assertEquals(expected, value * 2)
                  }

                  fun parametersForDoubled(): List<Array<Int>> = values()

                  @NamedParameters("rows")
                  fun values(): List<Array<Int>> = listOf(arrayOf(1, 2), arrayOf(2, 4))
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertEquals
              import org.junit.jupiter.params.ParameterizedTest
              import org.junit.jupiter.params.provider.MethodSource

              class ProvidersTest {

                  @ParameterizedTest
                  @MethodSource("parametersForDoubled")
                  fun doubled(value: Int, expected: Int) {
                      assertEquals(expected, value * 2)
                  }

                  @ParameterizedTest
                  @MethodSource("values")
                  fun named(value: Int, expected: Int) {
                      assertEquals(expected, value * 2)
                  }

                  companion object {
                      @JvmStatic
                      fun parametersForDoubled(): List<Array<Int>> = values()

                      @JvmStatic
                      fun values(): List<Array<Int>> = listOf(arrayOf(1, 2), arrayOf(2, 4))
                  }
              }
              """
          )
        );
    }

    @Test
    void junitParamsCsv() {
        rewriteRun(
          kotlin(
            """
              import junitparams.JUnitParamsRunner
              import junitparams.Parameters
              import org.junit.Assert.assertEquals
              import org.junit.Test
              import org.junit.runner.RunWith

              @RunWith(JUnitParamsRunner::class)
              class CsvTest {
                  @Test
                  @Parameters("1,2", "2,4")
                  fun test(value: Int, expected: Int) {
                      assertEquals(expected, value * 2)
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertEquals
              import org.junit.jupiter.params.ParameterizedTest
              import org.junit.jupiter.params.provider.CsvSource

              class CsvTest {

                  @ParameterizedTest
                  @CsvSource("1,2", "2,4")
                  fun test(value: Int, expected: Int) {
                      assertEquals(expected, value * 2)
                  }
              }
              """
          )
        );
    }

    @Test
    void junitParamsCsvArrayOf() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.all().methodInvocations(false))
            .afterTypeValidationOptions(TypeValidation.all()),
          kotlin(
            """
              import junitparams.JUnitParamsRunner
              import junitparams.Parameters
              import org.junit.Assert.assertEquals
              import org.junit.Test
              import org.junit.runner.RunWith

              @RunWith(JUnitParamsRunner::class)
              class ArrayCsv {
                  @Test
                  @Parameters(value = arrayOf("1,2", "3,4"))
                  fun csv(value: Int, next: Int) {
                      assertEquals(value + 1, next)
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertEquals
              import org.junit.jupiter.params.ParameterizedTest
              import org.junit.jupiter.params.provider.CsvSource

              class ArrayCsv {

                  @ParameterizedTest
                  @CsvSource(value = ["1,2", "3,4"])
                  fun csv(value: Int, next: Int) {
                      assertEquals(value + 1, next)
                  }
              }
              """
          )
        );
    }

    @Test
    void junitParamsMethodSource() {
        rewriteRun(
          kotlin(
            """
              import junitparams.JUnitParamsRunner
              import junitparams.Parameters
              import org.junit.Assert.assertEquals
              import org.junit.Test
              import org.junit.runner.RunWith

              @RunWith(JUnitParamsRunner::class)
              class ProviderTest {
                  @Test
                  @Parameters(method = "values")
                  fun test(value: Int, expected: Int) {
                      assertEquals(expected, value * 2)
                  }

                  fun values(): List<Array<Int>> = listOf(arrayOf(1, 2), arrayOf(2, 4))
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertEquals
              import org.junit.jupiter.params.ParameterizedTest
              import org.junit.jupiter.params.provider.MethodSource

              class ProviderTest {

                  @ParameterizedTest
                  @MethodSource("values")
                  fun test(value: Int, expected: Int) {
                      assertEquals(expected, value * 2)
                  }

                  companion object {
                      @JvmStatic
                      fun values(): List<Array<Int>> = listOf(arrayOf(1, 2), arrayOf(2, 4))
                  }
              }
              """
          )
        );
    }

    @Test
    void mockWebServerRule() {
        rewriteRun(
          kotlin(
            """
              import okhttp3.mockwebserver.MockWebServer
              import org.junit.Rule
              import org.junit.Test

              class ServerTest {
                  @Rule
                  @JvmField
                  val server = MockWebServer()

                  @Test
                  fun test() {
                  }
              }
              """,
            """
              import mockwebserver3.MockWebServer
              import org.junit.jupiter.api.AfterEach
              import org.junit.jupiter.api.Test

              class ServerTest {
                  @JvmField
                  val server = MockWebServer()

                  @Test
                  fun test() {
                  }
                  @AfterEach
                  fun afterEachTest() {
                      server.close()
                  }
              }
              """
          )
        );
    }

    @Test
    void externalResourceRule() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Rule
              import org.junit.Test
              import org.junit.rules.ExternalResource

              class ResourceTest {
                  @Rule
                  @JvmField
                  val resource = object : ExternalResource() {
                      override fun before() {}
                      override fun after() {}
                  }

                  @Test
                  fun test() {
                  }
              }
              """,
            """
              import org.junit.Rule
              import org.junit.jupiter.api.Test
              import org.junit.jupiter.api.extension.ExtendWith
              import org.junit.jupiter.migrationsupport.rules.ExternalResourceSupport
              import org.junit.rules.ExternalResource

              @ExtendWith(ExternalResourceSupport::class)
              class ResourceTest {
                  @Rule
                  @JvmField
                  val resource = object : ExternalResource() {
                      override fun before() {}
                      override fun after() {}
                  }

                  @Test
                  fun test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void wireMockExtension() {
        rewriteRun(
          kotlin(
            """
              import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
              import com.github.tomakehurst.wiremock.junit.WireMockRule
              import org.junit.Rule
              import org.junit.Test
              import org.junit.Assert.assertTrue

              class WireMockTest {
                  @Rule
                  @JvmField
                  val server = WireMockRule(options().bindAddress("127.0.0.1").dynamicPort())

                  @Test
                  fun test() {
                      assertTrue(server.port() > 0)
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
              import com.github.tomakehurst.wiremock.junit5.WireMockExtension
              import org.junit.jupiter.api.Assertions.assertTrue
              import org.junit.jupiter.api.Test
              import org.junit.jupiter.api.extension.RegisterExtension

              class WireMockTest {
                  @RegisterExtension
                  @JvmField
                  val server = WireMockExtension.newInstance().options(options().bindAddress("127.0.0.1").dynamicPort()).build()

                  @Test
                  fun test() {
                      assertTrue(server.getPort() > 0)
                  }
              }
              """
          )
        );
    }

    @Test
    void wireMockServerLifecycle() {
        rewriteRun(
          kotlin(
            """
              import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
              import com.github.tomakehurst.wiremock.junit.WireMockRule
              import org.junit.Rule
              import org.junit.Test
              import org.junit.Assert.assertTrue

              class WireMockTest {
                  @Rule
                  @JvmField
                  val server = WireMockRule(options().bindAddress("127.0.0.1").dynamicPort())

                  @Test
                  fun test() {
                      assertTrue(server.isRunning())
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.WireMockServer
              import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
              import org.junit.jupiter.api.AfterEach
              import org.junit.jupiter.api.Assertions.assertTrue
              import org.junit.jupiter.api.BeforeEach
              import org.junit.jupiter.api.Test

              class WireMockTest {
                  @JvmField
                  val server = WireMockServer(options().bindAddress("127.0.0.1").dynamicPort())
                  @BeforeEach
                  fun startWireMock() {
                      server.start()
                  }
                  @AfterEach
                  fun stopWireMock() {
                      server.stop()
                  }

                  @Test
                  fun test() {
                      assertTrue(server.isRunning())
                  }
              }
              """
          )
        );
    }
}
