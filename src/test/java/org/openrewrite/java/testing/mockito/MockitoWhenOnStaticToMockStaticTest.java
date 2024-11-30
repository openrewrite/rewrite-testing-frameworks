/*
 * Copyright 2024 the original author or authors.
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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class MockitoWhenOnStaticToMockStaticTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MockitoWhenOnStaticToMockStatic())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-4.13",
              "mockito-core-3.12",
              "mockito-junit-jupiter-3.12"
            ));
    }

    @DocumentExample
    @Test
    void shouldRefactorMockito_When() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().identifiers(false).build()),
          java(
            """
              package com.foo;
              public class A {
                  public static Integer getNumber() {
                      return 42;
                  }
              }
              """,
            SourceSpec::skip
          ),
          java(
            """
              import com.foo.A;

              import static org.junit.Assert.assertEquals;
              import static org.mockito.Mockito.*;

              class Test {
                  void test() {
                      when(A.getNumber()).thenReturn(-1);
                      assertEquals(A.getNumber(), -1);
                  }
              }
              """,
            """
              import com.foo.A;
              import org.mockito.MockedStatic;

              import static org.junit.Assert.assertEquals;
              import static org.mockito.Mockito.*;

              class Test {
                  void test() {
                      try (MockedStatic<A> mockA = mockStatic(A.class)) {
                          mockA.when(A.getNumber()).thenReturn(-1);
                          assertEquals(A.getNumber(), -1);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldHandleMultipleStaticMocks() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().identifiers(false).build()),
          java(
            """
              package com.foo;
              public class A {
                  public static Integer getNumber() {
                      return 42;
                  }
              }
              """,
            SourceSpec::skip
          ),
          java(
            """
              import com.foo.A;

              import static org.junit.Assert.assertEquals;
              import static org.mockito.Mockito.*;

              class Test {
                  void test() {
                      when(A.getNumber()).thenReturn(-1);
                      assertEquals(A.getNumber(), -1);

                      when(A.getNumber()).thenReturn(-2);
                      assertEquals(A.getNumber(), -2);
                  }
              }
              """,
            """
              import com.foo.A;
              import org.mockito.MockedStatic;

              import static org.junit.Assert.assertEquals;
              import static org.mockito.Mockito.*;

              class Test {
                  void test() {
                      try (MockedStatic<A> mockA = mockStatic(A.class)) {
                          mockA.when(A.getNumber()).thenReturn(-1);
                          assertEquals(A.getNumber(), -1);

                          try (MockedStatic<A> mockA1 = mockStatic(A.class)) {
                              mockA1.when(A.getNumber()).thenReturn(-2);
                              assertEquals(A.getNumber(), -2);
                          }
                      }
                  }
              }
              """
          )
        );
    }
}
