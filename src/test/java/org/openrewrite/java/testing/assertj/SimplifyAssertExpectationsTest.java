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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SimplifyAssertExpectationsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpath("assertj-core"))
                .recipe(new SimplifyAssertExpectationsRecipes());
    }

    @Test
    void simplifyIsZero() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.util.List;
              import org.junit.jupiter.api.Test;
              
              class Test {
              
                  @Test
                  void simpleTest() {
                      List<String> list = List.of();
                      assertThat(list.size()).isEqualTo(0);
                      assertThat(0).isEqualTo(0);
                      assertThat(list.size()).isNotEqualTo(0);
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.util.List;
              import org.junit.jupiter.api.Test;
              
              class Test {
              
                  @Test
                  void simpleTest() {
                      List<String> list = List.of();
                      assertThat(list.size()).isZero();
                      assertThat(0).isZero();
                      assertThat(list.size()).isNotZero();
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyIsNull() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import org.junit.jupiter.api.Test;
              
              class Test {
              
                  @Test
                  void simpleTest() {
                      String s = null;
                      assertThat(s).isEqualTo(null);
                      assertThat(s).isNotEqualTo(null);
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import org.junit.jupiter.api.Test;
              
              class Test {
              
                  @Test
                  void simpleTest() {
                      String s = null;
                      assertThat(s).isNull();
                      assertThat(s).isNull();
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyIsGreaterThanOrEqual() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import org.junit.jupiter.api.Test;
              
              class Test {
              
                  @Test
                  void simpleTest() {
                      assertThat(2 >= 1).isTrue();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import org.junit.jupiter.api.Test;
              
              class Test {
              
                  @Test
                  void simpleTest() {
                      assertThat(2).isGreaterThanOrEqualTo(1);
                  }
              }
              """
          )
        );
    }

}