/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SimplifyMockitoVerifyWhenGivenTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SimplifyMockitoVerifyWhenGiven())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "mockito-core"));
    }

    @DocumentExample
    @Test
    void shouldRemoveUnneccesaryEqFromVerify() {
        rewriteRun(
          //language=Java
          java(
            """
              import static org.mockito.Mockito.verify;
              import static org.mockito.Mockito.mock;
              import static org.mockito.ArgumentMatchers.eq;

              class Test {
                  void test() {
                      var mockString = mock(String.class);
                      verify(mockString).replace(eq("foo"), eq("bar"));
                  }
              }
              """,
                """
              import static org.mockito.Mockito.verify;
              import static org.mockito.Mockito.mock;

              class Test {
                  void test() {
                      var mockString = mock(String.class);
                      verify(mockString).replace("foo", "bar");
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/634")
    void shouldRemoveUnneccesaryEqFromVerify_withMockitoStarImport() {
        rewriteRun(
          //language=Java
          java(
            """
              import static org.mockito.Mockito.eq;
              import static org.mockito.Mockito.mock;
              import static org.mockito.Mockito.verify;

              class Test {
                  void test() {
                      var mockString = mock(String.class);
                      verify(mockString).replace(eq("foo"), eq("bar"));
                  }
              }
              """,
                """
              import static org.mockito.Mockito.mock;
              import static org.mockito.Mockito.verify;

              class Test {
                  void test() {
                      var mockString = mock(String.class);
                      verify(mockString).replace("foo", "bar");
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldRemoveUnneccesaryEqFromWhen() {
        rewriteRun(
          //language=Java
          java(
            """
              import static org.mockito.Mockito.mock;
              import static org.mockito.Mockito.when;
              import static org.mockito.ArgumentMatchers.eq;

              class Test {
                  void test() {
                      var mockString = mock(String.class);
                      when(mockString.replace(eq("foo"), eq("bar"))).thenReturn("bar");
                  }
              }
              """,
                """
              import static org.mockito.Mockito.mock;
              import static org.mockito.Mockito.when;

              class Test {
                  void test() {
                      var mockString = mock(String.class);
                      when(mockString.replace("foo", "bar")).thenReturn("bar");
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldNotRemoveEqWhenMatchersAreMixed() {
        rewriteRun(
          //language=Java
          java(
            """
              import static org.mockito.Mockito.mock;
              import static org.mockito.Mockito.when;
              import static org.mockito.ArgumentMatchers.eq;
              import static org.mockito.ArgumentMatchers.anyString;

              class Test {
                  void test() {
                      var mockString = mock(String.class);
                      when(mockString.replace(eq("foo"), anyString())).thenReturn("bar");
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldRemoveUnneccesaryEqFromStubber() {
        rewriteRun(
          //language=Java
          java(
            """
              import static org.mockito.Mockito.doThrow;
              import static org.mockito.ArgumentMatchers.eq;

              class Test {
                  void test() {
                      doThrow(new RuntimeException()).when("foo").substring(eq(1));
                  }
              }
              """,
                """
              import static org.mockito.Mockito.doThrow;

              class Test {
                  void test() {
                      doThrow(new RuntimeException()).when("foo").substring(1);
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldRemoveUnneccesaryEqFromBDDGiven() {
        rewriteRun(
          //language=Java
          java(
            """
              import static org.mockito.BDDMockito.given;
              import static org.mockito.ArgumentMatchers.eq;

              class Test {
                  void test() {
                      given("foo".substring(eq(1)));
                  }
              }
              """,
                """
              import static org.mockito.BDDMockito.given;

              class Test {
                  void test() {
                      given("foo".substring(1));
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldNotRemoveEqImportWhenStillNeeded() {
        rewriteRun(
          //language=Java
          java(
            """
              import static org.mockito.Mockito.mock;
              import static org.mockito.Mockito.when;
              import static org.mockito.ArgumentMatchers.eq;
              import static org.mockito.ArgumentMatchers.anyString;

              class Test {
                  void testRemoveEq() {
                      var mockString = mock(String.class);
                      when(mockString.replace(eq("foo"), eq("bar"))).thenReturn("bar");
                  }

                  void testKeepEq() {
                      var mockString = mock(String.class);
                      when(mockString.replace(eq("foo"), anyString())).thenReturn("bar");
                  }
              }
              """,
                """
              import static org.mockito.Mockito.mock;
              import static org.mockito.Mockito.when;
              import static org.mockito.ArgumentMatchers.eq;
              import static org.mockito.ArgumentMatchers.anyString;

              class Test {
                  void testRemoveEq() {
                      var mockString = mock(String.class);
                      when(mockString.replace("foo", "bar")).thenReturn("bar");
                  }

                  void testKeepEq() {
                      var mockString = mock(String.class);
                      when(mockString.replace(eq("foo"), anyString())).thenReturn("bar");
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldFixSonarExamples() {
        rewriteRun(
          //language=Java
          java(
            """
              import static org.mockito.Mockito.mock;
              import static org.mockito.Mockito.when;
              import static org.mockito.Mockito.verify;
              import static org.mockito.Mockito.doThrow;
              import static org.mockito.BDDMockito.given;
              import static org.mockito.ArgumentMatchers.eq;

              class Test {
                  void test(Object v1, Object v2, Object v3, Object v4, Object v5, Foo foo) {
                      given(foo.bar(eq(v1), eq(v2), eq(v3))).willReturn(null);
                      when(foo.baz(eq(v4), eq(v5))).thenReturn("foo");
                      doThrow(new RuntimeException()).when(foo).quux(eq(42));
                      verify(foo).bar(eq(v1), eq(v2), eq(v3));
                  }
              }

              class Foo {
                  Object bar(Object v1, Object v2, Object v3) { return null; }
                  String baz(Object v4, Object v5) { return  ""; }
                  void quux(int x) {}
              }
              """,
                """
              import static org.mockito.Mockito.mock;
              import static org.mockito.Mockito.when;
              import static org.mockito.Mockito.verify;
              import static org.mockito.Mockito.doThrow;
              import static org.mockito.BDDMockito.given;

              class Test {
                  void test(Object v1, Object v2, Object v3, Object v4, Object v5, Foo foo) {
                      given(foo.bar(v1, v2, v3)).willReturn(null);
                      when(foo.baz(v4, v5)).thenReturn("foo");
                      doThrow(new RuntimeException()).when(foo).quux(42);
                      verify(foo).bar(v1, v2, v3);
                  }
              }

              class Foo {
                  Object bar(Object v1, Object v2, Object v3) { return null; }
                  String baz(Object v4, Object v5) { return  ""; }
                  void quux(int x) {}
              }
              """
          )
        );
    }
}
