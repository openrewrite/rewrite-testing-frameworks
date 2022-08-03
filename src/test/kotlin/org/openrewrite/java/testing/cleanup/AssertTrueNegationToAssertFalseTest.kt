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

class AssertTrueNegationToAssertFalseTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .logCompilationWarningsAndErrors(true)
        .classpath("junit-jupiter-api", "apiguardian")
        .build()

    override val recipe: Recipe
        get() = AssertTrueNegationToAssertFalse()

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/205")
    @Suppress("ConstantConditions", "SimplifiableAssertion")
    @Test
    fun assertTrueNegationToAssertFalse() = assertChanged(
        before = """
            import static org.junit.jupiter.api.Assertions.assertTrue;
            
            public class Test {
                void test() {
                    boolean a = false;
                    assertTrue(!a);
                }
            }
        """,
        after = """
            import static org.junit.jupiter.api.Assertions.assertFalse;
            
            public class Test {
                void test() {
                    boolean a = false;
                    assertFalse(a);
                }
            }
        """,
    )

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/205")
    @Suppress("ConstantConditions", "SimplifiableAssertion")
    @Test
    fun preserveStyleOfStaticImportOrNot() = assertChanged(
        before = """
            import org.junit.jupiter.api.Assertions;
            
            public class Test {
                void test() {
                    boolean a = false;
                    Assertions.assertTrue(!a);
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.Assertions;
            
            public class Test {
                void test() {
                    boolean a = false;
                    Assertions.assertFalse(a);
                }
            }
        """,
    )
}
