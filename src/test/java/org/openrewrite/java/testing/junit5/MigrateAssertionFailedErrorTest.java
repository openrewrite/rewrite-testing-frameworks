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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateAssertionFailedErrorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4"))
          .recipeFromResources("org.openrewrite.java.testing.junit5.MigrateAssertionFailedError");
    }

    @DocumentExample
    @Test
    void migrateAssertionFailedError() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.AssertionFailedError;

              class MyTest {
                  void test() {
                      try {
                          check();
                      } catch (AssertionFailedError e) {
                          throw new AssertionFailedError("wrapped: " + e.getMessage());
                      }
                  }

                  void check() {
                  }
              }
              """,
            """
              import org.opentest4j.AssertionFailedError;

              class MyTest {
                  void test() {
                      try {
                          check();
                      } catch (AssertionFailedError e) {
                          throw new AssertionFailedError("wrapped: " + e.getMessage());
                      }
                  }

                  void check() {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/1114")
    @Test
    void leaveAssertionFailedErrorPassedToTestResultUntouched() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.AssertionFailedError;
              import junit.framework.TestCase;
              import junit.framework.TestResult;

              public class MultiThreadedTest extends TestCase {

                  private TestResult testResult = null;

                  @Override
                  public void run(TestResult result) {
                      this.testResult = result;
                      super.run(result);
                  }

                  public void handleException(Throwable t) {
                      if (t instanceof AssertionFailedError) {
                          testResult.addFailure(this, (AssertionFailedError) t);
                      } else {
                          testResult.addError(this, t);
                      }
                  }

                  public void testSomething() {
                      assertEquals(2, 1 + 1);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/1114")
    @Test
    void leaveAssertionFailedErrorUntouchedWhenTestResultOnlyUsedImplicitly() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.AssertionFailedError;
              import junit.framework.TestCase;

              public class ReportingTest extends TestCase {

                  public void report(AssertionFailedError e) {
                      createResult().addFailure(this, e);
                  }
              }
              """
          )
        );
    }
}
