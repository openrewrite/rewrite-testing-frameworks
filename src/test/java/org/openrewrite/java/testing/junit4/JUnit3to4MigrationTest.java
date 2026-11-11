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
package org.openrewrite.java.testing.junit4;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class JUnit3to4MigrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-4"))
          .recipeFromResources("org.openrewrite.java.testing.junit4.JUnit3to4Migration");
    }

    @Test
    void migratesTestsAndTheDependency() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <groupId>com.example</groupId>
                <artifactId>app</artifactId>
                <version>1.0</version>
                <dependencies>
                  <dependency>
                    <groupId>junit</groupId>
                    <artifactId>junit</artifactId>
                    <version>3.8.1</version>
                    <scope>test</scope>
                  </dependency>
                </dependencies>
              </project>
              """,
            spec -> spec.after(actual -> assertThat(actual)
              .containsPattern("<version>4\\.13\\.2</version>")
              .doesNotContain("3.8.1")
              .actual())
          ),
          //language=java
          java(
            """
              import junit.framework.AssertionFailedError;
              import junit.framework.TestCase;

              public class ExpectedFailureTest extends TestCase {
                  public void testFails() {
                      try {
                          assertTrue(false);
                          fail("should have failed");
                      } catch (AssertionFailedError expected) {
                          // JUnit 3 assertions throw this
                      }
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.junit.Assert.assertTrue;
              import static org.junit.Assert.fail;

              public class ExpectedFailureTest {
                  @Test
                  public void testFails() {
                      try {
                          assertTrue(false);
                          fail("should have failed");
                      } catch (AssertionError expected) {
                          // JUnit 3 assertions throw this
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removesHamcrestExclusion() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <groupId>com.example</groupId>
                <artifactId>app</artifactId>
                <version>1.0</version>
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
                </dependencies>
              </project>
              """,
            """
              <project>
                <groupId>com.example</groupId>
                <artifactId>app</artifactId>
                <version>1.0</version>
                <dependencies>
                  <dependency>
                    <groupId>junit</groupId>
                    <artifactId>junit</artifactId>
                    <version>4.13.2</version>
                    <scope>test</scope>
                  </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void upgradesOlderJUnit4() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <groupId>com.example</groupId>
                <artifactId>app</artifactId>
                <version>1.0</version>
                <dependencies>
                  <dependency>
                    <groupId>junit</groupId>
                    <artifactId>junit</artifactId>
                    <version>4.5</version>
                    <scope>test</scope>
                  </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <groupId>com.example</groupId>
                <artifactId>app</artifactId>
                <version>1.0</version>
                <dependencies>
                  <dependency>
                    <groupId>junit</groupId>
                    <artifactId>junit</artifactId>
                    <version>4.13.2</version>
                    <scope>test</scope>
                  </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void addsJUnit4WhenTheBuildOnlyHadJUnit3Transitively() {
        rewriteRun(
          mavenProject("app",
            srcTestJava(
              //language=java
              java(
                """
                  import junit.framework.TestCase;

                  public class FooTest extends TestCase {
                      public void testIt() {
                      }
                  }
                  """,
                """
                  import org.junit.Test;

                  public class FooTest {
                      @Test
                      public void testIt() {
                      }
                  }
                  """
              )
            ),
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0</version>
                </project>
                """,
              """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>junit</groupId>
                      <artifactId>junit</artifactId>
                      <version>4.13.2</version>
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
    void junitMatchersContainsStringMovesToCoreMatchers() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-4", "hamcrest-core-1.3")),
          //language=java
          java(
            """
              import junit.framework.TestCase;

              import static org.junit.Assert.assertThat;
              import static org.junit.matchers.JUnitMatchers.containsString;

              public class FooTest extends TestCase {
                  public void testIt() {
                      assertThat("abc", containsString("b"));
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.hamcrest.CoreMatchers.containsString;
              import static org.junit.Assert.assertThat;

              public class FooTest {
                  @Test
                  public void testIt() {
                      assertThat("abc", containsString("b"));
                  }
              }
              """
          )
        );
    }

    @Test
    void unmigratedTestCaseKeepsItsAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.AssertionFailedError;
              import junit.framework.TestCase;

              public class PinnedTest extends TestCase {
                  public PinnedTest(int x) {
                  }

                  public void testIt() {
                      assertEquals(1, 1);
                      try {
                          fail("x");
                      } catch (AssertionFailedError e) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void migratesAssertionsOutsideTestCases() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.Assert;

              public class Checks {
                  public static void check(int x) {
                      Assert.assertEquals(1, x);
                  }
              }
              """,
            """
              import org.junit.Assert;

              public class Checks {
                  public static void check(int x) {
                      Assert.assertEquals(1, x);
                  }
              }
              """
          )
        );
    }

    @Test
    void leavesAssertionsOfTestResultUsers() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.Assert;
              import junit.framework.TestResult;

              public class Listener {
                  public void check(TestResult result) {
                      Assert.assertTrue(result.wasSuccessful());
                  }
              }
              """
          )
        );
    }

    @Test
    void assertionsOfClassWithTestRunnerRunAreMigratedInOneCycle() {
        // TestRunner.run returns a TestResult, which is no reason to leave the assertions alone
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.Test;
              import junit.framework.TestCase;
              import junit.framework.TestSuite;
              import junit.textui.TestRunner;

              public class TestCoreTags extends TestCase {
                  public static void main(String[] args) {
                      TestRunner.run(suite());
                  }

                  public static Test suite() {
                      return new TestSuite(TestCoreTags.class);
                  }

                  public void testArgs() throws Exception {
                      String text = "x";
                      assertEquals("Produces", "one", text);
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.runner.JUnitCore;

              import static org.junit.Assert.assertEquals;

              public class TestCoreTags {
                  public static void main(String[] args) {
                      JUnitCore.runClasses(TestCoreTags.class);
                  }

                  @Test
                  public void testArgs() throws Exception {
                      String text = "x";
                      assertEquals("Produces", "one", text);
                  }
              }
              """
          )
        );
    }
}
