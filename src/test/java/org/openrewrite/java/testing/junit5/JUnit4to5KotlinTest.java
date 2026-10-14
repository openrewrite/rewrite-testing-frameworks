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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.testing.junit5.KotlinJUnit5ExecutionAssertions.kotlin;

@Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/1188")
@Tag("kotlin-execution")
class JUnit4to5KotlinTest implements RewriteTest {
    @Test
    void unrelatedKotlinImportsAreUnchanged() {
        rewriteRun(
          org.openrewrite.kotlin.Assertions.kotlin(
            """
              import java.util.List
              import java.io.File

              class Util
              """
          )
        );
    }

    @Test
    void existingJupiterImportsAndAssertionsAreUnchanged() {
        rewriteRun(
          spec -> spec.parser(KotlinParser.builder().classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5")),
          org.openrewrite.kotlin.Assertions.kotlin(
            """
              import org.junit.jupiter.api.*
              import org.junit.jupiter.api.Assertions.assertEquals

              class AlreadyJupiter {
                  @Test fun test() { assertEquals(1, 1) }
              }
              """
          )
        );
    }

    @Test
    void plainConstructorParametersDelegatesAndLateinitFields() {
        rewriteRun(
          kotlin(
            """
              package parity.plainparameters
              import org.junit.Assert.assertEquals
              import org.junit.Before
              import org.junit.Test
              import org.junit.runner.RunWith
              import org.junit.runners.Parameterized

              @RunWith(Parameterized::class)
              class PlainParameters(input: Int, private var text: String) {
                  private val input = "$input"
                  private val doubled = input * 2
                  private val mapped = listOf(input).map { input -> input + 1 }
                  private val delayed by lazy { input * 3 }
                  private var observed by kotlin.properties.Delegates.observable(input) { _, _, _ -> }
                  private lateinit var helper: StringBuilder

                  @Before fun setup() { helper = StringBuilder("ready") }
                  @Test fun test() {
                      assertEquals("${doubled / 2}", input)
                      assertEquals(doubled / 2 + 1, mapped.single())
                      assertEquals(doubled * 3 / 2, delayed)
                      assertEquals("ready", helper.toString())
                      assertEquals(doubled / 2, observed)
                      observed++
                      assertEquals(doubled / 2 + 1, observed)
                      text = "changed"
                      assertEquals(7, text.length)
                  }

                  companion object {
                      @JvmStatic @Parameterized.Parameters
                      fun values(): List<Array<Any>> = listOf(arrayOf(1, "x"), arrayOf(2, "xx"))
                  }
              }
              """,
            """
              package parity.plainparameters
              import org.junit.jupiter.api.Assertions.assertEquals
              import org.junit.jupiter.params.ParameterizedTest
              import org.junit.jupiter.params.provider.MethodSource

              class PlainParameters {
                  private var inputArgument: Int = 0
                  private lateinit var text: String

                  fun initPlainParameters(input: Int, text: String) {
                      this.inputArgument = input
                      this.text = text
                      this.input = "$input"
                      this.doubled = input * 2
                      this.mapped = listOf(input).map { input -> input + 1 }
                      this.delayedDelegate = lazy { input * 3 }
                      this.observedDelegate = kotlin.properties.Delegates.observable(input) { _, _, _ -> }
                  }
                  private lateinit var input: kotlin.String
                  private var doubled: kotlin.Int = 0
                  private lateinit var mapped: kotlin.collections.List<kotlin.Int>
                  private lateinit var delayedDelegate: kotlin.Lazy<kotlin.Int>
                  private val delayed: kotlin.Int
                      get() = delayedDelegate.getValue(this, ::delayed)
                  private lateinit var observedDelegate: kotlin.properties.ReadWriteProperty<kotlin.Any?, kotlin.Int>
                  private var observed: kotlin.Int
                      get() = observedDelegate.getValue(this, ::observed)
                      set(value) {
                          observedDelegate.setValue(this, ::observed, value)
                      }
                  private lateinit var helper: StringBuilder

                  fun setup() {
                      helper = StringBuilder("ready")
                  }

                  @MethodSource("values")
                  @ParameterizedTest
                  fun test(input: Int, text: String) {
                      initPlainParameters(input, text)
                      setup()
                      assertEquals("${doubled / 2}", this.input)
                      assertEquals(doubled / 2 + 1, mapped.single())
                      assertEquals(doubled * 3 / 2, delayed)
                      assertEquals("ready", helper.toString())
                      assertEquals(doubled / 2, observed)
                      observed++
                      assertEquals(doubled / 2 + 1, observed)
                      this.text = "changed"
                      assertEquals(7, this.text.length)
                  }

                  companion object {
                      @JvmStatic
                      fun values(): List<Array<Any>> = listOf(arrayOf(1, "x"), arrayOf(2, "xx"))
                  }
              }
              """
          )
        );
    }

    @Test
    void enclosedAbstractBasePrivateCompanionAndExtensionReceiver() {
        rewriteRun(
          kotlin(
            """
              package parity.abstractcompanion
              import org.junit.Assert.assertEquals
              import org.junit.Test
              import org.junit.experimental.runners.Enclosed
              import org.junit.runner.RunWith

              @RunWith(Enclosed::class)
              class AbstractCompanion {
                  abstract class Base { abstract fun required(): String }

                  class NestedTest : Base() {
                      override fun required() = "ready"

                      @Test fun test() {
                          val callable = Companion::helper
                          assertEquals(required(), callable())
                          assertEquals("ready!", "ready".shout())
                      }

                      private companion object {
                          fun helper() = "ready"
                          fun String.shout() = this + "!"
                      }
                  }
              }
              """,
            """
              package parity.abstractcompanion
              import org.junit.jupiter.api.Assertions.assertEquals
              import org.junit.jupiter.api.Nested
              import org.junit.jupiter.api.Test

              class AbstractCompanion {
                  abstract class Base {
                      abstract fun required(): String
                  }

                  @Nested
                  inner class NestedTest : NestedTestFixtures() {
                      override fun required() = "ready"

                      @Test
                      fun test() {
                          val callable = AbstractCompanion.NestedTestFixtures.Companion::helper
                          assertEquals(required(), callable())
                          assertEquals("ready!", with(NestedTestFixtures) { "ready".shout() })
                      }
                  }

                  abstract class NestedTestFixtures : Base() {
                      companion object {
                          fun helper() = "ready"
                          fun String.shout() = this + "!"
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void secondaryConstructorDelegatesToPrivatePrimaryConstructor() {
        rewriteRun(
          kotlin(
            """
              package parity.secondarydelegation
              import org.junit.Assert.assertEquals
              import org.junit.Test
              import org.junit.runner.RunWith
              import org.junit.runners.Parameterized

              @RunWith(Parameterized::class)
              class SecondaryDelegation private constructor(private val value: Int) {
                  private val doubled = value * 2
                  private var expected = 0

                  constructor(input: Int, expected: Int) : this(input) { this.expected = expected }

                  @Test fun test() { assertEquals(expected, doubled) }

                  companion object {
                      @JvmStatic @Parameterized.Parameters
                      fun values(): List<Array<Int>> = listOf(arrayOf(1, 2), arrayOf(2, 4))
                  }
              }
              """,
            """
              package parity.secondarydelegation
              import org.junit.jupiter.api.Assertions.assertEquals
              import org.junit.jupiter.params.ParameterizedTest
              import org.junit.jupiter.params.provider.MethodSource

              class SecondaryDelegation {
                  private var value: Int = 0

                  fun initSecondaryDelegation(input: Int, expected: Int) {
                      this.value = input
                      this.doubled = value * 2
                      this.expected = 0
                      this.expected = expected
                  }
                  private var doubled: kotlin.Int = 0
                  private var expected: kotlin.Int = 0

                  @MethodSource("values")
                  @ParameterizedTest
                  fun test(input: Int, expected: Int) {
                      initSecondaryDelegation(input, expected)
                      assertEquals(this.expected, doubled)
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
    void parameterizedTemporaryFolderAndExpectedException() {
        rewriteRun(
          kotlin(
            """
              package parity.parameterizedrule
              import org.junit.Assert.assertEquals
              import org.junit.Assert.assertTrue
              import org.junit.Rule
              import org.junit.Test
              import org.junit.rules.TemporaryFolder
              import org.junit.runner.RunWith
              import org.junit.runners.Parameterized

              @RunWith(Parameterized::class)
              class ParameterizedRule(private val value: Int) {
                  @JvmField @Rule val folder = TemporaryFolder()

                  @Test(expected = IllegalArgumentException::class)
                  fun test() {
                      assertTrue(folder.newFile("data.txt").exists())
                      assertEquals(true, value > 0)
                      throw IllegalArgumentException()
                  }

                  companion object {
                      @JvmStatic @Parameterized.Parameters
                      fun values(): List<Array<Int>> = listOf(arrayOf(1), arrayOf(2))
                  }
              }
              """,
            """
              package parity.parameterizedrule
              import org.junit.jupiter.api.Assertions.*
              import org.junit.jupiter.api.io.TempDir
              import org.junit.jupiter.params.ParameterizedTest
              import org.junit.jupiter.params.provider.MethodSource

              import java.io.File

              class ParameterizedRule {
                  private var value: Int = 0

                  fun initParameterizedRule(value: Int) {
                      this.value = value
                  }

                  @TempDir
                  lateinit var folder: File

                  @MethodSource("values")
                  @ParameterizedTest
                  fun test(value: Int) {
                      initParameterizedRule(value)
                      assertThrows(IllegalArgumentException::class.java) {
                          assertTrue(newFile(folder, "data.txt").exists())
                          assertEquals(true, this.value > 0)
                          throw IllegalArgumentException()
                      }
                  }

                  companion object {
                      @JvmStatic
                      fun values(): List<Array<Int>> = listOf(arrayOf(1), arrayOf(2))
                  }

                  private fun newFile(parent: File, child: String): File {
                      val result = File(parent, child)
                      result.createNewFile()
                      return result
                  }
              }
              """
          )
        );
    }

    @Test
    void companionImportsAndCallsOutsideTheEnclosingClass() {
        rewriteRun(
          kotlin(
            """
              package parity.importedcompanion
              import parity.importedcompanion.ImportedCompanion.NestedTest.Helpers.answer
              import org.junit.Assert.assertEquals
              import org.junit.Test
              import org.junit.experimental.runners.Enclosed
              import org.junit.runner.RunWith

              @RunWith(Enclosed::class)
              class ImportedCompanion {
                  class NestedTest {
                      @Test fun test() { assertEquals("ready", answer()) }
                      companion object Helpers { fun answer() = "ready" }
                  }
              }

              class Consumer {
                  @Test fun test() {
                      assertEquals("ready", answer())
                      assertEquals("ready", ImportedCompanion.NestedTest.answer())
                  }
              }
              """,
            """
              package parity.importedcompanion
              import parity.importedcompanion.ImportedCompanion.NestedTestFixtures.Helpers.answer
              import org.junit.jupiter.api.Assertions.assertEquals
              import org.junit.jupiter.api.Nested
              import org.junit.jupiter.api.Test

              class ImportedCompanion {
                  @Nested
                  inner class NestedTest : NestedTestFixtures() {

                      @Test
                      fun test() {
                          assertEquals("ready", NestedTestFixtures.answer())
                      }
                  }

                  abstract class NestedTestFixtures {
                      companion object Helpers {
                          fun answer() = "ready"
                      }
                  }
              }

              class Consumer {
                  @Test fun test() {
                      assertEquals("ready", ImportedCompanion.NestedTestFixtures.answer())
                      assertEquals("ready", ImportedCompanion.NestedTestFixtures.answer())
                  }
              }
              """
          )
        );
    }

    @Test
    void aliasedAssertionsDoNotReplaceKotlinTestImports() throws Exception {
        java.nio.file.Path kotlinTest = java.nio.file.Paths.get(kotlin.test.AssertionsKt.class.getProtectionDomain()
          .getCodeSource().getLocation().toURI());
        rewriteRun(
          spec -> spec.parser(KotlinParser.builder()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4")
            .addClasspathEntry(kotlinTest))
            .typeValidationOptions(TypeValidation.all().methodInvocations(false))
            .afterTypeValidationOptions(TypeValidation.all()),
          kotlin(
            """
              import kotlin.test.assertEquals
              import org.junit.Assert.assertEquals as jUnitAssertEquals
              import org.junit.Test

              class AliasImports {
                  @Test fun kotlinAssertions() { assertEquals("a", "a") }
                  @Test fun junitAssertions() { jUnitAssertEquals("a", "a") }
              }
              """,
            """
              import kotlin.test.assertEquals
              import org.junit.jupiter.api.Assertions
              import org.junit.jupiter.api.Test

              class AliasImports {
                  @Test fun kotlinAssertions() { assertEquals("a", "a") }
                  @Test fun junitAssertions() { Assertions.assertEquals("a", "a") }
              }
              """
          )
        );
    }

    @AfterAll
    static void compileAndRunMigratedTests() throws Exception {
        KotlinJUnit5ExecutionAssertions.compileAndRun();
    }

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.testing.junit5.JUnit4to5Migration")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-4"))
          .parser(KotlinParser.builder().classpathFromResources(new InMemoryExecutionContext(), "junit-4", "hamcrest-3"));
    }

    @Test
    void assertionsAndTestDiscovery() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Assert.*
              import org.junit.Test

              class AssertionsTest {
                  @Test
                  fun assertionMessagesAndOverloads() {
                      assertEquals("sum", 3, 1 + 2)
                      assertEquals("difference", 0.5, 1.0 - 0.5, 0.001)
                      assertTrue("condition", true)
                      assertNotNull("value", "present")
                      assertArrayEquals("array", intArrayOf(1, 2), intArrayOf(1, 2))
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.*
              import org.junit.jupiter.api.Test

              class AssertionsTest {
                  @Test
                  fun assertionMessagesAndOverloads() {
                      assertEquals(3, 1 + 2, "sum")
                      assertEquals(0.5, 1.0 - 0.5, 0.001, "difference")
                      assertTrue(true, "condition")
                      assertNotNull("present", "value")
                      assertArrayEquals(intArrayOf(1, 2), intArrayOf(1, 2), "array")
                  }
              }
              """
          )
        );
    }

    @Test
    void lifecycleAndDisabledTests() {
        rewriteRun(
          kotlin(
            """
              import org.junit.After
              import org.junit.AfterClass
              import org.junit.Before
              import org.junit.BeforeClass
              import org.junit.Ignore
              import org.junit.Test

              class LifecycleTest {
                  @Before
                  fun setUp() {
                  }

                  @After
                  fun tearDown() {
                  }

                  @Ignore("not implemented")
                  @Test
                  fun disabled() {
                  }

                  companion object {
                      @JvmStatic
                      @BeforeClass
                      fun beforeAll() {
                      }

                      @JvmStatic
                      @AfterClass
                      fun afterAll() {
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.*

              class LifecycleTest {
                  @BeforeEach
                  fun setUp() {
                  }

                  @AfterEach
                  fun tearDown() {
                  }

                  @Disabled("not implemented")
                  @Test
                  fun disabled() {
                  }

                  companion object {
                      @JvmStatic
                      @BeforeAll
                      fun beforeAll() {
                      }

                      @JvmStatic
                      @AfterAll
                      fun afterAll() {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void obsoleteRunnerAndOrdering() {
        rewriteRun(
          kotlin(
            """
              import org.junit.FixMethodOrder
              import org.junit.Test
              import org.junit.runner.RunWith
              import org.junit.runners.JUnit4
              import org.junit.runners.MethodSorters

              @RunWith(JUnit4::class)
              @FixMethodOrder(MethodSorters.NAME_ASCENDING)
              class OrderedTest {
                  @Test
                  fun test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.MethodOrderer.MethodName
              import org.junit.jupiter.api.Test
              import org.junit.jupiter.api.TestMethodOrder

              @TestMethodOrder(MethodName::class)
              class OrderedTest {

                  @Test
                  fun test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void categoriesBecomeTags() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Test
              import org.junit.experimental.categories.Category

              interface Fast
              interface Database

              @Category(Fast::class, Database::class)
              class TaggedTest {
                  @Test
                  fun test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Tag
              import org.junit.jupiter.api.Test

              interface Fast
              interface Database

              @Tag("Fast")
              @Tag("Database")
              class TaggedTest {
                  @Test
                  fun test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void timeoutRule() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Rule
              import org.junit.Test
              import org.junit.rules.Timeout

              class TimeoutTest {
                  @JvmField
                  @Rule
                  val timeout: Timeout = Timeout.millis(1000L)

                  @Test
                  fun test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test
              import org.junit.jupiter.api.Timeout

              import java.util.concurrent.TimeUnit

              @Timeout(value = 1000L, unit = TimeUnit.MILLISECONDS)
              class TimeoutTest {

                  @Test
                  fun test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void expectedExceptionRule() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Rule
              import org.junit.Test
              import org.junit.rules.ExpectedException

              class ExpectedRuleTest {
                  @JvmField
                  @Rule
                  val thrown: ExpectedException = ExpectedException.none()

                  @Test
                  fun test() {
                      thrown.expect(IllegalArgumentException::class.java)
                      throw IllegalArgumentException("expected")
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertThrows
              import org.junit.jupiter.api.Test

              class ExpectedRuleTest {

                  @Test
                  fun test() {
                      assertThrows(IllegalArgumentException::class.java) {
                          throw IllegalArgumentException("expected")
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void temporaryFolderRule() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Assert.assertTrue
              import org.junit.Rule
              import org.junit.Test
              import org.junit.rules.TemporaryFolder

              class TemporaryFolderTest {
                  @JvmField
                  @Rule
                  val folder: TemporaryFolder = TemporaryFolder()

                  @Test
                  fun test() {
                      val file = folder.newFile()
                      assertTrue(file.exists())
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertTrue
              import org.junit.jupiter.api.Test
              import org.junit.jupiter.api.io.TempDir

              import java.io.File

              class TemporaryFolderTest {
                  @TempDir
                  lateinit var folder: File

                  @Test
                  fun test() {
                      val file = File.createTempFile("junit", null, folder)
                      assertTrue(file.exists())
                  }
              }
              """
          )
        );
    }

    @Test
    void parameterizedTestWithAnnotationNameCollision() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Assert.assertTrue
              import org.junit.Test
              import org.junit.runner.RunWith
              import org.junit.runners.Parameterized

              @RunWith(Parameterized::class)
              class ParameterizedTest(private val value: Int) {
                  @Test
                  fun parameterIsProvided() {
                      assertTrue(value > 0)
                  }

                  companion object {
                      @JvmStatic
                      @Parameterized.Parameters(name = "{index}: value={0}")
                      fun values(): List<Array<Int>> = listOf(arrayOf(1), arrayOf(2))
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertTrue
              import org.junit.jupiter.params.provider.MethodSource

              class ParameterizedTest {
                  private var value: Int = 0

                  fun initParameterizedTest(value: Int) {
                      this.value = value
                  }

                  @MethodSource("values")
                  @org.junit.jupiter.params.ParameterizedTest(name = "{index}: value={0}")
                  fun parameterIsProvided(value: Int) {
                      initParameterizedTest(value)
                      assertTrue(this.value > 0)
                  }

                  companion object {
                      @JvmStatic
                      fun values(): List<Array<Int>> = listOf(arrayOf(1), arrayOf(2))
                  }
              }
              """
          )
        );
    }

    @Test
    void expectedExceptionAndTimeout() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Test

              class ExpectedTimeoutTest {
                  @Test(expected = IllegalArgumentException::class, timeout = 1000L)
                  fun test() {
                      throw IllegalArgumentException("expected")
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertThrows
              import org.junit.jupiter.api.Test
              import org.junit.jupiter.api.Timeout

              import java.util.concurrent.TimeUnit

              class ExpectedTimeoutTest {
                  @Test
                  @Timeout(value = 1000L, unit = TimeUnit.MILLISECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
                  fun test() {
                      assertThrows(IllegalArgumentException::class.java) {
                          throw IllegalArgumentException("expected")
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void genericFailure() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Assert.fail
              import org.junit.Test

              class FailureTest {
                  @Test(expected = AssertionError::class)
                  fun fails() {
                      fail("expected")
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertThrows
              import org.junit.jupiter.api.Test
              import org.junit.jupiter.api.fail

              class FailureTest {
                  @Test
                  fun fails() {
                      assertThrows(AssertionError::class.java) {
                          fail("expected")
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void expectedExceptionWithMessage() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Rule
              import org.junit.Test
              import org.junit.rules.ExpectedException

              class MessageTest {
                  @JvmField
                  @Rule
                  val thrown = ExpectedException.none()

                  @Test
                  fun test() {
                      thrown.expect(IllegalArgumentException::class.java)
                      thrown.expectMessage("expected")
                      throw IllegalArgumentException("the expected message")
                  }
              }
              """,
            """
              import org.hamcrest.CoreMatchers.containsString
              import org.hamcrest.MatcherAssert.assertThat
              import org.junit.jupiter.api.Assertions.assertThrows
              import org.junit.jupiter.api.Test

              class MessageTest {

                  @Test
                  fun test() {
                      val exception = assertThrows(IllegalArgumentException::class.java) {
                          throw IllegalArgumentException("the expected message")
                      }
                      assertThat(exception.message, containsString("expected"))
                  }
              }
              """
          )
        );
    }

    @Test
    void parameterizedFieldInjection() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Assert.assertTrue
              import org.junit.Test
              import org.junit.runner.RunWith
              import org.junit.runners.Parameterized

              @RunWith(Parameterized::class)
              class FieldTest {
                  @JvmField
                  @Parameterized.Parameter
                  var value: Int = 0

                  @Test
                  fun test() {
                      assertTrue(value > 0)
                  }

                  companion object {
                      @JvmStatic
                      @Parameterized.Parameters
                      fun values(): List<Array<Int>> = listOf(arrayOf(1), arrayOf(2))
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertTrue
              import org.junit.jupiter.params.ParameterizedTest
              import org.junit.jupiter.params.provider.MethodSource

              class FieldTest {

                  @JvmField
                  var value: Int = 0

                  @MethodSource("values")
                  @ParameterizedTest
                  fun test(value: Int) {
                      initFieldTest(value)
                      assertTrue(this.value > 0)
                  }

                  companion object {
                      @JvmStatic
                      fun values(): List<Array<Int>> = listOf(arrayOf(1), arrayOf(2))
                  }

                  fun initFieldTest(value: Int) {
                      this.value = value
                  }
              }
              """
          )
        );
    }

    @Test
    void parameterizedInitializers() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Assert.assertEquals
              import org.junit.Test
              import org.junit.runner.RunWith
              import org.junit.runners.Parameterized

              @RunWith(Parameterized::class)
              class InitializedTest(private val value: Int) {
                  private val doubled: Int = value * 2
                  private var initialized: Int = 0

                  init {
                      initialized = doubled + 1
                  }

                  @Test
                  fun test() {
                      assertEquals(value * 2 + 1, initialized)
                  }

                  companion object {
                      @JvmStatic
                      @Parameterized.Parameters
                      fun values(): List<Array<Int>> = listOf(arrayOf(1), arrayOf(2))
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertEquals
              import org.junit.jupiter.params.ParameterizedTest
              import org.junit.jupiter.params.provider.MethodSource

              class InitializedTest {
                  private var value: Int = 0

                  fun initInitializedTest(value: Int) {
                      this.value = value
                      this.doubled = value * 2
                      this.initialized = 0
                      initialized = doubled + 1
                  }
                  private var doubled: Int = 0
                  private var initialized: Int = 0

                  @MethodSource("values")
                  @ParameterizedTest
                  fun test(value: Int) {
                      initInitializedTest(value)
                      assertEquals(this.value * 2 + 1, initialized)
                  }

                  companion object {
                      @JvmStatic
                      fun values(): List<Array<Int>> = listOf(arrayOf(1), arrayOf(2))
                  }
              }
              """
          )
        );
    }

    @Test
    void parameterizedInferredPropertiesPreserveTypesAndInitialization() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Assert.assertEquals
              import org.junit.Test
              import org.junit.runner.RunWith
              import org.junit.runners.Parameterized

              @RunWith(Parameterized::class)
              class InferredProperties(private val value: Int) {
                  private val helper = StringBuilder("$value")
                  private val nullable = if (value == 1) null else "$value"
                  private val nullableNumber = if (value == 1) null else value
                  private val nullableByte = if (value == 1) null else value.toByte()
                  private val generic = listOf(mapOf("value" to value))
                  private val small = value.toByte()
                  private var calls = 0
                  private val initialized = ++calls

                  @Test
                  fun test() {
                      assertEquals("$value", helper.toString())
                      assertEquals(if (value == 1) null else "$value", nullable)
                      assertEquals(if (value == 1) null else value, nullableNumber)
                      assertEquals(if (value == 1) null else value.toByte(), nullableByte)
                      assertEquals(value, generic[0]["value"])
                      assertEquals(value.toByte(), small)
                      assertEquals(1, initialized)
                      assertEquals(1, calls)
                  }

                  companion object {
                      @JvmStatic
                      @Parameterized.Parameters
                      fun values(): List<Array<Int>> = listOf(arrayOf(1), arrayOf(2))
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertEquals
              import org.junit.jupiter.params.ParameterizedTest
              import org.junit.jupiter.params.provider.MethodSource

              class InferredProperties {
                  private var value: Int = 0

                  fun initInferredProperties(value: Int) {
                      this.value = value
                      this.helper = StringBuilder("$value")
                      this.nullable = if (value == 1) null else "$value"
                      this.nullableNumber = if (value == 1) null else value
                      this.nullableByte = if (value == 1) null else value.toByte()
                      this.generic = listOf(mapOf("value" to value))
                      this.small = value.toByte()
                      this.calls = 0
                      this.initialized = ++calls
                  }
                  private lateinit var helper: java.lang.StringBuilder
                  private var nullable: kotlin.String? = null
                  private var nullableNumber: kotlin.Int? = null
                  private var nullableByte: kotlin.Byte? = null
                  private lateinit var generic: kotlin.collections.List<kotlin.collections.Map<kotlin.String, kotlin.Int>>
                  private var small: kotlin.Byte = 0
                  private var calls: kotlin.Int = 0
                  private var initialized: kotlin.Int = 0

                  @MethodSource("values")
                  @ParameterizedTest
                  fun test(value: Int) {
                      initInferredProperties(value)
                      assertEquals("${this.value}", helper.toString())
                      assertEquals(if (this.value == 1) null else "${this.value}", nullable)
                      assertEquals(if (this.value == 1) null else this.value, nullableNumber)
                      assertEquals(if (this.value == 1) null else this.value.toByte(), nullableByte)
                      assertEquals(this.value, generic[0]["value"])
                      assertEquals(this.value.toByte(), small)
                      assertEquals(1, initialized)
                      assertEquals(1, calls)
                  }

                  companion object {
                      @JvmStatic
                      fun values(): List<Array<Int>> = listOf(arrayOf(1), arrayOf(2))
                  }
              }
              """
          )
        );
    }

    @Test
    void parameterizedSecondaryConstructor() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Assert.assertTrue
              import org.junit.Test
              import org.junit.runner.RunWith
              import org.junit.runners.Parameterized

              @RunWith(Parameterized::class)
              class SecondaryTest {
                  private val value: Int

                  constructor(value: Int) {
                      this.value = value
                  }

                  @Test
                  fun test() {
                      assertTrue(value > 0)
                  }

                  companion object {
                      @JvmStatic
                      @Parameterized.Parameters
                      fun values(): List<Array<Int>> = listOf(arrayOf(1), arrayOf(2))
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertTrue
              import org.junit.jupiter.params.ParameterizedTest
              import org.junit.jupiter.params.provider.MethodSource

              class SecondaryTest {
                  private var value: Int = 0

                  fun initSecondaryTest(value: Int) {
                      this.value = value
                  }

                  @MethodSource("values")
                  @ParameterizedTest
                  fun test(value: Int) {
                      initSecondaryTest(value)
                      assertTrue(this.value > 0)
                  }

                  companion object {
                      @JvmStatic
                      fun values(): List<Array<Int>> = listOf(arrayOf(1), arrayOf(2))
                  }
              }
              """
          )
        );
    }

    @Test
    void kotlinWildcardImports() {
        rewriteRun(
          kotlin(
            """
              import org.junit.*
              import org.junit.Assert.*

              class WildcardTest {
                  @Before
                  fun setUp() {
                  }

                  @After
                  fun tearDown() {
                  }

                  @Test(expected = IllegalArgumentException::class)
                  fun test() {
                      assertTrue(true)
                      throw IllegalArgumentException("expected")
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterEach
              import org.junit.jupiter.api.Assertions.*
              import org.junit.jupiter.api.BeforeEach
              import org.junit.jupiter.api.Test

              class WildcardTest {
                  @BeforeEach
                  fun setUp() {
                  }

                  @AfterEach
                  fun tearDown() {
                  }

                  @Test
                  fun test() {
                      assertThrows(IllegalArgumentException::class.java) {
                          assertTrue(true)
                          throw IllegalArgumentException("expected")
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void testNameRule() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Assert.assertEquals
              import org.junit.Rule
              import org.junit.Test
              import org.junit.rules.TestName

              class NameTest {
                  @JvmField
                  @Rule
                  val name: TestName = TestName()

                  @Test
                  fun test() {
                      assertEquals("test", name.getMethodName())
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertEquals
              import org.junit.jupiter.api.BeforeEach
              import org.junit.jupiter.api.Test
              import org.junit.jupiter.api.TestInfo

              class NameTest {
                  lateinit var name: String

                  @Test
                  fun test() {
                      assertEquals("test", name)
                  }

                  @BeforeEach
                  fun setup(testInfo: TestInfo) {
                      this.name = testInfo.testMethod.get().name
                  }
              }
              """
          )
        );
    }

    @Test
    void junitTestCase() {
        rewriteRun(
          kotlin(
            """
              import junit.framework.TestCase

              class LegacyTest : TestCase() {
                  private var state: Int = 0

                  override fun setUp() {
                      super.setUp()
                      state = 2
                  }

                  override fun tearDown() {
                      super.tearDown()
                      state = 0
                  }

                  fun testAddition() {
                      assertEquals(2, state)
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterEach
              import org.junit.jupiter.api.Assertions.assertEquals
              import org.junit.jupiter.api.BeforeEach
              import org.junit.jupiter.api.Test

              class LegacyTest {
                  private var state: Int = 0

                  @BeforeEach fun setUp() {
                      state = 2
                  }

                  @AfterEach fun tearDown() {
                      state = 0
                  }

                  @Test
                  fun testAddition() {
                      assertEquals(2, state)
                  }
              }
              """
          )
        );
    }

    @Test
    void enclosedRunner() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Test
              import org.junit.experimental.runners.Enclosed
              import org.junit.runner.RunWith

              @RunWith(Enclosed::class)
              class EnclosedTest {
                  class NestedTest {
                      @Test
                      fun test() {
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested
              import org.junit.jupiter.api.Test

              class EnclosedTest {
                  @Nested
                  inner class NestedTest {

                      @Test
                      fun test() {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void enclosedCompanionLifecyclePreservesPerMethodInstances() {
        rewriteRun(
          kotlin(
            """
              import org.junit.AfterClass
              import org.junit.BeforeClass
              import org.junit.Assert.assertEquals
              import org.junit.Test
              import org.junit.experimental.runners.Enclosed
              import org.junit.runner.RunWith

              @RunWith(Enclosed::class)
              class EnclosedLifecycle {
                  class NestedTest {
                      private var calls = 0

                      @Test
                      fun first() {
                          calls++
                          assertEquals(1, calls)
                          assertEquals("ready", state)
                          completed++
                      }

                      @Test
                      fun second() {
                          calls++
                          assertEquals(1, calls)
                          assertEquals("ready", state)
                          completed++
                      }

                      companion object {
                          private var completed = 0
                          private lateinit var state: String
                          class Lifecycle {
                              fun label() = "original"
                          }

                          @JvmStatic
                          @BeforeClass
                          fun beforeAll() {
                              assertEquals(0, completed)
                              assertEquals("original", Lifecycle().label())
                              state = "ready"
                          }

                          @JvmStatic
                          @AfterClass
                          fun afterAll() {
                              assertEquals(2, completed)
                              state = ""
                          }
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterAll
              import org.junit.jupiter.api.Assertions.assertEquals
              import org.junit.jupiter.api.BeforeAll
              import org.junit.jupiter.api.Nested
              import org.junit.jupiter.api.Test

              class EnclosedLifecycle {
                  @Nested
                  inner class NestedTest : NestedTestFixtures() {
                      private var calls = 0

                      @Test
                      fun first() {
                          calls++
                          assertEquals(1, calls)
                          assertEquals("ready", NestedTestFixtures.state)
                          NestedTestFixtures.completed++
                      }

                      @Test
                      fun second() {
                          calls++
                          assertEquals(1, calls)
                          assertEquals("ready", NestedTestFixtures.state)
                          NestedTestFixtures.completed++
                      }
                  }

                  abstract class NestedTestFixtures {
                      companion object {
                          var completed = 0
                          lateinit var state: String

                          class Lifecycle {
                              fun label() = "original"
                          }

                          @JvmStatic
                          @BeforeAll
                          fun beforeAll() {
                              assertEquals(0, completed)
                              assertEquals("original", Lifecycle().label())
                              state = "ready"
                          }

                          @JvmStatic
                          @AfterAll
                          fun afterAll() {
                              assertEquals(2, completed)
                              state = ""
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void enclosedCompanionProviderUsesExternalMethodSource() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Assert.assertEquals
              import org.junit.Test
              import org.junit.experimental.runners.Enclosed
              import org.junit.runner.RunWith
              import org.junit.runners.Parameterized

              @RunWith(Enclosed::class)
              class EnclosedParameterized {
                  @RunWith(Parameterized::class)
                  class NestedTest(private val value: Int) {
                      @Test
                      fun test() {
                          assertEquals(true, value > 0)
                      }

                      companion object {
                          @JvmStatic
                          @Parameterized.Parameters
                          fun values(): List<Array<Int>> = listOf(arrayOf(1), arrayOf(2))
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertEquals
              import org.junit.jupiter.api.Nested
              import org.junit.jupiter.params.ParameterizedTest
              import org.junit.jupiter.params.provider.MethodSource

              class EnclosedParameterized {
                  @Nested
                  inner class NestedTest : NestedTestFixtures() {
                      private var value: Int = 0

                      fun initNestedTest(value: Int) {
                          this.value = value
                      }

                      @MethodSource("EnclosedParameterized\\$NestedTestFixtures#values")
                      @ParameterizedTest
                      fun test(value: Int) {
                          initNestedTest(value)
                          assertEquals(true, this.value > 0)
                      }
                  }

                  abstract class NestedTestFixtures {
                      companion object {

                          @JvmStatic
                          fun values(): List<Array<Int>> = listOf(arrayOf(1), arrayOf(2))
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void enclosedPrivateJUnitParamsProviderKeepsJvmName() {
        rewriteRun(
          spec -> spec.parser(KotlinParser.builder().classpathFromResources(new InMemoryExecutionContext(),
            "junit-4", "JUnitParams-1.1")),
          kotlin(
            """
              import junitparams.JUnitParamsRunner
              import junitparams.Parameters
              import org.junit.Assert.assertEquals
              import org.junit.Test
              import org.junit.experimental.runners.Enclosed
              import org.junit.runner.RunWith

              @RunWith(Enclosed::class)
              class EnclosedJUnitParams {
                  @RunWith(JUnitParamsRunner::class)
                  class NestedTest {
                      @Test
                      @Parameters(method = "values")
                      fun test(value: Int, next: Int) {
                          assertEquals(value + 1, next)
                      }

                      private fun values(): Array<Array<Any>> = arrayOf(arrayOf(1, 2), arrayOf(3, 4))
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertEquals
              import org.junit.jupiter.api.Nested
              import org.junit.jupiter.params.ParameterizedTest
              import org.junit.jupiter.params.provider.MethodSource

              class EnclosedJUnitParams {
                  @Nested
                  inner class NestedTest : NestedTestFixtures() {

                      @ParameterizedTest
                      @MethodSource("EnclosedJUnitParams\\$NestedTestFixtures#values")
                      fun test(value: Int, next: Int) {
                          assertEquals(value + 1, next)
                      }
                  }

                  abstract class NestedTestFixtures {
                      companion object {

                          @JvmStatic
                          fun values(): Array<Array<Any>> = arrayOf(arrayOf(1, 2), arrayOf(3, 4))
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void enclosedClassRulePreservesSuperclassAndInterface() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Assert.assertEquals
              import org.junit.Assert.assertTrue
              import org.junit.ClassRule
              import org.junit.Test
              import org.junit.experimental.runners.Enclosed
              import org.junit.rules.TemporaryFolder
              import org.junit.runner.RunWith

              @RunWith(Enclosed::class)
              class EnclosedClassRule {
                  open class ExistingBase {
                      val inherited = 7
                  }
                  interface Marker

                  class NestedTest : ExistingBase(), Marker {
                      @Test
                      fun test() {
                          assertEquals(7, inherited)
                          assertTrue(folder.newFile("data.txt").exists())
                      }

                      companion object Storage {
                          @JvmField
                          @ClassRule
                          val folder = TemporaryFolder()
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertEquals
              import org.junit.jupiter.api.Assertions.assertTrue
              import org.junit.jupiter.api.Nested
              import org.junit.jupiter.api.Test
              import org.junit.jupiter.api.io.TempDir

              import java.io.File

              class EnclosedClassRule {
                  open class ExistingBase {
                      val inherited = 7
                  }

                  interface Marker

                  @Nested
                  inner class NestedTest : NestedTestFixtures(), Marker {

                      @Test
                      fun test() {
                          assertEquals(7, inherited)
                          assertTrue(newFile(NestedTestFixtures.folder, "data.txt").exists())
                      }

                      private fun newFile(parent: File, child: String): File {
                          val result = File(parent, child)
                          result.createNewFile()
                          return result
                      }
                  }

                  abstract class NestedTestFixtures : ExistingBase() {
                      companion object Storage {

                          @TempDir
                          lateinit var folder: File
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void temporaryFolderNamedPaths() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Assert.assertTrue
              import org.junit.Rule
              import org.junit.Test
              import org.junit.rules.TemporaryFolder

              class NamedPathsTest {
                  @JvmField
                  @Rule
                  val folder = TemporaryFolder()

                  @Test
                  fun test() {
                      val file = folder.newFile("data.txt")
                      val directory = folder.newFolder("one", "two")
                      assertTrue(file.exists())
                      assertTrue(directory.isDirectory)
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertTrue
              import org.junit.jupiter.api.Test
              import org.junit.jupiter.api.io.TempDir

              import java.io.File
              import java.io.IOException

              class NamedPathsTest {
                  @TempDir
                  lateinit var folder: File

                  @Test
                  fun test() {
                      val file = newFile(folder, "data.txt")
                      val directory = newFolder(folder, "one", "two")
                      assertTrue(file.exists())
                      assertTrue(directory.isDirectory)
                  }
                  private fun newFile(parent: File, child: String): File {
                      val result = File(parent, child)
                      result.createNewFile()
                      return result
                  }
                  private fun newFolder(root: File, vararg subDirs: String): File {
                      val result = File(root, subDirs.joinToString("/"))
                      if (!result.mkdirs()) {
                          throw IOException("Couldn't create folders " + root)
                      }
                      return result
                  }
              }
              """
          )
        );
    }

    @Test
    void assumptions() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Assume.*
              import org.junit.Test

              class AssumptionTest {
                  @Test
                  fun test() {
                      assumeTrue("enabled", true)
                      assumeFalse("disabled", false)
                      assumeNotNull("present")
                      assumeNotNull("one", "two")
                      assumeNotNull(*arrayOf("three", "four"))
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assumptions.*
              import org.junit.jupiter.api.Test

              class AssumptionTest {
                  @Test
                  fun test() {
                      assumeTrue(true, "enabled")
                      assumeFalse(false, "disabled")
                      assumeFalse("present" == null)
                      arrayOf("one", "two").forEach { assumeFalse(it == null) }
                      arrayOf("three", "four").forEach { assumeFalse(it == null) }
                  }
              }
              """
          )
        );
    }

    @Test
    void packagedAssertions() {
        rewriteRun(
          kotlin(
            """
              package example

              import org.junit.Assert.*
              import org.junit.Test

              class PackageTest {
                  @Test
                  fun test() {
                      assertEquals(3, 1 + 2)
                      assertTrue(true)
                      assertNotNull("present")
                      assertArrayEquals(intArrayOf(1, 2), intArrayOf(1, 2))
                  }
              }
              """,
            """
              package example

              import org.junit.jupiter.api.Assertions.*
              import org.junit.jupiter.api.Test

              class PackageTest {
                  @Test
                  fun test() {
                      assertEquals(3, 1 + 2)
                      assertTrue(true)
                      assertNotNull("present")
                      assertArrayEquals(intArrayOf(1, 2), intArrayOf(1, 2))
                  }
              }
              """
          )
        );
    }

    @Test
    void parameterizedSetupUsesInitializedFields() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Assert.assertEquals
              import org.junit.Before
              import org.junit.Test
              import org.junit.runner.RunWith
              import org.junit.runners.Parameterized

              @RunWith(Parameterized::class)
              class SetupTest(value: Int) {
                  private val value: Int = value * 2
                  private var initialized: Int = 0

                  @Before
                  fun setUp() {
                      initialized = value + 1
                  }

                  @Test
                  fun test() {
                      assertEquals(value + 1, initialized)
                  }

                  companion object {
                      @JvmStatic
                      @Parameterized.Parameters
                      fun values(): List<Array<Int>> = listOf(arrayOf(1), arrayOf(2))
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertEquals
              import org.junit.jupiter.params.ParameterizedTest
              import org.junit.jupiter.params.provider.MethodSource

              class SetupTest {
                  private var valueArgument: Int = 0

                  fun initSetupTest(value: Int) {
                      this.valueArgument = value
                      this.value = value * 2
                      this.initialized = 0
                  }
                  private var value: Int = 0
                  private var initialized: Int = 0

                  fun setUp() {
                      initialized = value + 1
                  }

                  @MethodSource("values")
                  @ParameterizedTest
                  fun test(value: Int) {
                      initSetupTest(value)
                      setUp()
                      assertEquals(this.value + 1, initialized)
                  }

                  companion object {
                      @JvmStatic
                      fun values(): List<Array<Int>> = listOf(arrayOf(1), arrayOf(2))
                  }
              }
              """
          )
        );
    }

    @Test
    void aliasedImports() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.all().methodInvocations(false))
            .afterTypeValidationOptions(TypeValidation.all()),
          kotlin(
            """
              import org.junit.Assert.assertEquals as same
              import org.junit.Test as JTest

              class AliasTest {
                  @JTest
                  fun test() {
                      same("sum", 3, 1 + 2)
                      same("present", "present")
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertEquals
              import org.junit.jupiter.api.Test as JTest

              class AliasTest {
                  @JTest
                  fun test() {
                      assertEquals(3, 1 + 2, "sum")
                      assertEquals("present", "present")
                  }
              }
              """
          )
        );
    }

    @Test
    void hamcrestAndAssumptionAliases() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.all().methodInvocations(false))
            .afterTypeValidationOptions(TypeValidation.all()),
          kotlin(
            """
              import org.hamcrest.CoreMatchers.`is`
              import org.junit.Assert.assertThat as matches
              import org.junit.Assume.assumeTrue as enabled
              import org.junit.Test as JTest

              class HamcrestTest {
                  @JTest
                  fun test() {
                      enabled("enabled", true)
                      matches("answer", 42, `is`(42))
                  }
              }
              """,
            """
              import org.hamcrest.CoreMatchers.`is`
              import org.hamcrest.MatcherAssert.assertThat
              import org.junit.jupiter.api.Assumptions.assumeTrue
              import org.junit.jupiter.api.Test as JTest

              class HamcrestTest {
                  @JTest
                  fun test() {
                      assumeTrue(true, "enabled")
                      assertThat("answer", 42, `is`(42))
                  }
              }
              """
          )
        );
    }

    @Test
    void expectedExceptionExpressionBody() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Test

              class ExpressionTest {
                  @Test(expected = IllegalArgumentException::class)
                  fun test(): Unit = throwingCall()

                  private fun throwingCall(): Unit = throw IllegalArgumentException()
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertThrows
              import org.junit.jupiter.api.Test

              class ExpressionTest {
                  @Test
                  fun test(): Unit {
                      assertThrows(IllegalArgumentException::class.java) {
                          throwingCall()
                      }
                  }

                  private fun throwingCall(): Unit = throw IllegalArgumentException()
              }
              """
          )
        );
    }

    @Test
    void inferredTestNameProperty() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Assert.assertEquals
              import org.junit.Rule
              import org.junit.Test
              import org.junit.rules.TestName

              class NameTest {
                  @JvmField
                  @Rule
                  val name = TestName()

                  @Test
                  fun test() {
                      assertEquals("test", name.methodName)
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertEquals
              import org.junit.jupiter.api.BeforeEach
              import org.junit.jupiter.api.Test
              import org.junit.jupiter.api.TestInfo

              class NameTest {
                  lateinit var name: String

                  @Test
                  fun test() {
                      assertEquals("test", name)
                  }

                  @BeforeEach
                  fun setup(testInfo: TestInfo) {
                      this.name = testInfo.testMethod.get().name
                  }
              }
              """
          )
        );
    }

    @Test
    void expectedExceptionCause() {
        rewriteRun(
          kotlin(
            """
              import org.hamcrest.CoreMatchers.instanceOf
              import org.junit.Rule
              import org.junit.Test
              import org.junit.rules.ExpectedException

              class CauseTest {
                  @Rule
                  @JvmField
                  val expected = ExpectedException.none()

                  @Test
                  fun test() {
                      expected.expect(IllegalArgumentException::class.java)
                      expected.expectCause(instanceOf(IllegalStateException::class.java))
                      throw IllegalArgumentException(IllegalStateException())
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertInstanceOf
              import org.junit.jupiter.api.Assertions.assertThrows
              import org.junit.jupiter.api.Test

              class CauseTest {

                  @Test
                  fun test() {
                      val exception = assertThrows(IllegalArgumentException::class.java) {
                          throw IllegalArgumentException(IllegalStateException())
                      }
                      assertInstanceOf(IllegalStateException::class.java, exception.cause)
                  }
              }
              """
          )
        );
    }

    @Test
    void junitTestCaseInheritance() {
        rewriteRun(
          kotlin(
            """
              import junit.framework.TestCase

              open class BaseTest : TestCase() {
                  protected var value = 0

                  override fun setUp() {
                      value = 1
                  }

                  open fun testValue() {
                      assertEquals(1, value)
                  }
              }

              class ChildTest : BaseTest() {
                  override fun setUp() {
                      super.setUp()
                      value = 2
                  }

                  override fun testValue() {
                      assertEquals(2, value)
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertEquals
              import org.junit.jupiter.api.BeforeEach
              import org.junit.jupiter.api.Test

              open class BaseTest {
                  protected var value = 0

                  @BeforeEach
                  open fun setUp() {
                      value = 1
                  }

                  @Test
                  open fun testValue() {
                      assertEquals(1, value)
                  }
              }

              class ChildTest : BaseTest() {
                  @BeforeEach
                  override fun setUp() {
                      super.setUp()
                      value = 2
                  }

                  @Test
                  override fun testValue() {
                      assertEquals(2, value)
                  }
              }
              """
          )
        );
    }

    @Test
    void categoryArrayAndAlias() {
        rewriteRun(
          kotlin(
            """
              import java.io.Serializable as Slow
              import java.lang.Runnable as Fast
              import org.junit.Test
              import org.junit.experimental.categories.Category

              @Category(value = [Slow::class, Fast::class])
              class TagsTest {
                  @Test
                  fun test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Tag
              import org.junit.jupiter.api.Test

              @Tag("Serializable")
              @Tag("Runnable")
              class TagsTest {
                  @Test
                  fun test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void parameterizedExpressionBody() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Assert.assertTrue
              import org.junit.Test
              import org.junit.runner.RunWith
              import org.junit.runners.Parameterized

              @RunWith(Parameterized::class)
              class ExpressionTest(private val value: Int) {
                  @Test
                  fun test() = assertTrue(value > 0)

                  companion object {
                      @JvmStatic
                      @Parameterized.Parameters
                      fun data(): List<Array<Int>> = listOf(arrayOf(1), arrayOf(2))
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions.assertTrue
              import org.junit.jupiter.params.ParameterizedTest
              import org.junit.jupiter.params.provider.MethodSource

              class ExpressionTest {
                  private var value: Int = 0

                  fun initExpressionTest(value: Int) {
                      this.value = value
                  }

                  @MethodSource("data")
                  @ParameterizedTest
                  fun test(value: Int) {
                      initExpressionTest(value)
                      assertTrue(this.value > 0)
                  }

                  companion object {
                      @JvmStatic
                      fun data(): List<Array<Int>> = listOf(arrayOf(1), arrayOf(2))
                  }
              }
              """
          )
        );
    }

    @Test
    void aliasedAssertionWithLocalNameCollision() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.all().methodInvocations(false))
            .afterTypeValidationOptions(TypeValidation.all()),
          kotlin(
            """
              import org.junit.Assert.assertEquals as equal
              import org.junit.Test

              class AssertionCollisionTest {
                  @Test
                  fun test() {
                      equal("value", "value")
                      assertEquals()
                  }

                  private fun assertEquals() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions
              import org.junit.jupiter.api.Test

              class AssertionCollisionTest {
                  @Test
                  fun test() {
                      Assertions.assertEquals("value", "value")
                      assertEquals()
                  }

                  private fun assertEquals() {
                  }
              }
              """
          )
        );
    }

    @Test
    void explicitZeroTimeout() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Test

              class ZeroTimeoutTest {
                  @Test(timeout = 0L)
                  fun test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test

              class ZeroTimeoutTest {
                  @Test
                  fun test() {
                  }
              }
              """
          )
        );
    }
}
