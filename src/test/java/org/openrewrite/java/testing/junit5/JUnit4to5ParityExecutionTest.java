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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openrewrite.*;
import org.openrewrite.config.Environment;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.kotlin.tree.K;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("kotlin-execution")
class JUnit4to5ParityExecutionTest {
    @Test
    void migratedJavaAndKotlinDiscoverAndRunTheSameTests() throws Exception {
        ExecutionContext ctx = new InMemoryExecutionContext(t -> {
            throw new AssertionError(t);
        });
        List<SourceFile> before = new ArrayList<>();
        before.addAll(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-4", "JUnitParams-1.1")
                .build().parse(ctx,
                        """
                        package parity.java;
                        import org.junit.*;
                        import static org.junit.Assert.*;

                        public class JavaOrdinaryTest {
                            private int value;
                            @Before public void setUp() { value = 42; }
                            @After public void tearDown() { value = 0; }
                            @Test public void equals() { assertEquals("message", 42, value); }
                            @Test public void truth() { assertTrue("message", value > 0); }
                            @Test public void notNull() { assertNotNull("message", "value"); }
                            @Test public void failure() {
                                try { fail("failure"); } catch (AssertionError expected) {
                                    assertEquals("failure", expected.getMessage());
                                }
                            }
                            @Ignore("intentionally disabled") @Test public void disabled() { fail(); }
                        }
                        """,
                        """
                        package parity.java;
                        import org.junit.Test;

                        public class JavaExpectedTest {
                            @Test(expected = IllegalArgumentException.class, timeout = 1000)
                            public void expected() { throw new IllegalArgumentException("expected"); }
                        }
                        """,
                        """
                        package parity.java;
                        import org.junit.Test;
                        import org.junit.Before;
                        import org.junit.runner.RunWith;
                        import org.junit.runners.Parameterized;
                        import java.util.Arrays;
                        import java.util.Collection;
                        import static org.junit.Assert.assertEquals;

                        @RunWith(Parameterized.class)
                        public class JavaParameterizedTest {
                            private final int value;
                            private int setups;
                            public JavaParameterizedTest(int value) { this.value = value; }
                            @Before public void setUp() {
                                assertEquals(true, value > 0);
                                setups++;
                            }
                            @Parameterized.Parameters(name = "{index}: {0}")
                            public static Collection<Object[]> data() {
                                return Arrays.asList(new Object[][] {{1}, {2}});
                            }
                            @Test public void parameter() {
                                assertEquals(value, this.value);
                                assertEquals(1, setups);
                            }
                        }
                        """,
                        """
                        package parity.java;
                        import org.junit.*;
                        import org.junit.experimental.runners.Enclosed;
                        import org.junit.runner.RunWith;
                        import org.junit.runners.Parameterized;
                        import static org.junit.Assert.assertEquals;

                        @RunWith(Enclosed.class)
                        public class JavaEnclosedTest {
                            public static class Ordinary {
                                private int calls;
                                private static int completed;
                                private static String state;
                                @BeforeClass public static void start() {
                                    assertEquals(0, completed);
                                    state = "ready";
                                }
                                @AfterClass public static void finish() { assertEquals(2, completed); }
                                @Test public void first() {
                                    assertEquals(1, ++calls);
                                    assertEquals("ready", state);
                                    completed++;
                                }
                                @Test public void second() {
                                    assertEquals(1, ++calls);
                                    assertEquals("ready", state);
                                    completed++;
                                }
                            }
                            @RunWith(Parameterized.class)
                            public static class Parameters {
                                private final int value;
                                public Parameters(int value) { this.value = value; }
                                @Parameterized.Parameters public static Object[][] values() {
                                    return new Object[][] {{1}, {2}};
                                }
                                @Test public void test() { assertEquals(true, value > 0); }
                            }
                        }
                        """,
                        """
                        package parity.java;
                        import junitparams.JUnitParamsRunner;
                        import junitparams.Parameters;
                        import org.junit.Test;
                        import org.junit.runner.RunWith;
                        import static org.junit.Assert.assertEquals;

                        @RunWith(JUnitParamsRunner.class)
                        public class JavaCsvTest {
                            @Test @Parameters(value = {"1,2", "3,4"})
                            public void test(int value, int next) { assertEquals(value + 1, next); }
                        }
                        """).toList());
        before.addAll(KotlinParser.builder().classpathFromResources(ctx, "junit-4", "JUnitParams-1.1")
                .build().parse(ctx,
                        """
                        package parity.kotlin
                        import org.junit.*
                        import org.junit.Assert.*

                        class KotlinOrdinaryTest {
                            private var value = 0
                            @Before fun setUp() { value = 42 }
                            @After fun tearDown() { value = 0 }
                            @Test fun equals() { assertEquals("message", 42, value) }
                            @Test fun truth() { assertTrue("message", value > 0) }
                            @Test fun notNull() { assertNotNull("message", "value") }
                            @Test fun failure() {
                                try { fail("failure") } catch (expected: AssertionError) {
                                    assertEquals("failure", expected.message)
                                }
                            }
                            @Ignore("intentionally disabled") @Test fun disabled() { fail() }
                        }
                        """,
                        """
                        package parity.kotlin
                        import org.junit.Test

                        class KotlinExpectedTest {
                            @Test(expected = IllegalArgumentException::class, timeout = 1000)
                            fun expected() { throw IllegalArgumentException("expected") }
                        }
                        """,
                        """
                        package parity.kotlin
                        import org.junit.Test
                        import org.junit.Before
                        import org.junit.runner.RunWith
                        import org.junit.runners.Parameterized
                        import org.junit.Assert.assertEquals

                        @RunWith(Parameterized::class)
                        class KotlinParameterizedTest(private val value: Int) {
                            private var setups = 0
                            @Before fun setUp() {
                                assertEquals(true, value > 0)
                                setups++
                            }
                            companion object {
                                @JvmStatic
                                @Parameterized.Parameters(name = "{index}: {0}")
                                fun data(): Collection<Array<Any>> = listOf(arrayOf(1), arrayOf(2))
                            }
                            @Test fun parameter() {
                                assertEquals(value, this.value)
                                assertEquals(1, setups)
                            }
                        }
                        """,
                        """
                        package parity.kotlin
                        import org.junit.*
                        import org.junit.experimental.runners.Enclosed
                        import org.junit.runner.RunWith
                        import org.junit.runners.Parameterized
                        import org.junit.Assert.assertEquals

                        @RunWith(Enclosed::class)
                        class KotlinEnclosedTest {
                            class Ordinary {
                                private var calls = 0
                                @Test fun first() {
                                    assertEquals(1, ++calls)
                                    assertEquals("ready", state)
                                    completed++
                                }
                                @Test fun second() {
                                    assertEquals(1, ++calls)
                                    assertEquals("ready", state)
                                    completed++
                                }
                                companion object {
                                    private var completed = 0
                                    private lateinit var state: String
                                    @JvmStatic @BeforeClass fun start() {
                                        assertEquals(0, completed)
                                        state = "ready"
                                    }
                                    @JvmStatic @AfterClass fun finish() { assertEquals(2, completed) }
                                }
                            }
                            @RunWith(Parameterized::class)
                            class Parameters(private val value: Int) {
                                @Test fun test() { assertEquals(true, value > 0) }
                                companion object {
                                    @JvmStatic @Parameterized.Parameters
                                    fun values(): Array<Array<Any>> = arrayOf(arrayOf(1), arrayOf(2))
                                }
                            }
                        }
                        """,
                        """
                        package parity.kotlin
                        import junitparams.JUnitParamsRunner
                        import junitparams.Parameters
                        import org.junit.Test
                        import org.junit.runner.RunWith
                        import org.junit.Assert.assertEquals

                        @RunWith(JUnitParamsRunner::class)
                        class KotlinCsvTest {
                            @Test @Parameters(value = arrayOf("1,2", "3,4"))
                            fun test(value: Int, next: Int) { assertEquals(value + 1, next) }
                        }
                        """).toList());
        for (int index = 0; index < before.size(); index++) {
            SourceFile source = before.get(index);
            if (source instanceof K.CompilationUnit) {
                before.set(index, source.withSourcePath(Paths.get("Fixture" + index + ".kt")));
            }
        }
        Recipe recipe = Environment.builder().scanRuntimeClasspath().build()
                .activateRecipes("org.openrewrite.java.testing.junit5.JUnit4to5Migration");
        assertThat(recipe.validateAll()).allMatch(validation -> validation.isValid());
        List<Result> results = recipe.run(new InMemoryLargeSourceSet(before), ctx).getChangeset().getAllResults();
        assertThat(results).hasSize(10);
        List<SourceFile> after = new ArrayList<>();
        for (Result result : results) {
            assertThat(result.getAfter()).isNotNull();
            SourceFile source = result.getAfter();
            if (source.getSourcePath().toString().endsWith(".java")) {
                String name = ((J.CompilationUnit) source).getClasses().stream()
                        .filter(clazz -> clazz.hasModifier(J.Modifier.Type.Public))
                        .findFirst().orElseThrow(() -> new AssertionError("No public Java test class")).getSimpleName();
                source = source.withSourcePath(Paths.get(name + ".java"));
            }
            after.add(source);
        }
        KotlinJUnit5ExecutionAssertions.compileAndRunParity(after);
    }
}
