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
package org.openrewrite.java.testing.assertj;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SimplifyChainedAssertJAssertionWithOptionalTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "junit-jupiter-api-5.9", "assertj-core-3.24"));
    }


    @DocumentExample
    @Test
    void simplifyPresenceAssertion() {
        rewriteRun(
          spec -> spec.recipes(
            new SimplifyChainedAssertJAssertion("isPresent", "isTrue", "isPresent", "java.util.Optional"),
            new SimplifyChainedAssertJAssertion("isEmpty", "isTrue", "isEmpty", "java.util.Optional"),
            new SimplifyChainedAssertJAssertion("isPresent", "isFalse", "isNotPresent", "java.util.Optional"),
            new SimplifyChainedAssertJAssertion("isEmpty", "isFalse", "isNotEmpty", "java.util.Optional")
          ),
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.util.Optional;
              import org.junit.jupiter.api.Test;
              
              class Test {
              
                  @Test
                  void simpleTest() {
                      Optional<String> o = Optional.empty();
                      assertThat(o.isPresent()).isTrue();
                      assertThat(o.isEmpty()).isTrue();
                      assertThat(o.isPresent()).isFalse();
                      assertThat(o.isEmpty()).isFalse();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.util.Optional;
              import org.junit.jupiter.api.Test;
              
              class Test {
              
                  @Test
                  void simpleTest() {
                      Optional<String> o = Optional.empty();
                      assertThat(o).isPresent();
                      assertThat(o).isEmpty();
                      assertThat(o).isNotPresent();
                      assertThat(o).isNotEmpty();
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifiyEqualityAssertion() {
        rewriteRun(
          spec -> spec.recipes(
            new SimplifyChainedAssertJAssertion("get", "isEqualTo", "contains", "java.util.Optional"),
            new SimplifyChainedAssertJAssertion("get", "isSameAs", "containsSame", "java.util.Optional")
          ),
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.util.Optional;
              import org.junit.jupiter.api.Test;
              
              class Test {
              
                  @Test
                  void simpleTest() {
                      Optional<String> o = Optional.empty();
                      assertThat(o.get()).isEqualTo("foo");
                      assertThat(o.get()).isSameAs("foo");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.util.Optional;
              import org.junit.jupiter.api.Test;
              
              class Test {
              
                  @Test
                  void simpleTest() {
                      Optional<String> o = Optional.empty();
                      assertThat(o).contains("foo");
                      assertThat(o).containsSame("foo");
                  }
              }
              """
          )
        );
    }

}
