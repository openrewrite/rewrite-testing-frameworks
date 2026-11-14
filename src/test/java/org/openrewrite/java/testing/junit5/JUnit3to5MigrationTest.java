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
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

/**
 * JUnit 3 reaches Jupiter through {@code JUnit3to4Migration} followed by the rest of {@code JUnit4to5Migration}.
 */
class JUnit3to5MigrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "hamcrest-3", "junit-jupiter-api-5"))
          .recipeFromResources("org.openrewrite.java.testing.junit5.JUnit4to5Migration");
    }

    @Test
    void testCaseGoesStraightToJupiter() {
        //language=java
        rewriteRun(
          java(
            """
              import junit.framework.TestCase;

              public class MathTest extends TestCase {
                  protected long value;

                  @Override
                  protected void setUp() {
                      value = 2;
                  }

                  public void testAdd() {
                      assertEquals(4, value + 2);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class MathTest {
                  protected long value;

                  @BeforeEach
                  public void setUp() {
                      value = 2;
                  }

                  @Test
                  public void testAdd() {
                      assertEquals(4, value + 2);
                  }
              }
              """
          )
        );
    }

    @Test
    void subclassOfMigratedBaseStaticallyImportsAssertions() {
        //language=java
        rewriteRun(
          java(
            """
              import junit.framework.TestCase;

              public abstract class BaseTest extends TestCase {
              }
              """,
            """
              public abstract class BaseTest {
              }
              """
          ),
          java(
            """
              public class ChildTest extends BaseTest {
                  public void testAdd() {
                      assertEquals(4, 2 + 2);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class ChildTest extends BaseTest {
                  @Test
                  public void testAdd() {
                      assertEquals(4, 2 + 2);
                  }
              }
              """
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void classJUnit4CannotRepresentStaysJUnit3() {
            // setName has no JUnit 4 equivalent, so the class is left alone rather than half-migrated
            //language=java
            rewriteRun(
              java(
                """
                  import junit.framework.TestCase;

                  public class NamedTest extends TestCase {
                      public void testAdd() {
                          setName("add");
                          assertEquals(4, 2 + 2);
                      }
                  }
                  """,
                """
                  import junit.framework.TestCase;

                  /*~~(Not migrated from JUnit 3: calls TestCase.setName())~~>*/public class NamedTest extends TestCase {
                      public void testAdd() {
                          setName("add");
                          assertEquals(4, 2 + 2);
                      }
                  }
                  """
              )
            );
        }
    }
}
