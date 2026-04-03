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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MockitoStubbingToLenientTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
                "mockito-core", "mockito-junit-jupiter", "junit-jupiter-api", "junit-4"))
          .recipe(new MockitoStubbingToLenient());
    }

    @DocumentExample
    @ParameterizedTest
    @CsvSource({
      "when,           'when(list.size()).thenReturn(1)'",
      "doReturn,       'doReturn(1).when(list).size()'",
      "doThrow,        'doThrow(RuntimeException.class).when(list).clear()'",
      "doAnswer,       'doAnswer(invocation -> 1).when(list).size()'",
      "doNothing,      'doNothing().when(list).clear()'",
      "doCallRealMethod, 'doCallRealMethod().when(list).clear()'"
    })
    void wrapsStubbingWithExtendWithMockitoExtension(String stubbingImport, String stubbingCall) {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import java.util.List;

              import static org.mockito.Mockito.%s;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  private List<String> list;

                  @Test
                  void test() {
                      %s;
                  }
              }
              """.formatted(stubbingImport, stubbingCall),
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import java.util.List;

              import static org.mockito.Mockito.lenient;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  private List<String> list;

                  @Test
                  void test() {
                      lenient().%s;
                  }
              }
              """.formatted(stubbingCall)
          )
        );
    }

    @Test
    void noChangeWithRunWithMockitoJUnitRunner() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;

              import java.util.List;

              import static org.mockito.Mockito.when;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  private List<String> list;

                  @Test
                  public void test() {
                      when(list.size()).thenReturn(1);
                  }
              }
              """
          )
        );
    }

    @Test
    void wrapsWhenWithRunWithStrictStubs() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;

              import java.util.List;

              import static org.mockito.Mockito.when;

              @RunWith(MockitoJUnitRunner.StrictStubs.class)
              class MyTest {
                  @Mock
                  private List<String> list;

                  @Test
                  public void test() {
                      when(list.size()).thenReturn(1);
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;

              import java.util.List;

              import static org.mockito.Mockito.lenient;

              @RunWith(MockitoJUnitRunner.StrictStubs.class)
              class MyTest {
                  @Mock
                  private List<String> list;

                  @Test
                  public void test() {
                      lenient().when(list.size()).thenReturn(1);
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWithRunWithStrictRunner() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;

              import java.util.List;

              import static org.mockito.Mockito.when;

              @RunWith(MockitoJUnitRunner.Strict.class)
              class MyTest {
                  @Mock
                  private List<String> list;

                  @Test
                  public void test() {
                      when(list.size()).thenReturn(1);
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenMockitoSettingsLenient() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.mockito.junit.jupiter.MockitoSettings;
              import org.mockito.quality.Strictness;

              import java.util.List;

              import static org.mockito.Mockito.when;

              @ExtendWith(MockitoExtension.class)
              @MockitoSettings(strictness = Strictness.LENIENT)
              class MyTest {
                  @Mock
                  private List<String> list;

                  @Test
                  void test() {
                      when(list.size()).thenReturn(1);
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenMockitoSettingsWarn() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.mockito.junit.jupiter.MockitoSettings;
              import org.mockito.quality.Strictness;

              import java.util.List;

              import static org.mockito.Mockito.when;

              @ExtendWith(MockitoExtension.class)
              @MockitoSettings(strictness = Strictness.WARN)
              class MyTest {
                  @Mock
                  private List<String> list;

                  @Test
                  void test() {
                      when(list.size()).thenReturn(1);
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenRunWithSilentRunner() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;

              import java.util.List;

              import static org.mockito.Mockito.when;

              @RunWith(MockitoJUnitRunner.Silent.class)
              class MyTest {
                  @Mock
                  private List<String> list;

                  @Test
                  public void test() {
                      when(list.size()).thenReturn(1);
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWithoutStrictStubbingContext() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;

              import java.util.List;

              import static org.mockito.Mockito.mock;
              import static org.mockito.Mockito.when;

              class MyTest {
                  @Test
                  void test() {
                      List<String> list = mock(List.class);
                      when(list.size()).thenReturn(1);
                  }
              }
              """
          )
        );
    }
}
