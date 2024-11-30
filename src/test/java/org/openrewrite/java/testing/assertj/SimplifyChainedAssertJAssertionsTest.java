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
package org.openrewrite.java.testing.assertj;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.openrewrite.java.Assertions.java;

class SimplifyChainedAssertJAssertionsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3.24"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing.assertj")
            .build()
            .activateRecipes("org.openrewrite.java.testing.assertj.SimplifyChainedAssertJAssertions"));
    }

    @Nested
    class Strings {
        @Test
        @DocumentExample
        void stringIsEmptyExample() {
            rewriteRun(
              //language=java
              java(
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class MyTest {
                      void testMethod() {
                          String s = "hello world";
                          assertThat(s.isEmpty()).isTrue();
                      }
                  }
                  """,
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class MyTest {
                      void testMethod() {
                          String s = "hello world";
                          assertThat(s).isEmpty();
                      }
                  }
                  """
              )
            );
        }

        private static Stream<Arguments> stringReplacements() {
            return Stream.of(
              Arguments.arguments("isEmpty", "isTrue", "isEmpty", "", ""),
              Arguments.arguments("equals", "isTrue", "isEqualTo", "expected", ""),
              Arguments.arguments("equalsIgnoreCase", "isTrue", "isEqualToIgnoringCase", "expected", ""),
              Arguments.arguments("contains", "isTrue", "contains", "expected", ""),
              Arguments.arguments("startsWith", "isTrue", "startsWith", "expected", ""),
              Arguments.arguments("startsWith", "isFalse", "doesNotStartWith", "expected", ""),
              Arguments.arguments("endsWith", "isTrue", "endsWith", "expected", ""),
              Arguments.arguments("endsWith", "isFalse", "doesNotEndWith", "expected", ""),
              Arguments.arguments("matches", "isTrue", "matches", "expected", ""),
              Arguments.arguments("matches", "isFalse", "doesNotMatch", "expected", ""),
              Arguments.arguments("trim", "isEmpty", "isBlank", "", ""),
              Arguments.arguments("length", "isEqualTo", "hasSize", "", "length"),
              Arguments.arguments("isEmpty", "isFalse", "isNotEmpty", "", "")
            );
        }

        @ParameterizedTest
        @MethodSource("stringReplacements")
        void stringReplacements(String chainedAssertion, String assertToReplace, String dedicatedAssertion, String firstArg, String secondArg) {
            //language=java
            String template = """

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test() {
                      int length = 5;
                      String expected = "hello world";
                      %s
                  }
              }
              """;
            String assertBefore = "assertThat(\"hello world\".%s(%s)).%s(%s);";
            String assertAfter = "assertThat(\"hello world\").%s(%s);";

            String formattedAssertBefore = assertBefore.formatted(chainedAssertion, firstArg, assertToReplace, secondArg);

            String finalArgument = firstArg.isEmpty() && !"0".equals(secondArg) ? secondArg : firstArg;
            finalArgument = finalArgument.contains(".") ? finalArgument.split("\\.")[0] : finalArgument;

            String before = String.format(template, formattedAssertBefore);
            String after = String.format(template, assertAfter.formatted(dedicatedAssertion, finalArgument));

            rewriteRun(
              java(before, after)
            );
        }
    }

    @Nested
    class FilesAndPaths {
        private static Stream<Arguments> fileReplacements() {
            return Stream.of(
              Arguments.arguments("length", "isZero", "isEmpty", "", ""),
              Arguments.arguments("length", "isEqualTo", "hasSize", "", "length"),
              Arguments.arguments("canRead", "isTrue", "canRead", "", ""),
              Arguments.arguments("canWrite", "isTrue", "canWrite", "", ""),
              Arguments.arguments("exists", "isTrue", "exists", "", ""),
              Arguments.arguments("getName", "isEqualTo", "hasName", "", "name"),
              Arguments.arguments("getParent", "isEqualTo", "hasParent", "", "pathname"),
              Arguments.arguments("getParentFile", "isNull", "hasNoParent", "", ""),
              Arguments.arguments("isAbsolute", "isTrue", "isAbsolute", "", ""),
              Arguments.arguments("isAbsolute", "isFalse", "isRelative", "", ""),
              Arguments.arguments("isDirectory", "isTrue", "isDirectory", "", ""),
              Arguments.arguments("isFile", "isTrue", "isFile", "", ""),
              Arguments.arguments("list", "isEmpty", "isEmptyDirectory", "", "")
            );
        }

        @ParameterizedTest
        @MethodSource("fileReplacements")
        void fileReplacements(String chainedAssertion, String assertToReplace, String dedicatedAssertion, String firstArg, String secondArg) {
            //language=java
            String template = """
              import java.io.File;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test() {
                      int length = 5;
                      String name = "hello world";
                      String pathname = "pathname";
                      File file = new File("");
                      %s
                  }
              }
              """;
            String assertBefore = "assertThat(file.%s(%s)).%s(%s);";
            String assertAfter = "assertThat(file).%s(%s);";

            String formattedAssertBefore = assertBefore.formatted(chainedAssertion, firstArg, assertToReplace, secondArg);

            String finalArgument = "".equals(firstArg) && !"0".equals(secondArg) ? secondArg : firstArg;
            finalArgument = finalArgument.contains(".") ? finalArgument.split("\\.")[0] : finalArgument;

            String before = String.format(template, formattedAssertBefore);
            String after = String.format(template, assertAfter.formatted(dedicatedAssertion, finalArgument));

            rewriteRun(
              java(before, after)
            );
        }

        private static Stream<Arguments> pathReplacements() {
            return Stream.of(
              Arguments.arguments("startsWith", "isTrue", "startsWithRaw", "path", ""),
              Arguments.arguments("endsWith", "isTrue", "endsWithRaw", "path", ""),
              Arguments.arguments("getParent", "isEqualTo", "hasParentRaw", "", "path"),
              Arguments.arguments("getParent", "isNull", "hasNoParentRaw", "", ""),
              Arguments.arguments("isAbsolute", "isTrue", "isAbsolute", "", ""),
              Arguments.arguments("isAbsolute", "isFalse", "isRelative", "", "")
            );
        }

        @ParameterizedTest
        @MethodSource("pathReplacements")
        void pathReplacements(String chainedAssertion, String assertToReplace, String dedicatedAssertion, String firstArg, String secondArg) {
            //language=java
            String template = """
              import java.nio.file.Path;
              import java.nio.file.Paths;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test() {
                      Path path = Paths.get("");
                      %s
                  }
              }
              """;
            String assertBefore = "assertThat(path.%s(%s)).%s(%s);";
            String assertAfter = "assertThat(path).%s(%s);";

            String formattedAssertBefore = assertBefore.formatted(chainedAssertion, firstArg, assertToReplace, secondArg);

            String finalArgument = "".equals(firstArg) && !"0".equals(secondArg) ? secondArg : firstArg;
            finalArgument = finalArgument.contains(".") ? finalArgument.split("\\.")[0] : finalArgument;

            String before = String.format(template, formattedAssertBefore);
            String after = String.format(template, assertAfter.formatted(dedicatedAssertion, finalArgument));

            rewriteRun(
              java(before, after)
            );
        }
    }

    @Nested
    class Collectionz {
        private static Stream<Arguments> collectionReplacements() {
            return Stream.of(
              Arguments.arguments("isEmpty", "isTrue", "isEmpty", "", ""),
              Arguments.arguments("isEmpty", "isFalse", "isNotEmpty", "", ""),
              Arguments.arguments("size", "isZero", "isEmpty", "", ""),
              Arguments.arguments("size", "isEqualTo", "hasSize", "", "5"),
              Arguments.arguments("contains", "isTrue", "contains", "something", ""),
              Arguments.arguments("contains", "isFalse", "doesNotContain", "something", ""),
              Arguments.arguments("containsAll", "isTrue", "containsAll", "otherCollection", "")
            );
        }

        @ParameterizedTest
        @MethodSource("collectionReplacements")
        void collectionReplacements(String chainedAssertion, String assertToReplace, String dedicatedAssertion, String firstArg, String secondArg) {
            //language=java
            String template = """
              import java.util.Collection;

              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void test(Collection<String> collection, Collection<String> otherCollection) {
                      String something = "";
                      %s
                  }
              }
              """;
            String assertBefore = "assertThat(collection.%s(%s)).%s(%s);";
            String assertAfter = "assertThat(collection).%s(%s);";

            String formattedAssertBefore = assertBefore.formatted(chainedAssertion, firstArg, assertToReplace, secondArg);

            String finalArgument = "".equals(firstArg) ? secondArg : firstArg;

            String before = String.format(template, formattedAssertBefore);
            String after = String.format(template, assertAfter.formatted(dedicatedAssertion, finalArgument));

            rewriteRun(
              java(before, after)
            );
        }

        private static Stream<Arguments> mapReplacements() {
            return Stream.of(
              Arguments.arguments("size", "isEqualTo", "hasSize", "", "1"),
              Arguments.arguments("containsKey", "isTrue", "containsKey", "key", ""),
              Arguments.arguments("keySet", "contains", "containsKey", "", "key"),
              Arguments.arguments("keySet", "containsOnly", "containsOnlyKeys", "", "key"),
              Arguments.arguments("containsValue", "isTrue", "containsValue", "value", ""),
              Arguments.arguments("values", "contains", "containsValue", "", "value"),
              Arguments.arguments("get", "isEqualTo", "containsEntry", "key", "value")
            );
        }

        @ParameterizedTest
        @MethodSource("mapReplacements")
        void mapReplacements(String chainedAssertion, String assertToReplace, String dedicatedAssertion, String firstArg, String secondArg) {
            //language=java
            String template = """
              import java.util.Collections;
              import java.util.Map;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test() {
                      Map<String, String> otherMap = Collections.emptyMap();
                      String key = "key";
                      String value = "value";
                      Map<String, String> map = Collections.emptyMap();
                      %s
                  }
              }
              """;
            String assertBefore = "assertThat(map.%s(%s)).%s(%s);";
            String assertAfter = !"".equals(firstArg) && !"".equals(secondArg) ? "assertThat(map).%s(%s, %s);" : "assertThat(map).%s(%s);";

            String formattedAssertBefore = assertBefore.formatted(chainedAssertion, firstArg, assertToReplace, secondArg);
            String before = String.format(template, formattedAssertBefore);

            String finalArgument = "".equals(firstArg) ? secondArg : firstArg;
            List<String> formattedArgs = new ArrayList<>(Arrays.asList(dedicatedAssertion, finalArgument));
            if (!"".equals(firstArg) && !"".equals(secondArg)) {
                formattedArgs.add(secondArg);
            }
            String after = String.format(template, assertAfter.formatted(formattedArgs.toArray()));

            rewriteRun(
              java(before, after)
            );
        }

        @Test
        void mapHasSameSizeAs() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.Map;

                  import static org.assertj.core.api.Assertions.assertThat;

                  class MyTest {
                      void testMethod() {
                          Map<String, String> mapA = Map.of();
                          Map<String, String> mapB = Map.of();
                          assertThat(mapA.size()).isEqualTo(mapB.size());
                      }
                  }
                  """,
                """
                  import java.util.Map;

                  import static org.assertj.core.api.Assertions.assertThat;

                  class MyTest {
                      void testMethod() {
                          Map<String, String> mapA = Map.of();
                          Map<String, String> mapB = Map.of();
                          assertThat(mapA).hasSize(mapB.size());
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class Optionals {
        private static Stream<Arguments> optionalReplacements() {
            return Stream.of(
              Arguments.arguments("isPresent", "isTrue", "isPresent", ""),
              Arguments.arguments("get", "isEqualTo", "contains", "something"),
              Arguments.arguments("get", "isSameAs", "containsSame", "something")
            );
        }

        @ParameterizedTest
        @MethodSource("optionalReplacements")
        void optionalReplacements(String chainedAssertion, String assertToReplace, String dedicatedAssertion, String arg) {
            //language=java
            String template = """
              import java.util.Optional;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test() {
                      String something = "hello world";
                      Optional<String> helloWorld = Optional.of("hello world");
                      %s
                  }
              }
              """;

            String assertBefore = String.format("assertThat(helloWorld.%s()).%s(%s);", chainedAssertion, assertToReplace, arg);
            String assertAfter = String.format("assertThat(helloWorld).%s(%s);", dedicatedAssertion, arg);

            String before = String.format(template, assertBefore);
            String after = String.format(template, assertAfter);

            rewriteRun(java(before, after));
        }
    }

    @Nested
    class Iterators {
        private static Stream<Arguments> collectionReplacements() {
            return Stream.of(
              Arguments.arguments("hasNext", "isTrue", "hasNext"),
              Arguments.arguments("hasNext", "isFalse", "isExhausted")
            );
        }

        @ParameterizedTest
        @MethodSource("collectionReplacements")
        void collectionReplacements(String chainedAssertion, String assertToReplace, String dedicatedAssertion) {
            //language=java
            String template = """
              import java.util.Iterator;

              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void test(Iterator<String> iterator, Iterator<String> otherIterator) {
                      String something = "";
                      %s
                  }
              }
              """;
            String assertBefore = "assertThat(iterator.%s()).%s();";
            String assertAfter = "assertThat(iterator).%s();";

            String formattedAssertBefore = assertBefore.formatted(chainedAssertion, assertToReplace);

            String before = String.format(template, formattedAssertBefore);
            String after = String.format(template, assertAfter.formatted(dedicatedAssertion));

            rewriteRun(
              java(before, after)
            );
        }
    }

    @Nested
    class Objects {
        @Test
        void objectoToStringReplacement() {
            rewriteRun(
              //language=java
              java(
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class MyTest {
                      void testMethod(Object argument) {
                          String s = "hello world";
                          assertThat(argument.toString()).isEqualTo("value");
                      }
                  }
                  """,
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class MyTest {
                      void testMethod(Object argument) {
                          String s = "hello world";
                          assertThat(argument).hasToString("value");
                      }
                  }
                  """
              )
            );
        }
    }
}
