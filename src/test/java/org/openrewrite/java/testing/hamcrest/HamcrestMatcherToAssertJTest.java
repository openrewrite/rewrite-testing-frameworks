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

class HamcrestMatcherToAssertJTest implements RewriteTest {
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
              spec -> spec.recipe(new HamcrestMatcherToAssertJ("not", "isNotEqualTo")),
              //language=java
              java("""
                import org.junit.jupiter.api.Test;
                import static org.hamcrest.MatcherAssert.assertThat;
                import static org.hamcrest.Matchers.not;
                import static org.hamcrest.Matchers.containsString;
                                
                class ATest {
                    @Test
                    void test() {
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
              spec -> spec.recipe(new HamcrestMatcherToAssertJ("is", "isEqualTo")),
              //language=java
              java("""
                import org.junit.jupiter.api.Test;
                import static org.hamcrest.MatcherAssert.assertThat;
                import static org.hamcrest.Matchers.is;
                import static org.hamcrest.Matchers.equalTo;
                                
                class ATest {
                    @Test
                    void test() {
                        String str1 = "Hello world!";
                        String str2 = "Hello world!";
                        assertThat(str1, is(equalTo(str2)));
                    }
                }
                """));
        }

        @Test
        void anyOfVarargsMatcher() {
            rewriteRun(
              spec -> spec.recipe(new HamcrestMatcherToAssertJ("is", "isEqualTo")),
              //language=java
              java("""
                import org.junit.jupiter.api.Test;
                import static org.hamcrest.MatcherAssert.assertThat;
                import static org.hamcrest.Matchers.anyof;
                import static org.hamcrest.Matchers.equalTo;
                                
                class ATest {
                    @Test
                    void test() {
                        String str1 = "Hello world!";
                        String str2 = "Hello world!";
                        assertThat(str1, anyof(equalTo(str2)));
                    }
                }
                """));
        }

        @Test
        void anyOfIterableMatcher() {
            rewriteRun(
              spec -> spec.recipe(new HamcrestMatcherToAssertJ("is", "isEqualTo")),
              //language=java
              java("""
                import java.util.List;
                import org.junit.jupiter.api.Test;
                import static org.hamcrest.MatcherAssert.assertThat;
                import static org.hamcrest.Matchers.anyof;
                import static org.hamcrest.Matchers.equalTo;
                                
                class ATest {
                    @Test
                    void test() {
                        String str1 = "Hello world!";
                        String str2 = "Hello world!";
                        assertThat(str1, anyof(List.of(equalTo(str2))));
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
              spec -> spec.recipe(new HamcrestMatcherToAssertJ("isEmptyString", "isEmpty")),
              //language=java
              java("""
                  import org.junit.jupiter.api.Test;
                                
                  import static org.hamcrest.MatcherAssert.assertThat;
                  import static org.hamcrest.Matchers.isEmptyString;
                              
                  class ATest {
                      @Test
                      void test() {
                          String str1 = "Hello world!";
                          assertThat(str1, isEmptyString());
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                                
                  import static org.assertj.core.api.Assertions.assertThat;
                                
                  class ATest {
                      @Test
                      void test() {
                          String str1 = "Hello world!";
                          assertThat(str1).isEmpty();
                      }
                  }
                  """)
            );
        }
    }

    @Nested
    class TwoArguments {
        @Test
        @DocumentExample
        void equalToString() {
            rewriteRun(
              spec -> spec.recipe(new HamcrestMatcherToAssertJ("equalTo", "isEqualTo")),
              //language=java
              java("""
                  import org.junit.jupiter.api.Test;
                                
                  import static org.hamcrest.MatcherAssert.assertThat;
                  import static org.hamcrest.Matchers.equalTo;
                              
                  class ATest {
                      @Test
                      void test() {
                          String str1 = "Hello world!";
                          String str2 = "Hello world!";
                          assertThat(str1, equalTo(str2));
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                                
                  import static org.assertj.core.api.Assertions.assertThat;
                                
                  class ATest {
                      @Test
                      void test() {
                          String str1 = "Hello world!";
                          String str2 = "Hello world!";
                          assertThat(str1).isEqualTo(str2);
                      }
                  }
                  """)
            );
        }

