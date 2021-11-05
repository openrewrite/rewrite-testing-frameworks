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
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

@Suppress("JUnit3StyleTestMethodInJUnit4Class")
class MigrateJunitTestCaseTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .classpath("junit", "hamcrest")
        .build()

    override val recipe: Recipe
        get() = MigrateJUnitTestCase()

    @Test
    fun convertTestCase() = assertChanged(
        before = """
            import junit.framework.TestCase;
            
            public class MathTest extends TestCase {
                protected long value1;
                protected long value2;
            
                @Override
                protected void setUp() {
                    super.setUp();
                    value1 = 2;
                    value2 = 3;
                }
            
                public void testAdd() {
                    setName("primitive test");
                    long result = value1 + value2;
                    assertEquals(5, result);
                    fail("some Failure message");
                }
            
                @Override
                protected void tearDown() {
                    super.tearDown();
                    value1 = 0;
                    value2 = 0;
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.AfterEach;
            import org.junit.jupiter.api.BeforeEach;
            import org.junit.jupiter.api.Test;
            
            import static org.junit.jupiter.api.Assertions.*;

            public class MathTest {
                protected long value1;
                protected long value2;
            
                @BeforeEach
                public void setUp() {
                    value1 = 2;
                    value2 = 3;
                }
            
                @Test
                public void testAdd() {
                    //setName("primitive test");
                    long result = value1 + value2;
                    assertEquals(5, result);
                    fail("some Failure message");
                }
            
                @AfterEach
                public void tearDown() {
                    value1 = 0;
                    value2 = 0;
                }
            }
        """
    )

    @Test
    fun convertExtendedTestCase() = assertChanged(
        dependsOn = arrayOf(
            """
                package com.abc;
                import junit.framework.TestCase;
                public abstract class CTest extends TestCase {
                    @Override
                    public void setUp() {}
                    
                    @Override
                    public void tearDown() {}
                }
            """
        ),
        before = """
            package com.abc;
            import com.abc.CTest;
            import static org.junit.Assert.assertEquals;
            public class MathTest extends CTest {
                protected long value1;
                protected long value2;
            
                @Override
                protected void setUp() {
                    value1 = 2;
                    value2 = 3;
                }
            
                public void testAdd() {
                    long result = value1 + value2;
                    assertEquals(5, result);
                }
            
                @Override
                protected void tearDown() {
                    value1 = 0;
                    value2 = 0;
                }
            }
        """,
        after = """
            package com.abc;
            import com.abc.CTest;
            import org.junit.jupiter.api.AfterEach;
            import org.junit.jupiter.api.BeforeEach;
            import org.junit.jupiter.api.Test;
            
            import static org.junit.jupiter.api.Assertions.assertEquals;

            public class MathTest extends CTest {
                protected long value1;
                protected long value2;
            
                @BeforeEach
                public void setUp() {
                    value1 = 2;
                    value2 = 3;
                }
            
                @Test
                public void testAdd() {
                    long result = value1 + value2;
                    assertEquals(5, result);
                }
            
                @AfterEach
                public void tearDown() {
                    value1 = 0;
                    value2 = 0;
                }
            }
        """
    )

    @Test
    fun notTestCaseHasTestCaseAssertion() = assertChanged(
        before = """
            import org.junit.Test;
            
            import static junit.framework.TestCase.assertTrue;
            
            class AaTest {
                @Test
                public void someTest() {
                    assertTrue("assert message", isSameStuff("stuff"));
                }
                private boolean isSameStuff(String stuff) {
                    return "stuff".equals(stuff);
                }
            }
        """,
        after = """
            import org.junit.Test;
            
            import static org.junit.jupiter.api.Assertions.assertTrue;
            
            class AaTest {
                @Test
                public void someTest() {
                    assertTrue(isSameStuff("stuff"), "assert message");
                }
                private boolean isSameStuff(String stuff) {
                    return "stuff".equals(stuff);
                }
            }
        """
    )

    @Test
    fun notTestCase() = assertUnchanged(
        before = """
            import org.junit.Test;
            class AaTest {
                @Test(expected = NumberFormatException.class)
                public void testSomeNumberStuff() {
                    Double n = Double.valueOf("a");
                }
            }
        """
    )
}
