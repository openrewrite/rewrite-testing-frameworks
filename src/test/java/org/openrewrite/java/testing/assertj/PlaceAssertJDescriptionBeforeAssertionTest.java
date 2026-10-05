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
package org.openrewrite.java.testing.assertj;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class PlaceAssertJDescriptionBeforeAssertionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3"))
          .recipe(new PlaceAssertJDescriptionBeforeAssertion());
    }

    @DocumentExample
    @Test
    void moveDescriptionBeforeAssertion() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.BDDAssertions.then;

              class Test {
                  void test(int port) {
                      then(port).isNotEqualTo(0).as("Lifecycle port is zero");
                  }
              }
              """,
            """
              import static org.assertj.core.api.BDDAssertions.then;

              class Test {
                  void test(int port) {
                      then(port).as("Lifecycle port is zero").isNotEqualTo(0);
                  }
              }
              """
          )
        );
    }

    @Test
    void moveWrappedFailMessage() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(List<String> entries, Throwable startupFailure) {
                      assertThat(entries).hasSize(2)
                              .withFailMessage("Fails because child has a stale reference to its parent");
                      assertThat(entries).hasSize(2)
                              // parent and child
                              .withFailMessage("Fails because child has a stale reference to its parent");
                      assertThat(startupFailure).hasRootCauseInstanceOf(IllegalStateException.class)
                              .withFailMessage("SSL bundle name 'test-bundle' is not valid");
                      assertThat(entries).isNotNull().hasSize(2).contains("a")
                              .as("only the last call wrapped");
                  }
              }
              """,
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(List<String> entries, Throwable startupFailure) {
                      assertThat(entries)
                              .withFailMessage("Fails because child has a stale reference to its parent")
                              .hasSize(2);
                      assertThat(entries)
                              // parent and child
                              .withFailMessage("Fails because child has a stale reference to its parent")
                              .hasSize(2);
                      assertThat(startupFailure)
                              .withFailMessage("SSL bundle name 'test-bundle' is not valid")
                              .hasRootCauseInstanceOf(IllegalStateException.class);
                      assertThat(entries)
                              .as("only the last call wrapped")
                              .isNotNull().hasSize(2).contains("a");
                  }
              }
              """
          )
        );
    }

    @Test
    void moveInUnbracedBodyAndSwitchCase() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(int i, boolean b) {
                      if (b) assertThat(i).isEqualTo(1).as("if");
                      else assertThat(i).isEqualTo(2).as("else");
                      for (int j = 0; j < i; j++) assertThat(j).isNotNegative().as("for");
                      switch (i) {
                          case 1:
                              assertThat(i).isEqualTo(1).as("case");
                              break;
                      }
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(int i, boolean b) {
                      if (b) assertThat(i).as("if").isEqualTo(1);
                      else assertThat(i).as("else").isEqualTo(2);
                      for (int j = 0; j < i; j++) assertThat(j).as("for").isNotNegative();
                      switch (i) {
                          case 1:
                              assertThat(i).as("case").isEqualTo(1);
                              break;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void moveFromChainsOfDifferentShapes() {
        rewriteRun(
          //language=java
          java(
            """
              import org.assertj.core.api.SoftAssertions;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.assertj.core.api.Assumptions.assumeThat;

              class Test {
                  void test(String s, Object o, SoftAssertions softly) {
                      assertThat(s).isNotNull().startsWith("a").endsWith("z").as("description %s", s);
                      assertThat(o).isNotNull().as("a").withFailMessage("b");
                      softly.assertThat(s).isEqualTo("a").as("description");
                      assumeThat(s).isNotEmpty().as("assumption");
                  }
              }
              """,
            """
              import org.assertj.core.api.SoftAssertions;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.assertj.core.api.Assumptions.assumeThat;

              class Test {
                  void test(String s, Object o, SoftAssertions softly) {
                      assertThat(s).as("description %s", s).isNotNull().startsWith("a").endsWith("z");
                      assertThat(o).as("a").withFailMessage("b").isNotNull();
                      softly.assertThat(s).as("description").isEqualTo("a");
                      assumeThat(s).as("assumption").isNotEmpty();
                  }
              }
              """
          )
        );
    }

    @Test
    void moveSupplierAndDescriptionOverloads() {
        rewriteRun(
          //language=java
          java(
            """
              import org.assertj.core.description.TextDescription;

              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(String s) {
                      assertThat(s).isNotEmpty().as(() -> "supplied");
                      assertThat(s).isNotEmpty().describedAs(new TextDescription("description"));
                      assertThat(s).isNotEmpty().overridingErrorMessage("message %s", s);
                      assertThat(s).contains("Started").withFailMessage(() -> s);
                  }
              }
              """,
            """
              import org.assertj.core.description.TextDescription;

              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(String s) {
                      assertThat(s).as(() -> "supplied").isNotEmpty();
                      assertThat(s).describedAs(new TextDescription("description")).isNotEmpty();
                      assertThat(s).overridingErrorMessage("message %s", s).isNotEmpty();
                      assertThat(s).withFailMessage(() -> s).contains("Started");
                  }
              }
              """
          )
        );
    }

    @Test
    void moveInVoidLambdaBody() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Consumer;

              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(Throwable t) {
                      verify(ex -> assertThat(ex).isInstanceOf(IllegalStateException.class)
                              .withFailMessage("The provided password is compromised"));
                  }

                  void verify(Consumer<Throwable> consumer) {
                  }
              }
              """,
            """
              import java.util.function.Consumer;

              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(Throwable t) {
                      verify(ex -> assertThat(ex)
                              .withFailMessage("The provided password is compromised")
                              .isInstanceOf(IllegalStateException.class));
                  }

                  void verify(Consumer<Throwable> consumer) {
                  }
              }
              """
          )
        );
    }

    @Test
    void moveAfterAssertThatThrownBy() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThatThrownBy;

              class Test {
                  void test() {
                      assertThatThrownBy(() -> {
                          throw new IllegalStateException("boom");
                      })
                              .isInstanceOf(IllegalStateException.class)
                              .hasMessageContaining("boom")
                              .as("description");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThatThrownBy;

              class Test {
                  void test() {
                      assertThatThrownBy(() -> {
                          throw new IllegalStateException("boom");
                      })
                              .as("description")
                              .isInstanceOf(IllegalStateException.class)
                              .hasMessageContaining("boom");
                  }
              }
              """
          )
        );
    }

    @Test
    void moveDescriptionBeforeIsThrownBy() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

              class Test {
                  void test() {
                      assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> {
                          throw new IllegalStateException("boom");
                      }).withMessage("boom").as("description");
                      assertThatExceptionOfType(IllegalStateException.class)
                              .isThrownBy(() -> {
                                  throw new IllegalStateException("boom");
                              })
                              .describedAs("visitors should not visit documents without a marker");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

              class Test {
                  void test() {
                      assertThatExceptionOfType(IllegalStateException.class).as("description").isThrownBy(() -> {
                          throw new IllegalStateException("boom");
                      }).withMessage("boom");
                      assertThatExceptionOfType(IllegalStateException.class)
                              .describedAs("visitors should not visit documents without a marker")
                              .isThrownBy(() -> {
                                  throw new IllegalStateException("boom");
                              });
                  }
              }
              """
          )
        );
    }

    @Test
    void keepFailMessageNotAvailableOnThrowableTypeAssert() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

              class Test {
                  void test() {
                      assertThatIllegalArgumentException()
                              .isThrownBy(() -> {
                                  throw new IllegalArgumentException();
                              })
                              .withFailMessage("priority");
                  }
              }
              """
          )
        );
    }

    @Test
    void keepMessageDirectlyAfterEntry() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(int i, boolean condition) {
                      assertThat(i).as("description").isEqualTo(1);
                      assertThat(i).withFailMessage("message").isEqualTo(1);
                      assertThat(condition).withFailMessage("Kafka Sync Producer should have been enabled.");
                  }
              }
              """
          )
        );
    }

    @Test
    void moveFailMessagePastEarlierDescription() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(int i) {
                      assertThat(i).as("description").isEqualTo(1).withFailMessage("message");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(int i) {
                      assertThat(i).withFailMessage("message").as("description").isEqualTo(1);
                  }
              }
              """
          )
        );
    }

    @Test
    void keepDescriptionOverriddenByEarlierDescription() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.BDDAssertions.then;

              class Test {
                  void test(String first, String second) {
                      then(second).as("second span id")
                              .isNotEqualTo(first).as("first span id");
                      then(second).withFailMessage("message").isNotEqualTo(first).overridingErrorMessage("ignored");
                  }
              }
              """
          )
        );
    }

    @Test
    void moveAfterNavigation() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.assertj.core.api.Assertions.assertThatThrownBy;

              class Test {
                  void test(List<String> list, Object o, Throwable t) {
                      assertThat(list).hasSize(1).first().isEqualTo("a").as("first");
                      assertThat(list).element(0).isEqualTo("a").as("element");
                      assertThat(list).singleElement().isEqualTo("a").as("single");
                      assertThat(o).extracting("name").isEqualTo("a").as("extracting");
                      assertThat(o).asString().contains("a").as("asString");
                      assertThat("s").isNotNull().asString().endsWith("s").as("asString on a String");
                      assertThat(t).cause().hasMessage("cause").as("cause");
                      assertThat(t).rootCause().hasMessage("root cause").as("root cause");
                      assertThatThrownBy(() -> {}).cause().hasMessage("cause").as("cause");
                      assertThat(o).satisfies(it -> assertThat(it).isNotNull()).as("satisfies");
                  }
              }
              """,
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.assertj.core.api.Assertions.assertThatThrownBy;

              class Test {
                  void test(List<String> list, Object o, Throwable t) {
                      assertThat(list).hasSize(1).first().as("first").isEqualTo("a");
                      assertThat(list).element(0).as("element").isEqualTo("a");
                      assertThat(list).singleElement().as("single").isEqualTo("a");
                      assertThat(o).extracting("name").as("extracting").isEqualTo("a");
                      assertThat(o).asString().as("asString").contains("a");
                      assertThat("s").isNotNull().asString().as("asString on a String").endsWith("s");
                      assertThat(t).cause().as("cause").hasMessage("cause");
                      assertThat(t).rootCause().as("root cause").hasMessage("root cause");
                      assertThatThrownBy(() -> {}).cause().as("cause").hasMessage("cause");
                      assertThat(o).satisfies(it -> assertThat(it).isNotNull()).as("satisfies");
                  }
              }
              """
          )
        );
    }

    @Test
    void keepFailMessageOnThrowableAssertAlternative() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

              class Test {
                  void test() {
                      assertThatExceptionOfType(IllegalStateException.class)
                              .isThrownBy(() -> {
                                  throw new IllegalStateException("boom");
                              })
                              .withMessage("boom")
                              .withFailMessage("ignored by the delegated withMessage(..)");
                  }
              }
              """
          )
        );
    }

    @Test
    void keepNonStatementChain() {
        rewriteRun(
          //language=java
          java(
            """
              import org.assertj.core.api.AbstractIntegerAssert;

              import java.util.function.Supplier;

              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  AbstractIntegerAssert<?> test(int x) {
                      return assertThat(x).isEqualTo(1).as("description");
                  }

                  Supplier<AbstractIntegerAssert<?>> supply(int x) {
                      return () -> assertThat(x).isEqualTo(1).as("description");
                  }
              }
              """
          )
        );
    }

    @Test
    void keepCustomAssertConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              import org.assertj.core.api.AbstractObjectAssert;

              class HttpHeadersAssert extends AbstractObjectAssert<HttpHeadersAssert, String> {
                  HttpHeadersAssert(String actual) {
                      super(actual, HttpHeadersAssert.class);
                      as("HTTP headers");
                  }
              }
              """
          )
        );
    }

    @Test
    void moveOnCustomEntryPointAndAssertInVariable() {
        rewriteRun(
          //language=java
          java(
            """
              import org.assertj.core.api.AbstractIntegerAssert;
              import org.assertj.core.api.ObjectAssert;

              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(Object token, int i) {
                      assertThatToken(token).isNotNull().withFailMessage("token is populated");
                      AbstractIntegerAssert<?> integerAssert = assertThat(i);
                      integerAssert.isPositive().as("positive");
                  }

                  ObjectAssert<Object> assertThatToken(Object token) {
                      return assertThat(token);
                  }
              }
              """,
            """
              import org.assertj.core.api.AbstractIntegerAssert;
              import org.assertj.core.api.ObjectAssert;

              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(Object token, int i) {
                      assertThatToken(token).withFailMessage("token is populated").isNotNull();
                      AbstractIntegerAssert<?> integerAssert = assertThat(i);
                      integerAssert.as("positive").isPositive();
                  }

                  ObjectAssert<Object> assertThatToken(Object token) {
                      return assertThat(token);
                  }
              }
              """
          )
        );
    }

    @Test
    void moveDescriptionAfterNavigationFromDescribedAssert() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(List<String> list) {
                      assertThat(list).as("list").first().isEqualTo("a").as("first");
                  }
              }
              """,
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(List<String> list) {
                      assertThat(list).as("list").first().as("first").isEqualTo("a");
                  }
              }
              """
          )
        );
    }
}
