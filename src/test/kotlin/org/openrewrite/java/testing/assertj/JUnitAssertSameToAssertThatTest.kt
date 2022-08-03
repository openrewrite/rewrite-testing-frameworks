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
package org.openrewrite.java.testing.assertj

import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

@Suppress("NewClassNamingConvention", "ExcessiveLambdaUsage")
class JUnitAssertSameToAssertThatTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .classpath("junit-jupiter-api", "apiguardian-api")
        .build()

    override val recipe: Recipe
        get() = JUnitAssertSameToAssertThat()

    @Test
    fun singleStaticMethodNoMessage() = assertChanged(
        before = """
            import org.junit.jupiter.api.Test;
            
            import static org.junit.jupiter.api.Assertions.assertSame;

            public class A {

                @Test
                public void test() {
                    String str = "String";
                    assertSame(notification(), str);
                }
                private String notification() {
                    return "String";
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.Test;

            import static org.assertj.core.api.Assertions.assertThat;

            public class A {

                @Test
                public void test() {
                    String str = "String";
                    assertThat(str).isSameAs(notification());
                }
                private String notification() {
                    return "String";
                }
            }
        """
    )

    @Test
    fun singleStaticMethodWithMessageString() = assertChanged(
        before = """
            import org.junit.jupiter.api.Test;

            import static org.junit.jupiter.api.Assertions.assertSame;

            public class A {

                @Test
                public void test() {
                    String str = "string";
                    assertSame(notification(), str, "Should be the same");
                }
                private String notification() {
                    return "String";
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.Test;

            import static org.assertj.core.api.Assertions.assertThat;

            public class A {

                @Test
                public void test() {
                    String str = "string";
                    assertThat(str).as("Should be the same").isSameAs(notification());
                }
                private String notification() {
                    return "String";
                }
            }
        """,
        typeValidation =  { methodInvocations = false; }
    )

    @Test
    fun singleStaticMethodWithMessageSupplier() = assertChanged(
        before = """
            import org.junit.jupiter.api.Test;

            import static org.junit.jupiter.api.Assertions.assertSame;

            public class A {

                @Test
                public void test() {
                    String str = "string";
                    assertSame(notification(), str, () -> "Should be the same");
                }
                private String notification() {
                    return "String";
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.Test;

            import static org.assertj.core.api.Assertions.assertThat;

            public class A {

                @Test
                public void test() {
                    String str = "string";
                    assertThat(str).as(() -> "Should be the same").isSameAs(notification());
                }
                private String notification() {
                    return "String";
                }
            }
        """
    )

    @Test
    fun inlineReference() = assertChanged(
        before = """
            import org.junit.jupiter.api.Test;

            public class A {
            
                @Test
                public void test() {
                    String str = "string";
                    org.junit.jupiter.api.Assertions.assertSame(notification(), str);
                    org.junit.jupiter.api.Assertions.assertSame(notification(), str, "Should be the same");
                    org.junit.jupiter.api.Assertions.assertSame(notification(), str, () -> "Should be the same");
                }
                private String notification() {
                    return "String";
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.Test;
            
            import static org.assertj.core.api.Assertions.assertThat;
            
            public class A {
            
                @Test
                public void test() {
                    String str = "string";
                    assertThat(str).isSameAs(notification());
                    assertThat(str).as("Should be the same").isSameAs(notification());
                    assertThat(str).as(() -> "Should be the same").isSameAs(notification());
                }
                private String notification() {
                    return "String";
                }
            }
        """,
        typeValidation =  { methodInvocations = false; }
    )

    @Test
    fun mixedReferences() = assertChanged(
        before = """
            import org.junit.jupiter.api.Test;
            
            import static org.assertj.core.api.Assertions.*;
            import static org.junit.jupiter.api.Assertions.assertSame;
            
            public class A {
            
                @Test
                public void test() {
                    String str = "string";
                    assertSame(notification(), str);
                    org.junit.jupiter.api.Assertions.assertSame(notification(), str, "Should be the same");
                    assertSame(notification(), str, () -> "Should be the same");
                }
                private String notification() {
                    return "String";
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.Test;
            
            import static org.assertj.core.api.Assertions.*;
            
            public class A {
            
                @Test
                public void test() {
                    String str = "string";
                    assertThat(str).isSameAs(notification());
                    assertThat(str).as("Should be the same").isSameAs(notification());
                    assertThat(str).as(() -> "Should be the same").isSameAs(notification());
                }
                private String notification() {
                    return "String";
                }
            }
        """,
        typeValidation =  { methodInvocations = false; }
    )
}
