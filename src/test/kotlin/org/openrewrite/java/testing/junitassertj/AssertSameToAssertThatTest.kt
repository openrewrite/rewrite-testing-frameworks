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
import org.openrewrite.RefactorVisitor
import org.openrewrite.RefactorVisitorTestForParser
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J

class AssertSameToAssertThatTest : RefactorVisitorTestForParser<J.CompilationUnit> {
    override val parser: Parser<J.CompilationUnit> = JavaParser.fromJavaVersion()
        .classpath("junit-jupiter-api", "assertj-core", "apiguardian-api")
        .build()

    override val visitors: Iterable<RefactorVisitor<*>> = listOf(AssertSameToAssertThat())

    @Test
    fun singleStaticMethodNoMessage() = assertRefactored(
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
    fun singleStaticMethodWithMessageString() = assertRefactored(
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
            """
    )

    @Test
    fun singleStaticMethodWithMessageSupplier() = assertRefactored(
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
                        assertThat(str).withFailMessage(() -> "Should be the same").isSameAs(notification());
                    }
                    private String notification() {
                        return "String";
                    }
                }
            """
    )

    @Test
    fun inlineReference() = assertRefactored(
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
                        assertThat(str).withFailMessage(() -> "Should be the same").isSameAs(notification());
                    }
                    private String notification() {
                        return "String";
                    }
                }
            """
    )

    @Test
    fun mixedReferences() = assertRefactored(
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
                        assertThat(str).withFailMessage(() -> "Should be the same").isSameAs(notification());
                    }
                    private String notification() {
                        return "String";
                    }
                }
            """
    )
}
