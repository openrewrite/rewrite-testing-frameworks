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
package org.openrewrite.java.testing.mockito;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

/**
 * Validates the recipes related to upgrading from Mockito 1 to Mockito 3
 */
@SuppressWarnings({"NotNullFieldNotInitialized", "NewClassNamingConvention"})
class JunitMockitoUpgradeIntegrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "mockito-all-1.10.19", "junit-4.13.2", "hamcrest-2.2", "junit-jupiter-api-5.9.+"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing.junit5")
            .build()
            .activateRecipes("org.openrewrite.java.testing.junit5.JUnit4to5Migration"));
    }

    /**
     * Replace org.mockito.MockitoAnnotations.Mock with org.mockito.Mock
     */
    @DocumentExample
    @Test
    void replaceMockAnnotation() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
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
            """
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
                  void usingAnnotationBasedMock() {
              
                      mockedList.add("one");
                      mockedList.clear();
              
                      verify(mockedList).add("one");
                      verify(mockedList).clear();
                  }
              }
              """
          )
        );
    }

    /**
     * Replaces org.mockito.Matchers with org.mockito.ArgumentMatchers
     */
    @Test
    void replacesMatchers() {
        //language=java
        rewriteRun(
          java(
            """
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
        );
    }

    /**
     * Mockito 1 used Matchers.anyVararg() to match the arguments to a variadic function.
     * Mockito 2+ uses Matchers.any() to match anything including the arguments to a variadic function.
     */
    @Test
    void replacesAnyVararg() {
        //language=java
        rewriteRun(
          java(
            """
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
            """
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
        );
    }

    /**
     * Mockito 1 has InvocationOnMock.getArgumentAt(int, Class)
     * Mockito 3 has InvocationOnMock.getArgument(int, Class)
     * swap 'em
     */
    @Test
    void replacesGetArgumentAt() {
        //language=java
        rewriteRun(
          java(
            """
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
            """
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
        );
    }

    @Test
    void removesRunWithJunit4() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.runner.RunWith;
              import org.junit.runners.JUnit4;
              
              @RunWith(JUnit4.class)
              public class Foo {
              }
              """,
            """
              public class Foo {
              }
              """
          )
        );
    }

    @Test
    void replacesMockitoJUnitRunner() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.runner.RunWith;
              import org.mockito.runners.MockitoJUnitRunner;

              @RunWith(MockitoJUnitRunner.class)
              public class ExampleTest {}
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              public class ExampleTest {}
              """
          )
        );
    }
}
