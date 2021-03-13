package org.openrewrite.java.testing.junit5

import org.junit.jupiter.api.Test
import org.openrewrite.Issue
import org.openrewrite.RecipeTest
import org.openrewrite.config.Environment
import org.openrewrite.java.JavaParser

class AssumeToAssumptionsTest: RecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .classpath("junit")
        .build()

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/54")
    @Test
    fun assumeToAssumptions() = assertChanged(
        recipe = Environment.builder()
            .scanClasspath(emptyList())
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
