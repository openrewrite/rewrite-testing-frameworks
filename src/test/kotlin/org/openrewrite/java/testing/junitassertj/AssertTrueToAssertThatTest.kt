package org.openrewrite.java.testing.junitassertj

import org.junit.jupiter.api.Test
import org.openrewrite.Parser
import org.openrewrite.RefactorVisitor
import org.openrewrite.RefactorVisitorTestForParser
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J

class AssertTrueToAssertThatTest: RefactorVisitorTestForParser<J.CompilationUnit> {
    override val parser: Parser<J.CompilationUnit> = JavaParser.fromJavaVersion()
            .classpath("junit", "assertj-core")
            .build()

    override val visitors: Iterable<RefactorVisitor<*>> = listOf(AssertTrueToAssertThat())

    @Test
    fun singleStaticMethodNoMessage() = assertRefactored(
            before = """
                import org.junit.Test;
                
                import static org.junit.jupiter.api.Assertions.assertTrue;
                
                public class A {
                
                    @Test
                    public void test() {
                        assertTrue(notification() != null && notification() > 0);
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
                        assertThat(notification() != null && notification() > 0).isTrue();
                    }
                    private Integer notification() {
                        return 1;
                    }
                }
            """
    )

    @Test
    fun singleStaticMethodWithMessageString() = assertRefactored(
            before = """
                import org.junit.Test;
                
                import static org.junit.jupiter.api.Assertions.*;
                
                public class A {
                
                    @Test
                    public void test() {
                        assertTrue(notification() != null && notification() > 0, "The notification should be positive");
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
                        assertThat(notification() != null && notification() > 0).withFailMessage("The notification should be positive").isTrue();
                    }
                    private Integer notification() {
                        return 1;
                    }
                }
            """
    )

    @Test
    fun singleStaticMethodWithMessageSupplier() = assertRefactored(
            before = """
                import org.junit.Test;
                
                import static org.junit.jupiter.api.Assertions.*;
                
                public class A {
                
                    @Test
                    public void test() {
                        assertTrue(notification() != null && notification() > 0, () -> "The notification should be positive");
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
                        assertThat(notification() != null && notification() > 0).withFailMessage(() -> "The notification should be positive").isTrue();
                    }
                    private Integer notification() {
                        return 1;
                    }
                }
            """
    )

    @Test
    fun inlineReference() = assertRefactored(
            before = """
                import org.junit.Test;
 
                public class A {
                
                    @Test
                    public void test() {
                        org.junit.jupiter.api.Assertions.assertTrue(notification() != null && notification() > 0);
                        org.junit.jupiter.api.Assertions.assertTrue(notification() != null && notification() > 0, "The notification should be positive");
                        org.junit.jupiter.api.Assertions.assertTrue(notification() != null && notification() > 0, () -> "The notification should be positive");
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
                        assertThat(notification() != null && notification() > 0).isTrue();
                        assertThat(notification() != null && notification() > 0).withFailMessage("The notification should be positive").isTrue();
                        assertThat(notification() != null && notification() > 0).withFailMessage(() -> "The notification should be positive").isTrue();
                    }
                    private Integer notification() {
                        return 1;
                    }
                }
            """
    )

    @Test
    fun mixedReferences() = assertRefactored(
            before = """
                import org.junit.Test;
                
                import static org.assertj.core.api.Assertions.*;
                import static org.junit.jupiter.api.Assertions.assertTrue;
                
                public class A {
                
                    @Test
                    public void test() {
                        assertTrue(notification() != null && notification() > 0);
                        org.junit.jupiter.api.Assertions.assertTrue(notification() != null && notification() > 0, "The notification should be positive");
                        assertTrue(notification() != null && notification() > 0, () -> "The notification should be positive");
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
                        assertThat(notification() != null && notification() > 0).isTrue();
                        assertThat(notification() != null && notification() > 0).withFailMessage("The notification should be positive").isTrue();
                        assertThat(notification() != null && notification() > 0).withFailMessage(() -> "The notification should be positive").isTrue();
                    }
                    private Integer notification() {
                        return 1;
                    }
                }
            """
    )

    @Test
    fun leaveBooleanSuppliersAlone() = assertRefactored(
            before = """
                import org.junit.Test;
                
                import static org.junit.jupiter.api.Assertions.assertTrue;
                
                public class A {
                
                    @Test
                    public void test() {
                        assertTrue(notification() != null && notification() > 0);
                        assertTrue(notification() != null && notification() > 0, "The notification should be positive");
                        assertTrue(notification() != null && notification() > 0, () -> "The notification should be positive");
                        assertTrue(() -> notification() != null && notification() > 0);
                        assertTrue(() -> notification() != null && notification() > 0, "The notification should be positive");
                        assertTrue(() -> notification() != null && notification() > 0, () -> "The notification should be positive");

                    }
                    private Integer notification() {
                        return 1;
                    }
                }
            """,
            after = """
                import org.junit.Test;
                
                import static org.assertj.core.api.Assertions.assertThat;
                import static org.junit.jupiter.api.Assertions.assertTrue;
                
                public class A {
                
                    @Test
                    public void test() {
                        assertThat(notification() != null && notification() > 0).isTrue();
                        assertThat(notification() != null && notification() > 0).withFailMessage("The notification should be positive").isTrue();
                        assertThat(notification() != null && notification() > 0).withFailMessage(() -> "The notification should be positive").isTrue();
                        assertTrue(() -> notification() != null && notification() > 0);
                        assertTrue(() -> notification() != null && notification() > 0, "The notification should be positive");
                        assertTrue(() -> notification() != null && notification() > 0, () -> "The notification should be positive");

                    }
                    private Integer notification() {
                        return 1;
                    }
                }
            """
    )

}