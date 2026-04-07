/*
 * Copyright 2026 the original author or authors.
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

class ThenThrowCheckedExceptionToRuntimeExceptionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion()
                .classpathFromResources(new InMemoryExecutionContext(), "mockito-core")
                //language=java
                .dependsOn(
                  """
                    public class MyService {
                        public String execute(String a, String b) {
                            return "";
                        }
                        public String executeWithThrows(String a) throws java.io.IOException {
                            return "";
                        }
                    }
                    """
                ))
          .recipe(new ThenThrowCheckedExceptionToRuntimeException());
    }

    @DocumentExample
    @Test
    void replacesUndeclaredCheckedExceptionWithRuntimeException() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.mockito.Mockito.when;

              class MyTest {
                  void test(MyService service) {
                      when(service.execute("a", "b")).thenThrow(Exception.class);
                  }
              }
              """,
            """
              import static org.mockito.Mockito.when;

              class MyTest {
                  void test(MyService service) {
                      when(service.execute("a", "b")).thenThrow(RuntimeException.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotReplaceDeclaredCheckedException() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.mockito.Mockito.when;

              class MyTest {
                  void test(MyService service) {
                      when(service.executeWithThrows("a")).thenThrow(java.io.IOException.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotReplaceRuntimeException() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.mockito.Mockito.when;

              class MyTest {
                  void test(MyService service) {
                      when(service.execute("a", "b")).thenThrow(RuntimeException.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void replacesOnlyUndeclaredExceptionInMultipleArgs() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.mockito.Mockito.when;

              class MyTest {
                  void test(MyService service) {
                      when(service.execute("a", "b")).thenThrow(Exception.class, RuntimeException.class);
                  }
              }
              """,
            """
              import static org.mockito.Mockito.when;

              class MyTest {
                  void test(MyService service) {
                      when(service.execute("a", "b")).thenThrow(RuntimeException.class, RuntimeException.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void removesImportForReplacedCheckedException() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              import static org.mockito.Mockito.when;

              class MyTest {
                  void test(MyService service) {
                      when(service.execute("a", "b")).thenThrow(IOException.class);
                  }
              }
              """,
            """
              import static org.mockito.Mockito.when;

              class MyTest {
                  void test(MyService service) {
                      when(service.execute("a", "b")).thenThrow(RuntimeException.class);
                  }
              }
              """
          )
        );
    }
}
