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

import org.openrewrite.java.JavaParser;

import static org.openrewrite.java.Assertions.java;


import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class SimplifyAssertOnOptionalTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpath("assertj-core"))
                .recipe(new SimplifyAssertOnOptionalRecipes());
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @Test
    void simplified() {
        rewriteRun(
                //language=java
                java(
                        """
                          import static org.assertj.core.api.Assertions.*;
                          import java.util.Optional;
                          import org.junit.jupiter.api.Test;
                          
                          class Test {
                          
                              @Test
                              void simpleTest() {
                                  Optional<String> o = Optional.empty();
                                  assertThat(o.isEmpty()).isFalse();
                                  assertThat(o.isEmpty()).isTrue();
                                  assertThat(o.isPresent()).isFalse();
                                  assertThat(o.isPresent()).isTrue();
                              }
                          }
                          """,
                        """
                          import static org.assertj.core.api.Assertions.*;
                          import java.util.Optional;
                          import org.junit.jupiter.api.Test;
                          
                          class Test {
                          
                              @Test
                              void simpleTest() {
                                  Optional<String> o = Optional.empty();
                                  assertThat(o).isPresent();
                                  assertThat(o).isEmpty();
                                  assertThat(o).isEmpty();
                                  assertThat(o).isPresent();
                              }
                          }
                          """
                )
        );
    }

    @Test
    void unchanged() {
        rewriteRun(
                spec -> spec.recipe(new SimplifyAssertOnOptionalRecipes()),
                //language=java
                java(
                        """
                          class Test {
                              boolean unchanged1 = booleanExpression() ? booleanExpression() : !booleanExpression();
                              boolean unchanged2 = booleanExpression() ? true : !booleanExpression();
                              boolean unchanged3 = booleanExpression() ? booleanExpression() : false;

                              boolean booleanExpression() {
                                return true;
                              }
                          }
                          """
                )
        );
    }
}