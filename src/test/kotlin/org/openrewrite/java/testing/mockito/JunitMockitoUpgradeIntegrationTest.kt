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
package org.openrewrite.java.testing.mockito

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.openrewrite.InMemoryExecutionContext
import org.openrewrite.Recipe
import org.openrewrite.SourceFile
import org.openrewrite.config.Environment
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest
import org.openrewrite.maven.MavenParser

/**
 * Validates the recipes related to upgrading from Mockito 1 to Mockito 3
 */
class JunitMockitoUpgradeIntegrationTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .logCompilationWarningsAndErrors(true)
        .classpath("mockito-all", "junit", "hamcrest")
        .build()

    override val recipe: Recipe = Environment.builder()
        .scanRuntimeClasspath("org.openrewrite.java.testing.junit5")
        .build()
        .activateRecipes(
            "org.openrewrite.java.testing.junit5.JUnit4to5Migration",
            "org.openrewrite.java.testing.mockito.Mockito1to3Migration"
        )

    /**
     * Replace org.mockito.MockitoAnnotations.Mock with org.mockito.Mock
     */
    @Test
    fun replaceMockAnnotation() = assertChanged(
        before = """
            package org.openrewrite.java.testing.junit5;
            
            import org.junit.Before;
            import org.junit.Test;
            import org.mockito.Mock;
            import org.mockito.MockitoAnnotations;
            
            import java.util.List;
            
            import static org.mockito.Mockito.verify;
            
            public class MockitoTests {
                @Mock
                List<String> mockedList;
            
                @Before
                public void initMocks() {
                    MockitoAnnotations.initMocks(this);
                }
            
                @Test
                public void usingAnnotationBasedMock() {
            
                    mockedList.add("one");
                    mockedList.clear();
            
                    verify(mockedList).add("one");
                    verify(mockedList).clear();
                }
            }
        """,
        after = """
            package org.openrewrite.java.testing.junit5;
            
            import org.junit.jupiter.api.BeforeEach;
            import org.junit.jupiter.api.Test;
            import org.mockito.Mock;
            import org.mockito.MockitoAnnotations;
            
            import java.util.List;
            
            import static org.mockito.Mockito.verify;
            
            public class MockitoTests {
                @Mock
                List<String> mockedList;
            
                @BeforeEach
                public void initMocks() {
                    MockitoAnnotations.initMocks(this);
                }
            
                @Test
                public void usingAnnotationBasedMock() {
            
                    mockedList.add("one");
                    mockedList.clear();
            
                    verify(mockedList).add("one");
                    verify(mockedList).clear();
                }
            }
        """
    )

    /**
     * Replaces org.mockito.Matchers with org.mockito.ArgumentMatchers
     */
    @Test
    fun replacesMatchers() = assertUnchanged(
        before = """
            package mockito.example;
            
            import java.util.List;
            
            import static org.mockito.Mockito.*;
            
            public class MockitoArgumentMatchersTest {
                static class Foo {
                    boolean bool(String str, int i, Object obj) { return false; }
                    int in(boolean b, List<String> strs) { return 0; }
                    int bar(byte[] bytes, String[] s, int i) { return 0; }
                    boolean baz(String ... strings) { return true; }
                }
            
                public void usesMatchers() {
                    Foo mockFoo = mock(Foo.class);
                    when(mockFoo.bool(anyString(), anyInt(), any(Object.class))).thenReturn(true);
                    when(mockFoo.bool(eq("false"), anyInt(), any(Object.class))).thenReturn(false);
                    when(mockFoo.in(anyBoolean(), anyList())).thenReturn(10);
                }
            }
        """
    )

    /**
     * Mockito 1 used Matchers.anyVararg() to match the arguments to a variadic function.
     * Mockito 2+ uses Matchers.any() to match anything including the arguments to a variadic function.
     */
    @Test
    fun replacesAnyVararg() = assertChanged(
        before = """
            package mockito.example;

            import static org.mockito.Matchers.anyVararg;
            import static org.mockito.Mockito.mock;
            import static org.mockito.Mockito.when;
            
            public class MockitoVarargMatcherTest {
                public static class Foo {
                    public boolean acceptsVarargs(String ... strings) { return true; }
                }
                public void usesVarargMatcher() {
                    Foo mockFoo = mock(Foo.class);
                    when(mockFoo.acceptsVarargs(anyVararg())).thenReturn(true);
                }
            }
        """,
        after = """
            package mockito.example;

            import static org.mockito.ArgumentMatchers.any;
            import static org.mockito.Mockito.mock;
            import static org.mockito.Mockito.when;
            
            public class MockitoVarargMatcherTest {
                public static class Foo {
                    public boolean acceptsVarargs(String ... strings) { return true; }
                }
                public void usesVarargMatcher() {
                    Foo mockFoo = mock(Foo.class);
                    when(mockFoo.acceptsVarargs(any())).thenReturn(true);
                }
            }
        """
    )

    /**
     * Mockito 1 has InvocationOnMock.getArgumentAt(int, Class)
     * Mockito 3 has InvocationOnMock.getArgument(int, Class)
     * swap 'em
     */
    @Test
    fun replacesGetArgumentAt() = assertChanged(
        before = """
            package mockito.example;

            import org.junit.jupiter.api.Test;
            
            import static org.mockito.Matchers.any;
            import static org.mockito.Mockito.mock;
            import static org.mockito.Mockito.when;
            
            public class MockitoDoAnswer {
                @Test
                public void aTest() {
                    String foo = mock(String.class);
                    when(foo.concat(any())).then(invocation -> invocation.getArgumentAt(0, String.class));
                }
            }
        """,
        after = """
            package mockito.example;

            import org.junit.jupiter.api.Test;
            
            import static org.mockito.ArgumentMatchers.any;
            import static org.mockito.Mockito.mock;
            import static org.mockito.Mockito.when;
            
            public class MockitoDoAnswer {
                @Test
                public void aTest() {
                    String foo = mock(String.class);
                    when(foo.concat(any())).then(invocation -> invocation.getArgument(0, String.class));
                }
            }
        """
    )

    @Test
    fun removesRunWithJunit4() = assertChanged(
        before = """
            import org.junit.runner.RunWith;
            import org.junit.runners.JUnit4;
            
            @RunWith(JUnit4.class)
            public class Foo {
            }
        """,
        after = """
            public class Foo {
            }
        """
    )

    @Test
    fun replacesMockitoJUnitRunner() = assertChanged(
        before = """
            import org.junit.runner.RunWith;
            import org.mockito.runners.MockitoJUnitRunner;
            
            @RunWith(MockitoJUnitRunner.class)
            public class ExampleTest { }
        """,
        after = """
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.mockito.junit.jupiter.MockitoExtension;
            
            @ExtendWith(MockitoExtension.class)
            public class ExampleTest {}
        """
    )
}
