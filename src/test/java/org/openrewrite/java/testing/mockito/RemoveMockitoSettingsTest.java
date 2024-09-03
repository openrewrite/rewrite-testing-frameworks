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

import static org.openrewrite.java.Assertions.java;

class RemoveMockitoSettingsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "mockito-core", "mockito-junit-jupiter", "junit-jupiter-api"))
          .recipeFromResource("/META-INF/rewrite/mockito.yml", "org.openrewrite.java.testing.mockito.MockitoBestPractices");
    }

    @Test
    @DocumentExample
    void removeMockitoSettings() {
        rewriteRun(
          //language=java
          java(
            """
              import org.mockito.junit.jupiter.MockitoSettings;
              import org.mockito.quality.Strictness;
              @MockitoSettings(strictness = Strictness.WARN)
              class A {}
              """,
            """
              class A {}
              """
          )
        );
    }

    @Test
    void removeMockitoSettingsFullyQualified() {
        rewriteRun(
          //language=java
          java(
            """
              import org.mockito.junit.jupiter.MockitoSettings;
              @MockitoSettings(strictness = org.mockito.quality.Strictness.WARN)
              class A {}
              """,
            """
              class A {}
              """
          )
        );
    }

    @Test
    void retainMisMatchedArgument() {
        rewriteRun(
          //language=java
          java(
            """
              import org.mockito.junit.jupiter.MockitoSettings;
              import org.mockito.quality.Strictness;
              @MockitoSettings(strictness = Strictness.LENIENT)
              class A {}
              """
          )
        );
    }
}
