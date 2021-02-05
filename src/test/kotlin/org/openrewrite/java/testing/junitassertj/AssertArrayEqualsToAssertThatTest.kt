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
package org.openrewrite.java.testing.junitassertj

import org.junit.jupiter.api.Test
import org.openrewrite.Parser
import org.openrewrite.Recipe
import org.openrewrite.RecipeTest
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J

class AssertArrayEqualsToAssertThatTest : RecipeTest {
    override val parser: Parser<J.CompilationUnit> = JavaParser.fromJavaVersion()
            .classpath("junit", "assertj-core", "apiguardian-api")
            .build()

    override val recipe: Recipe
        get() = AssertArrayEqualsToAssertThat()

    @Test
    fun singleStaticMethodNoMessage() = assertChanged(
            before = """
                import org.junit.Test;

                import static org.junit.jupiter.api.Assertions.assertArrayEquals;

                public class A {
 
                    @Test
                    public void test() {
                        Integer[] expected = new Integer[] {1, 2, 3};
                        assertArrayEquals(expected, notification());
                    }
                    private Integer[] notification() {
                        return new Integer[] {1, 2, 3};        
                    }
                }
            """,
            after = """
                import org.junit.Test;

                import static org.assertj.core.api.Assertions.assertThat;

                public class A {

                    @Test
                    public void test() {
                        Integer[] expected = new Integer[] {1, 2, 3};
                        assertThat(notification()).containsExactly(expected);
                    }
                    private Integer[] notification() {
                        return new Integer[] {1, 2, 3};        
                    }
                }
            """
    )

    @Test
    fun singleStaticMethodWithMessageLambda() = assertChanged(
            before = """
                import org.junit.Test;

                import static org.junit.jupiter.api.Assertions.assertArrayEquals;

                public class A {
 
                    @Test
                    public void test() {
                        assertArrayEquals(new int[] {1, 2, 3}, notification(), () -> "These arrays should be equal");
                    }
                    private int[] notification() {
                        return new int[] {1, 2, 3};        
                    }
                }
            """,
            after = """
                import org.junit.Test;

                import static org.assertj.core.api.Assertions.assertThat;

                public class A {

                    @Test
                    public void test() {
                        assertThat(notification()).withFailMessage(() -> "These arrays should be equal").containsExactly(new int[] {1, 2, 3});
                    }
                    private int[] notification() {
                        return new int[] {1, 2, 3};        
                    }
                }
            """
    )

    @Test
    fun doublesWithinNoMessage() = assertChanged(
            before = """
                import org.junit.Test;

                import static org.junit.jupiter.api.Assertions.assertArrayEquals;

                public class A {
 
                    @Test
                    public void test() {
                        assertArrayEquals(new double[] {1.0d, 2.0d, 3.0d}, notification(), .2d);
                    }
                    private double[] notification() {
                        return new double[] {1.1d, 2.1d, 3.1d};
                    }
                }
            """,
            after = """
                import org.junit.Test;

                import static org.assertj.core.api.Assertions.assertThat;
                import static org.assertj.core.api.Assertions.within;

                public class A {

                    @Test
                    public void test() {
                        assertThat(notification()).containsExactly(new double[] {1.0d, 2.0d, 3.0d}, within(.2d));
                    }
                    private double[] notification() {
                        return new double[] {1.1d, 2.1d, 3.1d};
                    }
                }
            """
    )

    @Test
    fun doublesWithinAndWithMessage() = assertChanged(
            before = """
                import org.junit.Test;

                import static org.junit.jupiter.api.Assertions.assertArrayEquals;

                public class A {
 
                    @Test
                    public void test() {
                        assertArrayEquals(new double[] {1.0d, 2.0d, 3.0d}, notification(), .2d, "These should be close");
                    }
                    private double[] notification() {
                        return new double[] {1.1d, 2.1d, 3.1d};
                    }
                }
            """,
            after = """
                import org.junit.Test;

                import static org.assertj.core.api.Assertions.assertThat;
                import static org.assertj.core.api.Assertions.within;

                public class A {

                    @Test
                    public void test() {
                        assertThat(notification()).as("These should be close").containsExactly(new double[] {1.0d, 2.0d, 3.0d}, within(.2d));
                    }
                    private double[] notification() {
                        return new double[] {1.1d, 2.1d, 3.1d};
                    }
                }
            """
    )

