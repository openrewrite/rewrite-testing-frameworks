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
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class LifecycleNonPrivateTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .classpath("junit")
        .build()

    override val recipe: Recipe
        get() = LifecycleNonPrivate()

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/241")
    fun beforeEachPrivate() = assertChanged(
        before = """
            import org.junit.jupiter.api.BeforeEach;
            
            class A {
                @BeforeEach
                private void beforeEach() {
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.BeforeEach;
            
            class A {
                @BeforeEach
                void beforeEach() {
                }
            }
        """
    )

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/241")
    fun beforeEachUnchanged() = assertUnchanged(
        before = """
            import org.junit.jupiter.api.BeforeEach;
            
            class A {
                @BeforeEach
                void beforeEach() {
                }
            }
        """
    )
}
