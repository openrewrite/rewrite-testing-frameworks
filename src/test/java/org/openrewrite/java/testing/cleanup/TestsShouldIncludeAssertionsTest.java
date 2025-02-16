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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.Parser;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.openrewrite.java.Assertions.java;

class TestsShouldIncludeAssertionsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4.13", "junit-jupiter-api-5", "mockito-all-1.10", "hamcrest-3", "assertj-core-3", "spring-test-6.1.12")
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

    @SuppressWarnings("CodeBlock2Expr")
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/201")
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
}
