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
package org.openrewrite.java.testing.fest2;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class Fest2ToAssertJTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "fest-assert-core-2.0M10", "assertj-core-3.24", "junit-jupiter-api-5.9"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing.fest")
            .build()
            .activateRecipes("org.openrewrite.java.testing.fest.MigrateFest2ToAssertJ"));
    }

    @Test
    void FestAssertThatToAssertj() {
        rewriteRun(
          //language=java
          java("""
              import org.assertj.core.api.Assertions;
              import org.junit.jupiter.api.Test;

              import static org.fest.assertions.api.Assertions.assertThat;

                            class TestAClass {
                                @Test
                                void foo() {
                                    Assertions.assertThat(1 + 2).isEqualTo(3);
                                    assertThat(2+3).isEqualTo(5);
                                }
                            }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;

                            class TestAClass {
                                @Test
                                void foo() {
                                    assertThat(1 + 2).isEqualTo(3);
                                    assertThat(2+3).isEqualTo(5);
                                }
                            }
              """));
    }

    @Test
    void FestisLenientEqualsToByIgnoringFieldsToAssertj() {
        rewriteRun(
          //language=java
          java("""
              import org.junit.jupiter.api.Test;
              
              import static org.fest.assertions.api.Assertions.assertThat;
              
              class TestAClass {
              
                  class Thing {
                      public Thing(String description, String name) {
                          this.description = description;
                          this.name = name;
                      }
              
                      String name;
                      String description;
                  }
              
                  @Test
                  void foo() {
                      assertThat(new Thing("bla", "blabla")).isLenientEqualsToByIgnoringFields(new Thing("bla", "bladiebla"), "name");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class TestAClass {
              
                  class Thing {
                      public Thing(String description, String name) {
                          this.description = description;
                          this.name = name;
                      }
              
                      String name;
                      String description;
                  }
              
                  @Test
                  void foo() {
                      assertThat(new Thing("bla", "blabla")).isEqualToIgnoringGivenFields(new Thing("bla", "bladiebla"), "name");
                  }
              }
              """));
    }
}
