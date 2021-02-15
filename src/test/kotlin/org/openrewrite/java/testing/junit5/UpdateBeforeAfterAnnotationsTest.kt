package org.openrewrite.java.testing.junit5

import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.RecipeTest
import org.openrewrite.java.JavaParser

class UpdateBeforeAfterAnnotationsTest : RecipeTest {

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

    @Test
    fun convertsToPackageVisibility() = assertChanged(
            before = """
                import org.junit.Before;
                
                class Test {
                
                    @Before
                    public void before() {
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
}