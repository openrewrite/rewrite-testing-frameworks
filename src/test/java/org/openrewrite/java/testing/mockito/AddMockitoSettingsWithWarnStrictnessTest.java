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
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AddMockitoSettingsWithWarnStrictnessTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "mockito-core-3.12", "mockito-junit-jupiter-3.12", "junit-jupiter-api-5"))
          .recipe(new AddMockitoSettingsWithWarnStrictness());
    }

    @DocumentExample
    @Test
    void addsMockitoSettingsWhenExtendWithPresent() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.InjectMocks;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import java.util.List;

              import static org.mockito.Mockito.when;

              @ExtendWith(MockitoExtension.class)
              class FooServiceTest {

                  @Mock
                  private List<String> mockList;

                  @Test
                  void testExecute() {
                      when(mockList.add("one")).thenReturn(true);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.InjectMocks;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.mockito.junit.jupiter.MockitoSettings;
              import org.mockito.quality.Strictness;

              import java.util.List;

              import static org.mockito.Mockito.when;

              @ExtendWith(MockitoExtension.class)
              @MockitoSettings(strictness = Strictness.WARN)
              class FooServiceTest {

                  @Mock
                  private List<String> mockList;

                  @Test
                  void testExecute() {
                      when(mockList.add("one")).thenReturn(true);
                  }
              }
              """
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"WARN", "LENIENT", "STRICT_STUBS"})
    void unchangedWhenMockitoSettingsAlreadyPresent(String strictness) {
        //language=java
        rewriteRun(
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
              @MockitoSettings(strictness = Strictness.%s)
              class FooServiceTest {

                  @Mock
                  private List<String> mockList;

                  @Test
                  void testExecute() {
                      when(mockList.add("one")).thenReturn(true);
                  }
              }
              """.formatted(strictness)
          )
        );
    }

    @Test
    void unchangedWhenNoExtendWith() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.mockito.Mock;

              import java.util.List;

              class FooServiceTest {

                  @Mock
                  private List<String> mockList;

                  @Test
                  void testExecute() {
                      mockList.add("one");
                  }
              }
              """
          )
        );
    }
}
