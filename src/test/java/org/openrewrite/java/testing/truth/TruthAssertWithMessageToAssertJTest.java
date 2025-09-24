/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.truth;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class TruthAssertWithMessageToAssertJTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new TruthAssertWithMessageToAssertJ())
                .parser(JavaParser.fromJavaVersion()
                        .classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3"));
    }

    @DocumentExample
    @Test
    void simpleMessage() {
        rewriteRun(
                //language=java
                java(
                        """
                                import static com.google.common.truth.Truth.assertWithMessage;

                                class Test {
                                    void test() {
                                        String actual = "hello";
                                        assertWithMessage("Expected greeting").that(actual).isEqualTo("hello");
                                    }
                                }
                                """,
                        """
                                import static org.assertj.core.api.Assertions.assertThat;

                                class Test {
                                    void test() {
                                        String actual = "hello";
                                        assertThat(actual).as("Expected greeting").isEqualTo("hello");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void formattedMessage() {
        rewriteRun(
                //language=java
                java(
                        """
                                import static com.google.common.truth.Truth.assertWithMessage;

                                class Test {
                                    void test() {
                                        int value = 42;
                                        assertWithMessage("Value %d is wrong", value).that(value).isEqualTo(42);
                                    }
                                }
                                """,
                        """
                                import static org.assertj.core.api.Assertions.assertThat;

                                class Test {
                                    void test() {
                                        int value = 42;
                                        assertThat(value).as(String.format("Value %d is wrong", value)).isEqualTo(42);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void multipleFormattedArguments() {
        rewriteRun(
                //language=java
                java(
                        """
                                import static com.google.common.truth.Truth.assertWithMessage;

                                class Test {
                                    void test() {
                                        String name = "John";
                                        int age = 30;
                                        assertWithMessage("Person %s with age %d", name, age).that(name).isNotNull();
                                    }
                                }
                                """,
                        """
                                import static org.assertj.core.api.Assertions.assertThat;

                                class Test {
                                    void test() {
                                        String name = "John";
                                        int age = 30;
                                        assertThat(name).as(String.format("Person %s with age %d", name, age)).isNotNull();
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void chainedAssertions() {
        rewriteRun(
                //language=java
                java(
                        """
                                import static com.google.common.truth.Truth.assertWithMessage;

                                class Test {
                                    void test() {
                                        String text = "hello world";
                                        assertWithMessage("Text validation").that(text)
                                                .contains("hello");
                                        assertWithMessage("Text length").that(text)
                                                .hasLength(11);
                                    }
                                }
                                """,
                        """
                                import static org.assertj.core.api.Assertions.assertThat;

                                class Test {
                                    void test() {
                                        String text = "hello world";
                                        assertThat(text).as("Text validation")
                                                .contains("hello");
                                        assertThat(text).as("Text length")
                                                .hasSize(11);
                                    }
                                }
                                """
                )
        );
    }
}
