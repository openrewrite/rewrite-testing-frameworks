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
package org.openrewrite.java.testing.hamcrest;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/1211")
class HamcrestClassOverloadTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new HamcrestIsMatcherToAssertJ())
          .parser(JavaParser.fromJavaVersion().classpath(
            "hamcrest-core-1.3", "hamcrest-library-1.3", "assertj-core"));
    }

    @Test
    void retainLegacyClassMatcher() {
        rewriteRun(
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.CoreMatchers.is;

              class Example {
                  void check(String value) {
                      assertThat(value, is(String.class));
                  }
              }
              """
          )
        );
    }

    @Test
    void retainLegacyArrayClassMatcher() {
        rewriteRun(
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.CoreMatchers.is;

              class Example {
                  void check(String[] value) {
                      assertThat(value, is(String[].class));
                  }
              }
              """
          )
        );
    }

    @Test
    void retainLegacyPropertyClassMatcher() {
        rewriteRun(
          spec -> spec.recipe(new HamcrestHasPropertyToAssertJ()),
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.hasProperty;
              import static org.hamcrest.Matchers.is;

              class Example {
                  public String getValue() {
                      return "value";
                  }

                  void check() {
                      assertThat(this, hasProperty("value", is(String.class)));
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateLegacyExplicitObjectEquality() {
        rewriteRun(
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.CoreMatchers.is;

              class Example {
                  void check() {
                      assertThat(String.class, is((Object) String.class));
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Example {
                  void check() {
                      assertThat(String.class).isEqualTo((Object) String.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateModernClassValueEquality() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "hamcrest-3", "assertj-core-3")),
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.CoreMatchers.is;

              class Example {
                  void check() {
                      assertThat(String.class, is(String.class));
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Example {
                  void check() {
                      assertThat(String.class).isEqualTo(String.class);
                  }
              }
              """
          )
        );
    }
}
