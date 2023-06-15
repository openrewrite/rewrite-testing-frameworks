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
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateFromHamcrestTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9", "hamcrest-2.2"))
          .recipe(new MigrateFromHamcrest());
    }

    @Test
    void equalToObject() {
        //language=java
        rewriteRun(
          java("""
            class Biscuit {
                String name;
                Biscuit(String name) {
                    this.name = name;
                }
            }
            """),
          java(
            """
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
              
              import static org.junit.jupiter.api.Assertions.assertEquals;
              
              class BiscuitTest {
                  @Test
                  void testEquals() {
                      Biscuit theBiscuit = new Biscuit("Ginger");
                      Biscuit myBiscuit = new Biscuit("Ginger");
                      assertEquals(theBiscuit, myBiscuit);
                  }
              }
              """
          ));
    }

    @Test
    void equalToString() {
        //language=java
        rewriteRun(
          java(
          """
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
            
            import static org.junit.jupiter.api.Assertions.assertEquals;
            
            class BiscuitTest {
                @Test
                void testEquals() {
                    String str1 = "Hello world!";
                    String str2 = "Hello world!";
                    assertEquals(str1, str2);
                }
            }
            """
        ));
    }

    @Test
    void greaterThan() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.greaterThan;
              
              class BiscuitTest {
                  @Test
                  void testEquals() {
                      int intt = 7;
                      assertThat(10, greaterThan(intt));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              class BiscuitTest {
                  @Test
                  void testEquals() {
                      int intt = 7;
                      assertTrue(10 > intt);
                  }
              }
              """
          ));
    }

    @Test
    void closeTo() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.closeTo;
              
              class Test {
                  @Test
                  void testCloseTo() {
                      double dbl = 179.1;
                      assertThat(dbl, closeTo(178.2, 1.0));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              class Test {
                  @Test
                  void testCloseTo() {
                      double dbl = 179.1;
                      assertTrue(Math.abs(dbl - 178.2) < 1.0);
                  }
              }
              """
          ));
    }
}