        @Test
        void equalToStringLiteral() {
            rewriteRun(
              spec -> spec.recipe(new HamcrestMatcherToAssertJ("equalTo", "isEqualTo")),
              //language=java
              java("""
                  import org.junit.jupiter.api.Test;
                                
                  import static org.hamcrest.MatcherAssert.assertThat;
                  import static org.hamcrest.Matchers.equalTo;
                              
                  class ATest {
                      @Test
                      void test() {
                          String str1 = "Hello world!";
                          assertThat(str1, equalTo("Hello world!"));
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                                
                  import static org.assertj.core.api.Assertions.assertThat;
                                
                  class ATest {
                      @Test
                      void test() {
                          String str1 = "Hello world!";
                          assertThat(str1).isEqualTo("Hello world!");
                      }
                  }
                  """)
            );
        }

        @Test
        void equalToObject() {
            rewriteRun(
              spec -> spec.recipe(new HamcrestMatcherToAssertJ("equalTo", "isEqualTo")),
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
                                
                  class ATest {
                      @Test
                      void test() {
                          Biscuit theBiscuit = new Biscuit("Ginger");
                          Biscuit myBiscuit = new Biscuit("Ginger");
                          assertThat(theBiscuit, equalTo(myBiscuit));
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                                
                  import static org.assertj.core.api.Assertions.assertThat;
                                
                  class ATest {
                      @Test
                      void test() {
                          Biscuit theBiscuit = new Biscuit("Ginger");
                          Biscuit myBiscuit = new Biscuit("Ginger");
                          assertThat(theBiscuit).isEqualTo(myBiscuit);
                      }
                  }
                  """)
            );
        }

        @Test
        void lessThanNumber() {
            rewriteRun(
              spec -> spec.recipe(new HamcrestMatcherToAssertJ("lessThan", "isLessThan")),
              //language=java
              java("""
                  import org.junit.jupiter.api.Test;
                                
                  import static org.hamcrest.MatcherAssert.assertThat;
                  import static org.hamcrest.Matchers.lessThan;
                                
                  class ATest {
                      @Test
                      void test() {
                          int intA = 1;
                          int intB = 1;
                          assertThat(intA, lessThan(intB));
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                                
                  import static org.assertj.core.api.Assertions.assertThat;
                                
                  class ATest {
                      @Test
                      void test() {
                          int intA = 1;
                          int intB = 1;
                          assertThat(intA).isLessThan(intB);
                      }
                  }
                  """)
            );
        }

        @SuppressWarnings("DataFlowIssue")
        @Test
        void containsInAnyOrderWithArray() {
            rewriteRun(
              spec -> spec.recipe(new HamcrestMatcherToAssertJ("containsInAnyOrder", "containsExactlyInAnyOrder")),
              //language=java
              java("""
                  import org.junit.jupiter.api.Test;
                                
                  import java.util.ArrayList;
                  import java.util.List;
                  import static org.hamcrest.MatcherAssert.assertThat;
                  import static org.hamcrest.Matchers.containsInAnyOrder;
                              
                  class ATest {
                      @Test
                      void test() {
                          List<String> list = new ArrayList<>();
                          List<String> states = null;
                          assertThat(list, containsInAnyOrder(states.toArray()));
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                                
                  import java.util.ArrayList;
                  import java.util.List;
                  
                  import static org.assertj.core.api.Assertions.assertThat;
                              
                  class ATest {
                      @Test
                      void test() {
                          List<String> list = new ArrayList<>();
                          List<String> states = null;
                          assertThat(list).containsExactlyInAnyOrder(states.toArray());
                      }
                  }
                  """)
            );
        }

        @Test
        void closeToTest() {
            rewriteRun(
              spec -> spec.recipe(new HamcrestMatcherToAssertJ("closeTo", "isCloseTo")),
              //language=java
              java("""
                  import org.junit.jupiter.api.Test;
                  
                  import static org.hamcrest.MatcherAssert.assertThat;
                  import static org.hamcrest.Matchers.closeTo;
                  
                  class ATest {
                      @Test
                      void replaceCloseTo() {
                          assertThat(1.0, closeTo(2.0, 1.0));
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                  
                  import static org.assertj.core.api.Assertions.assertThat;
                  import static org.assertj.core.api.Assertions.within;
                  
                  class ATest {
                      @Test
                      void replaceCloseTo() {
                          assertThat(1.0).isCloseTo(2.0, within(1.0));
                      }
                  }
                  """)
            );
        }

        @Test
        void closeToWorksWithBigDecimal() {
            rewriteRun(
              spec -> spec.recipe(new HamcrestMatcherToAssertJ("closeTo", "isCloseTo")),
              //language=java
              java("""
                  import org.junit.jupiter.api.Test;
                  import java.math.BigDecimal;
                  
                  import static org.hamcrest.MatcherAssert.assertThat;
                  import static org.hamcrest.Matchers.closeTo;
                  
                  class ATest {
                      @Test
                      void replaceCloseTo() {
                          BigDecimal x = new BigDecimal("1.000005");
                          BigDecimal y = new BigDecimal("2.0");
                          BigDecimal z = new BigDecimal("0.0005");
                          assertThat(x, closeTo(y, z));
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                  import java.math.BigDecimal;
                  
                  import static org.assertj.core.api.Assertions.assertThat;
                  import static org.assertj.core.api.Assertions.within;
                  
                  class ATest {
                      @Test
                      void replaceCloseTo() {
                          BigDecimal x = new BigDecimal("1.000005");
                          BigDecimal y = new BigDecimal("2.0");
                          BigDecimal z = new BigDecimal("0.0005");
                          assertThat(x).isCloseTo(y, within(z));
                      }
                  }
                  """)
            );
        }

        @Test
        void arrayContaining() {
            rewriteRun(
              spec -> spec.recipe(new HamcrestMatcherToAssertJ("arrayContaining", "containsExactly")),
              //language=java
              java("""
                  import org.junit.jupiter.api.Test;
                  
                  import static org.hamcrest.MatcherAssert.assertThat;
                  import static org.hamcrest.Matchers.arrayContaining;
                  
                  class ATest {
                      @Test
                      void testMethod() {
                          Integer[] numbers = {1, 2, 3, 6};
                          assertThat(numbers, arrayContaining(1, 2, 3, 6));
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                  
                  import static org.assertj.core.api.Assertions.assertThat;
                  
                  class ATest {
                      @Test
                      void testMethod() {
                          Integer[] numbers = {1, 2, 3, 6};
                          assertThat(numbers).containsExactly(1, 2, 3, 6);
                      }
                  }
                  """)
            );
        }

        @Test
        void arrayContainingInAnyOrder() {
            rewriteRun(
              spec -> spec.recipe(new HamcrestMatcherToAssertJ("arrayContainingInAnyOrder", "containsOnly")),
              //language=java
              java("""
                  import org.junit.jupiter.api.Test;
                  
                  import static org.hamcrest.MatcherAssert.assertThat;
                  import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
                  
                  class ATest {
                      @Test
                      void testMethod() {
                          Integer[] numbers = {1, 2};
                          assertThat(numbers, arrayContainingInAnyOrder(2, 1));
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                  
                  import static org.assertj.core.api.Assertions.assertThat;
                  
                  class ATest {
                      @Test
                      void testMethod() {
                          Integer[] numbers = {1, 2};
                          assertThat(numbers).containsOnly(2, 1);
                      }
                  }
                  """)
            );
        }

        @Test
        void arrayWithSize() {
            rewriteRun(
              spec -> spec.recipe(new HamcrestMatcherToAssertJ("arrayWithSize", "hasSize")),
              //language=java
              java("""
                  import org.junit.jupiter.api.Test;
                  
                  import static org.hamcrest.MatcherAssert.assertThat;
                  import static org.hamcrest.Matchers.arrayWithSize;
                  
                  class ATest {
                      @Test
                      void testMethod() {
                          Integer[] numbers = {2};
                          assertThat(numbers, arrayWithSize(1));
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                  
                  import static org.assertj.core.api.Assertions.assertThat;
                  
                  class ATest {
                      @Test
                      void testMethod() {
                          Integer[] numbers = {2};
                          assertThat(numbers).hasSize(1);
                      }
                  }
                  """)
            );
        }

        @Test
        void emptyArray() {
            rewriteRun(
              spec -> spec.recipe(new HamcrestMatcherToAssertJ("emptyArray", "isEmpty")),
              //language=java
              java("""
                  import org.junit.jupiter.api.Test;
                  
                  import static org.hamcrest.MatcherAssert.assertThat;
                  import static org.hamcrest.Matchers.emptyArray;
                  
                  class ATest {
                      @Test
                      void testMethod() {
                          Integer[] numbers = {};
                          assertThat(numbers, emptyArray());
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                  
                  import static org.assertj.core.api.Assertions.assertThat;
                  
                  class ATest {
                      @Test
                      void testMethod() {
                          Integer[] numbers = {};
                          assertThat(numbers).isEmpty();
                      }
                  }
                  """)
            );
        }

        @Test
        void hasItemInArray() {
            rewriteRun(
              spec -> spec.recipe(new HamcrestMatcherToAssertJ("hasItemInArray", "contains")),
              //language=java
              java("""
                  import org.junit.jupiter.api.Test;
                  
                  import static org.hamcrest.MatcherAssert.assertThat;
                  import static org.hamcrest.Matchers.hasItemInArray;
                  
                  class ATest {
                      @Test
                      void testMethod() {
                          Integer[] numbers = {1, 2, 3};
                          assertThat(numbers, hasItemInArray(1));
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                  
                  import static org.assertj.core.api.Assertions.assertThat;
                  
                  class ATest {
                      @Test
                      void testMethod() {
                          Integer[] numbers = {1, 2, 3};
                          assertThat(numbers).contains(1);
                      }
                  }
                  """)
            );
        }
    }

    @Nested
    class ThreeArguments {
        @Test
        void reasonAsLiteral() {
            rewriteRun(
              spec -> spec.recipe(new HamcrestMatcherToAssertJ("equalTo", "isEqualTo")),
              //language=java
              java("""
                  import org.junit.jupiter.api.Test;
                                
                  import static org.hamcrest.MatcherAssert.assertThat;
                  import static org.hamcrest.Matchers.equalTo;
                              
                  class ATest {
                      @Test
                      void test() {
                          String str1 = "Hello world!";
                          String str2 = "Hello world!";
                          assertThat("Should match", str1, equalTo(str2));
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                                
                  import static org.assertj.core.api.Assertions.assertThat;
                                
                  class ATest {
                      @Test
                      void test() {
                          String str1 = "Hello world!";
                          String str2 = "Hello world!";
                          assertThat(str1).as("Should match").isEqualTo(str2);
                      }
                  }
                  """)
            );
        }

        @Test
        void reasonAsMethodCall() {
            rewriteRun(
              spec -> spec.recipe(new HamcrestMatcherToAssertJ("equalTo", "isEqualTo")),
              //language=java
              java("""
                  import org.junit.jupiter.api.Test;
                                
                  import static org.hamcrest.MatcherAssert.assertThat;
                  import static org.hamcrest.Matchers.equalTo;
                              
                  class ATest {
                      @Test
                      void test() {
                          String str1 = "Hello world!";
                          String str2 = "Hello world!";
                          assertThat(reason(), str1, equalTo(str2));
                      }
                      
                      String reason() {
                          return "Should match";
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                                
                  import static org.assertj.core.api.Assertions.assertThat;
                                
                  class ATest {
                      @Test
                      void test() {
                          String str1 = "Hello world!";
                          String str2 = "Hello world!";
                          assertThat(str1).as(reason()).isEqualTo(str2);
                      }
                      
                      String reason() {
                          return "Should match";
                      }
                  }
                  """)
            );
        }
    }
}