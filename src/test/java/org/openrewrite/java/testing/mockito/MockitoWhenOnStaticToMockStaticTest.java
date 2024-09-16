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
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().methodInvocations(false).identifiers(false).build()),
          java(
            """
              package com.foo;
              public class A {
                  public static A getA() {
                      return new A();
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
              
                  private A aMock = mock(A.class);
              
                  void test() {
                      when(A.getA()).thenReturn(aMock);
                      assertEquals(A.getA(), aMock);
                  }
              }
              """,
            """
              import com.foo.A;
              import org.mockito.MockedStatic;
              
              import static org.junit.Assert.assertEquals;
              import static org.mockito.Mockito.*;
              
              class Test {
              
                  private A aMock = mock(A.class);
              
                  void test() {
                      try (MockedStatic<com.foo.A> mockA = mockStatic(com.foo.A.class)) {
                          mockA.when(A.getA()).thenReturn(aMock);
                          assertEquals(A.getA(), aMock);
                      }
                  }
              }
              """
          )
        );
    }
}
