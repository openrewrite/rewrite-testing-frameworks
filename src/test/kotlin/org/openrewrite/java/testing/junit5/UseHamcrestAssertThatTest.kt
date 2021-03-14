package org.openrewrite.java.testing.junit5

import org.junit.Test
import org.openrewrite.config.Environment
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class UseHamcrestAssertThatTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .classpath("junit", "hamcrest-core")
        .build()

    override val recipe = Environment.builder()
        .scanClasspath(emptyList())
        .build()
        .activateRecipes("org.openrewrite.java.testing.junit5.UseHamcrestAssertThat")

    @Test
    fun assertAssertThatToHamcrestMatcherAssert() = assertChanged(
        before = """
            import static org.hamcrest.CoreMatchers.is;
            import static org.junit.Assert.assertThat;
            
            class Test {
                void test() {
                    assertThat(1 + 1, is(2));
                }
            }
        """,
        after = """
            import static org.hamcrest.CoreMatchers.is;
            import static org.hamcrest.MatcherAssert.assertThat;
            
            class Test {
                void test() {
                    assertThat(1 + 1, is(2));
                }
            }
        """
    )
}
