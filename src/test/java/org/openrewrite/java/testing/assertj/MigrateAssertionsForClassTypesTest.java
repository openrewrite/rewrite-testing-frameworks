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

class MigrateAssertionsForClassTypesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3"))
          .recipe(new MigrateAssertionsForClassTypes());
    }

    @DocumentExample
    @Test
    void emitsAssertThatObjectOnAssertionsWhenRunOnItsOwn() {
        //language=java
        rewriteRun(
          java(
            """
              import org.assertj.core.api.AssertionsForClassTypes;

              import java.util.Set;

              class Test {
                  void method(Set<String> set) {
                      AssertionsForClassTypes.assertThat(set).hasNoNullFieldsOrProperties();
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              import java.util.Set;

              class Test {
                  void method(Set<String> set) {
                      Assertions.assertThatObject(set).hasNoNullFieldsOrProperties();
                  }
              }
              """
          )
        );
    }

    @Test
    void typesWithADedicatedAssertionInAssertions() {
        //language=java
        rewriteRun(
          java(
            """
              import org.assertj.core.api.AssertionsForClassTypes;

              import java.math.BigInteger;
              import java.util.concurrent.CompletionStage;
              import java.util.concurrent.Future;
              import java.util.concurrent.atomic.AtomicInteger;
              import java.util.concurrent.atomic.AtomicReference;
              import java.util.concurrent.atomic.LongAdder;

              class Test {
                  void method(AtomicInteger i, AtomicReference<String> r, LongAdder a, BigInteger b,
                              CharSequence cs, Future<String> f, CompletionStage<String> s) {
                      AssertionsForClassTypes.assertThat(i).isNotNull();
                      AssertionsForClassTypes.assertThat(r).isNotNull();
                      AssertionsForClassTypes.assertThat(a).isNotNull();
                      AssertionsForClassTypes.assertThat(b).isNotNull();
                      AssertionsForClassTypes.assertThat(cs).isNotNull();
                      AssertionsForClassTypes.assertThat(f).isNotNull();
                      AssertionsForClassTypes.assertThat(s).isNotNull();
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              import java.math.BigInteger;
              import java.util.concurrent.CompletionStage;
              import java.util.concurrent.Future;
              import java.util.concurrent.atomic.AtomicInteger;
              import java.util.concurrent.atomic.AtomicReference;
              import java.util.concurrent.atomic.LongAdder;

              class Test {
                  void method(AtomicInteger i, AtomicReference<String> r, LongAdder a, BigInteger b,
                              CharSequence cs, Future<String> f, CompletionStage<String> s) {
                      Assertions.assertThatObject(i).isNotNull();
                      Assertions.assertThatObject(r).isNotNull();
                      Assertions.assertThatObject(a).isNotNull();
                      Assertions.assertThatObject(b).isNotNull();
                      Assertions.assertThatObject(cs).isNotNull();
                      Assertions.assertThatObject(f).isNotNull();
                      Assertions.assertThatObject(s).isNotNull();
                  }
              }
              """
          )
        );
    }
}
