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

class TestsShouldNotBePublicTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .classpath("junit-jupiter-api", "junit-jupiter-params")
        .build()

    override val recipe: Recipe
        get() = TestsShouldNotBePublic(null)

    @Test
    fun removePublicClassModifiers() = assertChanged(
            before = """
            import org.junit.jupiter.api.Nested;
            import org.junit.jupiter.api.Test;

            public class ATest {

                @Test
                void testMethod() {
                }

                @Nested
                public class NestedTestClass {
                
                    @Test
                    void anotherTestMethod() {
                    }
                }

                @Nested
                public class AnotherNestedTestClass {

                    private static final String CONSTANT = "foo";

                    private void setup() {
                    }

                    @Test
                    void anotherTestMethod() {
                    }
                }
            }
        """,
            after = """
            import org.junit.jupiter.api.Nested;
            import org.junit.jupiter.api.Test;

            class ATest {

                @Test
                void testMethod() {
                }

                @Nested
                class NestedTestClass {

                    @Test
                    void anotherTestMethod() {
                    }
                }

                @Nested
                class AnotherNestedTestClass {

                    private static final String CONSTANT = "foo";

                    private void setup() {
                    }

                    @Test
                    void anotherTestMethod() {
                    }
                }
            }
        """,
    )

    @Test
    fun ignorePublicAbstractClass() = assertUnchanged(
            before = """
            import org.junit.jupiter.api.Test;

            public abstract class ATest {

                @Test
                void testMethod() {
                }
            }
        """,
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
        """,
    )

    @Test
    fun ignorePublicClassWithPublicVariables() = assertUnchanged(
            before = """
            import org.junit.jupiter.api.Test;

            public class ATest {

                public int foo;

                @Test
                void testMethod() {
                }
            }
        """
    )

    @Test
    fun ignorePublicClassWithPublicMethodsThatAreNotRelatedToTests() = assertUnchanged(
            before = """
            import org.junit.jupiter.api.Test;

            public class ATest {

                public int foo() {
                    return 0;
                }

                @Test
                void testMethod() {
                }
            }
        """
    )

    @Test
    fun removePublicMethodModifiers() = assertChanged(
        before = """
            import java.util.Collections;
            import org.junit.jupiter.api.*;
            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.ValueSource;

            class ATest {

                @BeforeEach
                public void beforeEachMethod() {
                }

                @AfterEach
                public void afterEachMethod() {
                }

                @Test
                public void testMethod() {
                }

                @RepeatedTest(2)
                public void repeatedTestMethod() {
                }

                @ValueSource(strings = {"a", "b"})
                @ParameterizedTest
                public void parameterizedTestMethod(String input) {
                }

                @TestFactory
                public Collection<DynamicTest> testFactoryMethod() {
                    return null;
                }
            }
        """,
        after = """
            import java.util.Collections;
            import org.junit.jupiter.api.*;
            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.ValueSource;

            class ATest {

                @BeforeEach
                void beforeEachMethod() {
                }

                @AfterEach
                void afterEachMethod() {
                }

                @Test
                void testMethod() {
                }

                @RepeatedTest(2)
                void repeatedTestMethod() {
                }

                @ValueSource(strings = {"a", "b"})
                @ParameterizedTest
                void parameterizedTestMethod(String input) {
                }

                @TestFactory
                Collection<DynamicTest> testFactoryMethod() {
                    return null;
                }
            }
        """
    )

    @Test
    fun ignorePrivateMethodModifiers() = assertUnchanged(
        before = """
            import java.util.Collections;
            import org.junit.jupiter.api.*;
            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.ValueSource;

            class ATest {

                @BeforeEach
                private void beforeEachMethod() {
                }
                @AfterEach
                private void afterEachMethod() {
                }
                @Test
                private void testMethod() {
                }
                @RepeatedTest(2)
                private void repeatedTestMethod() {
                }
                @ValueSource(strings = {"a", "b"})
                @ParameterizedTest
                private void parameterizedTestMethod(String input) {
                }
                @TestFactory
                private Collection<DynamicTest> testFactoryMethod() {
                    return Collections.emptyList();
                }
            }
        """
    )

    @Test
    fun ignoreProtectedMethodModifiers() = assertUnchanged(
        before = """
            import java.util.Collections;
            import org.junit.jupiter.api.*;
            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.ValueSource;

            class ATest {

                @BeforeEach
                protected void beforeEachMethod() {
                }
                @AfterEach
                protected void afterEachMethod() {
                }
                @Test
                protected void testMethod() {
                }
                @RepeatedTest(2)
                protected void repeatedTestMethod() {
                }
                @ValueSource(strings = {"a", "b"})
                @ParameterizedTest
                protected void parameterizedTestMethod(String input) {
                }
                @TestFactory
                protected Collection<DynamicTest> testFactoryMethod() {
                    return Collections.emptyList();
                }
            }
        """
    )

    @Test
    fun removeProtectedMethodModifiers() = assertChanged(
        recipe = TestsShouldNotBePublic(true),
        before = """
            import java.util.Collections;
            import org.junit.jupiter.api.*;
            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.ValueSource;

            class ATest {

                @BeforeEach
                protected void beforeEachMethod() {
                }

                @AfterEach
                protected void afterEachMethod() {
                }

                @Test
                protected void testMethod() {
                }

                @RepeatedTest(2)
                protected void repeatedTestMethod() {
                }

                @ValueSource(strings = {"a", "b"})
                @ParameterizedTest
                protected void parameterizedTestMethod(String input) {
                }

                @TestFactory
                protected Collection<DynamicTest> testFactoryMethod() {
                    return null;
                }
            }
        """,
        after = """
            import java.util.Collections;
            import org.junit.jupiter.api.*;
            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.ValueSource;

            class ATest {

                @BeforeEach
                void beforeEachMethod() {
                }

                @AfterEach
                void afterEachMethod() {
                }

                @Test
                void testMethod() {
                }

                @RepeatedTest(2)
                void repeatedTestMethod() {
                }

                @ValueSource(strings = {"a", "b"})
                @ParameterizedTest
                void parameterizedTestMethod(String input) {
                }

                @TestFactory
                Collection<DynamicTest> testFactoryMethod() {
                    return null;
                }
            }
        """
    )
}
