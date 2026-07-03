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
package org.openrewrite.java.testing.assertj;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.openrewrite.java.Assertions.java;

class AssertJBestPracticesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(
            JavaParser.fromJavaVersion()
              .classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3"))
          .recipeFromResources("org.openrewrite.java.testing.assertj.Assertj");
    }

    @DocumentExample
    @SuppressWarnings("DataFlowIssue")
    @Test
    void convertsIsEqualToEmptyString() {
        rewriteRun(
          // language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;
              class Test {
                  void test() {
                      assertThat("test").isEqualTo("");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;
              class Test {
                  void test() {
                      assertThat("test").isEmpty();
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/398")
    @Test
    void sizeIsEqualToZeroToIsEmpty() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(List<String> list) {
                      assertThat(list.size()).isEqualTo(0);
                  }
              }
              """,
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(List<String> list) {
                      assertThat(list).isEmpty();
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/1032")
    @Test
    void isSameAsZeroConvergesToIsZeroInSingleRun() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(int a, int b, int c, long d, double e, float f, byte g, short h) {
                      assertThat(a).isNotSameAs(0);
                      assertThat(b).isSameAs(0);
                      assertThat(c).isSameAs(1);
                      assertThat(d).isNotSameAs(0L);
                      assertThat(e).isSameAs(0.0);
                      assertThat(f).isSameAs(0f);
                      assertThat(g).isSameAs((byte) 0);
                      assertThat(h).isSameAs((short) 0);
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(int a, int b, int c, long d, double e, float f, byte g, short h) {
                      assertThat(a).isNotZero();
                      assertThat(b).isZero();
                      assertThat(c).isOne();
                      assertThat(d).isNotZero();
                      assertThat(e).isZero();
                      assertThat(f).isZero();
                      assertThat(g).isZero();
                      assertThat(h).isZero();
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/868")
    @Test
    void nullReferenceComparisonsConvergeToIsNull() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(Object a, Object b, Object c, Object d) {
                      assertThat(null == a).isEqualTo(true);
                      assertThat(b == null).isEqualTo(true);
                      assertThat(c != null).isEqualTo(true);
                      assertThat(d == null).isEqualTo(false);
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(Object a, Object b, Object c, Object d) {
                      assertThat(a).isNull();
                      assertThat(b).isNull();
                      assertThat(c).isNotNull();
                      assertThat(d).isNotNull();
                  }
              }
              """
          )
        );
    }

    @Nested
    class CollapseAfterConversion {
        @Test
        void hamcrestToCollapsedAssertions() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "hamcrest-3")),
              //language=java
              java(
                """
                  import java.util.List;

                  import static org.hamcrest.MatcherAssert.assertThat;
                  import static org.hamcrest.Matchers.*;

                  class BiscuitTest {
                      void biscuits() {
                          List<String> biscuits = List.of("Ginger", "Chocolate", "Oatmeal");
                          assertThat(biscuits, is(not(nullValue())));
                          assertThat(biscuits, hasSize(3));
                          assertThat(biscuits, hasItem("Chocolate"));
                      }
                  }
                  """,
                """
                  import java.util.List;

                  import static org.assertj.core.api.Assertions.assertThat;

                  class BiscuitTest {
                      void biscuits() {
                          List<String> biscuits = List.of("Ginger", "Chocolate", "Oatmeal");
                          assertThat(biscuits)
                                  .hasSize(3)
                                  .contains("Chocolate");
                      }
                  }
                  """
              )
            );
        }

        @Test
        void hamcrestToCollapsedAssertionsWithCustomType() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "hamcrest-3")),
              //language=java
              java(
                """
                  class Biscuit {
                      String name;
                      Biscuit(String name) {
                          this.name = name;
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  import java.util.List;

                  import static org.hamcrest.MatcherAssert.assertThat;
                  import static org.hamcrest.Matchers.*;

                  class BiscuitTest {
                      void biscuits() {
                          List<Biscuit> biscuits = List.of(new Biscuit("Ginger"), new Biscuit("Chocolate"), new Biscuit("Oatmeal"));
                          assertThat(biscuits, hasSize(3));
                          assertThat(biscuits, hasItem(new Biscuit("Chocolate")));
                          assertThat(biscuits, not(hasItem(new Biscuit("Raisin"))));
                      }
                  }
                  """,
                """
                  import java.util.List;

                  import static org.assertj.core.api.Assertions.assertThat;

                  class BiscuitTest {
                      void biscuits() {
                          List<Biscuit> biscuits = List.of(new Biscuit("Ginger"), new Biscuit("Chocolate"), new Biscuit("Oatmeal"));
                          assertThat(biscuits)
                                  .hasSize(3)
                                  .contains(new Biscuit("Chocolate"))
                                  .doesNotContain(new Biscuit("Raisin"));
                      }
                  }
                  """
              )
            );
        }

        @Test
        void junitCollapsedAssertions() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5")),
              //language=java
              java(
                """
                  import java.util.List;

                  import static org.junit.jupiter.api.Assertions.*;

                  class BiscuitTest {
                      void biscuits() {
                          List<String> biscuits = List.of("Ginger", "Chocolate", "Oatmeal");
                          assertNotNull(biscuits);
                          assertEquals(3, biscuits.size());
                          assertTrue(biscuits.contains("Chocolate"));
                      }
                  }
                  """,
                """
                  import java.util.List;

                  import static org.assertj.core.api.Assertions.assertThat;

                  class BiscuitTest {
                      void biscuits() {
                          List<String> biscuits = List.of("Ginger", "Chocolate", "Oatmeal");
                          assertThat(biscuits)
                                  .hasSize(3)
                                  .contains("Chocolate");
                      }
                  }
                  """
              )
            );
        }

        @Test
        void assertjCollapsedAssertions() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.List;

                  import static org.assertj.core.api.Assertions.assertThat;

                  class BiscuitTest {
                      void biscuits() {
                          List<String> biscuits = List.of("Ginger", "Chocolate", "Oatmeal");
                          assertThat(biscuits).isNotNull();
                          assertThat(biscuits).hasSize(3);
                          assertThat(biscuits).contains("Chocolate");
                      }
                  }
                  """,
                """
                  import java.util.List;

                  import static org.assertj.core.api.Assertions.assertThat;

                  class BiscuitTest {
                      void biscuits() {
                          List<String> biscuits = List.of("Ginger", "Chocolate", "Oatmeal");
                          assertThat(biscuits)
                                  .hasSize(3)
                                  .contains("Chocolate");
                      }
                  }
                  """
              )
            );
        }

        @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/387")
        @Test
        void decomposeConjunctionIntoDedicatedAssertions() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5")),
              //language=java
              java(
                """
                  import static org.junit.jupiter.api.Assertions.assertTrue;

                  class A {
                      void test(String name, int count) {
                          assertTrue(name != null && count > 5);
                      }
                  }
                  """,
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class A {
                      void test(String name, int count) {
                          assertThat(name).isNotNull();
                          assertThat(count).isGreaterThan(5);
                      }
                  }
                  """
              )
            );
        }

        @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/387")
        @Test
        void decomposeConjunctionWithMethodCallStaysSeparate() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5")),
              //language=java
              java(
                """
                  import static org.junit.jupiter.api.Assertions.assertTrue;

                  class A {
                      int notification() {
                          return 1;
                      }

                      void test() {
                          assertTrue(notification() > 5 && notification() < 10);
                      }
                  }
                  """,
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class A {
                      int notification() {
                          return 1;
                      }

                      void test() {
                          assertThat(notification()).isGreaterThan(5);
                          assertThat(notification()).isLessThan(10);
                      }
                  }
                  """
              )
            );
        }

        @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/387")
        @Test
        void decomposeConjunctionWithSameSubjectCollapsesToChain() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5")),
              //language=java
              java(
                """
                  import static org.junit.jupiter.api.Assertions.assertTrue;

                  class A {
                      void test(int x) {
                          assertTrue(x > 5 && x < 10);
                      }
                  }
                  """,
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class A {
                      void test(int x) {
                          assertThat(x)
                                  .isGreaterThan(5)
                                  .isLessThan(10);
                      }
                  }
                  """
              )
            );
        }
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/496")
    @Test
    void assertTrueContainsWithMethodCallArgPreservesMethodCall() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5")),
          //language=java
          java(
            """
              import java.util.Locale;
              import java.util.Set;

              import static org.junit.jupiter.api.Assertions.assertTrue;

              class MyTest {
                  static class UnderTest {
                      Locale asLocale() {
                          return Locale.US;
                      }
                  }

                  void test(Set<Locale> localeSet) {
                      UnderTest underTest = new UnderTest();
                      assertTrue(localeSet.contains(underTest.asLocale()));
                  }
              }
              """,
            """
              import java.util.Locale;
              import java.util.Set;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  static class UnderTest {
                      Locale asLocale() {
                          return Locale.US;
                      }
                  }

                  void test(Set<Locale> localeSet) {
                      UnderTest underTest = new UnderTest();
                      assertThat(localeSet).contains(underTest.asLocale());
                  }
              }
              """
          )
        );
    }

    /// Chained AssertJ assertions should be simplified to the corresponding dedicated assertion, as
    /// per <a href="https://next.sonarqube.com/sonarqube/coding_rules?open=java%3AS5838&rule_key=java%3AS5838">java:S5838</a>
    @Nested
    class SonarDedicatedAssertions {
        private static Stream<Arguments> replacements() {
            return Stream.of(
              // Related to Object
              arguments("Object", "assertThat(x).isEqualTo(null)", "assertThat(x).isNull()"),
              arguments("Boolean", "assertThat(x).isEqualTo(true)", "assertThat(x).isTrue()"),
              arguments("Boolean", "assertThat(x).isEqualTo(false)", "assertThat(x).isFalse()"),
              arguments("Object", "assertThat(x.equals(y)).isTrue()", "assertThat(x).isEqualTo(y)"),
              arguments("Object", "assertThat(x == y).isTrue()", "assertThat(x).isSameAs(y)"),
              arguments("Object", "assertThat(x == null).isTrue()", "assertThat(x).isNull()"),
              arguments("Object", "assertThat(null == x).isTrue()", "assertThat(x).isNull()"),
              arguments("Object", "assertThat(x != null).isTrue()", "assertThat(x).isNotNull()"),
              arguments("Object", "assertThat(x.toString()).isEqualTo(\"y\")", "assertThat(x).hasToString(\"y\")"),
              arguments("Object", "assertThat(x.hashCode()).isEqualTo(y.hashCode())", "assertThat(x).hasSameHashCodeAs(y)"),
              arguments("Object", "assertThat(x instanceof String).isTrue()", "assertThat(x).isInstanceOf(String.class)"),
              // Related to Comparable
              arguments("java.math.BigDecimal", "assertThat(x.compareTo(y)).isZero()", "assertThat(x).isEqualByComparingTo(y)"),
              arguments("int", "assertThat(x >= y).isTrue()", "assertThat(x).isGreaterThanOrEqualTo(y)"),
              arguments("long", "assertThat(x > y).isTrue()", "assertThat(x).isGreaterThan(y)"),
              arguments("double", "assertThat(x <= y).isTrue()", "assertThat(x).isLessThanOrEqualTo(y)"),
              arguments("float", "assertThat(x < y).isTrue()", "assertThat(x).isLessThan(y)"),
              // Related to String
              arguments("String", "assertThat(x.isEmpty()).isTrue()", "assertThat(x).isEmpty()"),
              arguments("String", "assertThat(x).hasSize(0)", "assertThat(x).isEmpty()"),
              arguments("String", "assertThat(x.equals(y)).isTrue()", "assertThat(x).isEqualTo(y)"),
              arguments(
                "String",
                "assertThat(x.equalsIgnoreCase(y)).isTrue()",
                "assertThat(x).isEqualToIgnoringCase(y)"),
              arguments("String", "assertThat(x.contains(y)).isTrue()", "assertThat(x).contains(y)"),
              arguments(
                "String", "assertThat(x.startsWith(y)).isTrue()", "assertThat(x).startsWith(y)"),
              arguments("String", "assertThat(x.endsWith(y)).isTrue()", "assertThat(x).endsWith(y)"),
              arguments("String", "assertThat(x.matches(y)).isTrue()", "assertThat(x).matches(y)"),
              arguments("String", "assertThat(x.trim()).isEmpty()", "assertThat(x).isBlank()"),
              arguments("String", "assertThat(x.length()).isEqualTo(5)", "assertThat(x).hasSize(5)"),
              arguments("String", "assertThat(x).hasSize(y.length())", "assertThat(x).hasSameSizeAs(y)"),
              // Related to File
              arguments("java.io.File", "assertThat(x).hasSize(0)", "assertThat(x).isEmpty()"),
              arguments("java.io.File", "assertThat(x.length()).isZero()", "assertThat(x).isEmpty()"),
              arguments(
                "java.io.File", "assertThat(x.length()).isEqualTo(3)", "assertThat(x).hasSize(3)"),
              arguments("java.io.File", "assertThat(x.canRead()).isTrue()", "assertThat(x).canRead()"),
              arguments(
                "java.io.File", "assertThat(x.canWrite()).isTrue()", "assertThat(x).canWrite()"),
              arguments("java.io.File", "assertThat(x.exists()).isTrue()", "assertThat(x).exists()"),
              arguments(
                "java.io.File",
                "assertThat(x.getName()).isEqualTo(\"a\")",
                "assertThat(x).hasName(\"a\")"),
              arguments(
                "java.io.File",
                "assertThat(x.getParent()).isEqualTo(\"b\")",
                "assertThat(x).hasParent(\"b\")"),
              arguments(
                "java.io.File",
                "assertThat(x.getParentFile()).isNull()",
                "assertThat(x).hasNoParent()"),
              arguments(
                "java.io.File", "assertThat(x.isAbsolute()).isTrue()", "assertThat(x).isAbsolute()"),
              arguments(
                "java.io.File", "assertThat(x.isAbsolute()).isFalse()", "assertThat(x).isRelative()"),
              arguments(
                "java.io.File",
                "assertThat(x.isDirectory()).isTrue()",
                "assertThat(x).isDirectory()"),
              arguments("java.io.File", "assertThat(x.isFile()).isTrue()", "assertThat(x).isFile()"),
              arguments(
                "java.io.File", "assertThat(x.list()).isEmpty()", "assertThat(x).isEmptyDirectory()"),
              // Related to Path
              arguments(
                "java.nio.file.Path",
                "assertThat(x.startsWith(\"x\")).isTrue()",
                "assertThat(x).startsWithRaw(Path.of(\"x\"))"),
              arguments(
                "java.nio.file.Path",
                "assertThat(x.endsWith(\"y\")).isTrue()",
                "assertThat(x).endsWithRaw(Path.of(\"y\"))"),
              arguments(
                "java.nio.file.Path",
                "assertThat(x.getParent()).isEqualTo(y)",
                "assertThat(x).hasParentRaw(y)"),
              arguments(
                "java.nio.file.Path",
                "assertThat(x.getParent()).isNull()",
                "assertThat(x).hasNoParentRaw()"),
              arguments(
                "java.nio.file.Path",
                "assertThat(x.isAbsolute()).isTrue()",
                "assertThat(x).isAbsolute()"),
              arguments(
                "java.nio.file.Path",
                "assertThat(x.isAbsolute()).isFalse()",
                "assertThat(x).isRelative()"),
              // Related to Array
              arguments("Object[]", "assertThat(x.length).isZero()", "assertThat(x).isEmpty()"),
              arguments("String[]", "assertThat(x.length).isEqualTo(7)", "assertThat(x).hasSize(7)"),
              arguments("int[]", "assertThat(x.length).isEqualTo(y.length)", "assertThat(x).hasSameSizeAs(y)"),
              arguments("boolean[]", "assertThat(x.length).isLessThanOrEqualTo(2)", "assertThat(x).hasSizeLessThanOrEqualTo(2)"),
              arguments("double[]", "assertThat(x.length).isLessThan(5)", "assertThat(x).hasSizeLessThan(5)"),
              arguments("long[]", "assertThat(x.length).isGreaterThan(4)", "assertThat(x).hasSizeGreaterThan(4)"),
              // `hasSizeGreaterThanOrEqualTo(1)` is further simplified to `isNotEmpty()` by AssertJEnumerableRules
              arguments("char[]", "assertThat(x.length).isGreaterThanOrEqualTo(1)", "assertThat(x).isNotEmpty()"),
              // Related to Collection
              arguments("java.lang.Iterable", "assertThat(x).hasSize(0)", "assertThat(x).isEmpty()"),
              arguments(
                "java.util.Collection<String>",
                "assertThat(x).hasSize(0)",
                "assertThat(x).isEmpty()"),
              arguments(
                "java.util.Collection<String>",
                "assertThat(x.isEmpty()).isTrue()",
                "assertThat(x).isEmpty()"),
              arguments(
                "java.util.Collection<String>",
                "assertThat(x.size()).isZero()",
                "assertThat(x).isEmpty()"),
              arguments(
                "java.util.Collection<String>",
                "assertThat(x.contains(\"f\")).isTrue()",
                "assertThat(x).contains(\"f\")"),
              arguments(
                "java.util.Collection<String>",
                "assertThat(x.containsAll(y)).isTrue()",
                "assertThat(x).containsAll(y)"),
              // Related to Map
              arguments(
                "java.util.Map<String, Object>",
                "assertThat(x).hasSize(y.size())",
                "assertThat(x).hasSameSizeAs(y)"),
              arguments(
                "java.util.Map<String, Object>",
                "assertThat(x.containsKey(\"b\")).isTrue()",
                "assertThat(x).containsKey(\"b\")"),
              arguments(
                "java.util.Map<String, Object>",
                "assertThat(x.keySet()).contains(\"b\")",
                "assertThat(x).containsKey(\"b\")"),
              arguments(
                "java.util.Map<String, Object>",
                "assertThat(x.keySet()).containsOnly(\"a\")",
                "assertThat(x).containsOnlyKeys(\"a\")"),
              arguments(
                "java.util.Map<String, Object>",
                "assertThat(x.containsValue(value)).isTrue()",
                "assertThat(x).containsValue(value)"),
              arguments(
                "java.util.Map<String, Object>",
                "assertThat(x.values()).contains(value)",
                "assertThat(x).containsValue(value)"),
              arguments(
                "java.util.Map<String, Object>",
                "assertThat(x.get(\"a\")).isEqualTo(value)",
                "assertThat(x).containsEntry(\"a\", value)"),
              // Related to Optional
              arguments(
                "java.util.Optional<Object>",
                "assertThat(x.isPresent()).isTrue()",
                "assertThat(x).isPresent()"),
              arguments(
                "java.util.Optional<Object>",
                "assertThat(x.get()).isEqualTo(value)",
                "assertThat(x).hasValue(value)"),
              arguments(
                "java.util.Optional<Object>",
                "assertThat(x.get()).isSameAs(value)",
                "assertThat(x).containsSame(value)"));
        }

        @MethodSource("replacements")
        @ParameterizedTest
        void sonarReplacements(
          String argumentsType, String assertToReplace, String dedicatedAssertion) {
            var template =
              """
                %1$simport static org.assertj.core.api.Assertions.assertThat;

                class A {
                    void test(%2$s x, %2$s y, Object value) {
                        %3$s;
                    }
                }
                """;
            String fqType = argumentsType
              .replaceAll("^([A-Z])", "java.lang.$1")
              .replaceAll("<.*>", "")
              .replaceAll("\\[\\]", "");
            // Primitives (e.g. int, float) have no package and need no import
            String imprt = fqType.contains(".") ? "import " + fqType + ";\n" : "";
            rewriteRun(
              java(
                template.formatted(imprt, argumentsType, assertToReplace),
                template.formatted(imprt, argumentsType, dedicatedAssertion)));
        }
    }

}
