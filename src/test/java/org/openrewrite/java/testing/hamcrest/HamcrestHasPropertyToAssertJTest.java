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
class HamcrestHasPropertyToAssertJTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "junit-jupiter-api-5",
            "hamcrest-3",
            "assertj-core-3"))
          .recipe(new HamcrestHasPropertyToAssertJ());
    }

    @DocumentExample
    @Test
    void hasPropertyName() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.hasProperty;

              class MyTest {
                  @Test
                  void testMethod() {
                      Object obj = new Object();
                      assertThat(obj, hasProperty("class"));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  @Test
                  void testMethod() {
                      Object obj = new Object();
                      assertThat(obj).hasFieldOrProperty("class");
                  }
              }
              """
          )
        );
    }

    @Test
    void hasPropertyWithEqualTo() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.equalTo;
              import static org.hamcrest.Matchers.hasProperty;

              class MyTest {
                  @Test
                  void testMethod() {
                      Object obj = new Object();
                      assertThat(obj, hasProperty("class", equalTo(Object.class)));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  @Test
                  void testMethod() {
                      Object obj = new Object();
                      assertThat(obj).hasFieldOrPropertyWithValue("class", Object.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void hasPropertyWithIs() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.hasProperty;
              import static org.hamcrest.Matchers.is;

              class MyTest {
                  @Test
                  void testMethod() {
                      Object obj = new Object();
                      assertThat(obj, hasProperty("class", is(Object.class)));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  @Test
                  void testMethod() {
                      Object obj = new Object();
                      assertThat(obj).hasFieldOrPropertyWithValue("class", Object.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void hasPropertyWithReason() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.hasProperty;

              class MyTest {
                  @Test
                  void testMethod() {
                      Object obj = new Object();
                      assertThat("should have class property", obj, hasProperty("class"));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  @Test
                  void testMethod() {
                      Object obj = new Object();
                      assertThat(obj).as("should have class property").hasFieldOrProperty("class");
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotConvertUnknownMatcher() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.greaterThan;
              import static org.hamcrest.Matchers.hasProperty;

              class MyTest {
                  @Test
                  void testMethod() {
                      Object obj = new Object();
                      assertThat(obj, hasProperty("size", greaterThan(5)));
                  }
              }
              """
          )
        );
    }
}
