package org.openrewrite.java.testing.junit5

import org.junit.jupiter.api.Test
import org.openrewrite.RefactorVisitor
import org.openrewrite.RefactorVisitorTestForParser
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J
import org.openrewrite.loadVisitors

class JUnit5MigrationTest : RefactorVisitorTestForParser<J.CompilationUnit> {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
            .classpath("junit")
            .build()
    override val visitors: Iterable<RefactorVisitor<*>> = loadVisitors("org.openrewrite.java.testing.junit5.migration")

    @Test
    fun changeBeforeToBeforeEach() = assertRefactored(
            before = """
                import org.junit.Before;

                public class Example {
                    @Before public void initialize() {}
                }
            """,
            after = """
                import org.junit.jupiter.api.BeforeEach;

                public class Example {
                    @BeforeEach public void initialize() {}
                }
            """
    )

    @Test
    fun changeAfterToAfterEach() = assertRefactored(
            before = """
                import org.junit.After;

                public class Example {
                    @After public void initialize() {}
                }
            """,
            after = """
                import org.junit.jupiter.api.AfterEach;

                public class Example {
                    @AfterEach public void initialize() {}
                }
            """
    )

    @Test
    fun changeBeforeClassToBeforeAll() = assertRefactored(
            before = """
                import org.junit.BeforeClass;

                public class Example {
                    @BeforeClass public void initialize() {}
                }
            """,
            after = """
                import org.junit.jupiter.api.BeforeAll;

                public class Example {
                    @BeforeAll public void initialize() {}
                }
            """
    )

    @Test
    fun changeAfterClassToAfterAll() = assertRefactored(
            before = """
                import org.junit.AfterClass;

                public class Example {
                    @AfterClass public void initialize() {}
                }
            """,
            after = """
                import org.junit.jupiter.api.AfterAll;

                public class Example {
                    @AfterAll public void initialize() {}
                }
            """
    )

    @Test
    fun changeIgnoreToDisabled() = assertRefactored(
            before = """
                import org.junit.Ignore;

                public class Example {
                    @Ignore @Test public void something() {}
                    
                    @Ignore("not ready yet") @Test public void somethingElse() {}
                }
            """,
            after = """
                import org.junit.jupiter.api.Disabled;

                public class Example {
                    @Disabled @Test public void something() {}
                    
                    @Disabled("not ready yet") @Test public void somethingElse() {}
                }
            """
    )
}