    @Test
    fun doublesObjectsWithMessage() = assertChanged(
            before = """
                import org.junit.Test;

                import static org.junit.jupiter.api.Assertions.assertArrayEquals;

                public class A {
 
                    @Test
                    public void test() {
                        assertArrayEquals(new Double[] {1.0d, 2.0d, 3.0d}, notification(), "These arrays should be equal");
                    }
                    private Double[] notification() {
                        return new Double[] {1.0d, 2.0d, 3.0d};
                    }
                }
            """,
            after = """
                import org.junit.Test;

                import static org.assertj.core.api.Assertions.assertThat;

                public class A {

                    @Test
                    public void test() {
                        assertThat(notification()).as("These arrays should be equal").containsExactly(new Double[] {1.0d, 2.0d, 3.0d});
                    }
                    private Double[] notification() {
                        return new Double[] {1.0d, 2.0d, 3.0d};
                    }
                }
            """
    )

    @Test
    fun floatCloseToWithNoMessage() = assertChanged(
            before = """
                import org.junit.Test;

                import static org.junit.jupiter.api.Assertions.assertArrayEquals;

                public class A {
 
                    @Test
                    public void test() {
                        assertArrayEquals(new float[] {1.0f, 2.0f, 3.0f}, notification(), .2f);
                    }
                    private float[] notification() {
                        return new float[] {1.1f, 2.1f, 3.1f};
                    }
                }
            """,
            after = """
                import org.junit.Test;

                import static org.assertj.core.api.Assertions.assertThat;
                import static org.assertj.core.api.Assertions.within;

                public class A {

                    @Test
                    public void test() {
                        assertThat(notification()).containsExactly(new float[] {1.0f, 2.0f, 3.0f}, within(.2f));
                    }
                    private float[] notification() {
                        return new float[] {1.1f, 2.1f, 3.1f};
                    }
                }
            """
    )

    @Test
    fun floatCloseToWithMessage() = assertChanged(
            before = """
                import org.junit.Test;

                import static org.junit.jupiter.api.Assertions.assertArrayEquals;

                public class A {
 
                    @Test
                    public void test() {
                        assertArrayEquals(new float[] {1.0f, 2.0f, 3.0f}, notification(), .2f, () -> "These should be close");
                    }
                    private float[] notification() {
                        return new float[] {1.1f, 2.1f, 3.1f};
                    }
                }
            """,
            after = """
                import org.junit.Test;

                import static org.assertj.core.api.Assertions.assertThat;
                import static org.assertj.core.api.Assertions.within;

                public class A {

                    @Test
                    public void test() {
                        assertThat(notification()).withFailMessage(() -> "These should be close").containsExactly(new float[] {1.0f, 2.0f, 3.0f}, within(.2f));
                    }
                    private float[] notification() {
                        return new float[] {1.1f, 2.1f, 3.1f};
                    }
                }
            """
    )

    @Test
    fun fullyQualifiedMethodWithMessage() = assertChanged(
            before = """
                import org.junit.Test;

                public class A {
 
                    @Test
                    public void test() {
                        String[] expected = new String[] {"Fred", "Alice", "Mary"};
                        org.junit.jupiter.api.Assertions.assertArrayEquals(expected, notification(), () -> "These should be close");
                    }
                    private String[] notification() {
                        return new String[] {"Fred", "Alice", "Mary"};        
                    }
                }
            """,
            after = """
                import org.junit.Test;

                import static org.assertj.core.api.Assertions.assertThat;

                public class A {

                    @Test
                    public void test() {
                        String[] expected = new String[] {"Fred", "Alice", "Mary"};
                        assertThat(notification()).withFailMessage(() -> "These should be close").containsExactly(expected);
                    }
                    private String[] notification() {
                        return new String[] {"Fred", "Alice", "Mary"};        
                    }
                }
            """
    )
}