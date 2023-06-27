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
    void greaterThanOrEqualTo() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.greaterThanOrEqualTo;
              
              class Test {
                  @Test
                  void testGreaterThanOrEqualTo() {
                      int intt = 7;
                      assertThat(10, greaterThanOrEqualTo(intt));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              class Test {
                  @Test
                  void testGreaterThanOrEqualTo() {
                      int intt = 7;
                      assertTrue(10 >= intt);
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

    @Test
    void collections() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.Collection;
              
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.empty;
              import static org.hamcrest.Matchers.hasSize;
              
              class Test {
                  private static final Collection<String> collection = new ArrayList<>();
                  @Test
                  void testEmpty() {
                      assertThat(collection, empty());
                      assertThat(collection, hasSize(0));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.Collection;
              
              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              class Test {
                  private static final Collection<String> collection = new ArrayList<>();
                  @Test
                  void testEmpty() {
                      assertTrue(collection.isEmpty());
                      assertEquals(collection.size(), 0);
                  }
              }
              """
          ));
    }

    @Test
    void arraysAndIterables() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.util.Arrays;
              
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.emptyArray;
              import static org.hamcrest.Matchers.emptyIterable;
              
              class Test {
                  private static final Integer[] ints = new Integer[]{};
                  @Test
                  void testEmpty() {
                      assertThat(ints, emptyArray());
                      Iterable<Integer> iterable = Arrays.stream(ints).toList();
                      assertThat(iterable, emptyIterable());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.util.Arrays;
              
              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.junit.jupiter.api.Assertions.assertFalse;
              
              class Test {
                  private static final Integer[] ints = new Integer[]{};
                  @Test
                  void testEmpty() {
                      assertEquals(0, ints.length);
                      Iterable<Integer> iterable = Arrays.stream(ints).toList();
                      assertFalse(iterable.iterator().hasNext());
                  }
              }
              """
          ));
    }

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
              
              class Test {
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
              
              class Test {
                  private static final List<Integer> list = List.of();
                  @Test
                  void testInstance() {
                      assertInstanceOf(Iterable.class, list);
                      assertInstanceOf(Iterable.class, list);
                  }
              }
              """
          ));
    }

    @Test
    void isAndNotInstanceOf() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.util.List;
              
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.instanceOf;
              import static org.hamcrest.Matchers.is;
              import static org.hamcrest.Matchers.not;
              
              class Test {
                  private static final List<Integer> list = List.of();
                  @Test
                  void testInstance() {
                      assertThat(list, not(instanceOf(Integer.class)));
                      assertThat(list, is(instanceOf(Iterable.class)));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.util.List;
              
              import static org.junit.jupiter.api.Assertions.assertFalse;
              import static org.junit.jupiter.api.Assertions.assertInstanceOf;
              
              class Test {
                  private static final List<Integer> list = List.of();
                  @Test
                  void testInstance() {
                      assertFalse(Integer.class.isAssignableFrom(list.getClass()));
                      assertInstanceOf(Iterable.class, list);
                  }
              }
              """
          ));
    }

    @Test
    void lessThan() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.lessThan;
              
              class Test {
                  @Test
                  void testLessThan() {
                      int intt = 7;
                      assertThat(5, lessThan(intt));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              class Test {
                  @Test
                  void testLessThan() {
                      int intt = 7;
                      assertTrue(5 < intt);
                  }
              }
              """
          ));
    }

    @Test
    void lessThanOrEqualTo() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.lessThanOrEqualTo;
              
              class Test {
                  @Test
                  void testLessThanOrEqualTo() {
                      int intt = 7;
                      assertThat(5, lessThanOrEqualTo(intt));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              class Test {
                  @Test
                  void testLessThanOrEqualTo() {
                      int intt = 7;
                      assertTrue(5 <= intt);
                  }
              }
              """
          ));
    }

    @Test
    void nullValue() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.nullValue;
              import static org.hamcrest.Matchers.notNullValue;
              
              class Test {
                  @Test
                  void testNullValue() {
                      Integer integer = null;
                      String str = "hello world";
                      assertThat(integer, nullValue());
                      assertThat(str, notNullValue());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertNotNull;
              import static org.junit.jupiter.api.Assertions.assertNull;
              
              class Test {
                  @Test
                  void testNullValue() {
                      Integer integer = null;
                      String str = "hello world";
                      assertNull(integer);
                      assertNotNull(str);
                  }
              }
              """
          ));
    }

    @Test
    void sameInstance() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.not;
              import static org.hamcrest.Matchers.sameInstance;
              import static org.hamcrest.Matchers.theInstance;
              
              class Test {
                  private final String string = "Hello world.";
                  @Test
                  void testSameInstance() {
                      String localString = string;
                      String differentString = "Hello void.";
                      assertThat(string, sameInstance(localString));
                      assertThat(string, not(theInstance(differentString)));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertNotSame;
              import static org.junit.jupiter.api.Assertions.assertSame;
              
              class Test {
                  private final String string = "Hello world.";
                  @Test
                  void testSameInstance() {
                      String localString = string;
                      String differentString = "Hello void.";
                      assertSame(string, localString);
                      assertNotSame(string, differentString);
                  }
              }
              """
          ));
    }

    @Test
    void hasEntry() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.HashMap;
              import org.junit.jupiter.api.Test;
              
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.hasEntry;
              
              class Test {
                  @Test
                  void testHasEntry() {
                      Map<String, String> map = new HashMap<>();
                      assertThat(map, hasEntry("hello", "world"));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.util.HashMap;
              
              import static org.junit.jupiter.api.Assertions.assertEquals;
              
              class Test {
                  @Test
                  void testHasEntry() {
                      Map<String, String> map = new HashMap<>();
                      assertEquals("world", map.get("hello"));
                  }
              }
              """
          ));
    }
}
