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

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AddMissingTestBeforeAfterAnnotationsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4.13", "junit-jupiter-api-5"))
          .recipe(new AddMissingTestBeforeAfterAnnotations());
    }

    @Test
    void addMissingTestBeforeAfterAnnotationsIfOldFound() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.After;
              import org.junit.Before;
              import org.junit.Test;

              public class AbstractTest {
                  @Before
                  public void before() {
                  }

                  @After
                  public void after() {
                  }

                  @Test
                  public void test() {
                  }
              }
              """
          ),
          java(
            """
              public class A extends AbstractTest {
                  public void before() {
                  }

                  public void after() {
                  }

                  public void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;

              public class A extends AbstractTest {
                  @BeforeEach
                  public void before() {
                  }

                  @AfterEach
                  public void after() {
                  }

                  @Test
                  public void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void addMissingTestBeforeAfterAnnotationsIfNewFound() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;

              public class AbstractTest {
                  @BeforeEach
                  public void before() {
                  }

                  @AfterEach
                  public void after() {
                  }

                  @Test
                  public void test() {
                  }
              }
              """
          ),
          java(
            """
              public class A extends AbstractTest {
                  public void before() {
                  }

                  public void after() {
                  }

                  public void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;

              public class A extends AbstractTest {
                  @BeforeEach
                  public void before() {
                  }

                  @AfterEach
                  public void after() {
                  }

                  @Test
                  public void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void addMissingTestBeforeAfterAnnotationsIfExtended() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;

              public class AbstractTest {
                  @BeforeEach
                  public void before() {
                  }

                  @AfterEach
                  public void after() {
                  }

                  @Test
                  public void test() {
                  }
              }
              """
          ),
          java(
            """
              public class A extends AbstractTest {
                  public void before() {
                  }

                  public void after() {
                  }

                  public void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;

              public class A extends AbstractTest {
                  @BeforeEach
                  public void before() {
                  }

                  @AfterEach
                  public void after() {
                  }

                  @Test
                  public void test() {
                  }
              }
              """
          ),
          java(
                """
                  public class B extends A {
                      public void before() {
                      }

                      public void after() {
                      }

                      public void test() {
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.AfterEach;
                  import org.junit.jupiter.api.BeforeEach;
                  import org.junit.jupiter.api.Test;

                  public class B extends A {
                      @BeforeEach
                      public void before() {
                      }

                      @AfterEach
                      public void after() {
                      }

                      @Test
                      public void test() {
                      }
                  }
                  """
              )
            );
    }

}
