/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.testing.hamcrest;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

/// The migration of the matchers that the dedicated Hamcrest recipes leave behind, run as part of
/// `MigrateHamcrestToAssertJ`, which no Hamcrest survives.
class HamcrestRemainingMatchersToAssertJTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-4",
              "hamcrest-3",
              "hamcrest-junit",
              "assertj-core-3"))
          .recipeFromResources("org.openrewrite.java.testing.hamcrest.MigrateHamcrestToAssertJ");
    }

    @DocumentExample
    @Test
    void matchersWithoutDedicatedRecipe() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.*;

              class A {
                  void test(String s, Object o, List<String> list, Iterable<String> it) {
                      assertThat(it, emptyIterable());
                      assertThat(s, oneOf("a", "b"));
                      assertThat(o, theInstance(s));
                      assertThat(s, blankOrNullString());
                      assertThat(s, stringContainsInOrder(List.of("a", "b")));
                      assertThat(String.class, typeCompatibleWith(CharSequence.class));
                      assertThat(o, anything());
                      assertThat(s, both(startsWith("a")).and(endsWith("b")));
                  }
              }
              """,
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void test(String s, Object o, List<String> list, Iterable<String> it) {
                      assertThat(it).isEmpty();
                      assertThat(s).isIn("a", "b");
                      assertThat(o).isSameAs(s);
                      assertThat(s).satisfiesAnyOf(arg -> assertThat(arg).isNull(), arg -> assertThat(arg).isBlank());
                      assertThat(s).containsSubsequence(List.of("a", "b"));
                      assertThat(String.class).isAssignableTo(CharSequence.class);
                      assertThat(s).startsWith("a").endsWith("b");
                  }
              }
              """
          )
        );
    }

    @Test
    void matchersOfMatchers() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              import java.util.Map;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.*;

              class A {
                  void test(String s, List<String> list, Map<String, Integer> map) {
                      assertThat(map, hasEntry(startsWith("a"), greaterThan(1)));
                      assertThat(map, hasKey(startsWith("a")));
                      assertThat(list, hasSize(greaterThan(1)));
                      assertThat("reason", list, contains(startsWith("a"), endsWith("b")));
                      assertThat(s, describedAs("a value", startsWith("a")));
                      assertThat(s, hasToString(startsWith("a")));
                  }
              }
              """,
            """
              import java.util.List;
              import java.util.Map;

              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void test(String s, List<String> list, Map<String, Integer> map) {
                      assertThat(map).anySatisfy((key, value) -> {
                          assertThat(key).startsWith("a");
                          assertThat(value).isGreaterThan(1);
                      });
                      assertThat(map).anySatisfy((key, value) -> assertThat(key).startsWith("a"));
                      assertThat(list).satisfies(arg -> assertThat(arg.size()).isGreaterThan(1));
                      assertThat(list).as("reason").satisfiesExactly(arg -> assertThat(arg).startsWith("a"),
                              arg -> assertThat(arg).endsWith("b"));
                      assertThat(s).as("a value").startsWith("a");
                      assertThat(s).satisfies(arg -> assertThat(arg.toString()).startsWith("a"));
                  }
              }
              """
          )
        );
    }

    @Test
    void junit4AssertThatAndQualifiedMatchers() {
        //language=java
        rewriteRun(
          java(
            """
              import org.hamcrest.Matchers;
              import org.junit.Assert;

              import static org.hamcrest.CoreMatchers.*;

              class A {
                  void test(String s, Integer i) {
                      Assert.assertThat(s, equalTo("a"));
                      Assert.assertThat(i, is(2));
                      Assert.assertThat(s, Matchers.startsWith("a"));
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void test(String s, Integer i) {
                      assertThat(s).isEqualTo("a");
                      assertThat(i).isEqualTo(2);
                      assertThat(s).startsWith("a");
                  }
              }
              """
          )
        );
    }

    @Test
    void nullActualAndArrayOfElements() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.*;

              class A {
                  void test(String s, List<String> names, List<String> sorted) {
                      assertThat(null, equalTo(s));
                      assertThat(names, contains(sorted.toArray()));
                      assertThat(names, containsInAnyOrder(sorted.toArray(new String[0])));
                  }
              }
              """,
            """
              import org.assertj.core.api.InstanceOfAssertFactories;

              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void test(String s, List<String> names, List<String> sorted) {
                      assertThat((Object) null).isEqualTo(s);
                      assertThat(names).asInstanceOf(InstanceOfAssertFactories.ITERABLE).containsExactly(sorted.toArray());
                      assertThat(names).containsExactlyInAnyOrder(sorted.toArray(new String[0]));
                  }
              }
              """
          )
        );
    }

    @Test
    void adaptersAreGeneratedInTheSourceSet() {
        //language=java
        rewriteRun(
          mavenProject("project",
            srcTestJava(
              java(
                """
                  package com.acme.beans;

                  import java.util.List;

                  import static org.hamcrest.MatcherAssert.assertThat;
                  import static org.hamcrest.Matchers.*;

                  class BeanTest {
                      static class Bean {
                          public String getName() {
                              return "a";
                          }
                      }

                      void test(Bean bean, Bean other, List<String> list) {
                          assertThat(bean, samePropertyValuesAs(other));
                          assertThat(list, not(hasSize(2)));
                      }
                  }
                  """,
                """
                  package com.acme.beans;

                  import java.util.List;

                  import static org.assertj.core.api.Assertions.assertThat;

                  class BeanTest {
                      static class Bean {
                          public String getName() {
                              return "a";
                          }
                      }

                      void test(Bean bean, Bean other, List<String> list) {
                          assertThat(bean).is(HamcrestAdapters.<Object>samePropertyValuesAs(other));
                          assertThat(list).isNot(HamcrestAdapters.<List<String>>satisfying(arg -> assertThat(arg).hasSize(2)));
                      }
                  }
                  """
              ),
              java(
                null,
                spec -> spec.path("com/acme/beans/HamcrestAdapters.java")
                  .after(actual -> assertThat(actual)
                    .startsWith("package com.acme.beans;")
                    .contains("public static <T> Condition<Object> satisfying(",
                      "public static <T> Condition<T> samePropertyValuesAs(")
                    .doesNotContain("hasXPath", "inRelativeOrder")
                    .actual())
              )
            )
          )
        );
    }

    @Test
    void adaptersAreAddedToTheGeneratedClass() {
        //language=java
        rewriteRun(
          java(
            """
              package com.acme;

              import java.util.function.Consumer;
              import org.assertj.core.api.Condition;

              /**
               * Generated by the OpenRewrite migration from Hamcrest to AssertJ.
               */
              public final class HamcrestAdapters {

                  private HamcrestAdapters() {
                  }

                  @SuppressWarnings("unchecked")
                  public static <T> Condition<Object> satisfying(Consumer<? super T> requirements) {
                      return new Condition<>(actual -> {
                          try {
                              requirements.accept((T) actual);
                              return true;
                          } catch (AssertionError e) {
                              return false;
                          }
                      }, "satisfying the requirements");
                  }
              }
              """,
            spec -> spec.path("com/acme/HamcrestAdapters.java")
              .after(actual -> assertThat(actual)
                .contains("satisfying(", "samePropertyValuesAs(")
                .actual())
          ),
          java(
            """
              package com.acme;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.samePropertyValuesAs;

              class BeanTest {
                  void test(Object bean, Object other) {
                      assertThat(bean, samePropertyValuesAs(other));
                  }
              }
              """,
            """
              package com.acme;

              import static org.assertj.core.api.Assertions.assertThat;

              class BeanTest {
                  void test(Object bean, Object other) {
                      assertThat(bean).is(HamcrestAdapters.<Object>samePropertyValuesAs(other));
                  }
              }
              """,
            spec -> spec.path("com/acme/BeanTest.java")
          )
        );
    }

    @Test
    void assumptions() {
        //language=java
        rewriteRun(
          java(
            """
              import org.hamcrest.junit.MatcherAssume;

              import static org.hamcrest.Matchers.*;
              import static org.junit.Assume.assumeThat;

              class A {
                  void test(String s) {
                      MatcherAssume.assumeThat(s, notNullValue());
                      assumeThat(s, startsWith("a"));
                      MatcherAssume.assumeThat("reason", s, containsString("b"));
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assumptions.assumeThat;

              class A {
                  void test(String s) {
                      assumeThat(s).isNotNull();
                      assumeThat(s).startsWith("a");
                      assumeThat(s).as("reason").contains("b");
                  }
              }
              """
          )
        );
    }

    @Test
    void hamcrestJUnit() {
        //language=java
        rewriteRun(
          java(
            """
              import org.hamcrest.junit.ExpectedException;
              import org.junit.Rule;
              import org.junit.Test;

              import static org.hamcrest.Matchers.*;
              import static org.hamcrest.junit.MatcherAssert.assertThat;

              public class A {
                  @Rule
                  public ExpectedException thrown = ExpectedException.none();

                  @Test
                  public void test() {
                      assertThat("abc", startsWith("a"));
                      thrown.expect(IllegalStateException.class);
                      thrown.expectMessage(containsString("boom"));
                      throw new IllegalStateException("boom");
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

              public class A {

                  @Test
                  public void test() {
                      assertThat("abc").startsWith("a");
                      Throwable exception = assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> {
                          throw new IllegalStateException("boom");
                      }).actual();
                      assertThat(exception.getMessage()).contains("boom");
                  }
              }
              """
          )
        );
    }

    @Test
    void expectedExceptionWithMatcher() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.Test;
              import org.junit.rules.ExpectedException;

              import static org.hamcrest.Matchers.containsString;

              public class A {
                  @Rule
                  public ExpectedException thrown = ExpectedException.none();

                  @Test
                  public void test() {
                      thrown.expect(IllegalStateException.class);
                      thrown.expectMessage(containsString("boom"));
                      throw new IllegalStateException("boom");
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

              public class A {

                  @Test
                  public void test() {
                      Throwable exception = assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> {
                          throw new IllegalStateException("boom");
                      }).actual();
                      assertThat(exception.getMessage()).contains("boom");
                  }
              }
              """
          )
        );
    }

    @Test
    void expectedExceptionWithCauseMatcher() {
        //language=java
        rewriteRun(
          java(
            """
              import java.io.FileNotFoundException;

              import org.junit.Rule;
              import org.junit.Test;
              import org.junit.rules.ExpectedException;

              import static org.hamcrest.CoreMatchers.isA;

              public class A {
                  @Rule
                  public ExpectedException thrown = ExpectedException.none();

                  @Test
                  public void test() {
                      thrown.expect(IllegalStateException.class);
                      thrown.expectCause(isA(FileNotFoundException.class));
                      throw new IllegalStateException(new FileNotFoundException());
                  }
              }
              """,
            """
              import java.io.FileNotFoundException;

              import org.junit.Test;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

              public class A {

                  @Test
                  public void test() {
                      Throwable exception = assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> {
                          throw new IllegalStateException(new FileNotFoundException());
                      }).actual();
                      assertThat(exception.getCause()).isInstanceOf(FileNotFoundException.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void expectedExceptionWithoutMatcherStays() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.Test;
              import org.junit.rules.ExpectedException;

              public class A {
                  @Rule
                  public ExpectedException thrown = ExpectedException.none();

                  @Test
                  public void test() {
                      thrown.expect(IllegalStateException.class);
                      thrown.expectMessage("boom");
                      throw new IllegalStateException("boom");
                  }
              }
              """
          )
        );
    }

    @Test
    void hamcrestJUnitDependencyIsRemoved() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(2),
          mavenProject("project",
            //language=java
            srcTestJava(java(
              """
                import static org.hamcrest.Matchers.startsWith;
                import static org.hamcrest.junit.MatcherAssert.assertThat;

                class A {
                    void test() {
                        assertThat("abc", startsWith("a"));
                    }
                }
                """,
              """
                import static org.assertj.core.api.Assertions.assertThat;

                class A {
                    void test() {
                        assertThat("abc").startsWith("a");
                    }
                }
                """
            )),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.hamcrest</groupId>
                            <artifactId>hamcrest-junit</artifactId>
                            <version>2.0.0.0</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> assertThat(pom)
                .contains("<artifactId>assertj-core</artifactId>")
                .doesNotContain("hamcrest")
                .actual())
            )
          )
        );
    }

    @Test
    void sourcesCompiledForJava7KeepHamcrest() {
        rewriteRun(
          mavenProject("project",
            //language=java
            srcTestJava(java(
              """
                import static org.hamcrest.MatcherAssert.assertThat;
                import static org.hamcrest.Matchers.anyOf;
                import static org.hamcrest.Matchers.is;
                import static org.hamcrest.Matchers.samePropertyValuesAs;

                class A {
                    void test(Object a, Object b) {
                        assertThat("abc", anyOf(is("abc"), is("def")));
                        assertThat(a, samePropertyValuesAs(b));
                    }
                }
                """,
              spec -> spec.markers(javaVersion(7))
            )),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.hamcrest</groupId>
                            <artifactId>hamcrest</artifactId>
                            <version>3.0</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void allOfHasItemsOfAList() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.allOf;
              import static org.hamcrest.Matchers.hasItem;

              class A {
                  void test(List<String> list) {
                      assertThat(list, allOf(hasItem("a"), hasItem("b")));
                  }
              }
              """,
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void test(List<String> list) {
                      assertThat(list).contains("a");
                      assertThat(list).contains("b");
                  }
              }
              """
          )
        );
    }

    @Test
    void dependencyStaysWhileAnotherModuleUsesHamcrest() {
        rewriteRun(
          mavenProject("a",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>a</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.hamcrest</groupId>
                            <artifactId>hamcrest</artifactId>
                            <version>3.0</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          ),
          mavenProject("b",
            //language=java
            srcTestJava(java(
              """
                import org.hamcrest.BaseMatcher;
                import org.hamcrest.Description;

                class IsEven extends BaseMatcher<Integer> {
                    @Override
                    public boolean matches(Object item) {
                        return ((Integer) item) % 2 == 0;
                    }

                    @Override
                    public void describeTo(Description description) {
                        description.appendText("even");
                    }
                }
                """
            )),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>b</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                </project>
                """
            )
          )
        );
    }

    @Test
    void resultOfGenericMethodWithoutTypeArgument() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.is;
              import static org.hamcrest.Matchers.nullValue;

              class A {
                  <T extends Comparable<?>> T get(int index) {
                      return null;
                  }

                  void test() {
                      assertThat(get(3), nullValue());
                      assertThat(this.<String>get(2), is("a"));
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  <T extends Comparable<?>> T get(int index) {
                      return null;
                  }

                  void test() {
                      assertThat((Object) get(3)).isNull();
                      assertThat(this.<String>get(2)).isEqualTo("a");
                  }
              }
              """
          )
        );
    }

    @Test
    void importsOfSourcesWithoutTypesStay() {
        rewriteRun(
          // as in sources that the build doesn't compile, such as Maven Invoker's integration tests
          spec -> spec.parser(JavaParser.fromJavaVersion())
            .typeValidationOptions(TypeValidation.none()),
          //language=java
          java(
            """
              import org.hamcrest.MatcherAssert;
              import org.hamcrest.Matchers;

              class A {
                  void test(java.util.List<String> deps) {
                      MatcherAssert.assertThat(deps, Matchers.not(Matchers.empty()));
                  }
              }
              """
          )
        );
    }

    @Test
    void isOfArrayOfArrays() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.is;

              class A {
                  void test(Integer[][] actual, Integer[][] expected, Integer[] row, Integer[] expectedRow) {
                      assertThat(actual, is(expected));
                      assertThat(row, is(expectedRow));
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void test(Integer[][] actual, Integer[][] expected, Integer[] row, Integer[] expectedRow) {
                      assertThat(actual).isDeepEqualTo(expected);
                      assertThat(row).containsExactly(expectedRow);
                  }
              }
              """
          )
        );
    }

    @Test
    void anyOfOnASet() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Set;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.anyOf;
              import static org.hamcrest.Matchers.is;

              class A {
                  void test(Set<String> actual, Set<String> a, Set<String> b) {
                      assertThat(actual, anyOf(is(a), is(b)));
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              import java.util.Set;

              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void test(Set<String> actual, Set<String> a, Set<String> b) {
                      Assertions.<Set<String>>assertThat(actual)
                              .satisfiesAnyOf(
                                      arg -> assertThat(arg).isEqualTo(a),
                                      arg -> assertThat(arg).isEqualTo(b)
                              );
                  }
              }
              """
          )
        );
    }

    @Test
    void notOfAMatcherNextToAVariableNamedOrg() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Collections;
              import java.util.List;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.is;
              import static org.hamcrest.Matchers.not;

              class A {
                  void test(List<String> list) {
                      String org = "org";
                      assertThat(list, not(is(Collections.<String>emptyList())));
                  }
              }
              """,
            """
              import java.util.Collections;
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void test(List<String> list) {
                      String org = "org";
                      assertThat(list).isNot(HamcrestAdapters.<List<String>>satisfying(arg -> assertThat(arg).isEqualTo(Collections.<String>emptyList())));
                  }
              }
              """
          ),
          java(
            null,
            "",
            spec -> spec.path("HamcrestAdapters.java").after(actual -> {
                assertThat(actual).contains("satisfying");
                return actual;
            })
          )
        );
    }

    @Test
    void assumptionsOnAList() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Collections;
              import java.util.List;

              import static org.hamcrest.Matchers.hasItem;
              import static org.hamcrest.Matchers.instanceOf;
              import static org.hamcrest.Matchers.is;
              import static org.hamcrest.Matchers.not;
              import static org.junit.Assume.assumeThat;

              class A {
                  void test(List<Number> list) {
                      assumeThat(list, is(Collections.<Number>emptyList()));
                      assumeThat(list, not(hasItem(instanceOf(Integer.class))));
                  }
              }
              """,
            """
              import org.assertj.core.api.Assumptions;

              import java.util.Collections;
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.assertj.core.api.Assumptions.assumeThat;

              class A {
                  void test(List<Number> list) {
                      Assumptions.<List<Number>>assumeThat(list).isEqualTo(Collections.<Number>emptyList());
                      assumeThat(list).isNot(HamcrestAdapters.<List<Number>>satisfying(arg -> assertThat(arg).hasAtLeastOneElementOfType(Integer.class)));
                  }
              }
              """
          ),
          java(
            null,
            "",
            spec -> spec.path("HamcrestAdapters.java").after(actual -> {
                assertThat(actual).contains("satisfying");
                return actual;
            })
          )
        );
    }

    @Test
    void comparableMap() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.HashMap;

              public class Json extends HashMap<String, Object> implements Comparable<Json> {
                  @Override
                  public int compareTo(Json o) {
                      return 0;
                  }
              }
              """
          ),
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.notNullValue;

              class A {
                  void test(Json json) {
                      assertThat(json, notNullValue());
                  }
              }
              """,
            """
              import java.util.HashMap;

              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void test(Json json) {
                      assertThat((HashMap<String, Object>) json).isNotNull();
                  }
              }
              """
          )
        );
    }

    @Test
    void equalToNaN() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.equalTo;
              import static org.hamcrest.Matchers.is;
              import static org.hamcrest.Matchers.not;

              class A {
                  void test(float f, Double d) {
                      assertThat(f, equalTo(Float.NaN));
                      assertThat("reason", d, is(Double.NaN));
                      assertThat(f, not(is(equalTo(Float.NaN))));
                      assertThat(f, equalTo(1.5f));
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void test(float f, Double d) {
                      assertThat(f).isNaN();
                      assertThat(d).as("reason").isNaN();
                      assertThat(f).isNotNaN();
                      assertThat(f).isEqualTo(1.5f);
                  }
              }
              """
          )
        );
    }

    @Test
    void junit4GetsBackTheHamcrestItWasExcludedFrom() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(2),
          mavenProject("a",
            //language=java
            srcTestJava(java(
              """
                import static org.hamcrest.MatcherAssert.assertThat;
                import static org.hamcrest.Matchers.equalTo;

                class ATest {
                    void test() {
                        assertThat(1, equalTo(1));
                    }
                }
                """,
              """
                import static org.assertj.core.api.Assertions.assertThat;

                class ATest {
                    void test() {
                        assertThat(1).isEqualTo(1);
                    }
                }
                """
            )),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>a</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>junit</groupId>
                            <artifactId>junit</artifactId>
                            <version>4.13.2</version>
                            <scope>test</scope>
                            <exclusions>
                                <exclusion>
                                    <groupId>org.hamcrest</groupId>
                                    <artifactId>hamcrest-core</artifactId>
                                </exclusion>
                            </exclusions>
                        </dependency>
                        <dependency>
                            <groupId>org.hamcrest</groupId>
                            <artifactId>hamcrest</artifactId>
                            <version>2.2</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> {
                  assertThat(pom)
                    .contains("<artifactId>assertj-core</artifactId>")
                    .doesNotContain("<exclusion>")
                    .doesNotContain("<artifactId>hamcrest</artifactId>");
                  return pom;
              })
            )
          )
        );
    }

    @Test
    void matcherOfAValueWithoutTypeStays() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import java.util.Arrays;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.hasItem;

              class A {
                  void test(String[] args) {
                      assertThat(Arrays.asList(args), hasItem(Missing.RESET.getOption()));
                  }
              }
              """
          ),
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.is;

              class B {
                  void test(Missing missing) {
                      assertThat(missing.getName(), is("a"));
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class B {
                  void test(Missing missing) {
                      assertThat(missing.getName()).isEqualTo("a");
                  }
              }
              """
          )
        );
    }

    @Test
    void nestedAssertionNextToAVariableNamedOrgWhileHamcrestStays() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import java.util.Collections;
              import java.util.List;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.is;
              import static org.hamcrest.Matchers.not;

              class A {
                  void test(List<String> list, Missing missing) {
                      String org = "org";
                      assertThat(list, not(is(Collections.<String>emptyList())));
                      assertThat(list, missing.matcher());
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              import java.util.Collections;
              import java.util.List;

              import static org.hamcrest.MatcherAssert.assertThat;

              class A {
                  void test(List<String> list, Missing missing) {
                      String org = "org";
                      Assertions.assertThat(list).isNot(HamcrestAdapters.<List<String>>satisfying(arg -> Assertions.assertThat(arg).isEqualTo(Collections.<String>emptyList())));
                      assertThat(list, missing.matcher());
                  }
              }
              """
          ),
          java(
            null,
            "",
            spec -> spec.path("HamcrestAdapters.java").after(actual -> {
                assertThat(actual).contains("satisfying");
                return actual;
            })
          )
        );
    }

    @Test
    void assumptionOnAListOfWildcards() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;

              import static org.hamcrest.Matchers.hasItem;
              import static org.hamcrest.Matchers.instanceOf;
              import static org.hamcrest.Matchers.not;
              import static org.junit.Assume.assumeThat;

              class A {
                  void test(List<Comparable<?>> list) {
                      assumeThat(list, not(hasItem(instanceOf(Integer.class))));
                  }
              }
              """,
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.assertj.core.api.Assumptions.assumeThat;

              class A {
                  void test(List<Comparable<?>> list) {
                      assumeThat(list).isNot(HamcrestAdapters.<List<Comparable<?>>>satisfying(arg -> assertThat(arg).hasAtLeastOneElementOfType(Integer.class)));
                  }
              }
              """
          ),
          java(
            null,
            "",
            spec -> spec.path("HamcrestAdapters.java").after(actual -> {
                assertThat(actual).contains("satisfying");
                return actual;
            })
          )
        );
    }

    @Test
    void assertionWithoutTypeNextToMigratedOnes() {
        //language=java
        rewriteRun(
          spec -> spec.recipe(new QualifyUnmigratedHamcrestAssertions())
            .typeValidationOptions(TypeValidation.none()),
          java(
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.hamcrest.Matchers.empty;
              import static org.hamcrest.Matchers.is;

              class A {
                  void test(List<String> list, Missing missing) {
                      assertThat(list).hasSize(1);
                      assertThat(missing.list(), is(empty()));
                  }
              }
              """,
            """
              import org.hamcrest.MatcherAssert;

              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.hamcrest.Matchers.empty;
              import static org.hamcrest.Matchers.is;

              class A {
                  void test(List<String> list, Missing missing) {
                      assertThat(list).hasSize(1);
                      MatcherAssert.assertThat(missing.list(), is(empty()));
                  }
              }
              """
          )
        );
    }

    @Test
    void equalToOfAFloatingPointValue() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.equalTo;
              import static org.hamcrest.Matchers.is;

              class A {
                  void test(double actual, double expected, Float boxed, float f) {
                      assertThat(actual, is(expected));
                      assertThat("reason", boxed, equalTo(f));
                      assertThat(actual, is(1.5));
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void test(double actual, double expected, Float boxed, float f) {
                      assertThat(actual).isEqualTo(Double.valueOf(expected));
                      assertThat(boxed).as("reason").isEqualTo(Float.valueOf(f));
                      assertThat(actual).isEqualTo(1.5);
                  }
              }
              """
          )
        );
    }

    @Test
    void assumptionOnAListOfAProjectTypeWithWildcards() {
        //language=java
        rewriteRun(
          // the template parser doesn't know the project's types, so the lambda's parameter has none after the migration
          spec -> spec.afterTypeValidationOptions(TypeValidation.none()),
          java(
            """
              package a;
              public abstract class Auth<S extends Comparable<S>, H extends Number> {
              }
              """
          ),
          java(
            """
              import a.Auth;
              import java.util.List;

              import static org.hamcrest.Matchers.hasItem;
              import static org.hamcrest.Matchers.instanceOf;
              import static org.hamcrest.Matchers.not;
              import static org.junit.Assume.assumeThat;

              class A {
                  List<Auth<?, ?>> authorities() { return null; }
                  void test() {
                      assumeThat(authorities(), not(hasItem(instanceOf(Integer.class))));
                  }
              }
              """,
            """
              import a.Auth;

              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.assertj.core.api.Assumptions.assumeThat;

              class A {
                  List<Auth<?, ?>> authorities() { return null; }
                  void test() {
                      assumeThat(authorities()).isNot(HamcrestAdapters.<List<Auth<?, ?>>>satisfying(arg -> assertThat(arg).hasAtLeastOneElementOfType(Integer.class)));
                  }
              }
              """
          ),
          java(
            null,
            "",
            spec -> spec.path("HamcrestAdapters.java").after(actual -> {
                assertThat(actual).contains("satisfying");
                return actual;
            })
          )
        );
    }
}
