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
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

class CleanupMockitoImportsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "mockito-all-1.10"))
          .parser(KotlinParser.builder().classpath("mockito-core", "mockito-kotlin"))
          .recipe(new CleanupMockitoImports());
    }

    @DocumentExample
    @Test
    void removesUnusedMockitoImport() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.Mock;
              import java.util.Arrays;

              public class MyTest {}
              """,
            """
              import java.util.Arrays;

              public class MyTest {}
              """
          )
        );
    }

    @Test
    void leavesOtherImportsAlone() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Arrays;
              import java.util.Collections;
              import java.util.HashSet;
              import java.util.List;

              public class MyTest {}
              """
          )
        );
    }

    @Test
    void doNotRemoveImportsPossiblyAssociatedWithAnUntypedMockitoMethod() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.mockito.Mockito.when;
              import static org.mockito.BDDMockito.given;
              import static org.mockito.Mockito.verifyZeroInteractions;

              class MyObjectTest {
                MyObject myObject;
                MyMockClass myMock;

                void test() {
                  when(myObject.getSomeField()).thenReturn("testValue");
                  given(myObject.getSomeField()).willReturn("testValue");
                  verifyZeroInteractions(myMock);
                }
              }
              class MyObject {
                String getSomeField() { return null; }
              }
              class MyMockClass {
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveImportsAssociatedWithAnUntypedMockitoMethodMixed() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.Mockito;
              import static org.mockito.Mockito.when;
              import static org.mockito.BDDMockito.given;
              import static org.mockito.Mockito.verifyZeroInteractions;

              class MyObjectTest {
                MyObject myObject;
                MyMockClass myMock;

                void test() {
                  when(myObject.getSomeField()).thenReturn("testValue");
                  given(myObject.getSomeField()).willReturn("testValue");
                  verifyZeroInteractions(myMock);
                  Mockito.reset();
                }
              }
              class MyObject {
                String getSomeField() { return null; }
              }
              class MyMockClass {
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveImportsAssociatedWithATypedMockitoMethodMixed() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.Mockito;
              import static org.mockito.Mockito.when;
              import static org.mockito.BDDMockito.given;
              import static org.mockito.Mockito.verifyZeroInteractions;

              class MyObjectTest {
                MyObject myObject;
                MyMockClass myMock;

                void test() {
                  when(myObject.getSomeField()).thenReturn("testValue");
                  given(myObject.getSomeField()).willReturn("testValue");
                  verifyZeroInteractions(myMock);
                  Mockito.mock(OtherClass.class);
                }
              }
              class MyObject {
                String getSomeField() { return null; }
              }
              class MyMockClass {
              }
              class OtherClass {
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveStartImportsPossiblyAssociatedWithAnUntypedMockitoMethod() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.mockito.Mockito.when;

              class MyObjectTest {
                MyObject myObject;

                void test() {
                  when(myObject.getSomeField()).thenReturn("testValue");
                }
              }
              class MyObject {
                String getSomeField() { return null; }
              }
              """
          )
        );
    }

    @Test
    void removeUnusedMockitoStaticImport() {
        //language=java
        rewriteRun(
          java(
            """
              class MyObject {
                  String getSomeField(){return null;}
              }
              """
          ),
          java(
            """
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
            """
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
        );
    }

    @Test
    void preserveStarImports() {
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
                  }

                  public void usesMatchers() {
                      Foo mockFoo = mock(Foo.class);
                      when(mockFoo.bool(anyString(), anyInt(), any(Object.class))).thenReturn(true);
                  }
              }
              """
          )
        );
    }

    @Test
    void removeUnusedStarImports() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.mockito.Mockito.*;

              public class MockitoArgumentMatchersTest {
              }
              """,
            """
              public class MockitoArgumentMatchersTest {
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveMockitoImportsForKotlin() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              import org.mockito.Mock
              import org.mockito.kotlin.times
              class Foo {
                fun bar() {
                  org.mockito.Mockito.mock(Foo::class.java)
                }
              }
              """
          )
        );
    }
}
