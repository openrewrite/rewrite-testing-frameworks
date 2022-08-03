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

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.openrewrite.Issue
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class UpdateBeforeAfterAnnotationsTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .classpath("junit")
        .build()

    override val recipe: Recipe
        get() = UpdateBeforeAfterAnnotations()

    @Test
    fun beforeToBeforeEach() = assertChanged(
        before = """
            import org.junit.Before;
            
            class Test {
            
                @Before
                void before() {
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.BeforeEach;
            
            class Test {
            
                @BeforeEach
                void before() {
                }
            }
        """
    )

    @Test
    fun afterToAfterEach() = assertChanged(
        before = """
            import org.junit.After;
            
            class Test {
            
                @After
                void after() {
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.AfterEach;
            
            class Test {
            
                @AfterEach
                void after() {
                }
            }
        """
    )

    @Test
    fun beforeClassToBeforeAll() = assertChanged(
        before = """
            import org.junit.BeforeClass;
            
            class Test {
            
                @BeforeClass
                void beforeClass() {
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.BeforeAll;
            
            class Test {
            
                @BeforeAll
                void beforeClass() {
                }
            }
        """
    )

    @Test
    fun afterClassToAfterAll() = assertChanged(
        before = """
            import org.junit.AfterClass;
            
            class Test {
            
                @AfterClass
                void afterClass() {
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.AfterAll;
            
            class Test {
            
                @AfterAll
                void afterClass() {
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite/issues/150")
    @Disabled
    @Test
    fun convertsToPackageVisibility() = assertChanged(
        before = """
            import org.junit.Before;
            
            class Test {
            
                @Before // comments
                public void before() {
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.BeforeEach;
            
            class Test {
            
                // comments
                @BeforeEach
                void before() {
                }
            }
        """
    )

    @Test
    @Disabled("Issue #59")
    fun testBeforeMethodOverridesPublicAbstract() = assertChanged(
        dependsOn = arrayOf("""
            public class AbstractTest {
                abstract public void setup();
            }
        """),
        before = """
            import org.junit.Before;
            
            public class A extends AbstractTest {
            
                @Before
                public void setup() {
                }
            } 
        """,
        after = """
            import org.junit.jupiter.api.BeforeEach;
            
            public class A extends AbstractTest {
            
                @BeforeEach
                public void setup() {
                }
            }
        """
    )
}