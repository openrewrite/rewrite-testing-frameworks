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
package org.openrewrite.java.testing.cleanup

import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class RemoveTestPrefixTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .classpath("junit-jupiter-api")
        .build()

    override val recipe: Recipe
        get() = RemoveTestPrefix()

    @Test
    fun removeTestPrefixes() = assertChanged(
            before = """
            import org.junit.jupiter.api.Nested;
            import org.junit.jupiter.api.Test;

            class ATest {

                @Test
                void testMethod() {
                }

                @Nested
                class NestedTestClass {
                    @Test
                    void testAnotherTestMethod() {
                    }
                }

                @Nested
                class AnotherNestedTestClass {
                    @Test
                    void testYetAnotherTestMethod() {
                    }
                }
            }
        """,
            after = """
            import org.junit.jupiter.api.Nested;
            import org.junit.jupiter.api.Test;

            class ATest {

                @Test
                void method() {
                }

                @Nested
                class NestedTestClass {
                    @Test
                    void anotherTestMethod() {
                    }
                }

                @Nested
                class AnotherNestedTestClass {
                    @Test
                    void yetAnotherTestMethod() {
                    }
                }
            }
        """
    )

    @Test
    fun ignoreTooShortMethodName() = assertUnchanged(
            before = """
            import org.junit.jupiter.api.Test;

            class ATest {
                @Test
                void test() {
                }
            }
        """
    )

    @Test
    fun ignoreOverriddenMethod() = assertUnchanged(
        before = """
            import org.junit.jupiter.api.Test;

            abstract class AbstractTest {
                public abstract void testMethod();
            }
            
            class BTest extends AbstractTest {
                @Test
                @Override
                public void testMethod() {
                }
            }
        """
    )

    @Test
    fun ignoreInvalidName() = assertUnchanged(
            before = """
            import org.junit.jupiter.api.Test;

            class ATest {
                @Test
                void test1Method() {
                }
            }
        """
    )

    @Test
    fun ignoreKeyword() = assertUnchanged(
            before = """
            import org.junit.jupiter.api.Test;

            class ATest {
                @Test
                void testSwitch() {
                }
            }
        """
    )

    @Test
    fun ignoreNull() = assertUnchanged(
            before = """
            import org.junit.jupiter.api.Test;

            class ATest {
                @Test
                void testNull() {
                }
            }
        """
    )

    @Test
    fun ignoreUnderscore() = assertUnchanged(
            before = """
            import org.junit.jupiter.api.Test;

            class ATest {
                @Test
                void test_() {
                }
            }
        """
    )

    @Test
    fun ignoreNotAnnotatedMethods() = assertUnchanged(
            before = """
            class ATest {
                void testMethod() {
                }
            }
        """
    )
}
