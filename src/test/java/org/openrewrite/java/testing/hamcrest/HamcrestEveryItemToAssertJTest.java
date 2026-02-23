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
package org.openrewrite.java.testing.hamcrest;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/915")
class HamcrestEveryItemToAssertJTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "junit-jupiter-api-5",
            "hamcrest-3",
            "assertj-core-3"))
          .recipe(new HamcrestEveryItemToAssertJ());
    }

    @DocumentExample
    @Test
    void everyItemInstanceOf() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              import org.junit.jupiter.api.Test;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.everyItem;
              import static org.hamcrest.Matchers.instanceOf;

              class MyTest {
                  @Test
                  void testMethod() {
                      List<Object> list = List.of("a", "b");
                      assertThat(list, everyItem(instanceOf(String.class)));
                  }
              }
              """,
            """
              import java.util.List;
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  @Test
                  void testMethod() {
                      List<Object> list = List.of("a", "b");
                      assertThat(list).hasOnlyElementsOfType(String.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void everyItemGeneralMatcher() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              import org.junit.jupiter.api.Test;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.everyItem;
              import static org.hamcrest.Matchers.greaterThan;

              class MyTest {
                  @Test
                  void testMethod() {
                      List<Integer> list = List.of(5, 6, 7);
                      assertThat(list, everyItem(greaterThan(4)));
                  }
              }
              """,
            """
              import java.util.List;
              import org.junit.jupiter.api.Test;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.greaterThan;

              class MyTest {
                  @Test
                  void testMethod() {
                      List<Integer> list = List.of(5, 6, 7);
                      org.assertj.core.api.Assertions.assertThat(list).allSatisfy(arg -> assertThat(arg, greaterThan(4)));
                  }
              }
              """
          )
        );
    }

    @Test
    void everyItemInstanceOfWithReason() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              import org.junit.jupiter.api.Test;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.everyItem;
              import static org.hamcrest.Matchers.instanceOf;

              class MyTest {
                  @Test
                  void testMethod() {
                      List<Object> list = List.of("a", "b");
                      assertThat("all should be strings", list, everyItem(instanceOf(String.class)));
                  }
              }
              """,
            """
              import java.util.List;
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  @Test
                  void testMethod() {
                      List<Object> list = List.of("a", "b");
                      assertThat(list).as("all should be strings").hasOnlyElementsOfType(String.class);
                  }
              }
              """
          )
        );
    }
}
