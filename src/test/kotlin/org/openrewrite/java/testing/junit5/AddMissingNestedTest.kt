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
import org.openrewrite.java.Assertions.java
import org.openrewrite.java.JavaParser
import org.openrewrite.test.RecipeSpec
import org.openrewrite.test.RewriteTest

class AddMissingNestedTest : RewriteTest {
    override fun defaults(spec: RecipeSpec) {
        spec.recipe(AddMissingNested())
            .parser(JavaParser.fromJavaVersion().classpath("junit").logCompilationWarningsAndErrors(true))
    }

    @Test
    fun `one inner class`() = rewriteRun(java(
        """
        import org.junit.jupiter.api.Test;
        
        public class RootTest {
            public class InnerTest {
                @Test
                public void test() {}
            }
        }
    """, """
        import org.junit.jupiter.api.Nested;
        import org.junit.jupiter.api.Test;
        
        public class RootTest {
            @Nested
            public class InnerTest {
                @Test
                public void test() {}
            }
        }
    """
    ))

    @Test
    fun `multiple inner classes`() = rewriteRun(java(
        """
        import org.junit.jupiter.api.Test;
        
        public class RootTest {
            public class InnerTest {
                @Test
                public void test() {}
            }
        
            public class Inner2Test {
                @Test
                public void test() {}
        
                public class InnermostTest {
                    @Test
                    public void test() {}
                }
            }
        }
    """, """
        import org.junit.jupiter.api.Nested;
        import org.junit.jupiter.api.Test;
        
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
    """
    ))

    @Test
    fun `does not annotate non-test inner class`() = rewriteRun(java(
        """
        import org.junit.jupiter.api.Test;
        
        public class RootTest {
            public class InnerTest {
                @Test
                public void test() {}
            }
            
            public static class Foo {
                public void bar() {}
            }
        }
    """, """
        import org.junit.jupiter.api.Nested;
        import org.junit.jupiter.api.Test;
        
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
    """
    ))

    @Test
    fun `removes static`() = rewriteRun(java(
        """
        import org.junit.jupiter.api.Test;
        
        public class RootTest {
            public static class InnerTest {
                @Test
                public void test() {}
            }
        }
    """, """
        import org.junit.jupiter.api.Nested;
        import org.junit.jupiter.api.Test;
        
        public class RootTest {
            @Nested
            public class InnerTest {
                @Test
                public void test() {}
            }
        }
    """
    ))
}
