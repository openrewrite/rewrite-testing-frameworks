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
package org.openrewrite.java.testing.mockito

import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class CleanupMockitoImportsTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .classpath("mockito")
        .build()

    override val recipe: Recipe
        get() = CleanupMockitoImports()

    @Test
    fun removesUnusedMockitoImport() = assertChanged(
        before = """
            import org.mockito.Mock;
            import java.util.Arrays;
            
            public class A {}
        """,
        after = """
            import java.util.Arrays;
            
            public class A {}
        """
    )

    @Test
    fun leavesOtherImportsAlone() = assertUnchanged(
        before = """
            import java.util.Arrays;
            import java.util.Collections;
            import java.util.HashSet;
            import java.util.List;
            
            public class A {}
        """
    )

    @Test
    fun `do not remove static import when possibly associated with method invocation having a null type`() =
        assertUnchanged(
            before = """
            import static org.mockito.Mockito.when;

            class MyObjectTest {
              MyObject myObject;
            
              void test() {
                when(myObject.getSomeField()).thenReturn("testValue");
              }
            }
        """
        )

    @Test
    fun `do not remove static star import when possibly associated with method invocation having a null type`() =
        assertUnchanged(
            before = """
            import static org.mockito.Mockito.*;

            class MyObjectTest {
              MyObject myObject;
            
              void test() {
                when(myObject.getSomeField()).thenReturn("testValue");
              }
            }
        """
        )

    @Test
    fun `remove unused mockito static import`() = assertChanged(
        dependsOn = arrayOf(
            """
            class MyObject {
                String getSomeField(){return null;}
            }
        """
        ),
        before = """
            import static org.mockito.Mockito.when;
            import static org.mockito.Mockito.after;
            import org.junit.jupiter.api.Test;
            import org.mockito.Mock;

            class MyObjectTest {
              @Mock
              MyObject myObject;
            
              void test() {
                when(myObject.getSomeField()).thenReturn("testValue");
              }
            }
        """,
        after = """
            import static org.mockito.Mockito.when;
            import org.junit.jupiter.api.Test;
            import org.mockito.Mock;

            class MyObjectTest {
              @Mock
              MyObject myObject;
            
              void test() {
                when(myObject.getSomeField()).thenReturn("testValue");
              }
            }
        """
    )

    @Test
    fun `preserve star imports`() = assertUnchanged(
        before = """
            package mockito.example;
            
            import java.util.List;
            
            import static org.mockito.Mockito.*;
            
            public class MockitoArgumentMatchersTest {
                static class Foo {
                    boolean bool(String str, int i, Object obj) { return false; }
                }
            
                public void usesMatchers() {
                    Foo mockFoo = mock(Foo.class);
                    when(mockFoo.bool(anyString(), anyInt(), any(Object.class))).thenReturn(true);
                }
            }
        """
    )

    @Test
    fun `remove unused star import`() = assertChanged(
        before = """
            import static org.mockito.Mockito.*;
            
            public class MockitoArgumentMatchersTest {
            }
        """,
        after = """
            public class MockitoArgumentMatchersTest {
            }
        """
    )
}


