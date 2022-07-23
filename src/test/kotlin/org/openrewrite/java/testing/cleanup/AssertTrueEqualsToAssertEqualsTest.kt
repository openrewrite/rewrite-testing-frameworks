/*
 * Copyright 2022 the original author or authors.
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
package org.openrewrite.java.testing.cleanup

import org.junit.jupiter.api.Test
import org.openrewrite.Issue
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class AssertTrueEqualsToAssertEqualsTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .logCompilationWarningsAndErrors(true)
        .classpath("junit-jupiter-api")
        .build()

    override val recipe: Recipe
        get() = AssertTrueEqualsToAssertEquals()

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/206")
    @Suppress("ConstantConditions", "SimplifiableAssertion")
    @Test
    fun assertTrueToAssertEquals() = assertChanged(
        before = """
            import static org.junit.jupiter.api.Assertions.assertTrue;
            
            public class Test {
                void test() {
                    String a = "a";
                    String b = "b";
                    assertTrue(a.equals(b));
                }
            }
        """,
        after = """
            import static org.junit.jupiter.api.Assertions.assertEquals;
            
            public class Test {
                void test() {
                    String a = "a";
                    String b = "b";
                    assertEquals(a, b);
                }
            }
        """)

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/206")
    @Suppress("ConstantConditions", "SimplifiableAssertion")
    @Test
    fun preserveStyleOfStaticImportOrNot() = assertChanged(
        before = """
            import org.junit.jupiter.api.Assertions;
            
            public class Test {
                void test() {
                    String a = "a";
                    String b = "b";
                    Assertions.assertTrue(a.equals(b));
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.Assertions;
            
            public class Test {
                void test() {
                    String a = "a";
                    String b = "b";
                    Assertions.assertEquals(a, b);
                }
            }
        """)

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/238")
    @Test
    fun retainArraysEquals() = assertUnchanged(
        before = """
            import java.util.Arrays;
            import org.junit.jupiter.api.Assertions;
            
            public class Test {
                void test() {
                    int[] a = {1, 2, 3};
                    int[] b = {1, 2, 3};
                    Assertions.assertTrue(Arrays.equals(a, b));
                }
            }
        """)
}
