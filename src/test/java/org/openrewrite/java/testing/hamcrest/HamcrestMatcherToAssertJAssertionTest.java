/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.testing.hamcrest;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class HamcrestMatcherToAssertJAssertionTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-jupiter-api-5.9",
              "hamcrest-2.2",
              "assertj-core-3.24"));
    }

    @Nested
    class DoNotConvert {
        @Test
        void notMatcher() {
            rewriteRun(
              spec -> spec.recipe(new HamcrestMatcherToAssertJAssertion("not", "isNotEqualTo")),
              //language=java
              java("""
                import org.junit.jupiter.api.Test;
                import static org.hamcrest.MatcherAssert.assertThat;
                import static org.hamcrest.Matchers.not;
                import static org.hamcrest.Matchers.containsString;
                                
                class BiscuitTest {
                    @Test
                    void testEquals() {
                        String str1 = "Hello world!";
                        String str2 = "Hello world!";
                        assertThat(str1, not(containsString(str2)));
                    }
                }
                """));
        }

        @Test
        void isMatcher() {
            rewriteRun(
              spec -> spec.recipe(new HamcrestMatcherToAssertJAssertion("is", "isEqualTo")),
              //language=java
              java("""
                import org.junit.jupiter.api.Test;
                import static org.hamcrest.MatcherAssert.assertThat;
                import static org.hamcrest.Matchers.is;
                import static org.hamcrest.Matchers.equalTo;
                                
                class BiscuitTest {
                    @Test
                    void testEquals() {
                        String str1 = "Hello world!";
                        String str2 = "Hello world!";
                        assertThat(str1, is(equalTo(str2)));
                    }
                }
                """));
        }
    }

    @Nested
    class NoArgument {
        @Test
        void isEmpty() {
            rewriteRun(
              spec -> spec.recipe(new HamcrestMatcherToAssertJAssertion("isEmptyString", "isEmpty")),
              //language=java
              java("""
                  import org.junit.jupiter.api.Test;
                                
                  import static org.hamcrest.MatcherAssert.assertThat;
                  import static org.hamcrest.Matchers.isEmptyString;
                              
                  class BiscuitTest {
                      @Test
                      void testEquals() {
                          String str1 = "Hello world!";
                          assertThat(str1, isEmptyString());
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                                
                  import static org.assertj.core.api.Assertions.assertThat;
                                
                  class BiscuitTest {
                      @Test
                      void testEquals() {
                          String str1 = "Hello world!";
                          assertThat(str1).isEmpty();
                      }
                  }
                  """)
            );
        }
    }

    @Nested
    class SingleArgument {
        @Test
        void equalToObject() {
            rewriteRun(
              spec -> spec.recipe(new HamcrestMatcherToAssertJAssertion("equalTo", "isEqualTo")),
              //language=java
              java("""
                class Biscuit {
                    String name;
                    Biscuit(String name) {
                        this.name = name;
                    }
                }
                """),
              //language=java
              java("""
                  import org.junit.jupiter.api.Test;
                                
                  import static org.hamcrest.MatcherAssert.assertThat;
                  import static org.hamcrest.Matchers.equalTo;
                                
                  class BiscuitTest {
                      @Test
                      void testEquals() {
                          Biscuit theBiscuit = new Biscuit("Ginger");
                          Biscuit myBiscuit = new Biscuit("Ginger");
                          assertThat(theBiscuit, equalTo(myBiscuit));
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                                
                  import static org.assertj.core.api.Assertions.assertThat;
                                
                  class BiscuitTest {
                      @Test
                      void testEquals() {
                          Biscuit theBiscuit = new Biscuit("Ginger");
                          Biscuit myBiscuit = new Biscuit("Ginger");
                          assertThat(theBiscuit).isEqualTo(myBiscuit);
                      }
                  }
                  """)
            );
        }

        @Test
        @DocumentExample
        void equalToString() {
            rewriteRun(
              spec -> spec.recipe(new HamcrestMatcherToAssertJAssertion("equalTo", "isEqualTo")),
              //language=java
              java("""
                  import org.junit.jupiter.api.Test;
                                
                  import static org.hamcrest.MatcherAssert.assertThat;
                  import static org.hamcrest.Matchers.equalTo;
                              
                  class BiscuitTest {
                      @Test
                      void testEquals() {
                          String str1 = "Hello world!";
                          String str2 = "Hello world!";
                          assertThat(str1, equalTo(str2));
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                                
                  import static org.assertj.core.api.Assertions.assertThat;
                                
                  class BiscuitTest {
                      @Test
                      void testEquals() {
                          String str1 = "Hello world!";
                          String str2 = "Hello world!";
                          assertThat(str1).isEqualTo(str2);
                      }
                  }
                  """)
            );
        }
    }
}
