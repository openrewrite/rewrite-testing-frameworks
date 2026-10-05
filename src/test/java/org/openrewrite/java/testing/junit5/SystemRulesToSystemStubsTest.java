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

class SystemRulesToSystemStubsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-4",
              "junit-jupiter-api-5",
              "system-rules-1",
              "system-stubs-core-2",
              "system-stubs-jupiter-2",
              "junit-pioneer-2"))
          .recipe(new SystemRulesToSystemStubs());
    }

    @DocumentExample
    @Test
    void outputRules() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.SystemErrRule;
              import org.junit.contrib.java.lang.system.SystemOutRule;
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertEquals;

              class GreeterTest {
                  @Rule
                  public final SystemOutRule out = new SystemOutRule().enableLog().mute();

                  @Rule
                  public final SystemErrRule err = new SystemErrRule().enableLog();

                  @Test
                  void greets() {
                      System.out.println("Hello");
                      assertEquals("Hello\\n", out.getLogWithNormalizedLineSeparator());
                      out.clearLog();
                      System.err.print("Oops");
                      assertEquals("Oops", err.getLog());
                      assertEquals(4, err.getLogAsBytes().length);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import uk.org.webcompere.systemstubs.jupiter.SystemStub;
              import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
              import uk.org.webcompere.systemstubs.stream.SystemErr;
              import uk.org.webcompere.systemstubs.stream.SystemOut;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static uk.org.webcompere.systemstubs.stream.output.OutputFactories.tapAndOutput;

              @ExtendWith(SystemStubsExtension.class)
              class GreeterTest {
                  @SystemStub
                  public final SystemOut out = new SystemOut();

                  @SystemStub
                  public final SystemErr err = new SystemErr(tapAndOutput());

                  @Test
                  void greets() {
                      System.out.println("Hello");
                      assertEquals("Hello\\n", out.getText().replace(System.lineSeparator(), "\\n"));
                      out.clear();
                      System.err.print("Oops");
                      assertEquals("Oops", err.getText());
                      assertEquals(4, err.getText().getBytes().length);
                  }
              }
              """
          )
        );
    }

    @Test
    void enableLogInTest() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.SystemErrRule;
              import org.junit.contrib.java.lang.system.SystemOutRule;
              import org.junit.jupiter.api.Test;

              class GreeterTest {
                  @Rule
                  public final SystemOutRule out = new SystemOutRule();

                  @Rule
                  public final SystemErrRule err = new SystemErrRule().mute();

                  @Test
                  void greets() {
                      System.out.println("ignored");
                      out.enableLog();
                      System.out.println("Hello");
                      System.out.println(out.getLog());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import uk.org.webcompere.systemstubs.jupiter.SystemStub;
              import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
              import uk.org.webcompere.systemstubs.stream.SystemErr;
              import uk.org.webcompere.systemstubs.stream.SystemOut;
              import uk.org.webcompere.systemstubs.stream.output.NoopStream;

              import static uk.org.webcompere.systemstubs.stream.output.OutputFactories.tapAndOutput;

              @ExtendWith(SystemStubsExtension.class)
              class GreeterTest {
                  @SystemStub
                  public final SystemOut out = new SystemOut(tapAndOutput());

                  @SystemStub
                  public final SystemErr err = new SystemErr(new NoopStream());

                  @Test
                  void greets() {
                      System.out.println("ignored");
                      out.clear();
                      System.out.println("Hello");
                      System.out.println(out.getText());
                  }
              }
              """
          )
        );
    }

    @Test
    void systemPropertyRules() {
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
                  private static final String HOME = System.getProperty("user.home");

                  @ClassRule
                  public static final ProvideSystemProperty PROPERTIES = new ProvideSystemProperty("home", HOME).and("tmp", null);

                  @Rule
                  public final ClearSystemProperties cleared = new ClearSystemProperties("a", "b");

                  @Rule
                  public final RestoreSystemProperties restore = new RestoreSystemProperties();

                  @Test
                  void overrides() {
                      restore.add("c");
                      PROPERTIES.setProperty("home", "/root");
                      cleared.clearProperty("d");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import uk.org.webcompere.systemstubs.jupiter.SystemStub;
              import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
              import uk.org.webcompere.systemstubs.properties.SystemProperties;

              @ExtendWith(SystemStubsExtension.class)
              class ConfigTest {
                  private static final String HOME = System.getProperty("user.home");

                  @SystemStub
                  public static final SystemProperties PROPERTIES = new SystemProperties("home", HOME).remove("tmp");

                  @SystemStub
                  public final SystemProperties cleared = new SystemProperties().remove("a").remove("b");

                  @SystemStub
                  public final SystemProperties restore = new SystemProperties();

                  @Test
                  void overrides() {
                      PROPERTIES.set("home", "/root");
                      cleared.remove("d");
                  }
              }
              """
          )
        );
    }

    @Test
    void systemPropertiesFromFileAndResource() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.ProvideSystemProperty;

              class ConfigTest {
                  @Rule
                  public final ProvideSystemProperty fromFile = ProvideSystemProperty.fromFile("src/test/test.properties");

                  @Rule
                  public final ProvideSystemProperty fromResource = ProvideSystemProperty.fromResource("/test.properties");
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import uk.org.webcompere.systemstubs.jupiter.SystemStub;
              import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
              import uk.org.webcompere.systemstubs.properties.SystemProperties;
              import uk.org.webcompere.systemstubs.resource.PropertySource;

              @ExtendWith(SystemStubsExtension.class)
              class ConfigTest {
                  @SystemStub
                  public final SystemProperties fromFile = new SystemProperties(PropertySource.fromFile("src/test/test.properties"));

                  @SystemStub
                  public final SystemProperties fromResource = new SystemProperties(PropertySource.fromResource("test.properties"));
              }
              """
          )
        );
    }

    @Test
    void standardInput() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.TextFromStandardInputStream;
              import org.junit.jupiter.api.Test;

              import java.io.IOException;

              import static org.junit.contrib.java.lang.system.TextFromStandardInputStream.emptyStandardInputStream;

              class PromptTest {
                  @Rule
                  public final TextFromStandardInputStream in = emptyStandardInputStream();

                  @Test
                  void readsLines() {
                      in.provideLines("first", "second");
                      in.throwExceptionOnInputEnd(new IOException("closed"));
                  }

                  @Test
                  void readsText() {
                      in.provideText("raw");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import uk.org.webcompere.systemstubs.jupiter.SystemStub;
              import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
              import uk.org.webcompere.systemstubs.stream.SystemIn;
              import uk.org.webcompere.systemstubs.stream.input.LinesAltStream;
              import uk.org.webcompere.systemstubs.stream.input.TextAltStream;

              import java.io.IOException;

              @ExtendWith(SystemStubsExtension.class)
              class PromptTest {
                  @SystemStub
                  public final SystemIn in = new SystemIn();

                  @Test
                  void readsLines() {
                      in.setInputStream(new LinesAltStream("first", "second"));
                      in.andExceptionThrownOnInputEnd(new IOException("closed"));
                  }

                  @Test
                  void readsText() {
                      in.setInputStream(new TextAltStream("raw"));
                  }
              }
              """
          )
        );
    }

    @Test
    void deprecatedStreamLogsAndDisallowWrite() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.DisallowWriteToSystemErr;
              import org.junit.contrib.java.lang.system.LogMode;
              import org.junit.contrib.java.lang.system.StandardOutputStreamLog;
              import org.junit.jupiter.api.Test;

              class QuietTest {
                  @Rule
                  public final StandardOutputStreamLog log = new StandardOutputStreamLog(LogMode.LOG_ONLY);

                  @Rule
                  public final DisallowWriteToSystemErr noErrors = new DisallowWriteToSystemErr();

                  @Test
                  void logs() {
                      log.clear();
                      System.out.println(log.getLog());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import uk.org.webcompere.systemstubs.jupiter.SystemStub;
              import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
              import uk.org.webcompere.systemstubs.stream.SystemErr;
              import uk.org.webcompere.systemstubs.stream.SystemOut;
              import uk.org.webcompere.systemstubs.stream.output.DisallowWriteStream;

              @ExtendWith(SystemStubsExtension.class)
              class QuietTest {
                  @SystemStub
                  public final SystemOut log = new SystemOut();

                  @SystemStub
                  public final SystemErr noErrors = new SystemErr(new DisallowWriteStream());

                  @Test
                  void logs() {
                      log.clear();
                      System.out.println(log.getText());
                  }
              }
              """
          )
        );
    }

    @Test
    void keepExistingExtension() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.SystemOutRule;
              import org.junit.jupiter.api.extension.ExtendWith;
              import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

              @ExtendWith(SystemStubsExtension.class)
              class GreeterTest {
                  @Rule
                  public final SystemOutRule out = new SystemOutRule().enableLog().mute();
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import uk.org.webcompere.systemstubs.jupiter.SystemStub;
              import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
              import uk.org.webcompere.systemstubs.stream.SystemOut;

              @ExtendWith(SystemStubsExtension.class)
              class GreeterTest {
                  @SystemStub
                  public final SystemOut out = new SystemOut();
              }
              """
          )
        );
    }

    @Test
    void flagRuleWithUnsupportedUsage() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.SystemOutRule;
              import org.junit.contrib.java.lang.system.TextFromStandardInputStream;
              import org.junit.jupiter.api.Test;

              import java.io.IOException;

              class GreeterTest {
                  @Rule
                  public final SystemOutRule out = new SystemOutRule().enableLog();

                  @Rule
                  public final TextFromStandardInputStream in = TextFromStandardInputStream.emptyStandardInputStream();

                  @Test
                  void greets() {
                      out.mute();
                      in.throwExceptionOnInputEnd(new IOException("closed"));
                      in.provideLines("first");
                  }
              }
              """,
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.SystemOutRule;
              import org.junit.contrib.java.lang.system.TextFromStandardInputStream;
              import org.junit.jupiter.api.Test;

              import java.io.IOException;

              class GreeterTest {
                  // TODO Migrate by hand to System Stubs: this rule is used in a way that has no direct System Stubs equivalent.
                  @Rule
                  public final SystemOutRule out = new SystemOutRule().enableLog();

                  // TODO Migrate by hand to System Stubs: this rule is used in a way that has no direct System Stubs equivalent.
                  @Rule
                  public final TextFromStandardInputStream in = TextFromStandardInputStream.emptyStandardInputStream();

                  @Test
                  void greets() {
                      out.mute();
                      in.throwExceptionOnInputEnd(new IOException("closed"));
                      in.provideLines("first");
                  }
              }
              """
          )
        );
    }

    @Test
    void flagRulePassedAround() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.SystemOutRule;
              import org.junit.jupiter.api.Test;

              class GreeterTest {
                  @Rule
                  public final SystemOutRule out = new SystemOutRule().enableLog();

                  @Test
                  void greets() {
                      check(out);
                  }

                  private void check(SystemOutRule rule) {
                  }
              }
              """,
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.SystemOutRule;
              import org.junit.jupiter.api.Test;

              class GreeterTest {
                  // TODO Migrate by hand to System Stubs: this rule is used in a way that has no direct System Stubs equivalent.
                  @Rule
                  public final SystemOutRule out = new SystemOutRule().enableLog();

                  @Test
                  void greets() {
                      check(out);
                  }

                  private void check(SystemOutRule rule) {
                  }
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
              import org.junit.contrib.java.lang.system.ProvideSystemProperty;
              import org.junit.rules.TestRule;

              class ConfigTest {
                  private static final String HOME = System.getProperty("user.home");

                  @Rule
                  public final TestRule properties = new ProvideSystemProperty("home", HOME);
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import uk.org.webcompere.systemstubs.jupiter.SystemStub;
              import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
              import uk.org.webcompere.systemstubs.properties.SystemProperties;

              @ExtendWith(SystemStubsExtension.class)
              class ConfigTest {
                  private static final String HOME = System.getProperty("user.home");

                  @SystemStub
                  public final SystemProperties properties = new SystemProperties("home", HOME);
              }
              """
          )
        );
    }

    @Test
    void addExtensionNextToPioneerAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.SystemOutRule;
              import org.junitpioneer.jupiter.RestoreSystemProperties;

              @RestoreSystemProperties
              class GreeterTest {
                  @Rule
                  public final SystemOutRule out = new SystemOutRule().enableLog().mute();
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.junitpioneer.jupiter.RestoreSystemProperties;
              import uk.org.webcompere.systemstubs.jupiter.SystemStub;
              import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
              import uk.org.webcompere.systemstubs.stream.SystemOut;

              @ExtendWith(SystemStubsExtension.class)
              @RestoreSystemProperties
              class GreeterTest {
                  @SystemStub
                  public final SystemOut out = new SystemOut();
              }
              """
          )
        );
    }

    @Test
    void flagRuleOnAbstractClass() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.SystemOutRule;

              public abstract class CliTestBase {
                  @Rule
                  public final SystemOutRule out = new SystemOutRule().enableLog();
              }
              """,
            """
              import org.junit.Rule;
              import org.junit.contrib.java.lang.system.SystemOutRule;

              public abstract class CliTestBase {
                  // TODO Migrate by hand: other classes, such as subclasses, may use this rule, which this migration does not see.
                  @Rule
                  public final SystemOutRule out = new SystemOutRule().enableLog();
              }
              """
          )
        );
    }
}
