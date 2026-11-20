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
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.opentest4j.MultipleFailuresError;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

@SuppressWarnings("JUnitMalformedDeclaration")
class UpdateBeforeAfterAnnotationsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4"))
          .parser(KotlinParser.builder()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4"))
          .recipe(new UpdateBeforeAfterAnnotations());
    }

    @DocumentExample
    @Test
    void beforeToBeforeEach() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Before;

              class Test {
                  @Before
                  void before() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;

              class Test {
                  @BeforeEach
                  void before() {
                  }
              }
              """
          ),
          //language=kotlin
          kotlin(
            """
              import org.junit.Before

              class Test {

                  @Before
                  fun before() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach

              class Test {

                  @BeforeEach
                  fun before() {
                  }
              }
              """
          )
        );
    }

    @Test
    void afterToAfterEach() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.After;

              class Test {

                  @After
                  void after() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterEach;

              class Test {

                  @AfterEach
                  void after() {
                  }
              }
              """
          )
        );
    }

    @Test
    void beforeClassToBeforeAll() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.BeforeClass;

              class Test {

                  @BeforeClass
                  void beforeClass() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeAll;

              class Test {

                  @BeforeAll
                  void beforeClass() {
                  }
              }
              """
          )
        );
    }

    @Test
    void afterClassToAfterAll() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.AfterClass;

              class Test {
                  @AfterClass
                  void afterClass() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterAll;

              class Test {
                  @AfterAll
                  void afterClass() {
                  }
              }
              """
          )
        );
    }

    @Disabled("`UpdateBeforeAfterAnnotations` keeps the `public` modifier and leaves the trailing comment on the annotation")
    @Issue("https://github.com/openrewrite/rewrite/issues/150")
    @Test
    void convertsToPackageVisibility() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Before;

              class Test {

                  @Before // comments
                  public void before() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;

              class Test {

                  // comments
                  @BeforeEach
                  void before() {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/501")
    @Test
    void removeJUnit4AnnotationWhenJupiterEquivalentAlreadyPresent() {
        //language=java
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "junit-jupiter-api")),
          java(
            """
              import org.junit.Before;
              import org.junit.After;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.AfterEach;

              class TestBase {
                  @Before
                  @BeforeEach
                  void setup() {
                  }

                  @After
                  @AfterEach
                  void tearDown() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.AfterEach;

              class TestBase {
                  @BeforeEach
                  void setup() {
                  }

                  @AfterEach
                  void tearDown() {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/59")
    @Test
    void retainPublicModifierOnOverriddenMethod() {
        //language=java
        rewriteRun(

          java(
            """
              public class AbstractTest {
                  abstract public void setup();
              }
              """
          ),
          java(
            """
              import org.junit.Before;

              public class A extends AbstractTest {

                  @Before
                  public void setup() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;

              public class A extends AbstractTest {

                  @BeforeEach
                  public void setup() {
                  }
              }
              """
          )
        );
    }

    @Test
    void consolidateMultipleBeforeMethods() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Before;
              import org.junit.Test;

              class MyTest {
                  @Before
                  void setup1() {
                      System.out.println("setup1");
                  }

                  @Before
                  void setup2() {
                      System.out.println("setup2");
                  }

                  @Test
                  void test() {
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.jupiter.api.BeforeEach;

              class MyTest {

                  @BeforeEach
                  void setUp() {
                      setup2();
                      setup1();
                  }

                  void setup1() {
                      System.out.println("setup1");
                  }

                  void setup2() {
                      System.out.println("setup2");
                  }

                  @Test
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void consolidateMultipleAfterMethods() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.After;
              import org.junit.Test;

              class MyTest {
                  @After
                  void cleanup1() {
                      System.out.println("cleanup1");
                  }

                  @After
                  void cleanup2() {
                      System.out.println("cleanup2");
                  }

                  @Test
                  void test() {
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.jupiter.api.AfterEach;
              import org.opentest4j.MultipleFailuresError;

              import java.util.ArrayList;
              import java.util.List;

              class MyTest {

                  @AfterEach
                  void tearDown() throws Throwable {
                      List<Throwable> failures = new ArrayList<>();
                      try {
                          cleanup1();
                      } catch (Throwable t) {
                          failures.add(t);
                      }
                      try {
                          cleanup2();
                      } catch (Throwable t) {
                          failures.add(t);
                      }
                      if (failures.size() == 1) {
                          throw failures.get(0);
                      }
                      if (failures.size() > 1) {
                          throw new MultipleFailuresError("Lifecycle failures", failures);
                      }
                  }

                  void cleanup1() {
                      System.out.println("cleanup1");
                  }

                  void cleanup2() {
                      System.out.println("cleanup2");
                  }

                  @Test
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void consolidateBothBeforeAndAfterMethods() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Before;
              import org.junit.After;
              import org.junit.Test;

              class MyTest {
                  @Before
                  void setup1() {
                  }

                  @Before
                  void setup2() {
                  }

                  @After
                  void cleanup1() {
                  }

                  @After
                  void cleanup2() {
                  }

                  @Test
                  void test() {
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.opentest4j.MultipleFailuresError;

              import java.util.ArrayList;
              import java.util.List;

              class MyTest {

                  @AfterEach
                  void tearDown() throws Throwable {
                      List<Throwable> failures = new ArrayList<>();
                      try {
                          cleanup1();
                      } catch (Throwable t) {
                          failures.add(t);
                      }
                      try {
                          cleanup2();
                      } catch (Throwable t) {
                          failures.add(t);
                      }
                      if (failures.size() == 1) {
                          throw failures.get(0);
                      }
                      if (failures.size() > 1) {
                          throw new MultipleFailuresError("Lifecycle failures", failures);
                      }
                  }

                  @BeforeEach
                  void setUp() {
                      setup2();
                      setup1();
                  }

                  void setup1() {
                  }

                  void setup2() {
                  }

                  void cleanup1() {
                  }

                  void cleanup2() {
                  }

                  @Test
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void noBeforeOrAfterAnnotations() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;

              class MyTest {
                  @Test
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void onlyBeforeAnnotation() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Before;
              import org.junit.Test;

              class MyTest {
                  @Before
                  void setup() {
                  }

                  @Test
                  void test() {
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.jupiter.api.BeforeEach;

              class MyTest {
                  @BeforeEach
                  void setup() {
                  }

                  @Test
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void onlyAfterAnnotation() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.After;
              import org.junit.Test;

              class MyTest {
                  @After
                  void cleanup() {
                  }

                  @Test
                  void test() {
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.jupiter.api.AfterEach;

              class MyTest {
                  @AfterEach
                  void cleanup() {
                  }

                  @Test
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Nested
    class ConsolidationEdgeCases {
        @TempDir
        Path outputDirectory;

        @ParameterizedTest
        @ValueSource(strings = {"Before", "After"})
        void propagateCheckedExceptions(String annotation) {
            rewriteRun(
              java(
                """
                  import org.junit.%1$s;

                  public class Checked {
                      @%1$s
                      public void first() throws java.io.IOException {
                      }

                      @%1$s
                      public void second() throws Exception {
                      }
                  }
                  """.formatted(annotation),
                spec -> spec.after(source -> assertCompiles(source, "Checked"))
              )
            );
        }

        @ParameterizedTest
        @ValueSource(strings = {"Before", "After"})
        void avoidExistingLifecycleMethodName(String annotation) {
            String methodName = "Before".equals(annotation) ? "setUp" : "tearDown";
            rewriteRun(
              java(
                """
                  import org.junit.%1$s;

                  public class Collision {
                      @%1$s
                      public void %2$s() {
                      }

                      @%1$s
                      public void second() {
                      }
                  }
                  """.formatted(annotation, methodName),
                spec -> spec.after(source -> assertCompiles(source, "Collision"))
              )
            );
        }

        @ParameterizedTest
        @ValueSource(strings = {"Before", "After"})
        void avoidInheritedFinalLifecycleMethodName(String annotation) {
            String methodName = "Before".equals(annotation) ? "setUp" : "tearDown";
            rewriteRun(
              java(
                """
                  import org.junit.%1$s;

                  class Parent {
                      public final void %2$s() {
                      }
                  }

                  public class Child extends Parent {
                      @%1$s
                      public void first() {
                      }

                      @%1$s
                      public void second() {
                      }
                  }
                  """.formatted(annotation, methodName),
                spec -> spec.after(source -> assertCompiles(source, "Child"))
              )
            );
        }

        @ParameterizedTest
        @ValueSource(strings = {"Before", "After"})
        void preserveSiblingLifecycleAnnotation(String annotation) {
            String jupiterAnnotation = "Before".equals(annotation) ? "BeforeEach" : "AfterEach";
            rewriteRun(
              java(
                """
                  import org.junit.%1$s;

                  class Multiple {
                      @%1$s
                      public void first() {
                      }

                      @%1$s
                      public void second() {
                      }
                  }

                  class Single {
                      @%1$s
                      public void only() {
                      }
                  }
                  """.formatted(annotation),
                spec -> spec.after(source -> {
                    assertThat(source.substring(source.indexOf("class Single"))).contains("@" + jupiterAnnotation);
                    return source;
                })
              )
            );
        }

        @ParameterizedTest
        @ValueSource(strings = {"Before", "After"})
        void preserveNestedLifecycleAnnotation(String annotation) {
            String jupiterAnnotation = "Before".equals(annotation) ? "BeforeEach" : "AfterEach";
            rewriteRun(
              java(
                """
                  import org.junit.%1$s;

                  class Multiple {
                      @%1$s
                      public void first() {
                      }

                      @%1$s
                      public void second() {
                      }

                      class Inner {
                          @%1$s
                          public void only() {
                          }
                      }
                  }
                  """.formatted(annotation),
                spec -> spec.after(source -> {
                    assertThat(source.substring(source.indexOf("class Inner"))).contains("@" + jupiterAnnotation);
                    return source;
                })
              )
            );
        }

        @Test
        void runRemainingCleanupAfterFailure() {
            rewriteRun(
              java(
                """
                  import org.junit.After;

                  public class Cleanup {
                      public boolean released;

                      @After
                      public void cleanupA() {
                          throw new IllegalStateException("cleanup failed");
                      }

                      @After
                      public void cleanupZ() {
                          released = true;
                      }
                  }
                  """,
                spec -> spec.after(source -> {
                    assertThatCode(() -> withCompiledSource(source, "Cleanup", type -> {
                        Object instance = type.getConstructor().newInstance();
                        boolean failureReported = false;
                        for (Method method : type.getDeclaredMethods()) {
                            if (method.isAnnotationPresent(AfterEach.class)) {
                                method.setAccessible(true);
                                try {
                                    method.invoke(instance);
                                } catch (InvocationTargetException expected) {
                                    failureReported = true;
                                }
                            }
                        }
                        assertThat(failureReported).as("Cleanup failures must still be reported").isTrue();
                        assertThat(type.getField("released").getBoolean(instance))
                          .as("Every cleanup must execute even when another cleanup fails").isTrue();
                    })).doesNotThrowAnyException();
                    return source;
                })
              )
            );
        }

        // JUnit 4.13.2 reverses MethodSorter order for @Before, so z() runs before a().
        @Test
        void preserveWorkingJUnit4SetupOrder() {
            rewriteRun(
              java(
                """
                  import org.junit.Before;

                  public class OrderedSetup {
                      boolean ready;

                      @Before
                      public void a() {
                          if (!ready) {
                              throw new IllegalStateException("z must run first");
                          }
                      }

                      @Before
                      public void z() {
                          ready = true;
                      }
                  }
                  """,
                spec -> spec.after(source -> {
                    assertThatCode(() -> withCompiledSource(source, "OrderedSetup", type -> {
                        Object instance = type.getConstructor().newInstance();
                        int lifecycleMethods = 0;
                        for (Method method : type.getDeclaredMethods()) {
                            if (method.isAnnotationPresent(BeforeEach.class)) {
                                lifecycleMethods++;
                                method.setAccessible(true);
                                method.invoke(instance);
                            }
                        }
                        assertThat(lifecycleMethods).as("Setup order must be represented by one Jupiter lifecycle method").isEqualTo(1);
                    })).doesNotThrowAnyException();
                    return source;
                })
              )
            );
        }

        @Test
        void preserveLifecycleAnnotationInSeparateSourceFile() {
            rewriteRun(
              java(
                """
                  import org.junit.Before;

                  class Multiple {
                      @Before
                      public void first() {
                      }

                      @Before
                      public void second() {
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.BeforeEach;

                  class Multiple {

                      @BeforeEach
                      void setUp() {
                          first();
                          second();
                      }

                      public void first() {
                      }

                      public void second() {
                      }
                  }
                  """
              ),
              java(
                """
                  import org.junit.Before;

                  class Single {
                      @Before
                      public void only() {
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.BeforeEach;

                  class Single {
                      @BeforeEach
                      public void only() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void migrateMultipleKotlinLifecycleAnnotations() {
            rewriteRun(
              kotlin(
                """
                  import org.junit.Before

                  class KotlinFixture {
                      @Before
                      fun first() {
                      }

                      @Before
                      fun second() {
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.BeforeEach

                  class KotlinFixture {
                      @BeforeEach
                      fun first() {
                      }

                      @BeforeEach
                      fun second() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void avoidOverridingGeneratedSuperclassWrapper() {
            rewriteRun(
              java(
                """
                  import org.junit.Before;

                  class Parent {
                      public int parentCount;

                      @Before
                      public void parentFirst() { parentCount++; }

                      @Before
                      public void parentSecond() { parentCount++; }
                  }

                  public class Child extends Parent {
                      public int childCount;

                      @Before
                      public void childFirst() { childCount++; }

                      @Before
                      public void childSecond() { childCount++; }
                  }
                  """,
                spec -> spec.after(source -> {
                    assertThatCode(() -> withCompiledSource(source, "Child", type -> {
                        Object instance = type.getConstructor().newInstance();
                        for (Class<?> current = type; current != Object.class; current = current.getSuperclass()) {
                            for (Method method : current.getDeclaredMethods()) {
                                if (method.isAnnotationPresent(BeforeEach.class)) {
                                    method.setAccessible(true);
                                    method.invoke(instance);
                                }
                            }
                        }
                        var parentCount = type.getSuperclass().getDeclaredField("parentCount");
                        parentCount.setAccessible(true);
                        assertThat(parentCount.getInt(instance)).isEqualTo(2);
                        assertThat(type.getField("childCount").getInt(instance)).isEqualTo(2);
                    })).doesNotThrowAnyException();
                    return source;
                })
              )
            );
        }

        @Test
        void retainPerMethodOverrideDetection() {
            rewriteRun(
              java(
                """
                  import org.junit.Before;

                  class Parent {
                      @Before
                      public void first() {
                      }

                      @Before
                      public void second() {
                      }
                  }

                  class Child extends Parent {
                      @Override
                      @Before
                      public void first() {
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.BeforeEach;

                  class Parent {
                      @BeforeEach
                      public void first() {
                      }

                      @BeforeEach
                      public void second() {
                      }
                  }

                  class Child extends Parent {
                      @Override
                      @BeforeEach
                      public void first() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void preserveExplicitNameOrdering() {
            rewriteRun(
              java(
                """
                  import org.junit.Before;
                  import org.junit.FixMethodOrder;
                  import org.junit.runners.MethodSorters;

                  @FixMethodOrder(MethodSorters.NAME_ASCENDING)
                  public class ExplicitOrder {
                      boolean ready;

                      @Before
                      public void aa() {
                          if (!ready) {
                              throw new IllegalStateException("z must run first");
                          }
                      }

                      @Before
                      public void z() { ready = true; }
                  }
                  """,
                spec -> spec.after(source -> {
                    assertThat(source).containsSubsequence("z();", "aa();");
                    return source;
                })
              )
            );
        }

        @Test
        void migrateJvmOrderedMethodsWithoutConsolidation() {
            rewriteRun(
              java(
                """
                  import org.junit.Before;
                  import org.junit.FixMethodOrder;
                  import static org.junit.runners.MethodSorters.JVM;

                  @FixMethodOrder(JVM)
                  class JvmOrder {
                      @Before
                      public void first() {
                      }

                      @Before
                      public void second() {
                      }
                  }
                  """,
                """
                  import org.junit.FixMethodOrder;
                  import org.junit.jupiter.api.BeforeEach;

                  import static org.junit.runners.MethodSorters.JVM;

                  @FixMethodOrder(JVM)
                  class JvmOrder {
                      @BeforeEach
                      public void first() {
                      }

                      @BeforeEach
                      public void second() {
                      }
                  }
                  """
              )
            );
        }

        @ParameterizedTest
        @ValueSource(strings = {"IllegalArgumentException", "org.opentest4j.TestAbortedException"})
        void retainAllCleanupFailures(String secondException) {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion()
                .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "opentest4j")),
              java(
                """
                  import org.junit.After;

                  public class Failures {
                      @After
                      public void first() { throw new IllegalStateException("first"); }

                      @After
                      public void second() { throw new %s("second"); }
                  }
                  """.formatted(secondException),
                spec -> spec.after(source -> {
                    assertThatCode(() -> withCompiledSource(source, "Failures", type -> {
                        Object instance = type.getConstructor().newInstance();
                        boolean failureReported = false;
                        for (Method method : type.getDeclaredMethods()) {
                            if (method.isAnnotationPresent(AfterEach.class)) {
                                method.setAccessible(true);
                                try {
                                    method.invoke(instance);
                                } catch (InvocationTargetException expected) {
                                    failureReported = true;
                                    assertThat(expected.getCause()).isInstanceOf(MultipleFailuresError.class);
                                    var failures = ((MultipleFailuresError) expected.getCause()).getFailures();
                                    assertThat(failures).extracting(Throwable::getMessage).containsExactly("second", "first");
                                }
                            }
                        }
                        assertThat(failureReported).isTrue();
                    })).doesNotThrowAnyException();
                    return source;
                })
              )
            );
        }

        @Test
        void preserveExplicitOrderingAsPartOfJUnit4to5Migration() {
            rewriteRun(
              spec -> spec.recipeFromResources("org.openrewrite.java.testing.junit5.JUnit4to5Migration"),
              java(
                """
                  import org.junit.Before;
                  import org.junit.FixMethodOrder;
                  import org.junit.Test;
                  import org.junit.runners.MethodSorters;

                  @FixMethodOrder(MethodSorters.NAME_ASCENDING)
                  public class OrderedTest {
                      boolean ready;

                      @Before
                      public void aa() {
                          if (!ready) {
                              throw new IllegalStateException("z must run first");
                          }
                      }

                      @Before
                      public void z() { ready = true; }

                      @Test
                      public void test() {
                          org.junit.Assert.assertTrue(ready);
                      }
                  }
                  """,
                spec -> spec.after(source -> {
                    assertThat(source).contains("@BeforeEach").containsSubsequence("z();", "aa();")
                      .doesNotContain("import org.junit.Before;", "@FixMethodOrder");
                    return source;
                })
              )
            );
        }

        @Test
        void preserveLocalClassLifecycleAnnotation() {
            rewriteRun(
              java(
                """
                  import org.junit.Before;

                  class Outer {
                      @Before
                      public void first() {
                          class Local {
                              @Before
                              public void only() {
                              }
                          }
                      }

                      @Before
                      public void second() {
                      }
                  }
                  """,
                spec -> spec.after(source -> {
                    assertThat(source.substring(source.indexOf("class Local"))).contains("@BeforeEach");
                    return source;
                })
              )
            );
        }

        @Test
        void doNotConsolidateAlreadyMigratedMethodsTwice() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion()
                .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "junit-jupiter-api-5")),
              java(
                """
                  import org.junit.Before;
                  import org.junit.jupiter.api.BeforeEach;

                  class Mixed {
                      @Before
                      @BeforeEach
                      public void first() {
                      }

                      @Before
                      @BeforeEach
                      public void second() {
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.BeforeEach;

                  class Mixed {
                      @BeforeEach
                      public void first() {
                      }

                      @BeforeEach
                      public void second() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotSuppressTheSameCleanupFailureIntoItself() {
            rewriteRun(
              java(
                """
                  import org.junit.After;

                  public class SharedFailure {
                      final RuntimeException failure = new IllegalStateException("shared");

                      @After
                      public void first() { throw failure; }

                      @After
                      public void second() { throw failure; }
                  }
                  """,
                spec -> spec.after(source -> {
                    assertThatCode(() -> withCompiledSource(source, "SharedFailure", type -> {
                        Object instance = type.getConstructor().newInstance();
                        boolean failureReported = false;
                        for (Method method : type.getDeclaredMethods()) {
                            if (method.isAnnotationPresent(AfterEach.class)) {
                                method.setAccessible(true);
                                try {
                                    method.invoke(instance);
                                } catch (InvocationTargetException expected) {
                                    failureReported = true;
                                    assertThat(expected.getCause()).isInstanceOf(MultipleFailuresError.class);
                                    var failures = ((MultipleFailuresError) expected.getCause()).getFailures();
                                    assertThat(failures).hasSize(2);
                                    assertThat(failures.get(0)).isSameAs(failures.get(1)).hasMessage("shared");
                                }
                            }
                        }
                        assertThat(failureReported).isTrue();
                    })).doesNotThrowAnyException();
                    return source;
                })
              )
            );
        }

        @Test
        void doNotGuessInheritedOrdering() {
            rewriteRun(
              java(
                """
                  import org.junit.Before;
                  import org.junit.FixMethodOrder;
                  import org.junit.runners.MethodSorters;

                  @FixMethodOrder(MethodSorters.JVM)
                  class Parent {
                  }

                  class Child extends Parent {
                      @Before
                      public void first() {
                      }

                      @Before
                      public void second() {
                      }
                  }
                  """,
                spec -> spec.after(source -> assertThat(source).contains("@BeforeEach")
                  .doesNotContain("void setUp").actual())
              )
            );
        }

        @Test
        void leaveAbstractLifecycleMethodsUnconsolidated() {
            rewriteRun(
              java(
                """
                  import org.junit.Before;

                  abstract class AbstractFixture {
                      @Before
                      public abstract void first();

                      @Before
                      public abstract void second();
                  }
                  """,
                spec -> spec.after(source -> assertThat(source).contains("@BeforeEach")
                  .doesNotContain("void setUp").actual())
              )
            );
        }

        private String assertCompiles(String source, String className) {
            assertThatCode(() -> withCompiledSource(source, className, type -> {
            })).doesNotThrowAnyException();
            return source;
        }

        private void withCompiledSource(String source, String className, CompiledSourceAssertion assertion) throws Exception {
            Path sourceFile = outputDirectory.resolve(className + ".java");
            Files.writeString(sourceFile, source);
            Path jupiterApi = Path.of(BeforeEach.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path openTest4j = Path.of(MultipleFailuresError.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            var diagnostics = new ByteArrayOutputStream();
            var exitCode = ToolProvider.getSystemJavaCompiler().run(null, diagnostics, diagnostics,
              "-proc:none", "-classpath", jupiterApi + java.io.File.pathSeparator + openTest4j, "-d", outputDirectory.toString(), sourceFile.toString());
            assertThat(exitCode).as("Generated source must compile:%n%s%n%s", source, diagnostics).isZero();
            try (var loader = new URLClassLoader(new URL[]{outputDirectory.toUri().toURL()}, getClass().getClassLoader())) {
                assertion.accept(loader.loadClass(className));
            }
        }

        @FunctionalInterface
        private interface CompiledSourceAssertion {
            void accept(Class<?> type) throws Exception;
        }
    }
}
