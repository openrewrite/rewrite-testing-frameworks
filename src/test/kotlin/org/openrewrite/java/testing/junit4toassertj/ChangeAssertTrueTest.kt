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
package org.openrewrite.java.testing.junit4toassertj

import org.junit.jupiter.api.Test
import org.openrewrite.Parser
import org.openrewrite.RefactorVisitor
import org.openrewrite.RefactorVisitorTestForParser
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J

class ChangeAssertTrueTest: RefactorVisitorTestForParser<J.CompilationUnit> {
    override val parser: Parser<J.CompilationUnit> = JavaParser.fromJavaVersion()
            .classpath("junit")
            .build()

    override val visitors: Iterable<RefactorVisitor<*>> = listOf(ChangeAssertTrue())

    @Test
    fun assertStaticMethod() = assertRefactored(
            before = """
                import org.junit.Test;

                import static org.junit.Assert.assertTrue;
                
                public class A {
                
                    @Test
                    public void test() {
                        assertTrue(notification() != null && notification() > 0);
                        assertTrue("This should be true.", notification() != null && notification() > 0);
                    }

                    private Integer notification() {
                        return 1;
                    }
                }
            """.trimIndent(),
            after = """
                import org.junit.Test;

                import static org.assertj.core.api.Assertions.assertThat;

                public class A {
                
                    @Test
                    public void test() {
                        assertThat(notification() != null && notification() > 0).isTrue();
                        assertThat(notification() != null && notification() > 0).as("This should be true.").isTrue();
                    }

                    private Integer notification() {
                        return 1;
                    }
                }
            """.trimIndent()
    )

    @Test
    fun inlineReference() = assertRefactored(
            before = """
                import org.junit.Test;
 
                public class A {
                
                    @Test
                    public void test() {
                        org.junit.Assert.assertTrue(notification() != null && notification() > 0);
                        org.junit.Assert.assertTrue("This should be true.", notification() != null && notification() > 0);
                    }

                    private Integer notification() {
                        return 1;
                    }
                }
            """.trimIndent(),
            after = """
                import org.junit.Test;

                import static org.assertj.core.api.Assertions.assertThat;

                public class A {
                
                    @Test
                    public void test() {
                        assertThat(notification() != null && notification() > 0).isTrue();
                        assertThat(notification() != null && notification() > 0).as("This should be true.").isTrue();
                    }

                    private Integer notification() {
                        return 1;
                    }
                }
            """.trimIndent()
    )

}
