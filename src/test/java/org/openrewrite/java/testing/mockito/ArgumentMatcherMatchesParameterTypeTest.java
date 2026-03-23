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

class ArgumentMatcherMatchesParameterTypeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "mockito-core-3.12"))
          .recipe(new ArgumentMatcherMatchesParameterType());
    }

    @DocumentExample
    @Test
    void convertsAnonymousMatcherToLambdaWithCastRemoval() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.ArgumentMatcher;

              class MyTest {
                  ArgumentMatcher<String> matcher = new ArgumentMatcher<String>() {
                      @Override
                      public boolean matches(Object argument) {
                          return ((String) argument).startsWith("prefix");
                      }
                  };
              }
              """,
            """
              import org.mockito.ArgumentMatcher;

              class MyTest {
                  ArgumentMatcher<String> matcher = (ArgumentMatcher<String>) argument -> argument.startsWith("prefix");
              }
              """
          )
        );
    }

    @Test
    void convertsAnonymousMatcherToLambdaWithoutCast() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.ArgumentMatcher;

              class MyTest {
                  ArgumentMatcher<String> matcher = new ArgumentMatcher<String>() {
                      @Override
                      public boolean matches(Object argument) {
                          return true;
                      }
                  };
              }
              """,
            """
              import org.mockito.ArgumentMatcher;

              class MyTest {
                  ArgumentMatcher<String> matcher = (ArgumentMatcher<String>) argument -> true;
              }
              """
          )
        );
    }

    @Test
    void doesNotChangeWhenParameterAlreadyTyped() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.ArgumentMatcher;

              class MyTest {
                  ArgumentMatcher<String> matcher = new ArgumentMatcher<String>() {
                      @Override
                      public boolean matches(String argument) {
                          return argument.startsWith("prefix");
                      }
                  };
              }
              """
          )
        );
    }

    @Test
    void doesNotChangeWhenTypeParameterIsObject() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.ArgumentMatcher;

              class MyTest {
                  ArgumentMatcher<Object> matcher = new ArgumentMatcher<Object>() {
                      @Override
                      public boolean matches(Object argument) {
                          return argument != null;
                      }
                  };
              }
              """
          )
        );
    }

    @Test
    void convertsToLambdaInsideArgThat() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.ArgumentMatcher;
              import static org.mockito.ArgumentMatchers.argThat;
              import static org.mockito.Mockito.mock;
              import static org.mockito.Mockito.verify;

              class MyTest {
                  interface Service {
                      void execute(String s);
                  }

                  void test() {
                      Service service = mock(Service.class);
                      verify(service).execute(argThat(new ArgumentMatcher<String>() {
                          @Override
                          public boolean matches(Object argument) {
                              return ((String) argument).startsWith("prefix");
                          }
                      }));
                  }
              }
              """,
            """
              import org.mockito.ArgumentMatcher;
              import static org.mockito.ArgumentMatchers.argThat;
              import static org.mockito.Mockito.mock;
              import static org.mockito.Mockito.verify;

              class MyTest {
                  interface Service {
                      void execute(String s);
                  }

                  void test() {
                      Service service = mock(Service.class);
                      verify(service).execute(argThat((ArgumentMatcher<String>) argument -> argument.startsWith("prefix")));
                  }
              }
              """
          )
        );
    }

    @Test
    void convertsToLambdaWithNonStringType() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              import org.mockito.ArgumentMatcher;

              class MyTest {
                  ArgumentMatcher<List<String>> matcher = new ArgumentMatcher<List<String>>() {
                      @Override
                      public boolean matches(Object argument) {
                          return ((List<String>) argument).size() > 0;
                      }
                  };
              }
              """,
            """
              import java.util.List;
              import org.mockito.ArgumentMatcher;

              class MyTest {
                  ArgumentMatcher<List<String>> matcher = (ArgumentMatcher<List<String>>) argument -> argument.size() > 0;
              }
              """
          )
        );
    }

    @Test
    void convertsToLambdaWithDiamondOperator() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.ArgumentMatcher;

              class MyTest {
                  ArgumentMatcher<String> matcher = new ArgumentMatcher<>() {
                      @Override
                      public boolean matches(Object argument) {
                          return ((String) argument).startsWith("prefix");
                      }
                  };
              }
              """,
            """
              import org.mockito.ArgumentMatcher;

              class MyTest {
                  ArgumentMatcher<String> matcher = (ArgumentMatcher<String>) argument -> argument.startsWith("prefix");
              }
              """
          )
        );
    }

    @Test
    void convertsToBlockLambdaForMultipleStatements() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.ArgumentMatcher;

              class MyTest {
                  ArgumentMatcher<String> matcher = new ArgumentMatcher<String>() {
                      @Override
                      public boolean matches(Object argument) {
                          String s = (String) argument;
                          return ((String) argument).length() > 0 && s.startsWith("prefix");
                      }
                  };
              }
              """,
            """
              import org.mockito.ArgumentMatcher;

              class MyTest {
                  ArgumentMatcher<String> matcher = (ArgumentMatcher<String>) argument -> {
                          String s = argument;
                          return argument.length() > 0 && s.startsWith("prefix");
                      };
              }
              """
          )
        );
    }
}
