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

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.openrewrite.Issue
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class UpdateTestAnnotationTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .classpath("junit")
        .build()

    override val recipe: Recipe
        get() = UpdateTestAnnotation()

    @Test
    fun assertThrowsSingleLine() = assertChanged(
        before = """
            import org.junit.Test;
            
            public class A {
            
                @Test(expected = IllegalArgumentException.class)
                public void test() {
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
                        throw new IllegalArgumentException("boom");
                    });
                }
            }
        """
    )

    @Test
    fun assertThrowsSingleStatement() = assertChanged(
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
    fun assertThrowsMultiLine() = assertChanged(
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
    fun noTestAnnotationValues() = assertChanged(
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
    fun junitStarImport() = assertChanged(
        before = """
            import org.junit.*;
            
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

    @Issue("https://github.com/openrewrite/rewrite/issues/150")
    @Disabled
    @Test
    fun testMethodModifierHasComments() = assertChanged(
        before = """
            import org.junit.Test;import org.openrewrite.Issue;
            
            public class A {
            
                // some comments
                @Issue("some issue")
                @Test
                public void test() { }
                
                // some comments
                @Test
                public void test1() { }
                
                @Test
                // some comments
                public void test2() { }
            }
        """,
        after = """
            import org.junit.jupiter.api.Test;import org.openrewrite.Issue;
            
            public class A {
            
                // some comments
                @Issue("some issue")
                @Test
                void test() {
                }
            
                // some comments
                @Test
                void test1() {
                }
            
                // some comments
                @Test
                void test2() {
                }
            }
        """
    )

    @Test
    fun testAnnotationWithTimeout() = assertChanged(
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
    fun testAnnotationWithImportedException() = assertChanged(
        dependsOn = arrayOf(
            """
            package com.abc;
            public class MyException extends Exception {
            }
        """.trimIndent()
        ),
        before = """
            import com.abc.MyException;
            import org.junit.Test;
            
            public class A {
            
                @Test(expected = MyException.class)
                public void test() {
                    throw new MyException("my exception");
                }
            }
        """,
        after = """
            import com.abc.MyException;
            import org.junit.jupiter.api.Test;
            
            import static org.junit.jupiter.api.Assertions.assertThrows;
            
            public class A {
            
                @Test
                public void test() {
                    assertThrows(MyException.class, () -> {
                        throw new MyException("my exception");
                    });
                }
            }
        """
    )

    @Test
    fun testAnnotationWithTimeoutAndException() = assertChanged(
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

    @Issue("https://github.com/openrewrite/rewrite/issues/150")
    @Disabled
    @Test
    fun protectedToPackageVisibility() = assertChanged(
        // An existing test method with protected visibility would not be executed by JUnit 4. This refactor will actual
        // fix this use case when moving to JUnit 5.
        before = """
            import org.junit.Test;

            public class A {
            
                @Test
                protected void test() {
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.Test;

            public class A {

                @Test
                void test() {
                }
            }
        """
    )

    @Disabled("test visibility changes require super class info")
    @Test
    fun privateToPackageVisibility() = assertChanged(
        // An existing test method with private visibility would not be executed by JUnit 4. This refactor will actual
        // fix this use case when moving to JUnit 5.
        before = """
            import org.junit.Test;
            
            public class A {
            
                @Test
                private void test() {
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.Test;
            
            public class A {
            
                @Test
                void test() {
                }
            }
        """
    )
}
