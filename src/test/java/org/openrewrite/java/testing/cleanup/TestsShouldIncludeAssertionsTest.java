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
package org.openrewrite.java.testing.cleanup;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.Parser;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.List;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

class TestsShouldIncludeAssertionsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-4",
              "junit-jupiter-api-5",
              "mockito-all-1.10",
              "hamcrest-3",
              "assertj-core-3",
              "spring-test-6.1")
            .dependsOn(
              List.of(
                //language=java
                Parser.Input.fromString(
                  """
                        package org.learning.math;
                        public interface MyMathService {
                            Integer addIntegers(String i1, String i2);
                        }
                    """
                )
              )
            )
          )
          .recipe(new TestsShouldIncludeAssertions(null));
    }

    @DocumentExample
    @Test
    void noAssertions() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              public class AaTest {
                  @Test
                  public void methodTest() {
                      Integer it = Integer.valueOf("2");
                      System.out.println(it);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

              public class AaTest {
                  @Test
                  public void methodTest() {
                      assertDoesNotThrow(() -> {
                          Integer it = Integer.valueOf("2");
                          System.out.println(it);
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void hasAssertDoesNotThrowAssertion() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

              public class AaTest {

                  @Test
                  public void methodTest() {
                      assertDoesNotThrow(() -> {
                          Integer it = Integer.valueOf("2");
                          System.out.println(it);
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void assertJAssertion() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              public class MyTest {

                  @Test
                  public void test() {
                      assertThat(notification()).isEqualTo(1);
                  }
                  private Integer notification() {
                      return 1;
                  }
              }
              """
          )
        );
    }

    @Test
    void hamcrestAssertion() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.*;

              public  class ATest {
                  @Test
                  public void methodTest() {
                      Integer i1 = Integer.valueOf("1");
                      Integer i2 = Integer.valueOf("1");
                      assertThat(i1, equalTo(i2));
                  }
              }
              """
          )
        );
    }

    @Test
    void hasAssertion() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.assertEquals;
              public class AaTest {
                  @Test
                  public void methodTest() {
                      assertEquals(1,1);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/201")
    @Test
    void methodBodyContainsMethodInvocationWithAssert() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Set;
              import org.junit.jupiter.api.Test;

              import static org.junit.Assert.assertTrue;

              public class TestClass {
                  @Test
                  public void methodTest() {
                      Set<String> s = Set.of("hello");
                      testContains(s, "hello");
                  }

                  private static void testContains(Set<String> set, String word) {
                      assertTrue(set.contains(word));
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/201")
    @SuppressWarnings("CodeBlock2Expr")
    @Test
    void usesAdditionalAssertion() {
        rewriteRun(
          spec -> spec.recipe(new TestsShouldIncludeAssertions("org.foo.TestUtil")),
          //language=java
          java(
            """
              package org.foo;

              import java.util.Set;

              public class TestUtil {
                  public static void testContains(Set<String> set, String word) {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import java.util.Set;
              import org.foo.TestUtil;
              import org.junit.jupiter.api.Test;

              public class TestClass {
                  @Test
                  public void doesNotChange() {
                      Set<String> s = Set.of("hello");
                      TestUtil.testContains(s, "hello");
                  }
                  @Test
                  public void changes() {
                      System.out.println("The test requires dependsOn.");
                  }
              }
              """,
            """
              import java.util.Set;
              import org.foo.TestUtil;

              import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
              import org.junit.jupiter.api.Test;

              public class TestClass {
                  @Test
                  public void doesNotChange() {
                      Set<String> s = Set.of("hello");
                      TestUtil.testContains(s, "hello");
                  }
                  @Test
                  public void changes() {
                      assertDoesNotThrow(() -> {
                          System.out.println("The test requires dependsOn.");
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void hasMockitoVerify() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.mockito.Mock;
              import org.learning.math.MyMathService;
              import static org.mockito.Mockito.when;
              import static org.mockito.Mockito.times;
              import static org.mockito.Mockito.verify;

              class AaTest {
                  @Mock
                  MyMathService myMathService;

                  @Test
                  public void verifyTest() {
                      when(myMathService.addIntegers("1", "2")).thenReturn(3);
                      Integer i = myMathService.addIntegers("1", "2");
                      verify(myMathService, times(1));
                  }
              }
              """
          )
        );
    }

    @Test
    void hasMockitoVerifyNoInteractions() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.mockito.Mock;
              import static org.mockito.Mockito.*;

              class AaTest {
                  @Mock
                  org.learning.math.MyMathService myMathService;

                  @Test
                  public void noMore() {
                      verifyNoMoreInteractions(myMathService);
                  }

                  @Test
                  public void zero() {
                      verifyZeroInteractions(myMathService);
                  }
              }
              """
          )
        );
    }

    @Test
    void hasMockitoDoesNotValidate() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.mockito.Mock;
              import org.learning.math.MyMathService;
              import static org.mockito.Mockito.when;
              import org.learning.math.Stuff;

              class AaTest {
                  @Mock
                  MyMathService myMathService;

                  @Test
                  public void methodTest() {
                      when(myMathService.addIntegers("1", "2")).thenReturn(3);
                      myMathService.addIntegers("1", "2");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.mockito.Mock;
              import org.learning.math.MyMathService;

              import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
              import static org.mockito.Mockito.when;
              import org.learning.math.Stuff;

              class AaTest {
                  @Mock
                  MyMathService myMathService;

                  @Test
                  public void methodTest() {
                      assertDoesNotThrow(() -> {
                          when(myMathService.addIntegers("1", "2")).thenReturn(3);
                          myMathService.addIntegers("1", "2");
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void hasMockRestServiceServerVerify() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.springframework.test.web.client.MockRestServiceServer;

              class AaTest {
                  private MockRestServiceServer mockServer;

                  @Test
                  public void verifyTest() {
                      mockServer.verify();
                  }
              }
              """
          )
        );
    }

    @Test
    void hasWebTestClientAssertion() {
        //language=java
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5")
            .dependsOn(
              """
                package org.springframework.test.web.support;
                public abstract class AbstractStatusAssertions<E, R> {
                    public R isOk() { return null; }
                }
                """,
              """
                package org.springframework.test.web.reactive.server;
                public class StatusAssertions extends org.springframework.test.web.support.AbstractStatusAssertions<Object, WebTestClient.ResponseSpec> {
                }
                """,
              """
                package org.springframework.test.web.reactive.server;
                public interface WebTestClient {
                    ResponseSpec exchange();
                    interface ResponseSpec {
                        StatusAssertions expectStatus();
                        BodyContentSpec expectBody();
                    }
                    interface BodyContentSpec {
                        BodyContentSpec json(String json);
                    }
                }
                """
            )),
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.springframework.test.web.reactive.server.WebTestClient;

              class HelloTests {
                  WebTestClient rest;

                  @Test
                  void statusIsOk() {
                      this.rest.exchange()
                          .expectStatus().isOk();
                  }

                  @Test
                  void bodyMatches() {
                      this.rest.exchange()
                          .expectBody().json("{}");
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsCommentsAtEndOfBodyOnce() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              class AaTest {
                  @Test
                  public void methodTest() {
                      // @formatter:off
                      Integer it = Integer.valueOf("2");
                      // @formatter:on
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

              class AaTest {
                  @Test
                  public void methodTest() {
                      assertDoesNotThrow(() -> {
                          // @formatter:off
                          Integer it = Integer.valueOf("2");
                          // @formatter:on
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreDisabled() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Disabled;
              import org.junit.jupiter.api.Test;
              public class AaTest {
                  @Disabled
                  @Test
                  public void methodTest() {
                      Integer it = Integer.valueOf("2");
                      System.out.println(it);
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreJupiterAssumption() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assumptions.assumeTrue;
              class AaTest {
                  @Test
                  void onLinux() {
                      assumeTrue(System.getProperty("os.name").contains("Linux"));
                      System.out.println("start");
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreJUnit4Assume() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Assume;
              import org.junit.jupiter.api.Test;
              class AaTest {
                  @Test
                  void onLinux() {
                      Assume.assumeTrue(System.getProperty("os.name").contains("Linux"));
                      System.out.println("start");
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreAssertJAssumption() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.assertj.core.api.Assumptions.assumeThat;
              class AaTest {
                  @Test
                  void onLinux() {
                      assumeThat(System.getProperty("os.name")).contains("Linux");
                      System.out.println("start");
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreEmpty() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              public class AaTest {
                  @Test
                  public void methodTest() {
                  }
              }
              """
          )
        );
    }

    @Test
    void retainKotlinBody() {
        //language=kotlin
        rewriteRun(
          kotlin(
            """
              import org.junit.jupiter.api.Test
              class FooTest {
                  @Test
                  fun `backtick test method name`() {
                      println("Not an assertion")
                  }
              }
              """
          )
        );
    }

    /**
     * With the real Spring, ArchUnit and Mockito types on the classpath.
     */
    @Nested
    class WithLibraries implements RewriteTest {
        @Override
        public void defaults(RecipeSpec spec) {
            spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
                "junit-jupiter-api-5", "spring-test-6", "mockito-core-5", "archunit-0"))
              .typeValidationOptions(TypeValidation.none())
              .recipe(new TestsShouldIncludeAssertions(null));
        }

        // Real spring-test 6.1 types: status assertion recognised
        @Test
        void webTestClientAssertions() {
            rewriteRun(java(
              """
                import org.junit.jupiter.api.Test;
                import org.springframework.test.web.reactive.server.WebTestClient;

                class HelloTests {
                    WebTestClient rest;

                    // spring-core is not on the test classpath, so javac fails to attribute the first expectBody(..) in a file
                    void warmUp() {
                        rest.get().uri("/").exchange().expectBody();
                    }

                    @Test
                    void statusIsOk() {
                        rest.get().uri("/").exchange().expectStatus().isOk();
                    }

                    @Test
                    void jsonPath() {
                        rest.get().uri("/").exchange().expectBody().jsonPath("$.a").isEqualTo(1);
                    }

                    @Test
                    void listSize() {
                        rest.get().uri("/").exchange().expectBodyList(String.class).hasSize(1);
                    }
                }
                """));
        }

        // returnResult() is declared on BodySpec but asserts nothing: should still be wrapped (it is on main)
        @Test
        void webTestClientReturnResultIsNoAssertion() {
            rewriteRun(java(
              """
                import org.junit.jupiter.api.Test;
                import org.springframework.test.web.reactive.server.WebTestClient;

                class HelloTests {
                    WebTestClient rest;

                    // spring-core is not on the test classpath, so javac fails to attribute the first expectBody(..) in a file
                    void warmUp() {
                        rest.get().uri("/").exchange().expectBody();
                    }

                    @Test
                    void fetch() {
                        rest.get().uri("/").exchange().expectBody(String.class).returnResult();
                    }
                }
                """,
              """
                import org.junit.jupiter.api.Test;
                import org.springframework.test.web.reactive.server.WebTestClient;

                import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

                class HelloTests {
                    WebTestClient rest;

                    // spring-core is not on the test classpath, so javac fails to attribute the first expectBody(..) in a file
                    void warmUp() {
                        rest.get().uri("/").exchange().expectBody();
                    }

                    @Test
                    void fetch() {
                        assertDoesNotThrow(() -> {
                            rest.get().uri("/").exchange().expectBody(String.class).returnResult();
                        });
                    }
                }
                """));
        }

        @Test
        void webTestClientBodyContentReturnResultIsNoAssertion() {
            rewriteRun(java(
              """
                import org.junit.jupiter.api.Test;
                import org.springframework.test.web.reactive.server.WebTestClient;

                class HelloTests {
                    WebTestClient rest;

                    // spring-core is not on the test classpath, so javac fails to attribute the first expectBody(..) in a file
                    void warmUp() {
                        rest.get().uri("/").exchange().expectBody();
                    }

                    @Test
                    void fetch() {
                        byte[] body = rest.get().uri("/").exchange().expectBody().returnResult().getResponseBody();
                        System.out.println(body);
                    }
                }
                """,
              """
                import org.junit.jupiter.api.Test;
                import org.springframework.test.web.reactive.server.WebTestClient;

                import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

                class HelloTests {
                    WebTestClient rest;

                    // spring-core is not on the test classpath, so javac fails to attribute the first expectBody(..) in a file
                    void warmUp() {
                        rest.get().uri("/").exchange().expectBody();
                    }

                    @Test
                    void fetch() {
                        assertDoesNotThrow(() -> {
                            byte[] body = rest.get().uri("/").exchange().expectBody().returnResult().getResponseBody();
                            System.out.println(body);
                        });
                    }
                }
                """));
        }

        // Real ArchUnit fluent rule: declaring type of check
        @Test
        void archRuleCheck() {
            rewriteRun(java(
              """
                import com.tngtech.archunit.core.domain.JavaClasses;
                import org.junit.jupiter.api.Test;

                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

                class ArchTests {
                    JavaClasses classes;

                    @Test
                    void layers() {
                        noClasses().that().resideInAPackage("..service..").should().dependOnClassesThat().resideInAPackage("..web..").check(classes);
                    }
                }
                """));
        }

        @Test
        void archRuleAssertionsCheck() {
            rewriteRun(java(
              """
                import com.tngtech.archunit.core.domain.JavaClasses;
                import com.tngtech.archunit.lang.ArchRule;
                import org.junit.jupiter.api.Test;

                class ArchTests {
                    JavaClasses classes;
                    ArchRule rule;

                    @Test
                    void layers() {
                        ArchRule.Assertions.check(rule, classes);
                    }
                }
                """));
        }

        @Test
        void bddMockitoThenShould() {
            rewriteRun(java(
              """
                import org.junit.jupiter.api.Test;

                import java.util.List;

                import static org.mockito.BDDMockito.then;
                import static org.mockito.Mockito.mock;

                class MyTest {
                    @Test
                    void none() {
                        List<?> list = mock(List.class);
                        then(list).shouldHaveNoInteractions();
                    }
                }
                """));
        }

        // Comment moving: several comments with a blank line between them and a trailing same-line comment
        @Test
        void keepsSeveralTrailingCommentsOnce() {
            rewriteRun(java(
              """
                import org.junit.jupiter.api.Test;

                class AaTest {
                    @Test
                    void methodTest() {
                        Integer it = Integer.valueOf("2"); // trailing

                        // first
                        /* second */
                    }
                }
                """,
              """
                import org.junit.jupiter.api.Test;

                import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

                class AaTest {
                    @Test
                    void methodTest() {
                        assertDoesNotThrow(() -> {
                            Integer it = Integer.valueOf("2"); // trailing

                            // first
                            /* second */
                        });
                    }
                }
                """));
        }

        @Test
        void keepsTrailingCommentOfSingleStatementOnce() {
            rewriteRun(java(
              """
                import org.junit.jupiter.api.Test;

                class AaTest {
                    @Test
                    void methodTest() {
                        Integer.valueOf("2");
                        // @formatter:on
                    }
                }
                """,
              """
                import org.junit.jupiter.api.Test;

                import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

                class AaTest {
                    @Test
                    void methodTest() {
                        assertDoesNotThrow(() -> {
                            Integer.valueOf("2");
                            // @formatter:on
                        });
                    }
                }
                """));
        }

        @Test
        void archRuleOverridingCheck() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
                  "junit-jupiter-api-5", "archunit-0"))
                .recipe(new TestsShouldIncludeAssertions(null)),
              java(
                """
                  import com.tngtech.archunit.core.domain.JavaClasses;
                  import org.junit.jupiter.api.Test;

                  import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

                  class ArchTests {
                      JavaClasses classes;

                      @Test
                      void layers() {
                          layeredArchitecture()
                              .layer("Web").definedBy("..web..")
                              .layer("Service").definedBy("..service..")
                              .whereLayer("Web").mayNotBeAccessedByAnyLayer()
                              .check(classes);
                      }
                  }
                  """
              )
            );
        }

        private static final String WEB = """
          import org.hamcrest.Matcher;
          import org.junit.jupiter.api.Test;
          import org.springframework.test.web.reactive.server.WebTestClient;
          import java.util.function.Consumer;
          import java.util.function.Function;

          class WebTests {
              WebTestClient client;
              Consumer<String> consumer;
              Matcher<String> matcher;
              Function<String, Integer> length;
              Matcher<Integer> intMatcher;

              // spring-core is not on the test classpath, so javac fails to attribute the first expectBody(..) in a file
              void warmUp() {
                  client.get().uri("/").exchange().expectBody();
              }

              @Test
              void body() {
                  %s
              }
          }
          """;

        private void web(String additional, String call, boolean wrapped) {
            String before = WEB.formatted(call);
            String after = WEB.formatted("assertDoesNotThrow(() -> {\n            " + call + "\n        });")
              .replace("import java.util.function.Function;\n", "import java.util.function.Function;\n\nimport static org.junit.jupiter.api.Assertions.assertDoesNotThrow;\n");
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
                  "junit-jupiter-api-5", "spring-test-6", "hamcrest-3"))
                .typeValidationOptions(TypeValidation.none())
                .recipe(new TestsShouldIncludeAssertions(additional)),
              wrapped ? java(before, after) : java(before)
            );
        }

        @Test
        void webTestClientValueWithMatcher() {
            web(null, "client.get().uri(\"/\").exchange().expectBody(String.class).value(matcher);", false);
        }

        @Test
        void webTestClientValueWithFunctionAndMatcher() {
            web(null, "client.get().uri(\"/\").exchange().expectBody(String.class).value(length, intMatcher);", false);
        }

        @Test
        void webTestClientValueWithConsumerIsNoAssertion() {
            web(null, "client.get().uri(\"/\").exchange().expectBody(String.class).value(consumer);", true);
        }

        @Test
        void additionalAssertsCountValueWithConsumer() {
            web("org.springframework.test.web.reactive.server.WebTestClient$BodySpec",
              "client.get().uri(\"/\").exchange().expectBody(String.class).value(consumer);", false);
        }
    }

    @Test
    void keepsCommentOnClosingBraceLineOnce() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              class AaTest {
                  @Test
                  public void methodTest() {
                      Integer it = Integer.valueOf("2"); /* end */ }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

              class AaTest {
                  @Test
                  public void methodTest() {
                      assertDoesNotThrow(() -> {
                          Integer it = Integer.valueOf("2"); /* end */
                      });
                  }
              }
              """
          )
        );
    }
}
