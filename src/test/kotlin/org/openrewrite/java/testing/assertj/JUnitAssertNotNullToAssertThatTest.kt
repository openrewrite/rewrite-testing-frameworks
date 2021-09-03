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

class JUnitAssertNotNullToAssertThatTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .classpath("junit")
        .build()

    override val recipe: Recipe
        get() = JUnitAssertNotNullToAssertThat()

    @Test
    fun singleStaticMethodNoMessage() = assertChanged(
        before = """
            import org.junit.Test;

            import static org.junit.jupiter.api.Assertions.assertNotNull;

            public class A {

                @Test
                public void test() {
                    assertNotNull(notification());
                }
                private String notification() {
                    return "";
                }
            }
        """,
        after = """
            import org.junit.Test;

            import static org.assertj.core.api.Assertions.assertThat;

            public class A {

                @Test
                public void test() {
                    assertThat(notification()).isNotNull();
                }
                private String notification() {
                    return "";
                }
            }
        """
    )

    @Test
    fun singleStaticMethodWithMessageString() = assertChanged(
        before = """
            import org.junit.Test;

            import static org.junit.jupiter.api.Assertions.assertNotNull;

            public class A {

                @Test
                public void test() {
                    assertNotNull(notification(), "Should not be null");
                }
                private String notification() {
                    return "";
                }
            }
        """,
        after = """
            import org.junit.Test;

            import static org.assertj.core.api.Assertions.assertThat;

            public class A {

                @Test
                public void test() {
                    assertThat(notification()).as("Should not be null").isNotNull();
                }
                private String notification() {
                    return "";
                }
            }
        """,
        typeValidation =  { methodInvocations = false; }
    )

    @Test
    fun singleStaticMethodWithMessageSupplier() = assertChanged(
        before = """
            import org.junit.Test;

            import static org.junit.jupiter.api.Assertions.assertNotNull;

            public class A {

                @Test
                public void test() {
                    assertNotNull(notification(), () -> "Should not be null");
                }
                private String notification() {
                    return "";
                }
            }
        """,
        after = """
            import org.junit.Test;

            import static org.assertj.core.api.Assertions.assertThat;

            public class A {

                @Test
                public void test() {
                    assertThat(notification()).withFailMessage(() -> "Should not be null").isNotNull();
                }
                private String notification() {
                    return "";
                }
            }
        """
    )

    @Test
    fun inlineReference() = assertChanged(
        before = """
            import org.junit.Test;

            public class A {
            
                @Test
                public void test() {
                    org.junit.jupiter.api.Assertions.assertNotNull(notification());
                    org.junit.jupiter.api.Assertions.assertNotNull(notification(), "Should not be null");
                    org.junit.jupiter.api.Assertions.assertNotNull(notification(), () -> "Should not be null");
                }
                private String notification() {
                    return "";
                }
            }
        """,
        after = """
            import org.junit.Test;
            
            import static org.assertj.core.api.Assertions.assertThat;
            
            public class A {
            
                @Test
                public void test() {
                    assertThat(notification()).isNotNull();
                    assertThat(notification()).as("Should not be null").isNotNull();
                    assertThat(notification()).withFailMessage(() -> "Should not be null").isNotNull();
                }
                private String notification() {
                    return "";
                }
            }
        """,
        typeValidation =  { methodInvocations = false; }
    )

    @Test
    fun mixedReferences() = assertChanged(
        before = """
            import org.junit.Test;
            
            import static org.assertj.core.api.Assertions.*;
            import static org.junit.jupiter.api.Assertions.assertNotNull;
            
            public class A {
            
                @Test
                public void test() {
                    assertNotNull(notification());
                    org.junit.jupiter.api.Assertions.assertNotNull(notification(), "Should not be null");
                    assertNotNull(notification(), () -> "Should not be null");
                }
                private String notification() {
                    return "";
                }
            }
        """,
        after = """
            import org.junit.Test;
            
            import static org.assertj.core.api.Assertions.*;
            
            public class A {
            
                @Test
                public void test() {
                    assertThat(notification()).isNotNull();
                    assertThat(notification()).as("Should not be null").isNotNull();
                    assertThat(notification()).withFailMessage(() -> "Should not be null").isNotNull();
                }
                private String notification() {
                    return "";
                }
            }
        """,
        typeValidation =  { methodInvocations = false; }
    )
}
