package org.openrewrite.java.testing.junit5

import org.junit.jupiter.api.Test
import org.openrewrite.Parser
import org.openrewrite.RefactorVisitor
import org.openrewrite.RefactorVisitorTestForParser
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J

class ChangeTestAnnotationTest: RefactorVisitorTestForParser<J.CompilationUnit> {
    override val parser: Parser<J.CompilationUnit> = JavaParser.fromJavaVersion()
            .classpath("junit")
            .build()

    override val visitors: Iterable<RefactorVisitor<*>> = listOf(ChangeTestAnnotation())

    @Test
    fun assertThrowsSingleLine() = assertRefactored(
            before = """
                import org.junit.Test;
                
                public class A {
                    @Test(expected = IllegalArgumentException.class)
                    public void test() {
                        throw new IllegalArgumentException("boom");
                    }
                }
            """.trimIndent(),
            after = """
                import org.junit.jupiter.api.Test;
                
                import static org.junit.jupiter.api.Assertions.assertThrows;
                
                public class A {
                    @Test
                    public void test() {
                        assertThrows(IllegalArgumentException.class, () -> throw new IllegalArgumentException("boom"));
                    }
                }
            """.trimIndent()
    )
}
