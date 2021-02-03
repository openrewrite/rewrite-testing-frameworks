/*
 * Copyright 2020 the original author or authors.
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
import org.openrewrite.RefactorVisitor
import org.openrewrite.RefactorVisitorTestForParser
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J
import org.openrewrite.loadVisitorsForTest

class JUnit5MigrationTest : RefactorVisitorTestForParser<J.CompilationUnit> {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
            .classpath("junit")
            .build()
    override val visitors = loadVisitorsForTest("org.openrewrite.java.testing.JUnit5Migration")

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

                    @BeforeEach
                    void initialize() {}
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

                    @AfterEach
                    void initialize() {}
                }
            """
    )

    @Test
    fun changeBeforeClassToBeforeAll() = assertRefactored(
            before = """
                import org.junit.BeforeClass;

                public class Example {
                    @BeforeClass
                    void initialize() {}
                }
            """,
            after = """
                import org.junit.jupiter.api.BeforeAll;

                public class Example {

                    @BeforeAll
                    void initialize() {}
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

                    @AfterAll
                    void initialize() {}
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
