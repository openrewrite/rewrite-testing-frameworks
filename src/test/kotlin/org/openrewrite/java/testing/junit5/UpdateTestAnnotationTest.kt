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
import org.openrewrite.Parser
import org.openrewrite.RefactorVisitor
import org.openrewrite.RefactorVisitorTestForParser
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J

class UpdateTestAnnotationTest: RefactorVisitorTestForParser<J.CompilationUnit> {
    override val parser: Parser<J.CompilationUnit> = JavaParser.fromJavaVersion()
            .classpath("junit")
            .build()

    override val visitors: Iterable<RefactorVisitor<*>> = listOf(UpdateTestAnnotation())

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
                        assertThrows(IllegalArgumentException.class, () -> {
                            throw new IllegalArgumentException("boom");
                        });
                    }
                }
            """.trimIndent()
    )

    @Test
    fun assertThrowsSingleStatement() = assertRefactored(
            before = """
                import org.junit.Test;
                
                public class A {
                
                    @Test(expected = IndexOutOfBoundsException.class)
                    public void test() {
                        int arr = new int[]{}[0];
                    }
                }
            """,
            after = """
                import org.junit.jupiter.api.Test;
                
                import static org.junit.jupiter.api.Assertions.assertThrows;
                
                public class A {
                
                    @Test
                    public void test() {
                        assertThrows(IndexOutOfBoundsException.class, () -> {
                            int arr = new int[]{}[0];
                        });
                    }
                }
            """
    )

    @Test
    fun assertThrowsMultiLine() = assertRefactored(
            before = """
                import org.junit.Test;
                
                public class A {
                
                    @Test(expected = IllegalArgumentException.class)
                    public void test() {
                        String foo = "foo";
                        throw new IllegalArgumentException("boom");
                    }
                }
            """,
            after = """
                import org.junit.jupiter.api.Test;
                
                import static org.junit.jupiter.api.Assertions.assertThrows;
                
                public class A {
                
                    @Test
                    public void test() {
                        assertThrows(IllegalArgumentException.class, () -> {
                            String foo = "foo";
                            throw new IllegalArgumentException("boom");
                        });
                    }
                }
            """
    )

    @Test
    fun noTestAnnotationValues() = assertRefactored(
            before = """
                import org.junit.Test;
                
                public class A {
                
                    @Test
                    public void test() { }
                }
            """,
            after = """
                import org.junit.jupiter.api.Test;
                
                public class A {
                
                    @Test
                    public void test() { }
                }
            """
    )

    @Test
    fun testAnnotationWithTimeout() = assertRefactored(
            before = """
                import org.junit.Test;
                
                public class A {
                
                    @Test(timeout = 500)
                    public void test() { }
                }
            """,
            after = """
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.api.Timeout;
                
                public class A {
                
                    @Test
                    @Timeout(500)
                    public void test() { }
                }
            """
    )

    @Test
    fun testAnnotationWithTimeoutAndException() = assertRefactored(
            before = """
                import org.junit.Test;
                
                public class A {
                
                    @Test(expected = IllegalArgumentException.class, timeout = 500)
                    public void test() {
                        throw new IllegalArgumentException("boom");
                    }
                }
            """,
            after = """
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.api.Timeout;
                
                import static org.junit.jupiter.api.Assertions.assertThrows;
                
                public class A {
                
                    @Test
                    @Timeout(500)
                    public void test() {
                        assertThrows(IllegalArgumentException.class, () -> {
                            throw new IllegalArgumentException("boom");
                        });
                    }
                }
            """
    )

    @Test
    fun foo() {
        val cu = parser.parse("""
            import static org.junit.jupiter.api.Assertions.assertThrows;
            import org.junit.jupiter.api.*;
            
            class A {
                @Test
                public void foo2() {
                    assertThrows(IndexOutOfBoundsException.class, () -> {
                        int arr = new int[]{}[0];
                    });
                }
            }
        """).first()

        cu
    }
}
