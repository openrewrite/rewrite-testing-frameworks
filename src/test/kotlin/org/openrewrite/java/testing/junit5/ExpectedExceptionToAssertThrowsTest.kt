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
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class ExpectedExceptionToAssertThrowsTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .classpath("junit", "hamcrest")
        .build()

    override val recipe = ExpectedExceptionToAssertThrows()

    @Test
    fun expectClass() = assertChanged(
        before = """
            package org.openrewrite.java.testing.junit5;

            import org.junit.Rule;
            import org.junit.rules.ExpectedException;
            
            public class SimpleExpectedExceptionTest {
                @Rule
                public ExpectedException thrown = ExpectedException.none();
            
                public void throwsNothing() {
                    // no exception expected, none thrown: passes.
                }
            
                public void throwsExceptionWithSpecificType() {
                    thrown.expect(NullPointerException.class);
                    throw new NullPointerException();
                }
            }
        """,
        after = """
            package org.openrewrite.java.testing.junit5;
            
            import static org.junit.jupiter.api.Assertions.assertThrows;
            
            public class SimpleExpectedExceptionTest {
            
                public void throwsNothing() {
                    // no exception expected, none thrown: passes.
                }
            
                public void throwsExceptionWithSpecificType() {
                    assertThrows(NullPointerException.class, () -> {
                        throw new NullPointerException();
                    });
                }
            }
        """
    )

    @Test
    fun leavesOtherRulesAlone() = assertUnchanged(
        before = """
            import org.junit.Rule;
            import org.junit.rules.TemporaryFolder;
            
            class A {
            
                @Rule
                TemporaryFolder tempDir = new TemporaryFolder();
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/77")
    @Test
    fun handlesExpectMessage() = assertChanged(
        before = """
            package org.openrewrite.java.testing.junit5;
            
            import org.junit.Rule;
            import org.junit.rules.ExpectedException;
            
            public class SimpleExpectedExceptionTest {
                @Rule
                public ExpectedException thrown = ExpectedException.none();
            
                public void statementsBeforeExpected() {
                    int[] a = new int[] { 1 };
                    thrown.expect(IndexOutOfBoundsException.class);
                    thrown.expectMessage("Index 1 out of bounds for length 1");
                    int b = a[1];
                }
            }
        """,
        after = """
            package org.openrewrite.java.testing.junit5;
            
            import static org.junit.jupiter.api.Assertions.assertThrows;
            
            public class SimpleExpectedExceptionTest {
            
                public void statementsBeforeExpected() {
                    assertThrows(IndexOutOfBoundsException.class, () -> {
                        int[] a = new int[]{1};
                        int b = a[1];
                    }, "Index 1 out of bounds for length 1");
                }
            }
        """
    )

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/72")
    @Disabled
    fun handlesExpectMessageWithMatchers() = assertChanged(
        before = """
            package org.openrewrite.java.testing.junit5;
    
            import org.junit.Rule;
            import org.junit.rules.ExpectedException;
            import static org.hamcrest.Matchers.containsString;
            
            public class ExampleTests {
                @Rule
                public ExpectedException thrown = ExpectedException.none();
            
                public void test() {
                    this.thrown.expectMessage(containsString("no proper implementation found"));
                    throw new NullPointerException();
                }
            }
        """,
        after = """
            package org.openrewrite.java.testing.junit5;
            
            import static org.junit.jupiter.api.Assertions.assertThrows;
            import static org.junit.jupiter.api.Assertions.assertTrue;
            import static org.hamcrest.Matchers.containsString;
            
            public class ExampleTests {
                
                // something to this effect
                public void test() {
                    Exception exception = assertThrows(NullPointerException.class, () -> {
                        throw new NullPointerException();
                    });
                    
                    assertTrue(exception.getMessage(), containsString("no proper implementation found"));
                }
            }
        """
    )

}
