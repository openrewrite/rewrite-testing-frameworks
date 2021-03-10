package org.openrewrite.java.testing.junit5

import org.junit.jupiter.api.Test
import org.openrewrite.Parser
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class RunnerToExtensionTest : JavaRecipeTest {
    override val parser: Parser<*>?
        get() = JavaParser.fromJavaVersion().classpath("junit", "mockito-all").build()

    @Test
    fun mockito() = assertChanged(
        recipe = RunnerToExtension(
            listOf("org.mockito.runners.MockitoJUnitRunner"),
            "org.mockito.junit.jupiter.MockitoExtension"
        ),
        before = """
            import org.junit.runner.RunWith;
            import org.mockito.runners.MockitoJUnitRunner;
            
            @RunWith(MockitoJUnitRunner.class)
            public class Test {
            }
        """,
        after = """
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.mockito.junit.jupiter.MockitoExtension;
            
            @ExtendWith(MockitoExtension.class)
            public class Test {
            }
        """
    )
}
