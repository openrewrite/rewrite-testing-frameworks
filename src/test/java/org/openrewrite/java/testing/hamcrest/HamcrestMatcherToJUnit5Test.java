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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class HamcrestMatcherToJUnit5Test implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9", "hamcrest-2.2"))
          .recipe(new HamcrestMatcherToJUnit5());
    }

    @Test
    void equalToObject() {
        //language=java
        rewriteRun(
          java(
                """
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

    @DocumentExample
    @Test
    void equalToString() {
        //language=java
        rewriteRun(
          java(
          """
            import org.junit.jupiter.api.Test;
            import static org.hamcrest.MatcherAssert.assertThat;
            import static org.hamcrest.Matchers.equalTo;
            
            class ATest {
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
            
            class ATest {
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
    void notEqualToString() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.equalTo;
              import static org.hamcrest.Matchers.not;
              
              class ATest {
                  @Test
                  void testEquals() {
                      String str1 = "Hello world!";
                      String str2 = "Hello world!";
                      assertThat(str1, not(equalTo(str2)));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertNotEquals;
              
              class ATest {
                  @Test
                  void testEquals() {
                      String str1 = "Hello world!";
                      String str2 = "Hello world!";
                      assertNotEquals(str1, str2);
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
              
              class ATest {
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
              
              class ATest {
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
              
              class ATest {
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
              
              class ATest {
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
              
              class ATest {
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
              
              class ATest {
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
              
              class ATest {
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
              
              class ATest {
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
              
              class ATest {
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
              
              class ATest {
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
    void lessThan() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.lessThan;
              
              class ATest {
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
              
              class ATest {
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
              
              class ATest {
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
              
              class ATest {
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
              
              class ATest {
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
              
              class ATest {
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
              
              class ATest {
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
              
              class ATest {
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
              import java.util.Map;
              import org.junit.jupiter.api.Test;
              
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.hasEntry;
              
              class ATest {
                  @Test
                  void testHasEntry() {
                      Map<String, String> map = new HashMap<>();
                      assertThat(map, hasEntry("hello", "world"));
                  }
              }
              """,
            """
              import java.util.HashMap;
              import java.util.Map;
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertEquals;
              
              class ATest {
                  @Test
                  void testHasEntry() {
                      Map<String, String> map = new HashMap<>();
                      assertEquals("world", map.get("hello"));
                  }
              }
              """
          ));
    }

    @Test
    void hasKey() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.HashMap;
              import java.util.Map;
              import org.junit.jupiter.api.Test;
              
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.hasKey;
              
              class ATest {
                  @Test
                  void testHasKey() {
                      Map<String, String> map = new HashMap<>();
                      assertThat(map, hasKey("hello"));
                  }
              }
              """,
            """
              import java.util.HashMap;
              import java.util.Map;
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              class ATest {
                  @Test
                  void testHasKey() {
                      Map<String, String> map = new HashMap<>();
                      assertTrue(map.containsKey("hello"));
                  }
              }
              """
          ));
    }

    @Test
    void hasValue() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.HashMap;
              import java.util.Map;
              import org.junit.jupiter.api.Test;
              
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.hasValue;
              
              class ATest {
                  @Test
                  void testHasValue() {
                      Map<String, String> map = new HashMap<>();
                      assertThat(map, hasValue("world"));
                  }
              }
              """,
            """
              import java.util.HashMap;
              import java.util.Map;
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              class ATest {
                  @Test
                  void testHasValue() {
                      Map<String, String> map = new HashMap<>();
                      assertTrue(map.containsValue("world"));
                  }
              }
              """
          ));
    }

    @Test
    void typeCompatibleWith() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              import org.junit.jupiter.api.Test;
              
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.typeCompatibleWith;
              
              class ATest {
                  @Test
                  void testTypeCompatibleWith() {
                      assertThat(List.class, typeCompatibleWith(Iterable.class));
                  }
              }
              """,
            """
              import java.util.List;
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              class ATest {
                  @Test
                  void testTypeCompatibleWith() {
                      assertTrue(Iterable.class.isAssignableFrom(List.class));
                  }
              }
              """
          ));
    }

    @Test
    void containsString() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.containsString;
              
              class ATest {
                  @Test
                  void testTypeCompatibleWith() {
                      String string = "hello world";
                      String substring = "llo wor";
                      assertThat(string, containsString(substring));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              class ATest {
                  @Test
                  void testTypeCompatibleWith() {
                      String string = "hello world";
                      String substring = "llo wor";
                      assertTrue(string.contains(substring));
                  }
              }
              """
          ));
    }

    @Test
    void endsWith() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.endsWith;
              
              class ATest {
                  @Test
                  void testTypeCompatibleWith() {
                      String string = "hello world";
                      String suffix = "world";
                      assertThat(string, endsWith(suffix));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              class ATest {
                  @Test
                  void testTypeCompatibleWith() {
                      String string = "hello world";
                      String suffix = "world";
                      assertTrue(string.endsWith(suffix));
                  }
              }
              """
          ));
    }

    @Test
    void equalToIgnoringCase() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.equalToIgnoringCase;
              
              class ATest {
                  @Test
                  void testTypeCompatibleWith() {
                      String string1 = "hELLo WoRLD";
                      String string2 = "HeLlO WOrLd";
                      assertThat(string1, equalToIgnoringCase(string2));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              class ATest {
                  @Test
                  void testTypeCompatibleWith() {
                      String string1 = "hELLo WoRLD";
                      String string2 = "HeLlO WOrLd";
                      assertTrue(string1.equalsIgnoreCase(string2));
                  }
              }
              """
          ));
    }

    @Test
    void hasToString() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.hasToString;
              
              class ATest {
                  @Test
                  void testTypeCompatibleWith() {
                      StringBuilder sb = new StringBuilder();
                      sb.append("hello");
                      assertThat(sb, hasToString("hello"));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertEquals;
              
              class ATest {
                  @Test
                  void testTypeCompatibleWith() {
                      StringBuilder sb = new StringBuilder();
                      sb.append("hello");
                      assertEquals(sb.toString(), "hello");
                  }
              }
              """
          ));
    }

    @Test
    void startsWith() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.startsWith;
              
              class ATest {
                  @Test
                  void testTypeCompatibleWith() {
                      String string = "hello world";
                      String prefix = "hello";
                      assertThat(string, startsWith(prefix));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              class ATest {
                  @Test
                  void testTypeCompatibleWith() {
                      String string = "hello world";
                      String prefix = "hello";
                      assertTrue(string.startsWith(prefix));
                  }
              }
              """
          ));
    }

    @Test
    void assertionsWithReason() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              import org.junit.jupiter.api.Test;
              
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.startsWith;
              
              class ATest {
                  private static final List<Integer> list = List.of();
                  
                  @Test
                  void testAssertionsWithReason() {
                      String string = "hello world";
                      String prefix = "hello";
                      assertThat("String does not start with given prefix.", string, startsWith(prefix));
                  }
              }
              """,
            """
              import java.util.List;
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              class ATest {
                  private static final List<Integer> list = List.of();
                  
                  @Test
                  void testAssertionsWithReason() {
                      String string = "hello world";
                      String prefix = "hello";
                      assertTrue(string.startsWith(prefix), "String does not start with given prefix.");
                  }
              }
              """
          ));
    }

    @Test
    void shouldNotRewriteCaseTest() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.containsString;
              
              class ATest {
                  @Test
                  void testTypeCompatibleWith() {
                      String string = "hello world";
                      String substring = "llo wor";
                      assertThat(string, containsString(substring));
                      assertThat("String does not contain the substring", string.contains(substring));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              class ATest {
                  @Test
                  void testTypeCompatibleWith() {
                      String string = "hello world";
                      String substring = "llo wor";
                      assertTrue(string.contains(substring));
                      assertThat("String does not contain the substring", string.contains(substring));
                  }
              }
              """
          ));
    }
}
