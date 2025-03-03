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
            .classpathFromResources(new InMemoryExecutionContext(), "mockito-core-3.12"))
          .recipe(new NoInitializationForInjectMock());
    }

    @Test
    @DocumentExample
    void removeInitializationOfInjectMocks() {
        //language=java
        rewriteRun(
          java(
            """
              class MyObject {
                  private String someField;

                  public MyObject(String someField) {
                      this.someField = someField;
                  }
              }
              """
          ),
          java(
            """
              import org.mockito.InjectMocks;

              class MyTest {
                  @InjectMocks
                  MyObject myObject = new MyObject("someField");
              }
              """,
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
    void removeInitializationOfInjectMocksWithFinal() {
        //language=java
        rewriteRun(
          java(
            """
              class MyObject {
                  private String someField;

                  public MyObject(String someField) {
                      this.someField = someField;
                  }
              }
              """
          ),
          java(
            """
              import org.mockito.InjectMocks;

              class MyTest {
                  @InjectMocks
                  final MyObject myObject = new MyObject("someField");
              }
              """,
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
}
