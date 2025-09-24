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

class MigrateTruthToAssertJTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new org.openrewrite.Recipe() {
                    @Override
                    public String getDisplayName() {
                        return "Migrate Truth to AssertJ";
                    }

                    @Override
                    public String getDescription() {
                        return "Migrate Google Truth assertions to AssertJ.";
                    }
                })
                .parser(JavaParser.fromJavaVersion()
                        .classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3"));
    }

    @DocumentExample
    @Test
    void basicAssertThatConversion() {
        rewriteRun(
                spec -> spec.recipeFromResources("META-INF/rewrite/truth.yml", "org.openrewrite.java.testing.truth.MigrateTruthToAssertJ"),
                //language=java
                java(
                        """
                                import static com.google.common.truth.Truth.assertThat;

                                class Test {
                                    void test() {
                                        String actual = "hello";
                                        assertThat(actual).isEqualTo("hello");
                                        assertThat(actual).isNotEqualTo("world");
                                        assertThat(actual).isNotNull();
                                        assertThat(actual).contains("ell");
                                    }
                                }
                                """,
                        """
                                import static org.assertj.core.api.Assertions.assertThat;

                                class Test {
                                    void test() {
                                        String actual = "hello";
                                        assertThat(actual).isEqualTo("hello");
                                        assertThat(actual).isNotEqualTo("world");
                                        assertThat(actual).isNotNull();
                                        assertThat(actual).contains("ell");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void stringAssertions() {
        rewriteRun(
                spec -> spec.recipeFromResources("META-INF/rewrite/truth.yml", "org.openrewrite.java.testing.truth.MigrateTruthToAssertJ"),
                //language=java
                java(
                        """
                                import static com.google.common.truth.Truth.assertThat;

                                class Test {
                                    void test() {
                                        String str = "hello world";
                                        assertThat(str).containsMatch("h.*d");
                                        assertThat(str).doesNotContainMatch("foo");
                                        assertThat(str).hasLength(11);
                                        assertThat(str).startsWith("hello");
                                        assertThat(str).endsWith("world");
                                    }
                                }
                                """,
                        """
                                import static org.assertj.core.api.Assertions.assertThat;

                                class Test {
                                    void test() {
                                        String str = "hello world";
                                        assertThat(str).matches("h.*d");
                                        assertThat(str).doesNotMatch("foo");
                                        assertThat(str).hasSize(11);
                                        assertThat(str).startsWith("hello");
                                        assertThat(str).endsWith("world");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void comparableAssertions() {
        rewriteRun(
                spec -> spec.recipeFromResources("META-INF/rewrite/truth.yml", "org.openrewrite.java.testing.truth.MigrateTruthToAssertJ"),
                //language=java
                java(
                        """
                                import static com.google.common.truth.Truth.assertThat;

                                class Test {
                                    void test() {
                                        Integer value = 5;
                                        assertThat(value).isGreaterThan(3);
                                        assertThat(value).isLessThan(10);
                                        assertThat(value).isAtLeast(5);
                                        assertThat(value).isAtMost(5);
                                    }
                                }
                                """,
                        """
                                import static org.assertj.core.api.Assertions.assertThat;

                                class Test {
                                    void test() {
                                        Integer value = 5;
                                        assertThat(value).isGreaterThan(3);
                                        assertThat(value).isLessThan(10);
                                        assertThat(value).isGreaterThanOrEqualTo(5);
                                        assertThat(value).isLessThanOrEqualTo(5);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void objectAssertions() {
        rewriteRun(
                spec -> spec.recipeFromResources("META-INF/rewrite/truth.yml", "org.openrewrite.java.testing.truth.MigrateTruthToAssertJ"),
                //language=java
                java(
                        """
                                import static com.google.common.truth.Truth.assertThat;

                                class Test {
                                    void test() {
                                        Object obj1 = new Object();
                                        Object obj2 = obj1;
                                        Object obj3 = new Object();

                                        assertThat(obj1).isSameInstanceAs(obj2);
                                        assertThat(obj1).isNotSameInstanceAs(obj3);
                                        assertThat(obj1).isInstanceOf(Object.class);
                                    }
                                }
                                """,
                        """
                                import static org.assertj.core.api.Assertions.assertThat;

                                class Test {
                                    void test() {
                                        Object obj1 = new Object();
                                        Object obj2 = obj1;
                                        Object obj3 = new Object();

                                        assertThat(obj1).isSameAs(obj2);
                                        assertThat(obj1).isNotSameAs(obj3);
                                        assertThat(obj1).isInstanceOf(Object.class);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void collectionAssertions() {
        rewriteRun(
                spec -> spec.recipeFromResources("META-INF/rewrite/truth.yml", "org.openrewrite.java.testing.truth.MigrateTruthToAssertJ"),
                //language=java
                java(
                        """
                                import static com.google.common.truth.Truth.assertThat;
                                import java.util.List;
                                import java.util.Arrays;

                                class Test {
                                    void test() {
                                        List<String> list = Arrays.asList("a", "b", "c");
                                        assertThat(list).contains("a");
                                        assertThat(list).containsExactly("a", "b", "c");
                                        assertThat(list).containsExactlyElementsIn(Arrays.asList("a", "b", "c"));
                                        assertThat(list).containsAnyIn(Arrays.asList("a", "z"));
                                        assertThat(list).containsNoneOf("x", "y", "z");
                                        assertThat(list).hasSize(3);
                                        assertThat(list).isEmpty();
                                    }
                                }
                                """,
                        """
                                import static org.assertj.core.api.Assertions.assertThat;
                                import java.util.List;
                                import java.util.Arrays;

                                class Test {
                                    void test() {
                                        List<String> list = Arrays.asList("a", "b", "c");
                                        assertThat(list).contains("a");
                                        assertThat(list).containsExactly("a", "b", "c");
                                        assertThat(list).containsExactlyElementsOf(Arrays.asList("a", "b", "c"));
                                        assertThat(list).containsAnyElementsOf(Arrays.asList("a", "z"));
                                        assertThat(list).doesNotContain("x", "y", "z");
                                        assertThat(list).hasSize(3);
                                        assertThat(list).isEmpty();
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void optionalAssertions() {
        rewriteRun(
                spec -> spec.recipeFromResources("META-INF/rewrite/truth.yml", "org.openrewrite.java.testing.truth.MigrateTruthToAssertJ"),
                //language=java
                java(
                        """
                                import static com.google.common.truth.Truth.assertThat;
                                import java.util.Optional;

                                class Test {
                                    void test() {
                                        Optional<String> optional = Optional.of("value");
                                        assertThat(optional).isPresent();
                                        assertThat(optional).hasValue("value");

                                        Optional<String> empty = Optional.empty();
                                        assertThat(empty).isEmpty();
                                    }
                                }
                                """,
                        """
                                import static org.assertj.core.api.Assertions.assertThat;
                                import java.util.Optional;

                                class Test {
                                    void test() {
                                        Optional<String> optional = Optional.of("value");
                                        assertThat(optional).isPresent();
                                        assertThat(optional).contains("value");

                                        Optional<String> empty = Optional.empty();
                                        assertThat(empty).isEmpty();
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void mapAssertions() {
        rewriteRun(
                spec -> spec.recipeFromResources("META-INF/rewrite/truth.yml", "org.openrewrite.java.testing.truth.MigrateTruthToAssertJ"),
                //language=java
                java(
                        """
                                import static com.google.common.truth.Truth.assertThat;
                                import java.util.Map;
                                import java.util.HashMap;

                                class Test {
                                    void test() {
                                        Map<String, Integer> map = new HashMap<>();
                                        map.put("one", 1);
                                        map.put("two", 2);

                                        assertThat(map).containsKey("one");
                                        assertThat(map).containsEntry("one", 1);
                                        assertThat(map).doesNotContainKey("three");
                                        assertThat(map).hasSize(2);
                                    }
                                }
                                """,
                        """
                                import static org.assertj.core.api.Assertions.assertThat;
                                import java.util.Map;
                                import java.util.HashMap;

                                class Test {
                                    void test() {
                                        Map<String, Integer> map = new HashMap<>();
                                        map.put("one", 1);
                                        map.put("two", 2);

                                        assertThat(map).containsKey("one");
                                        assertThat(map).containsEntry("one", 1);
                                        assertThat(map).doesNotContainKey("three");
                                        assertThat(map).hasSize(2);
                                    }
                                }
                                """
                )
        );
    }
}
