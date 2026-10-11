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

class SystemPropertyRulesToPioneerTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-4",
              "junit-jupiter-api-5",
              "system-rules-1",
              "junit-pioneer-2"))
          .recipe(new SystemPropertyRulesToPioneer());
    }

    @DocumentExample
    @Test
    void literalRulesBecomeClassAnnotations() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.ClassRule;
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.ClearSystemProperties;
              import org.junit.contrib.java.lang.system.ProvideSystemProperty;
              import org.junit.contrib.java.lang.system.RestoreSystemProperties;
              import org.junit.jupiter.api.Test;

              class ConfigTest {
                  @ClassRule
                  public static final ProvideSystemProperty PROPERTIES = new ProvideSystemProperty("a", "1").and("b", "2");

                  @Rule
                  public final ClearSystemProperties cleared = new ClearSystemProperties("c", "d");

                  @Rule
                  public final RestoreSystemProperties restore = new RestoreSystemProperties();

                  @Test
                  void readsProperties() {
                      System.setProperty("e", "5");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junitpioneer.jupiter.ClearSystemProperty;
              import org.junitpioneer.jupiter.RestoreSystemProperties;
              import org.junitpioneer.jupiter.SetSystemProperty;

              @ClearSystemProperty(key = "c")
              @ClearSystemProperty(key = "d")
              @RestoreSystemProperties
              @SetSystemProperty(key = "a", value = "1")
              @SetSystemProperty(key = "b", value = "2")
              class ConfigTest {

                  @Test
                  void readsProperties() {
                      System.setProperty("e", "5");
                  }
              }
              """
          )
        );
    }

    @Test
    void nullValueClearsProperty() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.ProvideSystemProperty;

              class ConfigTest {
                  @Rule
                  public final ProvideSystemProperty properties = new ProvideSystemProperty("a", null);
              }
              """,
            """
              import org.junitpioneer.jupiter.ClearSystemProperty;

              @ClearSystemProperty(key = "a")
              class ConfigTest {
              }
              """
          )
        );
    }

    @Test
    void keepRuleWithNonLiteralValue() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.ProvideSystemProperty;

              class ConfigTest {
                  private static final String HOME = System.getProperty("user.home");

                  @Rule
                  public final ProvideSystemProperty properties = new ProvideSystemProperty("home", HOME);
              }
              """
          )
        );
    }

    @Test
    void keepRuleThatIsReferenced() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.ProvideSystemProperty;
              import org.junit.jupiter.api.Test;

              class ConfigTest {
                  @Rule
                  public final ProvideSystemProperty properties = new ProvideSystemProperty("a", "1");

                  @Test
                  void overrides() {
                      properties.setProperty("a", "2");
                  }
              }
              """
          )
        );
    }

    @Test
    void keepRulesThatTouchTheSameKey() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.ClearSystemProperties;
              import org.junit.contrib.java.lang.system.ProvideSystemProperty;

              class ConfigTest {
                  @Rule
                  public final ProvideSystemProperty properties = new ProvideSystemProperty("a", "1");

                  @Rule
                  public final ClearSystemProperties cleared = new ClearSystemProperties("a");
              }
              """
          )
        );
    }

    @Test
    void ruleDeclaredAsTestRule() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.RestoreSystemProperties;
              import org.junit.rules.TestRule;

              class ConfigTest {
                  @Rule
                  public final TestRule restore = new RestoreSystemProperties();
              }
              """,
            """
              import org.junitpioneer.jupiter.RestoreSystemProperties;

              @RestoreSystemProperties
              class ConfigTest {
              }
              """
          )
        );
    }

    @Test
    void keepRuleOnAbstractClass() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.RestoreSystemProperties;

              public abstract class QueryTestBase {
                  @Rule
                  public RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();
              }
              """
          )
        );
    }
}
