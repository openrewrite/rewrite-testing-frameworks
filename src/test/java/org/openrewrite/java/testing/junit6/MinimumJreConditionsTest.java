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
import org.junit.jupiter.params.provider.ValueSource;
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

    @Nested
    class EnabledOnJreTests {
        @ParameterizedTest
        @ValueSource(strings = {
          "JAVA_11",
          "JAVA_8",
          "JRE.JAVA_11",
          "JRE.JAVA_8",
          "value = JRE.JAVA_11",
          "value = JRE.JAVA_8",
          "versions = { 11 }",
          "versions = { 8, 11 }",
          "versions = { 8 }"
        })
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

        @ParameterizedTest
        @ValueSource(strings = {
          "JAVA_17",
          "JRE.JAVA_17",
          "value = JRE.JAVA_17",
          "versions = 17",
          "JRE.JAVA_21",
          "value = JRE.JAVA_21",
          "versions = 21",
          "JAVA_21"
        })
        void keepAnnotationEnabledOnCurrentOrNewerJre(String jre) {
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
                  """.formatted(jre)
              )
            );
        }

        @ParameterizedTest
        @ValueSource(strings = {
          "versions = { 17 }",
          "versions = { 21 }",
        })
        void doNotUnwrapSingleValueArrayIfNotTouched(String jre) {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledOnJre;

                  class MyTest {
                      @Test
                      @EnabledOnJre(%s)
                      void notOnThisJava() {
                          System.out.println("Not this Java");
                      }
                  }
                  """.formatted(jre)
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
        void filterAnnotationVersionsToBeCurrentOrHigher() {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledOnJre;

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

                  class MyTest {
                      @Test
                      @EnabledOnJre(versions = 17)
                      void testOnJavaSomeJaveVersions() {
                          System.out.println("Java 17");
                      }
                  }
                  """
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
                      @EnabledOnJre(JRE.JAVA_21)
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
        @ParameterizedTest
        @ValueSource(strings = {
          "JAVA_11",
          "JAVA_8",
          "JRE.JAVA_11",
          "JRE.JAVA_8",
          "value = JRE.JAVA_11",
          "value = JRE.JAVA_8",
          "versions = { 11 }",
          "versions = { 8, 11 }",
          "versions = { 8 }"
        })
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

        @ParameterizedTest
        @ValueSource(strings = {
          "JRE.JAVA_17",
          "value = JRE.JAVA_17",
          "versions = 17",
          "JAVA_17",
          "JRE.JAVA_21",
          "value = JRE.JAVA_21",
          "versions = 21",
          "JAVA_21"
        })
        void keepDisabledOnCurrentOrNewerJRE(String jre) {
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

        @Test
        void doNotUnwrapIfNotTouched() {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.DisabledOnJre;

                  class MyTest {
                      @Test
                      @DisabledOnJre(versions = { 17 })
                      void notOnThisJava() {
                          System.out.println("Not this Java");
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class EnabledForJreRange {
        @ParameterizedTest
        @ValueSource(strings = {
          "min = JRE.JAVA_8, max = JRE.JAVA_11",
          "minVersion = 8, maxVersion = 11"
        })
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
                  class MyTest {
                  }
                  """
              )
            );
        }

        @Test
        void keepEnabledForRangeStartingAtMinimum() {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledForJreRange;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      @EnabledForJreRange(minVersion = 17, maxVersion = 21)
                      void testOnJava17To21() {
                          System.out.println("Java 17-21");
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledForJreRange;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      @EnabledForJreRange(maxVersion = 21)
                      void testOnJava17To21() {
                          System.out.println("Java 17-21");
                      }
                  }
                  """
              )
            );
        }

        @Test
        void keepEnabledForRangeStartingAtMinimumWithJREImport() {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledForJreRange;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      @EnabledForJreRange(min = JRE.JAVA_17, max = JRE.JAVA_21)
                      void testOnJava17To21() {
                          System.out.println("Java 17-21");
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledForJreRange;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      @EnabledForJreRange(max = JRE.JAVA_21)
                      void testOnJava17To21() {
                          System.out.println("Java 17-21");
                      }
                  }
                  """
              )
            );
        }

        @ParameterizedTest
        @ValueSource(strings = {
          "min = JRE.JAVA_21",
          "minVersion = 21"
        })
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

        @Test
        void handleRangeWithOnlyMinWithJREImport() {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledForJreRange;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      @EnabledForJreRange(min = JRE.JAVA_11)
                      void testOnJava11AndLater() {
                          System.out.println("Java 11+");
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;

                  class MyTest {
                      @Test
                      void testOnJava11AndLater() {
                          System.out.println("Java 11+");
                      }
                  }
                  """
              )
            );
        }

        @Test
        void handleRangeWithOnlyMin() {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledForJreRange;

                  class MyTest {
                      @Test
                      @EnabledForJreRange(minVersion = 11)
                      void testOnJava11AndLater() {
                          System.out.println("Java 11+");
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;

                  class MyTest {
                      @Test
                      void testOnJava11AndLater() {
                          System.out.println("Java 11+");
                      }
                  }
                  """
              )
            );
        }

        @ParameterizedTest
        @ValueSource(strings = {
          "max = JRE.JAVA_11",
          "maxVersion = 11"
        })
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
                  class MyTest {
                  }
                  """
              )
            );
        }

        @ParameterizedTest
        @ValueSource(strings = {
          "min = JRE.JAVA_8, max = JRE.JAVA_16",
          "minVersion = 8, maxVersion = 16"
        })
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
                  class MyTest {
                  }
                  """
              )
            );
        }

        @Test
        void handleEnabledForRangeStartingBefore17EndingAfter() {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledForJreRange;

                  class MyTest {
                      @Test
                      @EnabledForJreRange(minVersion = 11, maxVersion = 21)
                      void testOnJava11To21() {
                          System.out.println("Java 11-21");
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledForJreRange;

                  class MyTest {
                      @Test
                      @EnabledForJreRange(maxVersion = 21)
                      void testOnJava11To21() {
                          System.out.println("Java 11-21");
                      }
                  }
                  """
              )
            );
        }

        @Test
        void handleEnabledForRangeStartingBefore17EndingAfterWithJREImport() {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledForJreRange;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      @EnabledForJreRange(min = JRE.JAVA_11, max = JRE.JAVA_21)
                      void testOnJava11To21() {
                          System.out.println("Java 11-21");
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledForJreRange;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      @EnabledForJreRange(max = JRE.JAVA_21)
                      void testOnJava11To21() {
                          System.out.println("Java 11-21");
                      }
                  }
                  """
              )
            );
        }

        @Test
        void handleEnabledForRangeStartingBefore17EndingOn17() {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledForJreRange;

                  class MyTest {
                      @Test
                      @EnabledForJreRange(minVersion = 11, maxVersion = 17)
                      void testOnJava11To21() {
                          System.out.println("Java 11-17");
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledOnJre;

                  class MyTest {
                      @Test
                      @EnabledOnJre(versions = 17)
                      void testOnJava11To21() {
                          System.out.println("Java 11-17");
                      }
                  }
                  """
              )
            );
        }

        @Test
        void handleEnabledForRangeStartingBefore17EndingOn17WithJREImport() {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledForJreRange;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      @EnabledForJreRange(min = JRE.JAVA_11, max = JRE.JAVA_17)
                      void testOnJava11To21() {
                          System.out.println("Java 11-17");
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledOnJre;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      @EnabledOnJre(JRE.JAVA_17)
                      void testOnJava11To21() {
                          System.out.println("Java 11-17");
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class DisabledForJreRange {
        @ParameterizedTest
        @ValueSource(strings = {
          "min = JRE.JAVA_8, max = JRE.JAVA_11",
          "minVersion = 8, maxVersion = 11"
        })
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
          "min = JRE.JAVA_17, max = JRE.JAVA_21;max = JRE.JAVA_21",
          "minVersion = 17, maxVersion = 21;maxVersion = 21"
        }, delimiter = ';')
        @ParameterizedTest
        void keepDisabledForRangeStartingAtMinimum(String range, String afterRange) {
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
                  """.formatted(range),
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
                  """.formatted(afterRange)
              )
            );
        }

        @Test
        void handleDisabledForRangeStartingBefore17() {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.DisabledForJreRange;

                  class MyTest {
                      @Test
                      @DisabledForJreRange(minVersion = 11, maxVersion = 21)
                      void testNotOnJava11To21() {
                          System.out.println("Not Java 11-21");
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.DisabledForJreRange;

                  class MyTest {
                      @Test
                      @DisabledForJreRange(maxVersion = 21)
                      void testNotOnJava11To21() {
                          System.out.println("Not Java 11-21");
                      }
                  }
                  """
              )
            );
        }

        @Test
        void handleDisabledForRangeStartingBefore17WithJREImport() {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.DisabledForJreRange;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      @DisabledForJreRange(min = JRE.JAVA_11, max = JRE.JAVA_21)
                      void testNotOnJava11To21() {
                          System.out.println("Not Java 11-21");
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.DisabledForJreRange;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      @DisabledForJreRange(max = JRE.JAVA_21)
                      void testNotOnJava11To21() {
                          System.out.println("Not Java 11-21");
                      }
                  }
                  """
              )
            );
        }

        @Test
        void handleRangeWithOnlyMinWithJREImport() {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.DisabledForJreRange;
                  import org.junit.jupiter.api.condition.JRE;

                  class MyTest {
                      @Test
                      @DisabledForJreRange(min = JRE.JAVA_11)
                      void testOnJava11AndLater() {
                          System.out.println("Java 11+");
                      }
                  }
                  """,
                """

                  class MyTest {
                  }
                  """
              )
            );
        }

        @Test
        void handleRangeWithOnlyMin() {
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.condition.EnabledForJreRange;

                  class MyTest {
                      @Test
                      @EnabledForJreRange(minVersion = 11)
                      void testOnJava11AndLater() {
                          System.out.println("Java 11+");
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Test;

                  class MyTest {
                      @Test
                      void testOnJava11AndLater() {
                          System.out.println("Java 11+");
                      }
                  }
                  """
              )
            );
        }
    }

    @Test
    void convertRangeEndingOnCurrentVersionToDisabledAnnotation() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.condition.DisabledForJreRange;

              class MyTest {
                  @Test
                  @DisabledForJreRange(minVersion = 11, maxVersion = 17)
                  void testNotOnJava11To17() {
                      System.out.println("Not Java 11-17");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.condition.DisabledOnJre;

              class MyTest {
                  @Test
                  @DisabledOnJre(versions = 17)
                  void testNotOnJava11To17() {
                      System.out.println("Not Java 11-17");
                  }
              }
              """
          )
        );
    }

    @Test
    void convertRangeEndingOnCurrentVersionToDisabledAnnotationWithJREImport() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.condition.DisabledForJreRange;
              import org.junit.jupiter.api.condition.JRE;

              class MyTest {
                  @Test
                  @DisabledForJreRange(min = JRE.JAVA_11, max = JRE.JAVA_17)
                  void testNotOnJava11To17() {
                      System.out.println("Not Java 11-17");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.condition.DisabledOnJre;
              import org.junit.jupiter.api.condition.JRE;

              class MyTest {
                  @Test
                  @DisabledOnJre(JRE.JAVA_17)
                  void testNotOnJava11To17() {
                      System.out.println("Not Java 11-17");
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
    void tolerateOtherJreValue() {
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
