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

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

/**
 * The migration of the matchers that the dedicated Hamcrest recipes leave behind, run as part of
 * {@code MigrateHamcrestToAssertJ}, which no Hamcrest survives.
 */
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
                      assertThat(s).satisfies(arg -> assertThat(arg).startsWith("a"),
                              arg -> assertThat(arg).endsWith("b"));
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
}
