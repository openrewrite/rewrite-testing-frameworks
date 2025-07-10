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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class NoInitializationForInjectMockTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "mockito-core-3.12")
            .dependsOn(
              //language=java
              """
                interface MyObjectInterface {}
                class MyObject implements MyObjectInterface {
                    private String someField;

                    public MyObject(String someField) {
                        this.someField = someField;
                    }
                }
                class MyObjectSingleConstructor implements MyObjectInterface {
                    public MyObjectSingleConstructor() {}
                }
                class MyObjectMultipleConstructors implements MyObjectInterface {
                    public MyObjectMultipleConstructors() {}
                    public MyObjectMultipleConstructors(String someField) {}
                }
                """))
          .recipe(new NoInitializationForInjectMock());
    }

    @DocumentExample
    @Test
    void removeAnnotationFromInitializedField() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.InjectMocks;

              class MyTest {
                  @InjectMocks
                  MyObject myObject = new MyObject("someField");
              }
              """,
              """
              class MyTest {
                  MyObject myObject = new MyObject("someField");
              }
              """
          )
        );
    }

    @Test
    void removeAnnotationFromFinalField() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.InjectMocks;

              class MyTest {
                  @InjectMocks
                  final MyObject myObject = new MyObject("someField");
              }
              """,
            """
              class MyTest {
                  final MyObject myObject = new MyObject("someField");
              }
              """
          )
        );
    }

    @Test
    void removeInitializerWhenDefaultConstructorAndOnlySingleConstructorExists() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.InjectMocks;

              class MyTest {
                  @InjectMocks
                  MyObjectSingleConstructor myObject = new MyObjectSingleConstructor();
              }
              """,
            """
              import org.mockito.InjectMocks;

              class MyTest {
                  @InjectMocks
                  MyObjectSingleConstructor myObject;
              }
              """
          )
        );
    }

    @Test
    void removeFinalModifier() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.InjectMocks;

              class MyTest {
                  @InjectMocks
                  final Object myObject = new Object();
              }
              """,
            """
              import org.mockito.InjectMocks;

              class MyTest {
                  @InjectMocks
                  Object myObject;
              }
              """
          )
        );
    }

    @Test
    void retainAnnotationOnNotInitializedField() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.InjectMocks;

              class MyTest {
                  @InjectMocks
                  MyObject myObject;
              }
              """
          )
        );
    }

    @Test
    void retainConstructorOnInitializedFieldWhenTypeNonEquivalence() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.InjectMocks;

              class MyTest {
                  @InjectMocks
                  MyObjectInterface myObject = new MyObjectSingleConstructor();
              }
              """,
            """
              class MyTest {
                  MyObjectInterface myObject = new MyObjectSingleConstructor();
              }
              """
          )
        );
    }

    @Test
    void retainNoArgsConstructorInitializerFieldWhenMultipleConstructorsExist() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.InjectMocks;

              class MyTest {
                  @InjectMocks
                  MyObjectMultipleConstructors myObject = new MyObjectMultipleConstructors();
                  @InjectMocks
                  MyObjectMultipleConstructors myObject2 = new MyObjectMultipleConstructors("someField");
              }
              """,
            """
              class MyTest {
                  MyObjectMultipleConstructors myObject = new MyObjectMultipleConstructors();
                  MyObjectMultipleConstructors myObject2 = new MyObjectMultipleConstructors("someField");
              }
              """
          )
        );
    }
}
