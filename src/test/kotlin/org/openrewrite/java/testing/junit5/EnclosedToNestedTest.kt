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
package org.openrewrite.java.testing.junit5

import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaParser
import org.openrewrite.test.RecipeSpec
import org.openrewrite.test.RewriteTest

class EnclosedToNestedTest : RewriteTest {
    override fun defaults(spec: RecipeSpec) {
        spec.recipe(EnclosedToNested())
            .parser(JavaParser.fromJavaVersion().classpath("junit").logCompilationWarningsAndErrors(true).build())
    }

    @Test
    fun `one inner class`() = rewriteRun(java(
        """
        import org.junit.Test;
        import org.junit.experimental.runners.Enclosed;
        import org.junit.runner.RunWith;
        
        @RunWith(Enclosed.class)
        public class RootTest {
            public static class InnerTest {
                @Test
                public void test() {}
            }
        }
    """.trimIndent(), """
        import org.junit.Test;
        import org.junit.jupiter.api.Nested;
        
        
        
        public class RootTest {
            @Nested
            public class InnerTest {
                @Test
                public void test() {}
            }
        }
    """.trimIndent()
    ))

    @Test
    fun `multiple inner classes`() = rewriteRun(java(
        """
        import org.junit.Test;
        import org.junit.experimental.runners.Enclosed;
        import org.junit.runner.RunWith;
        
        @RunWith(Enclosed.class)
        public class RootTest {
            public static class InnerTest {
                @Test
                public void test() {}
            }
            
            public static class Inner2Test {
                @Test
                public void test() {}
        
                public static class InnermostTest {
                    @Test
                    public void test() {}
                }
            }
        }
    """.trimIndent(), """
        import org.junit.Test;
        import org.junit.jupiter.api.Nested;
        
        
        
        public class RootTest {
            @Nested
            public class InnerTest {
                @Test
                public void test() {}
            }
        
            @Nested
            public class Inner2Test {
                @Test
                public void test() {}
        
                @Nested
                public class InnermostTest {
                    @Test
                    public void test() {}
                }
            }
        }
    """.trimIndent()
    ))

    @Test
    fun `recognizes test annotation with arguments`() = rewriteRun(java(
        """
        import org.junit.Test;
        import org.junit.experimental.runners.Enclosed;
        import org.junit.runner.RunWith;
        
        @RunWith(Enclosed.class)
        public class RootTest {
            public static class InnerTest {
                @Test(timeout = 10)
                public void test() {}
            }
        }
    """.trimIndent(), """
        import org.junit.Test;
        import org.junit.jupiter.api.Nested;
        
        
        
        public class RootTest {
            @Nested
            public class InnerTest {
                @Test(timeout = 10)
                public void test() {}
            }
        }
    """.trimIndent()
    ))

    @Test
    fun `does not annotate non-test inner class`() = rewriteRun(java(
        """
        import org.junit.Test;
        import org.junit.experimental.runners.Enclosed;
        import org.junit.runner.RunWith;
        
        @RunWith(Enclosed.class)
        public class RootTest {
            public static class InnerTest {
                @Test
                public void test() {}
            }
            
            public static class Foo {
                public void bar() {}
            }
        }
    """.trimIndent(), """
        import org.junit.Test;
        import org.junit.jupiter.api.Nested;
        
        
        
        public class RootTest {
            @Nested
            public class InnerTest {
                @Test
                public void test() {}
            }
            
            public static class Foo {
                public void bar() {}
            }
        }
    """.trimIndent()
    ))
}