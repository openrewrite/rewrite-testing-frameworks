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
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.kotlin.Assertions.kotlin;

class KotlinAssumeNotNullToAssumptionsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new KotlinAssumeNotNullToAssumptions())
          .parser(KotlinParser.builder().classpathFromResources(new InMemoryExecutionContext(), "junit-4"));
    }

    @Test
    void singleArgument() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Assume.assumeNotNull

              fun test(value: String?) { assumeNotNull(value) }
              """,
            """
              import org.junit.jupiter.api.Assumptions.assumeFalse

              fun test(value: String?) { assumeFalse(value == null) }
              """
          )
        );
    }

    @Test
    void spreadArguments() {
        rewriteRun(
          kotlin(
            """
              import org.junit.Assume.assumeNotNull

              fun test(values: Array<String?>) { assumeNotNull(*values) }
              """,
            """
              import org.junit.jupiter.api.Assumptions.assumeFalse

              fun test(values: Array<String?>) { values.forEach { assumeFalse(it == null) } }
              """
          )
        );
    }

    @Test
    void unrelatedMethodIsUnchanged() {
        rewriteRun(kotlin("fun assumeNotNull(value: String?) {}\nfun test() { assumeNotNull(null) }"));
    }
}
