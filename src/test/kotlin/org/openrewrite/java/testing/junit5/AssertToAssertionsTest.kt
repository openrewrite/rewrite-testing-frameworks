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
import org.openrewrite.Recipe
import org.openrewrite.RecipeTest
import org.openrewrite.java.JavaParser

class AssertToAssertionsTest : RecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
            .classpath("junit")
            .build()

    override val recipe: Recipe
        get() = AssertToAssertions()

    @Test
    fun assertWithoutMessage() = assertChanged(
            before = """
                import org.junit.Assert;
                
                class A {
                
                    void foo() {
                        Assert.assertEquals(1, 1);
                        Assert.assertArrayEquals(new int[]{}, new int[]{});
                        Assert.assertNotEquals(1, 2);
                        Assert.assertFalse(false);
                        Assert.assertTrue(true);
                        Assert.assertEquals("foo", "foo");
                        Assert.assertNull(null);
                        Assert.fail();
                    }
                }
            """,
            after = """
                import org.junit.jupiter.api.Assertions;
                
                class A {
                
                    void foo() {
                        Assertions.assertEquals(1, 1);
                        Assertions.assertArrayEquals(new int[]{}, new int[]{});
                        Assertions.assertNotEquals(1, 2);
                        Assertions.assertFalse(false);
                        Assertions.assertTrue(true);
                        Assertions.assertEquals("foo", "foo");
                        Assertions.assertNull(null);
                        Assertions.fail();
                    }
                }
            """
    )

    @Test
    fun staticAssertWithoutMessage() = assertChanged(
            before = """
                import static org.junit.Assert.*;
                
                class A {
                
                    void foo() {
                        assertEquals(1, 1);
                        assertArrayEquals(new int[]{}, new int[]{});
                        assertNotEquals(1, 2);
                        assertFalse(false);
                        assertTrue(true);
                        assertEquals("foo", "foo");
                        assertNull(null);
                        fail();
                    }
                }
            """,
            after = """
                import static org.junit.jupiter.api.Assertions.*;
                
                class A {
                
                    void foo() {
                        assertEquals(1, 1);
                        assertArrayEquals(new int[]{}, new int[]{});
                        assertNotEquals(1, 2);
                        assertFalse(false);
                        assertTrue(true);
                        assertEquals("foo", "foo");
                        assertNull(null);
                        fail();
                    }
                }
            """
    )

    @Test
    fun assertWithMessage() = assertChanged(
            before = """
                import org.junit.Assert;
                
                class A {
                
                    void foo() {
                        Assert.assertEquals("One is one", 1, 1);
                        Assert.assertArrayEquals("Empty is empty", new int[]{}, new int[]{});
                        Assert.assertNotEquals("one is not two", 1, 2);
                        Assert.assertFalse("false is false", false);
                        Assert.assertTrue("true is true", true);
                        Assert.assertEquals("foo is foo", "foo", "foo");
                        Assert.assertNull("null is null", null);
                        Assert.fail("fail");
                    }
                }
            """,
            after = """
                import org.junit.jupiter.api.Assertions;
                
                class A {
                
                    void foo() {
                        Assertions.assertEquals(1, 1, "One is one");
                        Assertions.assertArrayEquals(new int[]{}, new int[]{}, "Empty is empty");
                        Assertions.assertNotEquals(1, 2, "one is not two");
                        Assertions.assertFalse(false, "false is false");
                        Assertions.assertTrue(true, "true is true");
                        Assertions.assertEquals("foo", "foo", "foo is foo");
                        Assertions.assertNull(null, "null is null");
                        Assertions.fail("fail");
                    }
                }
            """
    )
}
