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
import org.openrewrite.java.testing.junit5.MockitoJUnitAddMockitoSettingsLenientStrictness;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MockitoJUnitAddMockitoSettingsLenientStrictnessTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "mockito-core", "mockito-junit-jupiter", "junit-jupiter-api"))
          .recipe(new MockitoJUnitAddMockitoSettingsLenientStrictness());
    }

    @Test
    @DocumentExample
    void shouldAddMockitoSettingsWithLenientStubbing() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.mockito.junit.jupiter.MockitoSettings;
              import org.mockito.quality.Strictness;

              @MockitoSettings(strictness = Strictness.LENIENT)
              class MyTest {
              }
              """
          )
        );
    }

    @Test
    @DocumentExample
    void shouldLeaveExisting() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.junit.jupiter.MockitoSettings;
              import org.mockito.quality.Strictness;

              @MockitoSettings(strictness = Strictness.STRICT_STUBS)
              class MyTest {
              }
              """
          )
        );
    }

}
