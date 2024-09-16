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
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-4.13",
              "junit-jupiter-api-5.9",
              "mockito-core-3.12",
              "mockito-junit-jupiter-3.12"
            ))
          .recipe(new MockitoWhenOnStaticToMockStatic());
    }

    @Test
    void shouldRefactorMockito_When() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.builder().methodInvocations(false).identifiers(false).build()),
          java(
            """
              package a.b;

              public class A {

                  public A() {
                  }

                  public static A getA() {
                      return new A();
                  }
              }
              """,
            SourceSpec::skip
          ),
          java(
            """
            import a.b.A;

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
            import a.b.A;
            import org.mockito.MockedStatic;

            import static org.junit.Assert.assertEquals;
            import static org.mockito.Mockito.*;

            class Test {

                private A aMock = mock(A.class);

                void test() {
                    try (MockedStatic<a.b.A> mockA = mockStatic(a.b.A.class)) {
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
