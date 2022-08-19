/*
 * Copyright 2021 the original author or authors.
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
import org.openrewrite.Issue
import org.openrewrite.Recipe
import org.openrewrite.config.Environment
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

@Suppress("SimplifiableAssertion", "ConstantConditions", "UnnecessaryLocalVariable")
class AssertToAssertionsTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .classpath("junit", "hamcrest")
        .build()

    override val recipe: Recipe
        get() = AssertToAssertions()

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/128")
    @Test
    fun dontSwitchAssertEqualsStringArguments() = assertChanged(
        dependsOn = arrayOf("""
            class Entity {
                String getField() {
                    return "b";
                }
            }
        """),
        before = """
            import static org.junit.Assert.assertEquals;
            
            class A {
                void foo() {
                    Entity entity = new Entity();
                    String hello = "a";
                    assertEquals(hello, entity.getField());
                }
            }
        """,
        after = """
            import static org.junit.jupiter.api.Assertions.assertEquals;
            
            class A {
                void foo() {
                    Entity entity = new Entity();
                    String hello = "a";
                    assertEquals(hello, entity.getField());
                }
            }
        """
    )

    @Test
    fun stringArgumentIsMethodInvocation() = assertChanged(
        before = """
            import org.junit.Test;

            import static org.junit.Assert.assertFalse;

            public class A {
                T t = new T();
                @Test
                public void test() {
                    assertFalse(t.getName(), A.class.isAssignableFrom(t.getClass()));
                }

                class T {
                    String getName() {
                        return "World";
                    }
                }
            }
        """,
        after = """
            import org.junit.Test;
            
            import static org.junit.jupiter.api.Assertions.assertFalse;

            public class A {
                T t = new T();
                @Test
                public void test() {
                    assertFalse(A.class.isAssignableFrom(t.getClass()), t.getName());
                }

                class T {
                    String getName() {
                        return "World";
                    }
                }
            }
        """
    )

    @Test
    fun lineBreakInArguments() = assertChanged(
        before = """
            import org.junit.Test;
            import static org.junit.Assert.assertFalse;

            public class A {
                @Test
                public void test() {
                    assertFalse("boom",
                            true);
                }
            }
        """,
        after = """
            import org.junit.Test;
            
            import static org.junit.jupiter.api.Assertions.assertFalse;

            public class A {
                @Test
                public void test() {
                    assertFalse(true,
                            "boom");
                }
            }
        """
    )

    @Test
    fun assertWithoutMessage() = assertChanged(
        before = """
            import org.junit.Assert;
            
            class A {
            
                void foo() {
                    Assert.assertEquals(1, 2);
                    Assert.assertArrayEquals(new int[]{}, new int[]{});
                    Assert.assertNotEquals(1, 2);
                    Assert.assertFalse(false);
                    Assert.assertTrue(true);
                    Assert.assertEquals("foo", "foo");
                    Assert.assertNull(null);
                    Assert.fail();
                    String value1 = "value1";
                    String value2 = value1;
                    Assert.assertEquals(value1, value2);
                    String value3 = "value3";
                    Assert.assertNotEquals(value1, value3);
                    Assert.assertSame(value1, value2);
                    Assert.assertNotSame(value1, value3);
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.Assertions;
            
            class A {
            
                void foo() {
                    Assertions.assertEquals(1, 2);
                    Assertions.assertArrayEquals(new int[]{}, new int[]{});
                    Assertions.assertNotEquals(1, 2);
                    Assertions.assertFalse(false);
                    Assertions.assertTrue(true);
                    Assertions.assertEquals("foo", "foo");
                    Assertions.assertNull(null);
                    Assertions.fail();
                    String value1 = "value1";
                    String value2 = value1;
                    Assertions.assertEquals(value1, value2);
                    String value3 = "value3";
                    Assertions.assertNotEquals(value1, value3);
                    Assertions.assertSame(value1, value2);
                    Assertions.assertNotSame(value1, value3);
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
                    assertEquals(1, 2);
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
                    assertEquals(1, 2);
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
                    String value = null;
                    Assert.assertNull("value is null", value);
                    value = "hello";
                    Assert.assertNotNull("value is not null", value);
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
                    String value = null;
                    Assertions.assertNull(value, "value is null");
                    value = "hello";
                    Assertions.assertNotNull(value, "value is not null");
                    Assertions.fail("fail");
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/58")
    @Test
    fun staticallyImportAssertions() = assertChanged(
        recipe = Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing.junit5")
            .build()
            .activateRecipes("org.openrewrite.java.testing.junit5.JUnit5BestPractices"),
        before = """
            import org.junit.Assert;
            
            class Test {
                void test() {
                    Assert.assertEquals("One is one", 1, 1);
                }
            }
        """,
        after = """
            import static org.junit.jupiter.api.Assertions.assertEquals;
            
            class Test {
                void test() {
                    assertEquals(1, 1, "One is one");
                }
            }
        """,
        cycles = 3
    )

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/56")
    @Test
    fun swapAssertTrueArgumentsWhenMessageIsBinaryExpression() = assertChanged(
        before = """
            import org.junit.Assert;
            
            class Test {
                void test() {
                    Assert.assertTrue("one" + "one", true);
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.Assertions;
            
            class Test {
                void test() {
                    Assertions.assertTrue(true, "one" + "one");
                }
            }
        """
    )

    @Issue("#76")
    @Test
    fun isJUnitAssertMethodChecksDeclaringType() = assertChanged(
        before = """
            import static org.junit.Assert.assertNotNull;
            class A {
                Long l = 1L;
                void testNestedPartitionStepStepReference() {
                    assertNotNull("message", l);
                }
            }
        """,
        after = """
            import static org.junit.jupiter.api.Assertions.assertNotNull;
            
            class A {
                Long l = 1L;
                void testNestedPartitionStepStepReference() {
                    assertNotNull(l, "message");
                }
            }
        """
    )

    @Test
    fun preservesWildcardImport() = assertChanged(
        before = """
            import static org.junit.Assert.*;
            
            class A {
                void test() {
                    assertNotNull(UnknownType.unknownMethod());
                }
            }
        """,
        after = """
            import static org.junit.jupiter.api.Assertions.*;
        
            class A {
                void test() {
                    assertNotNull(UnknownType.unknownMethod());
                }
            }
        """,
        typeValidation =  { methodInvocations = false; identifiers = false; }
    )
}
