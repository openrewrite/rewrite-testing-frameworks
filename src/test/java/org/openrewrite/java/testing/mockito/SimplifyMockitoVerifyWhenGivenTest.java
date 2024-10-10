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
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class SimplifyMockitoVerifyWhenGivenTest implements RewriteTest {

    @DocumentExample
    @Test
    void verify_Simple_ShouldUpdate() {
        rewriteRun(
          spec -> spec.recipe(new SimplifyMockitoVerifyWhenGiven())
            .typeValidationOptions(TypeValidation.builder().build()),
          //language=Java
          java(
            """
              import static org.mockito.Mockito.verify;
              import static org.mockito.Mockito.mock;
              import static org.mockito.ArgumentMatchers.eq;
              class Test {
                  public void test() {
                      final var mockString = mock(String.class);
                      verify(mockString).replace(eq("foo"), eq("bar"));
                  }
              }
              """,
            """
              import static org.mockito.Mockito.verify;
              import static org.mockito.Mockito.mock;
              import static org.mockito.ArgumentMatchers.eq;
              class Test {
                  public void test() {
                      final var mockString = mock(String.class);
                      verify(mockString).replace("foo","bar");
                  }
              }
              """
          )
        );
    }

    @Test
    void when_Simple_ShouldUpdate() {
        rewriteRun(
          spec -> spec.recipe(new SimplifyMockitoVerifyWhenGiven())
            .typeValidationOptions(TypeValidation.builder().build()),
          //language=Java
          java(
            """
              import static org.mockito.Mockito.mock;
              import static org.mockito.Mockito.when;
              import static org.mockito.ArgumentMatchers.eq;
              class Test {
                  public void test() {
                      final var mockString = mock(String.class);
                      when(mockString.replace(eq("foo"), eq("bar"))).thenReturn("bar");
                  }
              }
              """,
            """
              import static org.mockito.Mockito.mock;
              import static org.mockito.Mockito.when;
              import static org.mockito.ArgumentMatchers.eq;
              class Test {
                  public void test() {
                      final var mockString = mock(String.class);
                      when(mockString.replace("foo", "bar")).thenReturn("bar");
                  }
              }
              """
          )
        );
    }

    @Test
    void when_MoreComplexMatchers_ShouldNotUpdate() {
        rewriteRun(
          spec -> spec.recipe(new SimplifyMockitoVerifyWhenGiven())
            .typeValidationOptions(TypeValidation.builder().build()),
          //language=Java
          java(
            """
              import static org.mockito.Mockito.mock;
              import static org.mockito.Mockito.when;
              import static org.mockito.ArgumentMatchers.eq;
              import static org.mockito.ArgumentMatchers.anyString;
              class Test {
                  public void test() {
                      final var mockString = mock(String.class);
                      when(mockString.replace(eq("foo"), anyString())).thenReturn("bar");
                  }
              }
              """
          )
        );
    }
}
