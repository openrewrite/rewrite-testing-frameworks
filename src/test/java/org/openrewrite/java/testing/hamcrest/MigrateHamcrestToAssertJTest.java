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

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

@Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/212")
class MigrateHamcrestToAssertJTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-jupiter-api-5.9",
              "hamcrest-2.2",
              "assertj-core-3.24"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing.hamcrest")
            .build()
            .activateRecipes("org.openrewrite.java.testing.hamcrest.MigrateHamcrestToAssertJ"));
    }

    @Test
    @DocumentExample
    void isEqualTo() {
        rewriteRun(
          //language=java
          java("""
            import org.junit.jupiter.api.Test;
            import static org.hamcrest.MatcherAssert.assertThat;
            import static org.hamcrest.Matchers.is;
            import static org.hamcrest.Matchers.equalTo;
                            
            class ATest {
                @Test
                void testEquals() {
                    String str1 = "Hello world!";
                    String str2 = "Hello world!";
                    assertThat(str1, is(equalTo(str2)));
                }
            }
            """, """
            import org.junit.jupiter.api.Test;
                        
            import static org.assertj.core.api.Assertions.assertThat;
                        
            class ATest {
                @Test
                void testEquals() {
                    String str1 = "Hello world!";
                    String str2 = "Hello world!";
                    assertThat(str1).isEqualTo(str2);
                }
            }
            """));
    }

    @Test
    @DocumentExample
    void flattenAllOfStringMatchersAndConvert() {
        rewriteRun(
          //language=java
          java("""
            import org.junit.jupiter.api.Test;
                        
            import static org.hamcrest.MatcherAssert.assertThat;
            import static org.hamcrest.Matchers.allOf;
            import static org.hamcrest.Matchers.equalTo;
            import static org.hamcrest.Matchers.hasLength;
                            
            class ATest {
                @Test
                void test() {
                    String str1 = "Hello world!";
                    String str2 = "Hello world!";
                    assertThat(str1, allOf(equalTo(str2), hasLength(12)));
                }
            }
            """, """
            import org.junit.jupiter.api.Test;

            import static org.assertj.core.api.Assertions.assertThat;

            class ATest {
                @Test
                void test() {
                    String str1 = "Hello world!";
                    String str2 = "Hello world!";
                    assertThat(str1).isEqualTo(str2);
                    assertThat(str1).hasSize(12);
                }
            }
            """));
    }

    @Test
    @DocumentExample
    void convertAnyOfMatchersAfterSatisfiesAnyOfConversion() {
        rewriteRun(
          //language=java
          java("""
            import org.junit.jupiter.api.Test;
                        
            import static org.hamcrest.MatcherAssert.assertThat;
            import static org.hamcrest.Matchers.anyOf;
            import static org.hamcrest.Matchers.equalTo;
            import static org.hamcrest.Matchers.hasLength;
                            
            class ATest {
                @Test
                void test() {
                    String str1 = "Hello world!";
                    String str2 = "Hello world!";
                    assertThat(str1, anyOf(equalTo(str2), hasLength(12)));
                }
            }
            """, """
            import org.junit.jupiter.api.Test;

            import static org.assertj.core.api.Assertions.assertThat;

            class ATest {
                @Test
                void test() {
                    String str1 = "Hello world!";
                    String str2 = "Hello world!";
                    assertThat(str1)
                            .satisfiesAnyOf(
                                    arg -> assertThat(arg).isEqualTo(str2),
                                    arg -> assertThat(arg).hasSize(12)
                            );
                }
            }
            """));
    }

    private static Stream<Arguments> arrayReplacements() {
        return Stream.of(
          Arguments.arguments("numbers", "arrayContaining", "1, 2, 3", "containsExactly"),
          Arguments.arguments("numbers", "arrayContainingInAnyOrder", "2, 1", "containsExactlyInAnyOrder"),
          Arguments.arguments("numbers", "arrayWithSize", "1", "hasSize"),
          Arguments.arguments("numbers", "emptyArray", "", "isEmpty"),
          Arguments.arguments("numbers", "hasItemInArray", "1", "contains")
        );
    }

    @ParameterizedTest
    @MethodSource("arrayReplacements")
    void arrayReplacements(String actual, String hamcrestMatcher, String matcherArgs, String assertJAssertion) {
        String importsBefore = """
          import static org.hamcrest.MatcherAssert.assertThat;
          import static org.hamcrest.Matchers.%s;""".formatted(hamcrestMatcher);
        String importsAfter = "import static org.assertj.core.api.Assertions.assertThat;";
        //language=java
        String template = """
          import org.junit.jupiter.api.Test;
                    
          %s
                    
          class ATest {
              @Test
              void test() {
                  Integer[] numbers = {1, 2, 3, 4};
                  %s
              }
          }
          """;
        String before = template.formatted(importsBefore, "assertThat(%s, %s(%s));".formatted(actual, hamcrestMatcher, matcherArgs));
        String after = template.formatted(importsAfter, "assertThat(%s).%s(%s);".formatted(actual, assertJAssertion, matcherArgs));
        rewriteRun(java(before, after));
    }

    private static Stream<Arguments> stringReplacements() {
        return Stream.of(
          Arguments.arguments("str1", "blankString", "", "isBlank"),
          Arguments.arguments("str1", "comparesEqualTo", "str2", "isEqualTo"),
          Arguments.arguments("str1", "containsString", "str2", "contains"),
          Arguments.arguments("str1", "containsStringIgnoringCase", "str2", "containsIgnoringCase"),
          Arguments.arguments("str1", "emptyOrNullString", "", "isNullOrEmpty"),
          Arguments.arguments("str1", "emptyString", "", "isEmpty"),
          Arguments.arguments("str1", "endsWith", "str2", "endsWith"),
          Arguments.arguments("str1", "endsWithIgnoringCase", "str2", "endsWithIgnoringCase"),
          Arguments.arguments("str1", "equalToIgnoringCase", "str2", "isEqualToIgnoringCase"),
          Arguments.arguments("str1", "equalToIgnoringWhiteSpace", "str2", "isEqualToIgnoringWhitespace"),
          Arguments.arguments("str1", "equalTo", "str2", "isEqualTo"),
          Arguments.arguments("str1", "greaterThanOrEqualTo", "str2", "isGreaterThanOrEqualTo"),
          Arguments.arguments("str1", "greaterThan", "str2", "isGreaterThan"),
          Arguments.arguments("str1", "hasLength", "5", "hasSize"),
          Arguments.arguments("str1", "hasToString", "str2", "hasToString"),
          Arguments.arguments("str1", "isEmptyString", "", "isEmpty"),
          Arguments.arguments("str1", "isEmptyOrNullString", "", "isNullOrEmpty"),
          Arguments.arguments("str1", "lessThanOrEqualTo", "str2", "isLessThanOrEqualTo"),
          Arguments.arguments("str1", "lessThan", "str2", "isLessThan"),
          Arguments.arguments("str1", "matchesPattern", "\"[a-z]+\"", "matches"),
          Arguments.arguments("str1", "matchesRegex", "\"[a-z]+\"", "matches"),
          Arguments.arguments("str1", "notNullValue", "", "isNotNull"),
          Arguments.arguments("str1", "not", "str2", "isNotEqualTo"),
          Arguments.arguments("str1", "nullValue", "", "isNull"),
          Arguments.arguments("str1", "sameInstance", "str2", "isSameAs"),
          Arguments.arguments("str1", "startsWith", "str2", "startsWith"),
          Arguments.arguments("str1", "startsWithIgnoringCase", "str2", "startsWithIgnoringCase")
        );
    }

    @ParameterizedTest
    @MethodSource("stringReplacements")
    void stringReplacements(String actual, String hamcrestMatcher, String matcherArgs, String assertJAssertion) {
        String importsBefore = """
          import static org.hamcrest.MatcherAssert.assertThat;
          import static org.hamcrest.Matchers.%s;""".formatted(hamcrestMatcher);
        String importsAfter = "import static org.assertj.core.api.Assertions.assertThat;";
        //language=java
        String template = """
          import org.junit.jupiter.api.Test;
                    
          %s
                    
          class ATest {
              @Test
              void test() {
                  String str1 = "Hello world!";
                  String str2 = "Hello world!";
                  %s
              }
          }
          """;
        String before = template.formatted(importsBefore, "assertThat(%s, %s(%s));".formatted(actual, hamcrestMatcher, matcherArgs));
        String after = template.formatted(importsAfter, "assertThat(%s).%s(%s);".formatted(actual, assertJAssertion, matcherArgs));
        rewriteRun(java(before, after));
    }

    private static Stream<Arguments> objectReplacements() {
        return Stream.of(
          Arguments.arguments("bis1", "equalTo", "bis2", "isEqualTo"),
          Arguments.arguments("bis1", "hasToString", "bis2.toString()", "hasToString"),
          Arguments.arguments("bis1", "instanceOf", "String.class", "isInstanceOf"),
          Arguments.arguments("bis1", "isA", "String.class", "isInstanceOf"),
          Arguments.arguments("bis1", "notNullValue", "", "isNotNull"),
          Arguments.arguments("bis1", "nullValue", "", "isNull"),
          Arguments.arguments("bis1", "sameInstance", "bis2", "isSameAs")
        );
    }

    @ParameterizedTest
    @MethodSource("objectReplacements")
    void objectReplacements(String actual, String hamcrestMatcher, String matcherArgs, String assertJAssertion) {
        String importsBefore = """
          import static org.hamcrest.MatcherAssert.assertThat;
          import static org.hamcrest.Matchers.%s;""".formatted(hamcrestMatcher);
        String importsAfter = "import static org.assertj.core.api.Assertions.assertThat;";
        //language=java
        String template = """
          import org.junit.jupiter.api.Test;
                    
          %s
                    
          class ATest {
              @Test
              void test() {
                  Biscuit bis1 = new Biscuit("Ginger");
                  Biscuit bis2 = new Biscuit("Ginger");
                  %s
              }
          }
          """;
        String before = template.formatted(importsBefore, "assertThat(%s, %s(%s));".formatted(actual, hamcrestMatcher, matcherArgs));
        String after = template.formatted(importsAfter, "assertThat(%s).%s(%s);".formatted(actual, assertJAssertion, matcherArgs));
        rewriteRun(
          java("""
            class Biscuit {
                String name;
                Biscuit(String name) {
                    this.name = name;
                }
            }
            """),
          java(before, after));
    }

    private static Stream<Arguments> numberReplacements() {
        return Stream.of(
          Arguments.arguments("num1", "greaterThanOrEqualTo", "num2", "isGreaterThanOrEqualTo"),
          Arguments.arguments("num1", "greaterThan", "num2", "isGreaterThan"),
          Arguments.arguments("num1", "lessThanOrEqualTo", "num2", "isLessThanOrEqualTo"),
          Arguments.arguments("num1", "lessThan", "num2", "isLessThan"),
          Arguments.arguments("num1", "not", "num2", "isNotEqualTo")
        );
    }

    @ParameterizedTest
    @MethodSource("numberReplacements")
    void numberReplacements(String actual, String hamcrestMatcher, String matcherArgs, String assertJAssertion) {
        String importsBefore = """
          import static org.hamcrest.MatcherAssert.assertThat;
          import static org.hamcrest.Matchers.%s;""".formatted(hamcrestMatcher);
        String importsAfter = "import static org.assertj.core.api.Assertions.assertThat;";
        //language=java
        String template = """
          import org.junit.jupiter.api.Test;
                    
          %s
                    
          class ATest {
              @Test
              void test() {
                  int num1 = 5;
                  int num2 = 5;
                  %s
              }
          }
          """;
        String before = template.formatted(importsBefore, "assertThat(%s, %s(%s));".formatted(actual, hamcrestMatcher, matcherArgs));
        String after = template.formatted(importsAfter, "assertThat(%s).%s(%s);".formatted(actual, assertJAssertion, matcherArgs));
        rewriteRun(java(before, after));
    }


    private static Stream<Arguments> listReplacements() {
        return Stream.of(
          Arguments.arguments("list1", "contains", "item", "containsExactly"),
          Arguments.arguments("list1", "containsInAnyOrder", "item", "containsExactlyInAnyOrder"),
          Arguments.arguments("list1", "empty", "", "isEmpty"),
          Arguments.arguments("list1", "hasSize", "5", "hasSize"),
          Arguments.arguments("list1", "hasItem", "item", "contains"),
          Arguments.arguments("list1", "hasItems", "item", "contains"),
          Arguments.arguments("item", "in", "list1", "isIn"),
          Arguments.arguments("item", "isIn", "list1", "isIn")
        );
    }

    @ParameterizedTest
    @MethodSource("listReplacements")
    void listReplacements(String actual, String hamcrestMatcher, String matcherArgs, String assertJAssertion) {
        String importsBefore = """
          import static org.hamcrest.MatcherAssert.assertThat;
          import static org.hamcrest.Matchers.%s;""".formatted(hamcrestMatcher);
        String importsAfter = "import static org.assertj.core.api.Assertions.assertThat;";
        //language=java
        String template = """
          import java.util.List;
          import org.junit.jupiter.api.Test;
                    
          %s
                    
          class ATest {
              @Test
              void test(String item) {
                  List<String> list1 = List.of("a", "b", "c");
                  %s
              }
          }
          """;
        String before = template.formatted(importsBefore, "assertThat(%s, %s(%s));".formatted(actual, hamcrestMatcher, matcherArgs));
        String after = template.formatted(importsAfter, "assertThat(%s).%s(%s);".formatted(actual, assertJAssertion, matcherArgs));
        rewriteRun(java(before, after));
    }

    private static Stream<Arguments> mapReplacements() {
        return Stream.of(
          Arguments.arguments("map1", "hasEntry", "key, value", "containsEntry"),
          Arguments.arguments("map1", "hasKey", "key", "containsKey"),
          Arguments.arguments("map1", "hasValue", "value", "containsValue"),
          Arguments.arguments("map1", "anEmptyMap", "", "isEmpty"),
          Arguments.arguments("map1", "aMapWithSize", "5", "hasSize")
        );
    }

    @ParameterizedTest
    @MethodSource("mapReplacements")
    void mapReplacements(String actual, String hamcrestMatcher, String matcherArgs, String assertJAssertion) {
        String importsBefore = """
          import static org.hamcrest.MatcherAssert.assertThat;
          import static org.hamcrest.Matchers.%s;""".formatted(hamcrestMatcher);
        String importsAfter = "import static org.assertj.core.api.Assertions.assertThat;";
        //language=java
        String template = """
          import java.util.Map;
          import org.junit.jupiter.api.Test;
                    
          %s
                    
          class ATest {
              @Test
              void test(String key, String value) {
                  Map<String, String> map1 = Map.of("a", "b", "c", "d");
                  %s
              }
          }
          """;
        String before = template.formatted(importsBefore, "assertThat(%s, %s(%s));".formatted(actual, hamcrestMatcher, matcherArgs));
        String after = template.formatted(importsAfter, "assertThat(%s).%s(%s);".formatted(actual, assertJAssertion, matcherArgs));
        rewriteRun(java(before, after));
    }

    private static Stream<Arguments> notReplacements() {
        return Stream.of(
          Arguments.arguments("str1", "equalTo", "str2", "isNotEqualTo"),
          Arguments.arguments("str1", "hasToString", "str2", "doesNotHaveToString"),
          Arguments.arguments("str1", "in", "java.util.List.of()", "isNotIn"),
          Arguments.arguments("str1", "isIn", "java.util.List.of()", "isNotIn"),
          Arguments.arguments("str1", "instanceOf", "String.class", "isNotInstanceOf"),
          Arguments.arguments("str1", "nullValue", "", "isNotNull"),
          Arguments.arguments("str1", "sameInstance", "str2", "isNotSameAs"),
          // String specific from org.assertj.core.api.AbstractCharSequenceAssert
          Arguments.arguments("str1", "startsWith", "str2", "doesNotStartWith"),
          Arguments.arguments("str1", "endsWith", "str2", "doesNotEndWith"),
          Arguments.arguments("str1", "containsString", "str2", "doesNotContain"),
          Arguments.arguments("str1", "containsStringIgnoringCase", "str2", "doesNotContainIgnoringCase"),
          Arguments.arguments("str1", "matchesPattern", "str2", "doesNotMatch"),
          Arguments.arguments("str1", "equalToIgnoringCase", "str2", "isNotEqualToIgnoringCase"),
          Arguments.arguments("str1", "equalToIgnoringWhiteSpace", "str2", "isNotEqualToIgnoringWhitespace"),
          Arguments.arguments("str1", "blankString", "", "isNotBlank"),
          Arguments.arguments("str1", "emptyString", "", "isNotEmpty")
        );
    }

    @ParameterizedTest
    @MethodSource("notReplacements")
    void notReplacements(String actual, String hamcrestMatcher, String matcherArgs, String assertJAssertion) {
        String importsBefore = """
          import static org.hamcrest.MatcherAssert.assertThat;
          import static org.hamcrest.Matchers.not;
          import static org.hamcrest.Matchers.%s;""".formatted(hamcrestMatcher);
        String importsAfter = "import static org.assertj.core.api.Assertions.assertThat;";
        //language=java
        String template = """
          import org.junit.jupiter.api.Test;
                    
          %s
                    
          class ATest {
              @Test
              void test() {
                  String str1 = "Hello world!";
                  String str2 = "Hello world!";
                  %s
              }
          }
          """;
        String before = template.formatted(importsBefore, "assertThat(%s, not(%s(%s)));".formatted(actual, hamcrestMatcher, matcherArgs));
        String after = template.formatted(importsAfter, "assertThat(%s).%s(%s);".formatted(actual, assertJAssertion, matcherArgs));
        rewriteRun(java(before, after));
    }

    @Nested
    class Dependencies {
        @Language("java")
        private static final String JAVA_BEFORE = """
          import org.junit.jupiter.api.Test;
                              
          import static org.hamcrest.MatcherAssert.assertThat;
          import static org.hamcrest.Matchers.equalTo;
                        
          class ATest {
              @Test
              void test() {
                  assertThat("Hello world!", equalTo("Hello world!"));
              }
          }
          """;
        @Language("java")
        private static final String JAVA_AFTER = """
          import org.junit.jupiter.api.Test;
                              
          import static org.assertj.core.api.Assertions.assertThat;
                        
          class ATest {
              @Test
              void test() {
                  assertThat("Hello world!").isEqualTo("Hello world!");
              }
          }
          """;

        @Test
        void assertjMavenDependencyAddedWithTestScope() {
            rewriteRun(
              mavenProject("project",
                //language=java
                srcTestJava(java(JAVA_BEFORE, JAVA_AFTER)),
                //language=xml
                pomXml("""
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>com.example</groupId>
                      <artifactId>demo</artifactId>
                      <version>0.0.1-SNAPSHOT</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.hamcrest</groupId>
                              <artifactId>hamcrest</artifactId>
                              <version>2.2</version>
                              <scope>test</scope>
                          </dependency>
                      </dependencies>
                  </project>
                  """, """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>com.example</groupId>
                      <artifactId>demo</artifactId>
                      <version>0.0.1-SNAPSHOT</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.assertj</groupId>
                              <artifactId>assertj-core</artifactId>
                              <version>3.24.2</version>
                              <scope>test</scope>
                          </dependency>
                          <dependency>
                              <groupId>org.hamcrest</groupId>
                              <artifactId>hamcrest</artifactId>
                              <version>2.2</version>
                              <scope>test</scope>
                          </dependency>
                      </dependencies>
                  </project>
                  """)));
        }

        @Test
        void assertjGradleDependencyAddedWithTestScope() {
            rewriteRun(
              spec -> spec.beforeRecipe(withToolingApi()),
              mavenProject("project",
                //language=java
                srcTestJava(java(JAVA_BEFORE, JAVA_AFTER)),
                //language=groovy
                buildGradle("""
                  plugins {
                      id "java-library"
                  }
                                      
                  repositories {
                      mavenCentral()
                  }
                                      
                  dependencies {
                      testImplementation "org.hamcrest:hamcrest:2.2"
                  }
                  """, """
                  plugins {
                      id "java-library"
                  }
                                      
                  repositories {
                      mavenCentral()
                  }
                                      
                  dependencies {
                      testImplementation "org.assertj:assertj-core:3.24.2"
                      testImplementation "org.hamcrest:hamcrest:2.2"
                  }
                  """)));
        }
    }

}
