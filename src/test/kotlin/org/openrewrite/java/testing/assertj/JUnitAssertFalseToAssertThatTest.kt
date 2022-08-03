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

@Suppress("NewClassNamingConvention", "ConstantConditions", "ExcessiveLambdaUsage")
class JUnitAssertFalseToAssertThatTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .classpath("junit", "apiguardian")
        .build()

    override val recipe: Recipe
        get() = JUnitAssertFalseToAssertThat()

    @Test
    fun singleStaticMethodNoMessage() = assertChanged(
        before = """
            import org.junit.Test;

            import static org.junit.jupiter.api.Assertions.assertFalse;

            public class A {

                @Test
                public void test() {
                    assertFalse(notification() != null && notification() > 0);
                }
                private Integer notification() {
                    return 1;
                }
            }
        """,
        after = """
            import org.junit.Test;

            import static org.assertj.core.api.Assertions.assertThat;

            public class A {

                @Test
                public void test() {
                    assertThat(notification() != null && notification() > 0).isFalse();
                }
                private Integer notification() {
                    return 1;
                }
            }
        """
    )

    @Test
    fun singleStaticMethodWithMessageString() = assertChanged(
        before = """
            import org.junit.Test;

            import static org.junit.jupiter.api.Assertions.*;

            public class A {

                @Test
                public void test() {
                    assertFalse(notification() != null && notification() > 0, "The notification should be negative");
                }
                private Integer notification() {
                    return 1;
                }
            }
        """,
        after = """
            import org.junit.Test;

            import static org.assertj.core.api.Assertions.assertThat;

            public class A {

                @Test
                public void test() {
                    assertThat(notification() != null && notification() > 0).as("The notification should be negative").isFalse();
                }
                private Integer notification() {
                    return 1;
                }
            }
        """,
        typeValidation =  { methodInvocations = false; }
    )

    @Test
    fun singleStaticMethodWithMessageSupplier() = assertChanged(
        before = """
            import org.junit.Test;

            import static org.junit.jupiter.api.Assertions.*;

            public class A {

                @Test
                public void test() {
                    assertFalse(notification() != null && notification() > 0, () -> "The notification should be negative");
                }
                private Integer notification() {
                    return 1;
                }
            }
        """,
        after = """
            import org.junit.Test;

            import static org.assertj.core.api.Assertions.assertThat;

            public class A {

                @Test
                public void test() {
                    assertThat(notification() != null && notification() > 0).as(() -> "The notification should be negative").isFalse();
                }
                private Integer notification() {
                    return 1;
                }
            }
        """,
        typeValidation =  { methodInvocations = false; }
    )

    @Test
    fun inlineReference() = assertChanged(
        before = """
            import org.junit.Test;

            public class A {

                @Test
                public void test() {
                    org.junit.jupiter.api.Assertions.assertFalse(notification() != null && notification() > 0);
                    org.junit.jupiter.api.Assertions.assertFalse(notification() != null && notification() > 0, "The notification should be negative");
                    org.junit.jupiter.api.Assertions.assertFalse(notification() != null && notification() > 0, () -> "The notification should be negative");
                }
                private Integer notification() {
                    return 1;
                }
            }
        """,
        after = """
            import org.junit.Test;

            import static org.assertj.core.api.Assertions.assertThat;

            public class A {
            
                @Test
                public void test() {
                    assertThat(notification() != null && notification() > 0).isFalse();
                    assertThat(notification() != null && notification() > 0).as("The notification should be negative").isFalse();
                    assertThat(notification() != null && notification() > 0).as(() -> "The notification should be negative").isFalse();
                }
                private Integer notification() {
                    return 1;
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
            import static org.junit.jupiter.api.Assertions.assertFalse;

            public class A {

                @Test
                public void test() {
                    assertFalse(notification() != null && notification() > 0);
                    org.junit.jupiter.api.Assertions.assertFalse(notification() != null && notification() > 0, "The notification should be negative");
                    assertFalse(notification() != null && notification() > 0, () -> "The notification should be negative");
                }
                private Integer notification() {
                    return 1;
                }
            }
        """,
        after = """
            import org.junit.Test;

            import static org.assertj.core.api.Assertions.*;

            public class A {

                @Test
                public void test() {
                    assertThat(notification() != null && notification() > 0).isFalse();
                    assertThat(notification() != null && notification() > 0).as("The notification should be negative").isFalse();
                    assertThat(notification() != null && notification() > 0).as(() -> "The notification should be negative").isFalse();
                }
                private Integer notification() {
                    return 1;
                }
            }
        """,
        typeValidation =  { methodInvocations = false; }
    )

    @Test
    fun leaveBooleanSuppliersAlone() = assertChanged(
        before = """
            import org.junit.Test;

            import static org.junit.jupiter.api.Assertions.assertFalse;

            public class A {

                @Test
                public void test() {
                    assertFalse(notification() != null && notification() > 0);
                    assertFalse(notification() != null && notification() > 0, "The notification should be negative");
                    assertFalse(notification() != null && notification() > 0, () -> "The notification should be negative");
                    assertFalse(() -> notification() != null && notification() > 0);
                    assertFalse(() -> notification() != null && notification() > 0, "The notification should be negative");
                    assertFalse(() -> notification() != null && notification() > 0, () -> "The notification should be negative");
                }
                private Integer notification() {
                    return 1;
                }
            }
        """,
        after = """
            import org.junit.Test;

            import static org.assertj.core.api.Assertions.assertThat;
            import static org.junit.jupiter.api.Assertions.assertFalse;

            public class A {

                @Test
                public void test() {
                    assertThat(notification() != null && notification() > 0).isFalse();
                    assertThat(notification() != null && notification() > 0).as("The notification should be negative").isFalse();
                    assertThat(notification() != null && notification() > 0).as(() -> "The notification should be negative").isFalse();
                    assertFalse(() -> notification() != null && notification() > 0);
                    assertFalse(() -> notification() != null && notification() > 0, "The notification should be negative");
                    assertFalse(() -> notification() != null && notification() > 0, () -> "The notification should be negative");
                }
                private Integer notification() {
                    return 1;
                }
            }
        """,
        typeValidation =  { methodInvocations = false; }
    )
}
