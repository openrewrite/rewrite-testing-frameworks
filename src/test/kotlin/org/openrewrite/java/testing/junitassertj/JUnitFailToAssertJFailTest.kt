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
import org.openrewrite.java.JavaRecipeTest
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J

class JUnitFailToAssertJFailTest : JavaRecipeTest {
    override val parser: Parser<J.CompilationUnit> = JavaParser.fromJavaVersion()
            .classpath("junit", "assertj-core", "apiguardian-api")
            .build()

    override val recipe: Recipe
        get() = JUnitFailToAssertJFail()

    @Test
    fun singleStaticMethodNoMessage() = assertChanged(
            before = """
                import org.junit.Test;

                import static org.junit.jupiter.api.Assertions.fail;

                public class A {
 
                    @Test
                    public void test() {
                        fail();
                    }
                }
            """,
            after = """
                import org.junit.Test;

                import static org.assertj.core.api.Assertions.fail;

                public class A {

                    @Test
                    public void test() {
                        fail("");
                    }
                }
            """
    )

    @Test
    fun singleStaticMethodWithMessage() = assertChanged(
            before = """
                import org.junit.Test;

                import static org.junit.jupiter.api.Assertions.fail;

                public class A {
 
                    @Test
                    public void test() {
                        fail("This should fail");
                    }
                }
            """,
            after = """
                import org.junit.Test;

                import static org.assertj.core.api.Assertions.fail;

                public class A {

                    @Test
                    public void test() {
                        fail("This should fail");
                    }
                }
            """
    )

    @Test
    fun singleStaticMethodWithMessageAndCause() = assertChanged(
            before = """
                import org.junit.Test;

                import static org.junit.jupiter.api.Assertions.fail;

                public class A {
 
                    @Test
                    public void test() {
                        Throwable t = new Throwable();
                        fail("This should fail", t);
                    }
                }
            """,
            after = """
                import org.junit.Test;

                import static org.assertj.core.api.Assertions.fail;

                public class A {

                    @Test
                    public void test() {
                        Throwable t = new Throwable();
                        fail("This should fail", t);
                    }
                }
            """
    )

    @Test
    fun singleStaticMethodWithCause() = assertChanged(
            before = """
                import org.junit.Test;

                import static org.junit.jupiter.api.Assertions.fail;

                public class A {
 
                    @Test
                    public void test() {
                        Throwable t = new Throwable();
                        fail(t);
                        fail(new Throwable());
                    }
                }
            """,
            after = """
                import org.junit.Test;

                import static org.assertj.core.api.Assertions.fail;

                public class A {

                    @Test
                    public void test() {
                        Throwable t = new Throwable();
                        fail("", t);
                        fail("", new Throwable());
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
                        org.junit.jupiter.api.Assertions.fail();
                        org.junit.jupiter.api.Assertions.fail("This should fail");
                        org.junit.jupiter.api.Assertions.fail("This should fail", new Throwable());
                        org.junit.jupiter.api.Assertions.fail(new Throwable());
                    }
                }
            """,
            after = """
                import org.junit.Test;

                import static org.assertj.core.api.Assertions.fail;
                
                public class A {
                
                    @Test
                    public void test() {
                        fail("");
                        fail("This should fail");
                        fail("This should fail", new Throwable());
                        fail("", new Throwable());
                    }
                }
            """
    )

    @Test
    fun mixedReferences() = assertChanged(
            before = """
                import org.junit.Test;
                
                import static org.junit.jupiter.api.Assertions.fail;
                
                public class A {
                
                    @Test
                    public void test() {
                        fail();
                        org.junit.jupiter.api.Assertions.fail("This should fail");
                        fail("This should fail", new Throwable());
                        org.junit.jupiter.api.Assertions.fail(new Throwable());
                    }
                }
            """,
            after = """
                import org.junit.Test;

                import static org.assertj.core.api.Assertions.fail;
                
                public class A {
                
                    @Test
                    public void test() {
                        fail("");
                        fail("This should fail");
                        fail("This should fail", new Throwable());
                        fail("", new Throwable());
                    }
                }
            """
    )
}
