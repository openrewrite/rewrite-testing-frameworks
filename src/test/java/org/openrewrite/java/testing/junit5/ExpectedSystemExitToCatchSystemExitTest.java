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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ExpectedSystemExitToCatchSystemExitTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-4", "junit-jupiter-api-5", "system-rules", "system-stubs-core"))
          .recipe(new ExpectedSystemExitToCatchSystemExit());
    }

    @DocumentExample
    @Test
    void expectedStatusAndAssertion() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.ExpectedSystemExit;
              import org.junit.jupiter.api.Test;

              import java.io.File;

              import static org.junit.jupiter.api.Assertions.assertTrue;

              class CliTest {
                  @Rule
                  public final ExpectedSystemExit exit = ExpectedSystemExit.none();

                  @Test
                  void exitsWithError() {
                      String[] args = {"--bogus"};
                      exit.expectSystemExitWithStatus(2);
                      exit.checkAssertionAfterwards(() -> assertTrue(new File("error.log").exists()));
                      run(args);
                  }

                  private static void run(String[] args) {
                      System.exit(2);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import java.io.File;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.junit.jupiter.api.Assertions.assertTrue;
              import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

              class CliTest {

                  @Test
                  void exitsWithError() throws Exception {
                      String[] args = {"--bogus"};
                      int status = catchSystemExit(() -> run(args));
                      assertEquals(2, status);
                      assertTrue(new File("error.log").exists());
                  }

                  private static void run(String[] args) {
                      System.exit(2);
                  }
              }
              """
          )
        );
    }

    @Test
    void expectedExitWithoutStatus() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.ExpectedSystemExit;
              import org.junit.jupiter.api.Test;

              import java.io.IOException;

              class CliTest {
                  @Rule
                  public final ExpectedSystemExit exit = ExpectedSystemExit.none();

                  @Test
                  void exits() throws IOException {
                      exit.expectSystemExit();
                      exit.checkAssertionAfterwards(() -> {
                          String log = "done";
                          System.out.println(log);
                      });
                      System.out.println("exiting");
                      System.exit(1);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

              class CliTest {

                  @Test
                  void exits() throws Exception {
                      catchSystemExit(() -> {
                          System.out.println("exiting");
                          System.exit(1);
                      });
                      String log = "done";
                      System.out.println(log);
                  }
              }
              """
          )
        );
    }

    @Test
    void avoidStatusNameClash() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.ExpectedSystemExit;
              import org.junit.jupiter.api.Test;

              class CliTest {
                  @Rule
                  public final ExpectedSystemExit exit = ExpectedSystemExit.none();

                  @Test
                  void exits() {
                      int status = 3;
                      exit.expectSystemExitWithStatus(status);
                      System.exit(status);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

              class CliTest {

                  @Test
                  void exits() throws Exception {
                      int status = 3;
                      int status1 = catchSystemExit(() -> System.exit(status));
                      assertEquals(status, status1);
                  }
              }
              """
          )
        );
    }

    @Test
    void removeUnusedPrivateRule() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.ExpectedSystemExit;
              import org.junit.jupiter.api.Test;

              class CliTest {
                  @Rule
                  private final ExpectedSystemExit exit = ExpectedSystemExit.none();

                  @Test
                  void doesNotExit() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              class CliTest {

                  @Test
                  void doesNotExit() {
                  }
              }
              """
          )
        );
    }

    @Test
    void flagUnusedRuleOtherClassesMayUse() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.ExpectedSystemExit;
              import org.junit.jupiter.api.Test;

              class CliTest {
                  @Rule
                  public final ExpectedSystemExit exit = ExpectedSystemExit.none();

                  @Test
                  void doesNotExit() {
                  }
              }
              """,
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.ExpectedSystemExit;
              import org.junit.jupiter.api.Test;

              class CliTest {
                  // TODO Migrate by hand: other classes, such as subclasses, may use this rule, which this migration does not see.
                  @Rule
                  public final ExpectedSystemExit exit = ExpectedSystemExit.none();

                  @Test
                  void doesNotExit() {
                  }
              }
              """
          )
        );
    }

    @Test
    void flagExpectationOutsideTestBody() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.ExpectedSystemExit;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;

              class CliTest {
                  @Rule
                  public final ExpectedSystemExit exit = ExpectedSystemExit.none();

                  @BeforeEach
                  void expectExit() {
                      exit.expectSystemExit();
                  }

                  @Test
                  void exits() {
                      System.exit(1);
                  }
              }
              """,
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.ExpectedSystemExit;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;

              class CliTest {
                  // TODO Migrate by hand to System Stubs' `catchSystemExit(..)`: an expectation is set outside of the test method body, or the code after it can not move into a lambda.
                  @Rule
                  public final ExpectedSystemExit exit = ExpectedSystemExit.none();

                  @BeforeEach
                  void expectExit() {
                      exit.expectSystemExit();
                  }

                  @Test
                  void exits() {
                      System.exit(1);
                  }
              }
              """
          )
        );
    }

    @Test
    void flagReassignedLocal() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.ExpectedSystemExit;
              import org.junit.jupiter.api.Test;

              class CliTest {
                  @Rule
                  public final ExpectedSystemExit exit = ExpectedSystemExit.none();

                  @Test
                  void exits() {
                      int code = 1;
                      code++;
                      exit.expectSystemExitWithStatus(2);
                      System.exit(code);
                  }
              }
              """,
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.ExpectedSystemExit;
              import org.junit.jupiter.api.Test;

              class CliTest {
                  // TODO Migrate by hand to System Stubs' `catchSystemExit(..)`: an expectation is set outside of the test method body, or the code after it can not move into a lambda.
                  @Rule
                  public final ExpectedSystemExit exit = ExpectedSystemExit.none();

                  @Test
                  void exits() {
                      int code = 1;
                      code++;
                      exit.expectSystemExitWithStatus(2);
                      System.exit(code);
                  }
              }
              """
          )
        );
    }

    @Test
    void keepJUnit4Tests() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.Test;
              import org.junit.contrib.java.lang.system.ExpectedSystemExit;

              public class CliTest {
                  @Rule
                  public final ExpectedSystemExit exit = ExpectedSystemExit.none();

                  @Test
                  public void exits() {
                      exit.expectSystemExit();
                      System.exit(1);
                  }
              }
              """
          )
        );
    }
}
