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
package org.openrewrite.java.testing.cleanup;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.groovy.GroovyParser;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.groovy.Assertions.groovy;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

class FindIncompleteAssertionsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new FindIncompleteAssertions())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "assertj-core-3", "fest-assert-core", "mockito-core-5", "guava", "truth"));
    }

    @DocumentExample
    @Test
    void bareAssertJAssertThat() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test(boolean result) {
                      assertThat(result);
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test(boolean result) {
                      /*~~>*/assertThat(result);
                  }
              }
              """
          )
        );
    }

    @Test
    void assertJEntryPoints() {
        rewriteRun(
          //language=java
          java(
            """
              import org.assertj.core.api.Assertions;
              import org.assertj.core.api.AssertionsForClassTypes;
              import org.assertj.core.api.AssertionsForInterfaceTypes;
              import org.assertj.core.api.Java6Assertions;

              import java.util.List;

              class MyTest {
                  void test(Object o, List<String> list) {
                      Assertions.assertThat(o);
                      AssertionsForClassTypes.assertThat(o);
                      AssertionsForInterfaceTypes.assertThat(list);
                      Java6Assertions.assertThat(o);
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;
              import org.assertj.core.api.AssertionsForClassTypes;
              import org.assertj.core.api.AssertionsForInterfaceTypes;
              import org.assertj.core.api.Java6Assertions;

              import java.util.List;

              class MyTest {
                  void test(Object o, List<String> list) {
                      /*~~>*/Assertions.assertThat(o);
                      /*~~>*/AssertionsForClassTypes.assertThat(o);
                      /*~~>*/AssertionsForInterfaceTypes.assertThat(list);
                      /*~~>*/Java6Assertions.assertThat(o);
                  }
              }
              """
          )
        );
    }

    @Test
    void withAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import org.assertj.core.api.WithAssertions;

              class MyTest implements WithAssertions {
                  void test(Object o) {
                      assertThat(o);
                      assertThat(o).isNotNull();
                  }
              }
              """,
            """
              import org.assertj.core.api.WithAssertions;

              class MyTest implements WithAssertions {
                  void test(Object o) {
                      /*~~>*/assertThat(o);
                      assertThat(o).isNotNull();
                  }
              }
              """
          )
        );
    }

    @Test
    void configurationOnlyChain() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test(Object o) {
                      assertThat(o).as("description");
                      assertThat(o).describedAs("description").withFailMessage("message");
                      assertThat(o).usingRecursiveComparison();
                      assertThat(o).extracting("name");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test(Object o) {
                      /*~~>*/assertThat(o).as("description");
                      /*~~>*/assertThat(o).describedAs("description").withFailMessage("message");
                      /*~~>*/assertThat(o).usingRecursiveComparison();
                      /*~~>*/assertThat(o).extracting("name");
                  }
              }
              """
          )
        );
    }

    @Test
    void configurationAfterAssertionIsComplete() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test(Object o) {
                      assertThat(o).isNotNull().as("description");
                      assertThat(o).as("description").isNotNull();
                      assertThat(o).isNotNull().extracting("name");
                  }
              }
              """
          )
        );
    }

    @Test
    void lambdaBody() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test(List<String> list) {
                      assertThat(list).allSatisfy(s -> assertThat(s));
                      assertThat(list).allSatisfy(s -> assertThat(s).isNotEmpty());
                  }
              }
              """,
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test(List<String> list) {
                      assertThat(list).allSatisfy(s -> /*~~>*/assertThat(s));
                      assertThat(list).allSatisfy(s -> assertThat(s).isNotEmpty());
                  }
              }
              """
          )
        );
    }

    @Test
    void lambdaReturningTheAssertIsNotDiscarded() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.stream.Collectors;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test(List<String> list) {
                      list.stream().map(s -> assertThat(s)).collect(Collectors.toList());
                  }
              }
              """
          )
        );
    }

    @Test
    void unbracedBody() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test(boolean b, Object o) {
                      if (b) assertThat(o);
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test(boolean b, Object o) {
                      if (b) /*~~>*/assertThat(o);
                  }
              }
              """
          )
        );
    }

    @Test
    void completeAssertJAssertion() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test(boolean x) {
                      assertThat(x).isTrue();
                  }
              }
              """
          )
        );
    }

    @Test
    void assertAssignedToVariable() {
        rewriteRun(
          //language=java
          java(
            """
              import org.assertj.core.api.AbstractStringAssert;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test(String s) {
                      AbstractStringAssert<?> a = assertThat(s);
                      a.as("description");
                      a.isNotEmpty();
                  }
              }
              """
          )
        );
    }

    @Test
    void ownAssertThatHelper() {
        rewriteRun(
          //language=java
          java(
            """
              class MyTest {
                  void test(Object o) {
                      assertThat(o);
                  }

                  static void assertThat(Object o) {
                      if (o == null) {
                          throw new AssertionError();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void fest() {
        rewriteRun(
          //language=java
          java(
            """
              import org.fest.assertions.api.Assertions;

              class MyTest {
                  void test(Object o) {
                      Assertions.assertThat(o);
                      Assertions.assertThat(o).isNotNull();
                  }
              }
              """,
            """
              import org.fest.assertions.api.Assertions;

              class MyTest {
                  void test(Object o) {
                      /*~~>*/Assertions.assertThat(o);
                      Assertions.assertThat(o).isNotNull();
                  }
              }
              """
          )
        );
    }

    @Test
    void truth() {
        rewriteRun(
          //language=java
          java(
            """
              import com.google.common.truth.OptionalSubject;

              import java.util.Optional;

              import static com.google.common.truth.Truth.assertAbout;
              import static com.google.common.truth.Truth.assertThat;
              import static com.google.common.truth.Truth.assertWithMessage;

              class MyTest {
                  void test(String s) {
                      assertThat(s);
                      assertWithMessage("message").that(s);
                      assertAbout(OptionalSubject.optionals()).that(Optional.of(s));
                      assertThat(s).isEqualTo("a");
                      assertWithMessage("message").that(s).isEqualTo("a");
                      assertAbout(OptionalSubject.optionals()).that(Optional.of(s)).isPresent();
                  }
              }
              """,
            """
              import com.google.common.truth.OptionalSubject;

              import java.util.Optional;

              import static com.google.common.truth.Truth.assertAbout;
              import static com.google.common.truth.Truth.assertThat;
              import static com.google.common.truth.Truth.assertWithMessage;

              class MyTest {
                  void test(String s) {
                      /*~~>*/assertThat(s);
                      /*~~>*/assertWithMessage("message").that(s);
                      /*~~>*/assertAbout(OptionalSubject.optionals()).that(Optional.of(s));
                      assertThat(s).isEqualTo("a");
                      assertWithMessage("message").that(s).isEqualTo("a");
                      assertAbout(OptionalSubject.optionals()).that(Optional.of(s)).isPresent();
                  }
              }
              """
          )
        );
    }

    @Test
    void mockitoVerify() {
        rewriteRun(
          //language=java
          java(
            """
              import org.mockito.Mockito;

              import java.util.List;

              import static org.mockito.Mockito.times;
              import static org.mockito.Mockito.verify;

              class MyTest {
                  void test(List<String> mockedList) {
                      Mockito.verify(mockedList);
                      verify(mockedList, times(1));
                      verify(mockedList).add("one");
                      verify(mockedList, times(1)).clear();
                  }
              }
              """,
            """
              import org.mockito.Mockito;

              import java.util.List;

              import static org.mockito.Mockito.times;
              import static org.mockito.Mockito.verify;

              class MyTest {
                  void test(List<String> mockedList) {
                      /*~~>*/Mockito.verify(mockedList);
                      /*~~>*/verify(mockedList, times(1));
                      verify(mockedList).add("one");
                      verify(mockedList, times(1)).clear();
                  }
              }
              """
          )
        );
    }

    @Test
    void mockitoInOrderVerify() {
        rewriteRun(
          //language=java
          java(
            """
              import org.mockito.InOrder;

              import java.util.List;

              class MyTest {
                  void test(InOrder inOrder, List<String> mockedList) {
                      inOrder.verify(mockedList);
                      inOrder.verify(mockedList).clear();
                  }
              }
              """,
            """
              import org.mockito.InOrder;

              import java.util.List;

              class MyTest {
                  void test(InOrder inOrder, List<String> mockedList) {
                      /*~~>*/inOrder.verify(mockedList);
                      inOrder.verify(mockedList).clear();
                  }
              }
              """
          )
        );
    }

    @Test
    void verifyNoInteractionsIsComplete() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              import static org.mockito.Mockito.verifyNoInteractions;
              import static org.mockito.Mockito.verifyNoMoreInteractions;

              class MyTest {
                  void test(List<String> mockedList) {
                      verifyNoMoreInteractions(mockedList);
                      verifyNoInteractions(mockedList);
                  }
              }
              """
          )
        );
    }

    @Test
    void otherStatementPositions() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test(boolean b, int k, Object o) {
                      if (b) o.hashCode(); else assertThat(o);
                      while (b) assertThat(o);
                      for (int i = 0; i < k; i++) assertThat(o);
                      for (Object e : new Object[]{o}) assertThat(e);
                      do assertThat(o); while (b);
                      label: assertThat(o);
                      switch (k) {
                          case 1:
                              assertThat(o);
                              break;
                          default:
                              assertThat(o).isNotNull();
                      }
                      switch (k) {
                          case 1 -> assertThat(o);
                          default -> assertThat(o).isNotNull();
                      }
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test(boolean b, int k, Object o) {
                      if (b) o.hashCode(); else /*~~>*/assertThat(o);
                      while (b) /*~~>*/assertThat(o);
                      for (int i = 0; i < k; i++) /*~~>*/assertThat(o);
                      for (Object e : new Object[]{o}) /*~~>*/assertThat(e);
                      do /*~~>*/assertThat(o); while (b);
                      label: /*~~>*/assertThat(o);
                      switch (k) {
                          case 1:
                              /*~~>*/assertThat(o);
                              break;
                          default:
                              assertThat(o).isNotNull();
                      }
                      switch (k) {
                          case 1 -> /*~~>*/assertThat(o);
                          default -> assertThat(o).isNotNull();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void switchExpressionValueIsNotDiscarded() {
        rewriteRun(
          //language=java
          java(
            """
              import org.assertj.core.api.AbstractObjectAssert;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test(int k, Object o) {
                      AbstractObjectAssert<?, ?> a = switch (k) {
                          case 1 -> assertThat(o);
                          default -> assertThat(o).as("other");
                      };
                      a.isNotNull();
                  }
              }
              """
          )
        );
    }

    @Test
    void recursiveComparisonConfigurationOnly() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test(Object actual, Object expected) {
                      assertThat(actual).usingRecursiveComparison().ignoringFields("id");
                      assertThat(actual).usingRecursiveComparison().ignoringFields("id").withStrictTypeChecking();
                      assertThat(actual).usingRecursiveComparison().ignoringFields("id").isEqualTo(expected);
                      assertThat(actual).usingRecursiveAssertion();
                      assertThat(actual).usingRecursiveAssertion().ignoringAllNullFields();
                      assertThat(actual).usingRecursiveAssertion().ignoringAllNullFields().hasNoNullFields();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test(Object actual, Object expected) {
                      /*~~>*/assertThat(actual).usingRecursiveComparison().ignoringFields("id");
                      /*~~>*/assertThat(actual).usingRecursiveComparison().ignoringFields("id").withStrictTypeChecking();
                      assertThat(actual).usingRecursiveComparison().ignoringFields("id").isEqualTo(expected);
                      /*~~>*/assertThat(actual).usingRecursiveAssertion();
                      /*~~>*/assertThat(actual).usingRecursiveAssertion().ignoringAllNullFields();
                      assertThat(actual).usingRecursiveAssertion().ignoringAllNullFields().hasNoNullFields();
                  }
              }
              """
          )
        );
    }

    @Test
    void lambdaOfInterfaceWithNonVoidAbstractMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import org.assertj.core.api.AbstractObjectAssert;

              import java.util.function.Consumer;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  interface AssertFactory extends Consumer<Object> {
                      AbstractObjectAssert<?, ?> create(Object o);

                      @Override
                      default void accept(Object o) {
                          create(o).isNotNull();
                      }
                  }

                  void test(Object o) {
                      use(x -> assertThat(x));
                  }

                  void use(AssertFactory factory) {
                  }
              }
              """
          )
        );
    }

    @Test
    void inOrderVerifyOfMockedStaticIsComplete() {
        rewriteRun(
          //language=java
          java(
            """
              import org.mockito.InOrder;
              import org.mockito.MockedStatic;

              import java.util.UUID;

              class MyTest {
                  void test(InOrder inOrder, MockedStatic<UUID> mocked) {
                      inOrder.verify(mocked, () -> UUID.randomUUID());
                  }
              }
              """
          )
        );
    }

    @Test
    void kotlinIsSkipped() {
        rewriteRun(
          spec -> spec.parser(KotlinParser.builder()
            .classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3")),
          //language=kotlin
          kotlin(
            """
              import org.assertj.core.api.Assertions.assertThat

              class MyTest {
                  fun test(list: List<String>) {
                      val asserts = list.map { assertThat(it) }
                      asserts.forEach { it.isNotEmpty() }
                  }
              }
              """
          )
        );
    }

    @Test
    void groovyIsSkipped() {
        rewriteRun(
          spec -> spec.parser(GroovyParser.builder()
            .classpathFromResource(new InMemoryExecutionContext(), "assertj-core-3")),
          //language=groovy
          groovy(
            """
              import static org.assertj.core.api.Assertions.assertThat

              class MyTest {
                  void test(List<String> list) {
                      def asserts = list.collect { assertThat(it) }
                      asserts.each { it.isNotEmpty() }
                  }
              }
              """
          )
        );
    }
}
