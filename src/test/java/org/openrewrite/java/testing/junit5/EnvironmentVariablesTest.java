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
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class EnvironmentVariablesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new EnvironmentVariables())
          .parser(
            JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit", "junit-jupiter-api", "system-rules"));
    }

    @DocumentExample
    @Test
    void supportedRule() {
        rewriteRun(
          // language=java
          java(
            // before
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.contrib.java.lang.system.EnvironmentVariables;
              import org.junit.Rule;
              import org.junit.jupiter.api.Test;

              public class RuleTest {
                  @Rule
                  public EnvironmentVariables environmentVariables = new EnvironmentVariables()
                          .set("testSetInline", "valueSetInline").clear("testClearInline");

                  @BeforeEach
                  public void setUp() {
                      System.out.println("Setting up...");
                  }

                  @Test
                  public void test() {
                      environmentVariables.clear();
                      environmentVariables.set("testSet", "valueSet").clear("testClear");
                      environmentVariables.clear("clear1", "clear2").clear();
                  }
              }
              """,
            // after
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
              import uk.org.webcompere.systemstubs.jupiter.SystemStub;
              import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

              @ExtendWith(SystemStubsExtension.class)
              public class RuleTest {
                  @SystemStub
                  public EnvironmentVariables environmentVariables = new EnvironmentVariables()
                          .set("testSetInline", "valueSetInline").remove("testClearInline");

                  @BeforeEach
                  public void setUp() {
                      System.out.println("Setting up...");
                  }

                  @Test
                  public void test() {
                      environmentVariables.set("testSet", "valueSet").remove("testClear");
                      environmentVariables.remove("clear1").remove("clear2");
                  }
              }
              """
          )
        );
    }

    @Test
    void supportedClassRule() {
        rewriteRun(
          // language=java
          java(
            // before
            """
              import org.junit.ClassRule;
              import org.junit.jupiter.api.BeforeAll;
              import org.junit.contrib.java.lang.system.EnvironmentVariables;

              public class RuleTest {
                  @ClassRule
                  public static EnvironmentVariables environmentVariables = new EnvironmentVariables()
                          .set("testSetInline", "valueSetInline").clear("clearInline");

                  @BeforeAll
                  public static void setUp() {
                      environmentVariables.set("testSet", "valueSet").clear("clear1", "clear2");
                  }
              }
              """,
            // after
            """
              import org.junit.jupiter.api.BeforeAll;
              import org.junit.jupiter.api.extension.ExtendWith;
              import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
              import uk.org.webcompere.systemstubs.jupiter.SystemStub;
              import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

              @ExtendWith(SystemStubsExtension.class)
              public class RuleTest {
                  @SystemStub
                  public static EnvironmentVariables environmentVariables = new EnvironmentVariables()
                          .set("testSetInline", "valueSetInline").remove("clearInline");

                  @BeforeAll
                  public static void setUp() {
                      environmentVariables.set("testSet", "valueSet").remove("clear1").remove("clear2");
                  }
              }
              """
          )
        );
    }

    @Test
    void notAsRule() {
        rewriteRun(
          // language=java
          java(
            // before
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.contrib.java.lang.system.EnvironmentVariables;

              public class RuleTest {
                  public EnvironmentVariables environmentVariables = new EnvironmentVariables();

                  @BeforeEach
                  public void setUp() {
                      environmentVariables.set("testSet", "valueSet");
                  }
                  @AfterEach
                  public void tearDown() {
                      environmentVariables.clear("testSet");
                  }
              }
              """,
            // after
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

              public class RuleTest {
                  public EnvironmentVariables environmentVariables = new EnvironmentVariables();

                  @BeforeEach
                  public void setUp() {
                      environmentVariables.set("testSet", "valueSet");
                  }
                  @AfterEach
                  public void tearDown() {
                      environmentVariables.remove("testSet");
                  }
              }
              """
          )
        );
    }

    @Test
    void dontTouchOtherRules() {
        rewriteRun(
          // language=java
          java(
            // before
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.SystemOutRule;

              public class RuleTest {
                  @Rule
                  public SystemOutRule systemOutRule = new SystemOutRule().mute().enableLog();
              }
              """
          )
        );
    }
}
