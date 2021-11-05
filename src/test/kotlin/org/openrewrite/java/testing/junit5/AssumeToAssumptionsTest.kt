/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.testing.junit5

import org.junit.jupiter.api.Test
import org.openrewrite.Issue
import org.openrewrite.config.Environment
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class AssumeToAssumptionsTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .classpath("junit", "hamcrest")
        .build()

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/54")
    @Test
    fun assumeToAssumptions() = assertChanged(
        recipe = Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing.junit5")
            .build()
            .activateRecipes("org.openrewrite.java.testing.junit5.JUnit5BestPractices"),
        before = """
            import org.junit.Assume;
            
            class Test {
                void test() {
                    Assume.assumeTrue("One is one", true);
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.Assumptions;
            
            class Test {
                void test() {
                    Assumptions.assumeTrue(true, "One is one");
                }
            }
        """,
        cycles = 2
    )
}
