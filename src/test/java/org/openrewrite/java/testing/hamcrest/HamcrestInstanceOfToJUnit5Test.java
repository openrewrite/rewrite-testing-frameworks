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
import org.junit.jupiter.api.Timeout;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.concurrent.TimeUnit;

import static org.openrewrite.java.Assertions.java;

class HamcrestInstanceOfToJUnit5Test implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5", "hamcrest-3"))
          .recipe(new HamcrestInstanceOfToJUnit5());
    }

    @DocumentExample
    @Test
    void instanceOf() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.util.List;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.instanceOf;
              import static org.hamcrest.Matchers.isA;
              import static org.hamcrest.Matchers.not;

              class ATest {
                  private static final List<Integer> list = List.of();
                  @Test
                  void testInstance() {
                      assertThat(list, instanceOf(Iterable.class));
                      assertThat(list, not(instanceOf(Integer.class)));
                      assertThat(list, isA(Iterable.class));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.util.List;

              import static org.junit.jupiter.api.Assertions.assertFalse;
              import static org.junit.jupiter.api.Assertions.assertInstanceOf;

              class ATest {
                  private static final List<Integer> list = List.of();
                  @Test
                  void testInstance() {
                      assertInstanceOf(Iterable.class, list);
                      assertFalse(Integer.class.isAssignableFrom(list.getClass()));
                      assertInstanceOf(Iterable.class, list);
                  }
              }
              """
          )
        );
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void notFromCoreMatchers() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.util.List;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.CoreMatchers.instanceOf;
              import static org.hamcrest.CoreMatchers.not;

              class ATest {
                  private static final List<Integer> list = List.of();
                  @Test
                  void testInstance() {
                      assertThat(list, not(instanceOf(Integer.class)));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.util.List;

              import static org.junit.jupiter.api.Assertions.assertFalse;

              class ATest {
                  private static final List<Integer> list = List.of();
                  @Test
                  void testInstance() {
                      assertFalse(Integer.class.isAssignableFrom(list.getClass()));
                  }
              }
              """
          )
        );
    }

    @Test
    void instanceOfAndIsAFromCoreMatchers() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.util.List;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.CoreMatchers.instanceOf;
              import static org.hamcrest.CoreMatchers.isA;

              class ATest {
                  private static final List<Integer> list = List.of();
                  @Test
                  void testInstance() {
                      assertThat(list, instanceOf(Iterable.class));
                      assertThat(list, isA(Iterable.class));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.util.List;

              import static org.junit.jupiter.api.Assertions.assertInstanceOf;

              class ATest {
                  private static final List<Integer> list = List.of();
                  @Test
                  void testInstance() {
                      assertInstanceOf(Iterable.class, list);
                      assertInstanceOf(Iterable.class, list);
                  }
              }
              """
          )
        );
    }

    @Test
    void assertionsWithReason() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              import org.junit.jupiter.api.Test;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.instanceOf;
              import static org.hamcrest.Matchers.isA;
              import static org.hamcrest.Matchers.not;

              class ATest {
                  private static final List<Integer> list = List.of();

                  @Test
                  void testInstance() {
                      assertThat("Examined object is not instance of Iterable", list, instanceOf(Iterable.class));
                      assertThat("Examined object is not instance of Iterable", list, isA(Iterable.class));
                      assertThat("Examined object must not be instance of Integer", list, not(instanceOf(Integer.class)));
                  }
              }
              """,
            """
              import java.util.List;
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertFalse;
              import static org.junit.jupiter.api.Assertions.assertInstanceOf;

              class ATest {
                  private static final List<Integer> list = List.of();

                  @Test
                  void testInstance() {
                      assertInstanceOf(Iterable.class, list, "Examined object is not instance of Iterable");
                      assertInstanceOf(Iterable.class, list, "Examined object is not instance of Iterable");
                      assertFalse(Integer.class.isAssignableFrom(list.getClass()), "Examined object must not be instance of Integer");
                  }
              }
              """
          )
        );
    }

    @Test
    void retainMatcherIdentifier() {
        rewriteRun(
          java(
            """
              import org.hamcrest.Matcher;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.instanceOf;

              class Example {
                  void test(Object actual) {
                      Matcher<Object> matcher = instanceOf(String.class);
                      assertThat(actual, matcher);
                  }
              }
              """
          )
        );
    }

    @Test
    void retainMatcherField() {
        rewriteRun(
          java(
            """
              import org.hamcrest.Matcher;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.instanceOf;

              class Example {
                  private final Matcher<Object> matcher = instanceOf(String.class);

                  void test(Object actual) {
                      assertThat(actual, this.matcher);
                  }
              }
              """
          )
        );
    }

    @Test
    void retainParenthesizedMatcher() {
        rewriteRun(
          java(
            """
              import org.hamcrest.Matcher;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.instanceOf;

              class Example {
                  void test(Object actual) {
                      Matcher<Object> matcher = instanceOf(String.class);
                      assertThat(actual, (matcher));
                  }
              }
              """
          )
        );
    }

    @Test
    void retainCastMatcher() {
        rewriteRun(
          java(
            """
              import org.hamcrest.Matcher;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.instanceOf;

              class Example {
                  void test(Object actual) {
                      Matcher<Object> matcher = instanceOf(String.class);
                      assertThat(actual, (Matcher<Object>) matcher);
                  }
              }
              """
          )
        );
    }

    @Test
    void retainNotWrappedMatcher() {
        rewriteRun(
          java(
            """
              import org.hamcrest.Matcher;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.instanceOf;
              import static org.hamcrest.Matchers.not;

              class Example {
                  void test(Object actual) {
                      Matcher<Object> matcher = instanceOf(String.class);
                      assertThat(actual, not(matcher));
                  }
              }
              """
          )
        );
    }

    @Test
    void retainNestedNotWrappedMatcher() {
        rewriteRun(
          java(
            """
              import org.hamcrest.Matcher;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.instanceOf;
              import static org.hamcrest.Matchers.not;

              class Example {
                  void test(Object actual) {
                      Matcher<Object> matcher = instanceOf(String.class);
                      assertThat(actual, not(not(matcher)));
                  }
              }
              """
          )
        );
    }

    @Test
    void retainOpaqueMatcherWithLiteralReason() {
        rewriteRun(
          java(
            """
              import org.hamcrest.Matcher;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.instanceOf;

              class Example {
                  void test(Object actual) {
                      Matcher<Object> matcher = instanceOf(String.class);
                      assertThat("literal reason", actual, matcher);
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateSupportedMatchersBesideOpaqueMatcher() {
        rewriteRun(
          java(
            """
              import org.hamcrest.Matcher;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.instanceOf;
              import static org.hamcrest.Matchers.isA;

              class Example {
                  void test(String actual) {
                      Matcher<Object> matcher = instanceOf(String.class);
                      assertThat(actual, matcher);
                      assertThat(actual, instanceOf(CharSequence.class));
                      assertThat(actual, isA(String.class));
                  }
              }
              """,
            """
              import org.hamcrest.Matcher;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.instanceOf;
              import static org.junit.jupiter.api.Assertions.assertInstanceOf;

              class Example {
                  void test(String actual) {
                      Matcher<Object> matcher = instanceOf(String.class);
                      assertThat(actual, matcher);
                      assertInstanceOf(CharSequence.class, actual);
                      assertInstanceOf(String.class, actual);
                  }
              }
              """
          )
        );
    }

    @Test
    void retainNullMatcher() {
        rewriteRun(
          java(
            """
              import org.hamcrest.Matcher;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.instanceOf;

              class Example {
                  void test() {
                      Matcher<Object> applicability = instanceOf(String.class);
                      Matcher<Object> matcher = null;
                      assertThat("value", matcher);
                  }
              }
              """
          )
        );
    }

    @Test
    void retainNullActualWithOpaqueNegation() {
        rewriteRun(
          java(
            """
              import org.hamcrest.Matcher;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.instanceOf;
              import static org.hamcrest.Matchers.not;

              class Example {
                  void test() {
                      Matcher<Object> matcher = instanceOf(String.class);
                      assertThat((Object) null, not(matcher));
                  }
              }
              """
          )
        );
    }

    @Test
    void retainObservableMatcherEvaluation() {
        rewriteRun(
          java(
            """
              import org.hamcrest.Matcher;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.instanceOf;

              class Example {
                  private final StringBuilder trace = new StringBuilder();

                  void test() {
                      assertThat(reason(), actual(), matcher());
                  }

                  String reason() {
                      trace.append("R");
                      return "literal reason";
                  }

                  Object actual() {
                      trace.append("A");
                      return "value";
                  }

                  Matcher<Object> matcher() {
                      trace.append("M");
                      return instanceOf(String.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveNestedDirectNotParity() {
        rewriteRun(
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.instanceOf;
              import static org.hamcrest.Matchers.not;

              class Example {
                  void test(String actual) {
                      assertThat(actual, not(not(instanceOf(String.class))));
                      assertThat("literal reason", actual, not(not(not(instanceOf(Integer.class)))));
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.assertFalse;
              import static org.junit.jupiter.api.Assertions.assertInstanceOf;

              class Example {
                  void test(String actual) {
                      assertInstanceOf(String.class, actual);
                      assertFalse(Integer.class.isAssignableFrom(actual.getClass()), "literal reason");
                  }
              }
              """
          )
        );
    }
}
