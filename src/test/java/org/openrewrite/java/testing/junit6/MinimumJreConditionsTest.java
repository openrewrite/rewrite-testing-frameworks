/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.testing.junit6;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MinimumJreConditionsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api"))
          .recipe(new MinimumJreConditions("17"));
    }

    @Nested
    class EnabledOnJreTests {
        @CsvSource({"JRE.JAVA_8", "value = JRE.JAVA_8", "versions = { 8 }", "JRE.JAVA_11", "value = JRE.JAVA_11", "versions = { 11 }", "JAVA_8", "JAVA_11"})
        @ParameterizedTest
        void removeTestEnabledOnOlderJre(String jre) {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledOnJre;
                  import org.junit.jupiter.api.condition.JRE;

                  import static org.junit.jupiter.api.condition.JRE.*;

                  class MyTest {
                      @Test
                      @EnabledOnJre(%s)
                      void testOnlyOnJava8() {
                          System.out.println("Java 8");
                      }

                      @Test
                      void normalTest() {
                          System.out.println("Any version");
                      }
                  }
                  """.formatted(jre),
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledOnJre;
                  import org.junit.jupiter.api.condition.JRE;

                  import static org.junit.jupiter.api.condition.JRE.*;

                  class MyTest {

                      @Test
                      void normalTest() {
                          System.out.println("Any version");
                      }
                  }
                  """
              )
            );
        }

        @CsvSource({"JRE.JAVA_17", "value = JRE.JAVA_17", "versions = 17", "versions = { 17 }", "JAVA_17"})
        @ParameterizedTest
        void removeAnnotationEnabledOnJava17(String jre) {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledOnJre;
                  import org.junit.jupiter.api.condition.JRE;

                  import static org.junit.jupiter.api.condition.JRE.*;

                  class MyTest {
                      @Test
                      @EnabledOnJre(%s)
                      void testOnJava17() {
                          System.out.println("Java 17");
                      }
                  }
                  """.formatted(jre),
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledOnJre;
                  import org.junit.jupiter.api.condition.JRE;

                  import static org.junit.jupiter.api.condition.JRE.*;

                  class MyTest {
                      @Test
                      void testOnJava17() {
                          System.out.println("Java 17");
                      }
                  }
                  """
              )
            );
        }

        @Test
        void keepAnnotationValuesForCurrentOrNewerJREs() {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledOnJre;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      @EnabledOnJre(versions = { 11, 17, 21 })
                      void testOnJavaSomeJaveVersions() {
                          System.out.println("Java 17 and 21");
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledOnJre;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      @EnabledOnJre(versions = {17, 21})
                      void testOnJavaSomeJaveVersions() {
                          System.out.println("Java 17 and 21");
                      }
                  }
                  """
              )
            );
        }

        @Test
        void dropAnnotationIfOnlyCurrentJreRemains() {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledOnJre;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      @EnabledOnJre(versions = { 11, 17 })
                      void testOnJavaSomeJaveVersions() {
                          System.out.println("Java 17");
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledOnJre;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      void testOnJavaSomeJaveVersions() {
                          System.out.println("Java 17");
                      }
                  }
                  """
              )
            );
        }

        @CsvSource({"JRE.JAVA_21", "value = JRE.JAVA_21", "versions = 21", "versions = { 21 }", "JAVA_21"})
        @ParameterizedTest
        void keepTestEnabledOnNewerJre(String jre) {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledOnJre;
                  import org.junit.jupiter.api.condition.JRE;

                  import static org.junit.jupiter.api.condition.JRE.*;

                  class MyTest {
                      @Test
                      @EnabledOnJre(%s)
                      void testOnlyOnJava21() {
                          System.out.println("Java 21");
                      }
                  }
                  """.formatted(jre)
              )
            );
        }

        @Test
        void withMinimumJava21() {
            rewriteRun(
              spec -> spec.recipe(new MinimumJreConditions("21")),
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledOnJre;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      @EnabledOnJre(JRE.JAVA_17)
                      void testOnJava17() {
                          System.out.println("Java 17");
                      }

                      @Test
                      @EnabledOnJre(JRE.JAVA_21)
                      void testOnJava21() {
                          System.out.println("Java 21");
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledOnJre;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {

                      @Test
                      void testOnJava21() {
                          System.out.println("Java 21");
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class DisabledOnJreTests {
        @CsvSource({"JRE.JAVA_8", "value = JRE.JAVA_8", "versions = { 8 }", "JRE.JAVA_11", "value = JRE.JAVA_11", "versions = { 11 }", "JAVA_8", "JAVA_11"})
        @ParameterizedTest
        void removeAnnotationDisabledOnOlderJre(String jre) {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.DisabledOnJre;
                  import org.junit.jupiter.api.condition.JRE;

                  import static org.junit.jupiter.api.condition.JRE.*;

                  class MyTest {
                      @Test
                      @DisabledOnJre(%s)
                      void testNotOnJava8() {
                          System.out.println("Not Java 8");
                      }
                  }
                  """.formatted(jre),
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.DisabledOnJre;
                  import org.junit.jupiter.api.condition.JRE;

                  import static org.junit.jupiter.api.condition.JRE.*;

                  class MyTest {
                      @Test
                      void testNotOnJava8() {
                          System.out.println("Not Java 8");
                      }
                  }
                  """
              )
            );
        }

        @Test
        void keepAnnotationValuesForCurrentOrNewerJREs() {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.DisabledOnJre;
                  import org.junit.jupiter.api.condition.JRE;

                  import static org.junit.jupiter.api.condition.JRE.*;

                  class MyTest {
                      @Test
                      @DisabledOnJre(versions = { 8, 17, 21 })
                      void testNotOnJava17() {
                          System.out.println("Not Java 17 or 21");
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.DisabledOnJre;
                  import org.junit.jupiter.api.condition.JRE;

                  import static org.junit.jupiter.api.condition.JRE.*;

                  class MyTest {
                      @Test
                      @DisabledOnJre(versions = {17, 21})
                      void testNotOnJava17() {
                          System.out.println("Not Java 17 or 21");
                      }
                  }
                  """
              )
            );
        }

        @CsvSource({"JRE.JAVA_17", "value = JRE.JAVA_17", "versions = 17", "versions = { 17 }", "JAVA_17"})
        @ParameterizedTest
        void keepDisabledOnSameJRE(String jre) {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.DisabledOnJre;
                  import org.junit.jupiter.api.condition.JRE;

                  import static org.junit.jupiter.api.condition.JRE.*;

                  class MyTest {
                      @Test
                      @DisabledOnJre(%s)
                      void testNotOnJava17() {
                          System.out.println("Not Java 17");
                      }
                  }
                  """.formatted(jre)
              )
            );
        }

        @CsvSource({"JRE.JAVA_21", "value = JRE.JAVA_21", "versions = 21", "versions = { 21 }", "JAVA_21"})
        @ParameterizedTest
        void keepDisabledOnNewerJre(String jre) {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.DisabledOnJre;
                  import org.junit.jupiter.api.condition.JRE;

                  import static org.junit.jupiter.api.condition.JRE.*;

                  class MyTest {
                      @Test
                      @DisabledOnJre(%s)
                      void testNotOnJava21() {
                          System.out.println("Not Java 21");
                      }
                  }
                  """.formatted(jre)
              )
            );
        }
    }

    @Nested
    class EnabledForJreRange {
        @CsvSource(value = {
            "min = JRE.JAVA_8, max = JRE.JAVA_11",
            "minVersion = 8, maxVersion = 11"
        }, delimiter = ';')
        @ParameterizedTest
        void removeTestEnabledForRangeEndingBeforeMinimum(String range) {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledForJreRange;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      @EnabledForJreRange(%s)
                      void testOnJava8To11() {
                          System.out.println("Java 8-11");
                      }
                  }
                  """.formatted(range),
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledForJreRange;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                  }
                  """
              )
            );
        }

        @CsvSource(value = {
            "min = JRE.JAVA_17, max = JRE.JAVA_21",
            "minVersion = 17, maxVersion = 21"
        }, delimiter = ';')
        @ParameterizedTest
        void keepEnabledForRangeStartingAtMinimum(String range) {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledForJreRange;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      @EnabledForJreRange(%s)
                      void testOnJava17To21() {
                          System.out.println("Java 17-21");
                      }
                  }
                  """.formatted(range)
              )
            );
        }

        @CsvSource(value = {"min = JRE.JAVA_21", "minVersion = 21"}, delimiter = ';')
        @ParameterizedTest
        void keepEnabledForRangeAfterMinimum(String range) {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledForJreRange;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      @EnabledForJreRange(%s)
                      void testOnJava21AndLater() {
                          System.out.println("Java 21+");
                      }
                  }
                  """.formatted(range)
              )
            );
        }

        @CsvSource(value = {"min = JRE.JAVA_11", "minVersion = 11"}, delimiter = ';')
        @ParameterizedTest
        void handleRangeWithOnlyMin(String range) {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledForJreRange;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      @EnabledForJreRange(%s)
                      void testOnJava11AndLater() {
                          System.out.println("Java 11+");
                      }
                  }
                  """.formatted(range)
              )
            );
        }

        @CsvSource(value = {"max = JRE.JAVA_11", "maxVersion = 11"}, delimiter = ';')
        @ParameterizedTest
        void handleRangeWithOnlyMax(String range) {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledForJreRange;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      @EnabledForJreRange(%s)
                      void testUpToJava11() {
                          System.out.println("Up to Java 11");
                      }
                  }
                  """.formatted(range),
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledForJreRange;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                  }
                  """
              )
            );
        }

        @CsvSource(value = {
            "min = JRE.JAVA_8, max = JRE.JAVA_16",
            "minVersion = 8, maxVersion = 16"
        }, delimiter = ';')
        @ParameterizedTest
        void handleEnabledForRangeEndingAt16(String range) {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledForJreRange;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      @EnabledForJreRange(%s)
                      void testOnJava8To16() {
                          System.out.println("Java 8-16");
                      }
                  }
                  """.formatted(range),
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledForJreRange;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                  }
                  """
              )
            );
        }

        @CsvSource(value = {
            "min = JRE.JAVA_11, max = JRE.JAVA_21",
            "minVersion = 11, maxVersion = 21"
        }, delimiter = ';')
        @ParameterizedTest
        void handleEnabledForRangeStartingBefore17EndingAfter(String range) {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledForJreRange;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      @EnabledForJreRange(%s)
                      void testOnJava11To21() {
                          System.out.println("Java 11-21");
                      }
                  }
                  """.formatted(range)
              )
            );
        }
    }

    @Nested
    class DisabledForJreRange {
        @CsvSource(value = {
            "min = JRE.JAVA_8, max = JRE.JAVA_11",
            "minVersion = 8, maxVersion = 11"
        }, delimiter = ';')
        @ParameterizedTest
        void removeAnnotationDisabledForRangeEndingBeforeMinimum(String range) {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.DisabledForJreRange;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      @DisabledForJreRange(%s)
                      void testNotOnJava8To11() {
                          System.out.println("Not Java 8-11");
                      }
                  }
                  """.formatted(range),
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.DisabledForJreRange;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      void testNotOnJava8To11() {
                          System.out.println("Not Java 8-11");
                      }
                  }
                  """
              )
            );
        }

        @CsvSource(value = {
            "min = JRE.JAVA_17, max = JRE.JAVA_21",
            "minVersion = 17, maxVersion = 21"
        }, delimiter = ';')
        @ParameterizedTest
        void keepDisabledForRangeStartingAtMinimum(String range) {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.DisabledForJreRange;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      @DisabledForJreRange(%s)
                      void testNotOnJava17To21() {
                          System.out.println("Not Java 17-21");
                      }
                  }
                  """.formatted(range)
              )
            );
        }

        @CsvSource(value = {
            "min = JRE.JAVA_11, max = JRE.JAVA_21",
            "minVersion = 11, maxVersion = 21"
        }, delimiter = ';')
        @ParameterizedTest
        void handleDisabledForRangeStartingBefore17(String range) {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.DisabledForJreRange;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      @DisabledForJreRange(%s)
                      void testNotOnJava11To21() {
                          System.out.println("Not Java 11-21");
                      }
                  }
                  """.formatted(range)
              )
            );
        }
    }

    @DocumentExample
    @Test
    void handleTestClassWithMixedConditions() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.condition.EnabledOnJre;
              import org.junit.jupiter.api.condition.DisabledOnJre;
              import org.junit.jupiter.api.condition.EnabledForJreRange;
              import org.junit.jupiter.api.condition.DisabledForJreRange;
              import org.junit.jupiter.api.condition.JRE;

              class MyTest {
                  @Test
                  @EnabledOnJre(JRE.JAVA_8)
                  void testOnJava8() {
                      System.out.println("Java 8");
                  }

                  @Test
                  @EnabledOnJre(JRE.JAVA_21)
                  void testOnJava21() {
                      System.out.println("Java 21");
                  }

                  @Test
                  @DisabledOnJre(JRE.JAVA_11)
                  void testNotOnJava11() {
                      System.out.println("Not Java 11");
                  }

                  @Test
                  @EnabledForJreRange(min = JRE.JAVA_8, max = JRE.JAVA_11)
                  void testRange8To11() {
                      System.out.println("Java 8-11");
                  }

                  @Test
                  @DisabledForJreRange(min = JRE.JAVA_8, max = JRE.JAVA_11)
                  void testNotRange8To11() {
                      System.out.println("Not Java 8-11");
                  }

                  @Test
                  void testAlways() {
                      System.out.println("Always");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.condition.EnabledOnJre;
              import org.junit.jupiter.api.condition.DisabledOnJre;
              import org.junit.jupiter.api.condition.EnabledForJreRange;
              import org.junit.jupiter.api.condition.DisabledForJreRange;
              import org.junit.jupiter.api.condition.JRE;

              class MyTest {

                  @Test
                  @EnabledOnJre(JRE.JAVA_21)
                  void testOnJava21() {
                      System.out.println("Java 21");
                  }

                  @Test
                  void testNotOnJava11() {
                      System.out.println("Not Java 11");
                  }

                  @Test
                  void testNotRange8To11() {
                      System.out.println("Not Java 8-11");
                  }

                  @Test
                  void testAlways() {
                      System.out.println("Always");
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveNonTestMethods() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.condition.EnabledOnJre;
              import org.junit.jupiter.api.condition.JRE;

              class MyTest {
                  @EnabledOnJre(JRE.JAVA_8)
                  void notATest() {
                      System.out.println("Not a test");
                  }
              }
              """
          )
        );
    }

    @Test
    void handleOtherJreValue() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.condition.EnabledOnJre;
              import org.junit.jupiter.api.condition.JRE;

              class MyTest {
                  @Test
                  @EnabledOnJre(JRE.OTHER)
                  void testOnOther() {
                      System.out.println("Other JRE");
                  }
              }
              """
          )
        );
    }
}
