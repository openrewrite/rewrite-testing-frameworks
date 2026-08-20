/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.testing.truth;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class TruthCustomSubjectsToAssertJTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new TruthCustomSubjectsToAssertJ())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "guava", "truth"));
    }

    @Test
    void addsCommentOnce() {
        rewriteRun(
          spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
          //language=java
          java(
            """
              import com.google.common.truth.Subject;

              import static com.google.common.truth.Truth.assertAbout;

              class Test {
                  void test(Subject.Factory<Subject, Object> factory) {
                      assertAbout(factory).that(new Object());
                  }
              }
              """,
            """
              import com.google.common.truth.Subject;

              import static com.google.common.truth.Truth.assertAbout;

              class Test {
                  void test(Subject.Factory<Subject, Object> factory) {
                      /* Truth's assertAbout() with custom subjects requires manual migration to AssertJ custom assertions */assertAbout(factory).that(new Object());
                  }
              }
              """
          )
        );
    }
}
