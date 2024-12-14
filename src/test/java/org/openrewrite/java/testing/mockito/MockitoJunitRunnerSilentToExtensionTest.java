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

class MockitoJunitRunnerSilentToExtensionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4.13", "mockito-core-3.12"))
          .recipe(new MockitoJUnitRunnerSilentToExtension());
    }

    @DocumentExample
    @Test
    void migrateMockitoRunnerSilentToExtension() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.runner.RunWith;
              import org.mockito.junit.MockitoJUnitRunner;
              
              @RunWith(MockitoJUnitRunner.Silent.class)
              public class ExternalAPIServiceTest {
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.mockito.junit.jupiter.MockitoSettings;
              import org.mockito.quality.Strictness;
              
              @MockitoSettings(strictness = Strictness.LENIENT)
              @ExtendWith(MockitoExtension.class)
              public class ExternalAPIServiceTest {
              }
              """
          )
        );
    }
}
