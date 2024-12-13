/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.testing.cleanup;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveTestPrefixTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "junit-jupiter-api-5.9", "junit-jupiter-params-5.9"))
          .recipe(new RemoveTestPrefix());
    }

    @DocumentExample
    @Test
    void removeTestPrefixes() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void testMethod() {
                  }

                  @Test
                  void test_snake_case() {
                  }

                  @Test
                  void testRTFCharacters() {
                  }

                  @Nested
                  class NestedTestClass {
                      @Test
                      void testAnotherTestMethod() {
                      }
                  }

                  @Nested
                  class AnotherNestedTestClass {
                      @Test
                      void testYetAnotherTestMethod() {
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void method() {
                  }

                  @Test
                  void snake_case() {
                  }

                  @Test
                  void rtfCharacters() {
                  }

                  @Nested
                  class NestedTestClass {
                      @Test
                      void anotherTestMethod() {
                      }
                  }

                  @Nested
                  class AnotherNestedTestClass {
                      @Test
                      void yetAnotherTestMethod() {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreTooShortMethodName() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void test() {
                  }
              }
              """
          )
        );
    }


    @Test
    void ignoreOverriddenMethod() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              abstract class AbstractTest {
                  public abstract void testMethod();
              }

              class BTest extends AbstractTest {
                  @Test
                  @Override
                  public void testMethod() {
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreInvalidName() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void test1Method() {
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreKeyword() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void testSwitch() {
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreNull() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void testNull() {
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreUnderscoreOnly() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void test_() {
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreNotAnnotatedMethods() {
        //language=java
        rewriteRun(
          java(
            """
              class ATest {
                  void testMethod() {
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreToString() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void testToString() {
                  }
              }
              """
          )
        );
    }

    @Test
    void renamedMethodExists() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void testMyDoSomethingLogic() {
                  }

                  void myDoSomethingLogic() {}
              }
              """
          )
        );
    }

    @Test
    void skipImpliedMethodSource() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.params.provider.Arguments;
              import org.junit.jupiter.params.provider.MethodSource;
              import java.util.stream.Stream;

              class ATest {
                  @Test
                  @MethodSource
                  void testMyDoSomethingLogic(Arguments args) {
                  }

                  static Stream<Arguments> testMyDoSomethingLogic() {
                      return Stream.empty();
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/471")
    void ignoreTestingAsPrefix() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void testingEnvironment() {
                  }

                  @Test
                  void tests() {
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/258")
    void ignoreWhenStaticImportConflicts() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import static java.util.List.of;

              class FooTest {
                  @Test
                  void testOf() {
                    of();
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/258")
    void removeOnQualifiedMethodDespiteConflict() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.util.List;

              class FooTest {
                  @Test
                  void testOf() {
                    List.of();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.util.List;

              class FooTest {
                  @Test
                  void of() {
                    List.of();
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/258")
    @Test
    void removeTestPrefixWhenCalled() {
        rewriteRun(
          // language=java
          java(
            """
            import org.junit.jupiter.api.Test;

            public class FooTest {
              @Test
              void bar() {
                  testFoo();
              }

              @Test
              void testFoo() {}
            }
            """,
            """
            import org.junit.jupiter.api.Test;

            public class FooTest {
              @Test
              void bar() {
                  foo();
              }

              @Test
              void foo() {}
            }
            """
          )
        );
    }
}
