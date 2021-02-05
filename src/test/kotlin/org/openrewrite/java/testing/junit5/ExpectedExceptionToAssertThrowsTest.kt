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

class ExpectedExceptionToAssertThrowsTest : RecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
            .classpath("junit", "hamcrest")
            .build()

    override val recipe: Recipe
        get() = ExpectedExceptionToAssertThrows()

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
}
