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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

class MigrateChainedAssertToAssertJTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-jupiter-api-5.9",
              "assertj-core-3.24"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing.assertj")
            .build()
            .activateRecipes("org.openrewrite.java.testing.assertj.SimplifyChainedAssertJAssertions"));
    }

    private static Stream<Arguments> stringReplacements() {
        return Stream.of(
          Arguments.arguments("isEmpty", "isTrue", "isEmpty", "", ""),
          Arguments.arguments("getString", "hasSize", "isEmpty", "", "0"),
          Arguments.arguments("equals", "isTrue", "isEqualTo", "expected", ""),
          Arguments.arguments("equalsIgnoreCase", "isTrue", "isEqualToIgnoringCase", "expected", ""),
          Arguments.arguments("contains", "isTrue", "contains", "expected", ""),
          Arguments.arguments("startsWith", "isTrue", "startsWith", "expected", ""),
          Arguments.arguments("endsWith", "isTrue", "endsWith", "expected", ""),
          Arguments.arguments("matches", "isTrue", "matches", "expected", ""),
          Arguments.arguments("trim", "isEmpty", "isBlank", "", ""),
          Arguments.arguments("length", "isEqualTo", "hasSize", "", "length"),
          Arguments.arguments("isEmpty", "isFalse", "isNotEmpty", "", ""),
          Arguments.arguments("length", "hasSize", "hasSameSizeAs", "", "expected.length()")
        );
    }

    @ParameterizedTest
    @MethodSource("stringReplacements")
    void stringReplacements(String chainedAssertion, String assertToReplace, String dedicatedAssertion, String firstArg, String secondArg) {
        //language=java
        String template = """
          import org.junit.jupiter.api.Test;
          
          import static org.assertj.core.api.Assertions.assertThat;
          
          class MyTest {
              @Test
              void test() {
                  int length = 5;
                  String expected = "hello world";
                  %s
              }
              
              String getString() {
                  return "hello world";
              }
          }
          """;
        String assertBefore = chainedAssertion.equals("getString") ? "assertThat(%s(%s)).%s(%s);" : "assertThat(getString().%s(%s)).%s(%s);";
        String assertAfter = "assertThat(getString()).%s(%s);";

        String formattedAssertBefore = assertBefore.formatted(chainedAssertion, firstArg, assertToReplace, secondArg);

        String finalArgument = firstArg.isEmpty() && !secondArg.equals("0") ? secondArg : firstArg;
        finalArgument = finalArgument.contains(".") ? finalArgument.split("\\.")[0] : finalArgument;

        String before = String.format(template, formattedAssertBefore);
        String after = String.format(template, assertAfter.formatted(dedicatedAssertion, finalArgument));

        rewriteRun(
          java(before, after)
        );
    }

    private static Stream<Arguments> fileReplacements() {
        return Stream.of(
          Arguments.arguments("getFile", "hasSize", "isEmpty", "", "0"),
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
          import org.junit.jupiter.api.Test;
          import java.io.File;
          
          import static org.assertj.core.api.Assertions.assertThat;
          
          class MyTest {
              @Test
              void test() {
                  int length = 5;
                  String name = "hello world";
                  String pathname = "pathname";
                  %s
              }
              
              File getFile() {
                  return new File("");
              }
          }
          """;
        String assertBefore = chainedAssertion.equals("getFile") ? "assertThat(%s(%s)).%s(%s);" : "assertThat(getFile().%s(%s)).%s(%s);";
        String assertAfter = "assertThat(getFile()).%s(%s);";

        String formattedAssertBefore = assertBefore.formatted(chainedAssertion, firstArg, assertToReplace, secondArg);

        String finalArgument = firstArg.equals("") && !secondArg.equals("0") ? secondArg : firstArg;
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
          import org.junit.jupiter.api.Test;
          import java.nio.file.Path;
          import java.nio.file.Paths;
          
          import static org.assertj.core.api.Assertions.assertThat;
          
          class MyTest {
              @Test
              void test() {
                  Path path = Paths.get("");
                  %s
              }
              
              Path getPath() {
                  return Paths.get("");
              }
          }
          """;
        String assertBefore = chainedAssertion.equals("getPath") ? "assertThat(%s(%s)).%s(%s);" : "assertThat(getPath().%s(%s)).%s(%s);";
        String assertAfter = "assertThat(getPath()).%s(%s);";

        String formattedAssertBefore = assertBefore.formatted(chainedAssertion, firstArg, assertToReplace, secondArg);

        String finalArgument = firstArg.equals("") && !secondArg.equals("0") ? secondArg : firstArg;
        finalArgument = finalArgument.contains(".") ? finalArgument.split("\\.")[0] : finalArgument;

        String before = String.format(template, formattedAssertBefore);
        String after = String.format(template, assertAfter.formatted(dedicatedAssertion, finalArgument));

        rewriteRun(
          java(before, after)
        );
    }

    private static Stream<Arguments> collectionReplacements() {
        return Stream.of(
          Arguments.arguments("isEmpty", "isTrue", "isEmpty", "", ""),
          Arguments.arguments("size", "isZero", "isEmpty", "", ""),
          Arguments.arguments("contains", "isTrue", "contains", "something", ""),
          Arguments.arguments("containsAll", "isTrue", "containsAll", "otherCollection", "")
        );
    }

    @ParameterizedTest
    @MethodSource("collectionReplacements")
    void collectionReplacements(String chainedAssertion, String assertToReplace, String dedicatedAssertion, String firstArg, String secondArg) {
        //language=java
        String template = """
          import org.junit.jupiter.api.Test;
          import java.util.ArrayList;
          
          import static org.assertj.core.api.Assertions.assertThat;
          
          class MyTest {
              @Test
              void test() {
                  String something = "";
                  ArrayList<String> otherCollection = new ArrayList<>();
                  %s
              }
              
              ArrayList<String> getCollection() {
                  return new ArrayList<>();
              }
          }
          """;
        String assertBefore = "assertThat(getCollection().%s(%s)).%s(%s);";
        String assertAfter = "assertThat(getCollection()).%s(%s);";

        String formattedAssertBefore = assertBefore.formatted(chainedAssertion, firstArg, assertToReplace, secondArg);

        String finalArgument = firstArg.equals("") ? secondArg : firstArg;

        String before = String.format(template, formattedAssertBefore);
        String after = String.format(template, assertAfter.formatted(dedicatedAssertion, finalArgument));

        rewriteRun(
          java(before, after)
        );
    }

    private static Stream<Arguments> mapReplacements() {
        return Stream.of(
          Arguments.arguments("size", "isEqualTo", "hasSameSizeAs", "", "otherMap.size()"),
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
          import org.junit.jupiter.api.Test;
          import java.util.Collections;
          import java.util.Map;
          
          import static org.assertj.core.api.Assertions.assertThat;
          
          class MyTest {
              @Test
              void test() {
                  Map<String, String> otherMap = Collections.emptyMap();
                  String key = "key";
                  String value = "value";
                  %s
              }
              
              Map<String, String> getMap() {
                  return Collections.emptyMap();
              }
          }
          """;
        String assertBefore = "assertThat(getMap().%s(%s)).%s(%s);";
        String assertAfter = !firstArg.equals("") && !secondArg.equals("") ? "assertThat(getMap()).%s(%s, %s);" : "assertThat(getMap()).%s(%s);";

        String formattedAssertBefore = assertBefore.formatted(chainedAssertion, firstArg, assertToReplace, secondArg);

        String finalArgument = firstArg.equals("") ? secondArg : firstArg;
        finalArgument = finalArgument.contains(".") ? finalArgument.split("\\.")[0] : finalArgument;

        String before = String.format(template, formattedAssertBefore);
        List<String> formattedArgs = new ArrayList<>(Arrays.asList(dedicatedAssertion, finalArgument));
        if (!firstArg.equals("") && !secondArg.equals("")) {
            formattedArgs.add(secondArg);
        }
        String after = String.format(template, assertAfter.formatted(formattedArgs.toArray()));

        rewriteRun(
          java(before, after)
        );
    }

    private static Stream<Arguments> optionalReplacements() {
        return Stream.of(
          Arguments.arguments("isPresent", "isTrue", "isPresent", ""),
          Arguments.arguments("get", "isEqualTo", "contains", "something"),
          Arguments.arguments("get", "isSameAs", "containsSame", "something")
        );
    }

    @ParameterizedTest
    @MethodSource("optionalReplacements")
    void optionalReplacements(String chainedAssertion, String assertToReplace, String dedicatedAssertion, String arg ) {
        //language=java
        String template = """
        import org.junit.jupiter.api.Test;
        import java.util.Optional;
        
        import static org.assertj.core.api.Assertions.assertThat;
        
        class MyTest {
            @Test
            void test() {
                String something = "hello world";
                %s
            }
            
            Optional<String> getOptional() {
                return Optional.of("hello world");
            }
        }
        """;

        String assertBefore = String.format("assertThat(getOptional().%s()).%s(%s);", chainedAssertion, assertToReplace, arg);
        String assertAfter = String.format("assertThat(getOptional()).%s(%s);", dedicatedAssertion, arg);

        String before = String.format(template, assertBefore);
        String after = String.format(template, assertAfter);

        rewriteRun(java(before, after));
    }

    private static Stream<Arguments> arrayReplacements() {
        return Stream.of(
          Arguments.arguments("isZero", "isEmpty", ""),
          Arguments.arguments("isEqualTo", "hasSize", "length"),
          Arguments.arguments("isEqualTo", "hasSameSizeAs", "anotherArray.length"),
          Arguments.arguments("isLessThanOrEqualTo", "hasSizeLessThanOrEqualTo", "expression"),
          Arguments.arguments("isLessThan", "hasSizeLessThan", "expression"),
          Arguments.arguments("isGreaterThan", "hasSizeGreaterThan", "expression"),
          Arguments.arguments("isGreaterThanOrEqualTo", "hasSizeGreaterThanOrEqualTo", "expression")
        );
    }

    @ParameterizedTest
    @MethodSource("arrayReplacements")
    void arrayReplacements(String assertToReplace, String dedicatedAssertion, String arg) {
        //language=java
        String template = """
        import org.junit.jupiter.api.Test;
        
        import static org.assertj.core.api.Assertions.assertThat;
        
        class MyTest {
            @Test
            void test() {
                String[] anotherArray = {""};
                String expression = "";
                int length = 5;
                %s
            }
            
            String[] getArray() {
                String[] arr = {"hello", "world"};
                return arr;
            }
        }
        """;

        String assertBefore = String.format("assertThat(getArray().length).%s(%s);", assertToReplace, arg);
        String afterArg = arg.contains(".") ? arg.split("\\.")[0] : arg;
        String assertAfter = String.format("assertThat(getArray()).%s(%s);", dedicatedAssertion, afterArg);

        String before = String.format(template, assertBefore);
        String after = String.format(template, assertAfter);

        rewriteRun(java(before, after));
    }
}
