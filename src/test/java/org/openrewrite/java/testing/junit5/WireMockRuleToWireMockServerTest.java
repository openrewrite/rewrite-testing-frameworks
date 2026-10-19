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

@SuppressWarnings("JUnitMalformedDeclaration")
class WireMockRuleToWireMockServerTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-4",
              "wiremock-jre8-2.35"))
          .recipe(new WireMockRuleToWireMockServer());
    }

    @DocumentExample
    @Test
    void stoppedRuleBecomesServer() {
        //language=java
        rewriteRun(
          java(
            """
              import com.github.tomakehurst.wiremock.junit.WireMockRule;
              import org.junit.Rule;

              import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

              class Test {
                  @Rule
                  public WireMockRule wm = new WireMockRule(options().dynamicPort());

                  void outage() {
                      int port = wm.port();
                      wm.stop();
                      if (!wm.isRunning()) {
                          wm.start();
                      }
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.WireMockServer;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;

              import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

              class Test {
                  public WireMockServer wm = new WireMockServer(options().dynamicPort());

                  @BeforeEach
                  void startWireMock() {
                      wm.start();
                  }

                  @AfterEach
                  void stopWireMock() {
                      wm.stop();
                  }

                  void outage() {
                      int port = wm.port();
                      wm.stop();
                      if (!wm.isRunning()) {
                          wm.start();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void ruleWithoutServerMethodsUnchanged() {
        //language=java
        rewriteRun(
          java(
            """
              import com.github.tomakehurst.wiremock.junit.WireMockRule;
              import org.junit.Rule;

              class Test {
                  @Rule
                  public WireMockRule wm = new WireMockRule(8089);

                  String baseUrl() {
                      return "http://localhost:" + wm.port();
                  }
              }
              """
          )
        );
    }

    @Test
    void startAndStopInExistingLifecycleMethods() {
        //language=java
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-4",
              "junit-jupiter-api-5",
              "junit-platform-commons-1",
              "wiremock-jre8-2.35")),
          java(
            """
              import com.github.tomakehurst.wiremock.junit.WireMockRule;
              import org.junit.Rule;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;

              class Test {
                  @Rule
                  public WireMockRule wm = new WireMockRule(8089);
                  String baseUrl;

                  @BeforeEach
                  void setUp() {
                      baseUrl = "http://localhost:" + wm.port();
                  }

                  @AfterEach
                  void tearDown() {
                      baseUrl = null;
                  }

                  void outage() {
                      wm.shutdown();
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.WireMockServer;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;

              class Test {
                  public WireMockServer wm = new WireMockServer(8089);
                  String baseUrl;

                  @BeforeEach
                  void setUp() {
                      wm.start();
                      baseUrl = "http://localhost:" + wm.port();
                  }

                  @AfterEach
                  void tearDown() {
                      baseUrl = null;
                      wm.stop();
                  }

                  void outage() {
                      wm.shutdown();
                  }
              }
              """
          )
        );
    }

    @Test
    void classRuleBecomesStaticServer() {
        //language=java
        rewriteRun(
          java(
            """
              import com.github.tomakehurst.wiremock.junit.WireMockRule;
              import org.junit.ClassRule;

              class Test {
                  @ClassRule
                  public static WireMockRule wm = new WireMockRule(8089, 8443);

                  void outage() {
                      wm.stop();
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.WireMockServer;
              import org.junit.jupiter.api.AfterAll;
              import org.junit.jupiter.api.BeforeAll;

              class Test {
                  public static WireMockServer wm = new WireMockServer(8089, 8443);

                  @BeforeAll
                  static void startWireMock() {
                      wm.start();
                  }

                  @AfterAll
                  static void stopWireMock() {
                      wm.stop();
                  }

                  void outage() {
                      wm.stop();
                  }
              }
              """
          )
        );
    }

    @Test
    void ruleUsedAsServerBecomesServer() {
        //language=java
        rewriteRun(
          java(
            """
              import com.github.tomakehurst.wiremock.WireMockServer;

              class Client {
                  Client(WireMockServer server) {
                  }
              }
              """
          ),
          java(
            """
              import com.github.tomakehurst.wiremock.junit.WireMockRule;
              import org.junit.Rule;

              import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

              class Test {
                  @Rule
                  public WireMockRule wm = new WireMockRule(options().dynamicPort(), false);

                  Client client() {
                      return new Client(wm);
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.WireMockServer;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;

              import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

              class Test {
                  public WireMockServer wm = new WireMockServer(options().dynamicPort());

                  @BeforeEach
                  void startWireMock() {
                      wm.start();
                  }

                  @AfterEach
                  void stopWireMock() {
                      wm.stop();
                  }

                  Client client() {
                      return new Client(wm);
                  }
              }
              """
          )
        );
    }

    @Test
    void ruleStoppedInSubclassBecomesServer() {
        //language=java
        rewriteRun(
          java(
            """
              import com.github.tomakehurst.wiremock.junit.WireMockRule;
              import org.junit.Rule;

              abstract class AbstractWireMockTest {
                  @Rule
                  public WireMockRule wm = new WireMockRule(8089);
              }
              """,
            """
              import com.github.tomakehurst.wiremock.WireMockServer;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;

              abstract class AbstractWireMockTest {
                  public WireMockServer wm = new WireMockServer(8089);

                  @BeforeEach
                  void startWireMock() {
                      wm.start();
                  }

                  @AfterEach
                  void stopWireMock() {
                      wm.stop();
                  }
              }
              """
          ),
          java(
            """
              class OutageTest extends AbstractWireMockTest {
                  void outage() {
                      wm.stop();
                  }
              }
              """
          )
        );
    }

    @Test
    void ruleAssignedInConstructorBecomesServer() {
        //language=java
        rewriteRun(
          java(
            """
              import com.github.tomakehurst.wiremock.junit.WireMockRule;
              import org.junit.Rule;

              class Test {
                  @Rule
                  public WireMockRule wm;

                  Test() {
                      wm = new WireMockRule(8089);
                  }

                  void outage() {
                      wm.stop();
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.WireMockServer;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;

              class Test {
                  public WireMockServer wm;

                  @BeforeEach
                  void startWireMock() {
                      wm.start();
                  }

                  @AfterEach
                  void stopWireMock() {
                      wm.stop();
                  }

                  Test() {
                      wm = new WireMockServer(8089);
                  }

                  void outage() {
                      wm.stop();
                  }
              }
              """
          )
        );
    }

    @Test
    void ruleReturnedAsServerBecomesServer() {
        //language=java
        rewriteRun(
          java(
            """
              import com.github.tomakehurst.wiremock.WireMockServer;
              import com.github.tomakehurst.wiremock.junit.WireMockRule;
              import org.junit.Rule;

              class Test {
                  @Rule
                  public WireMockRule wm = new WireMockRule(8089);

                  WireMockServer server() {
                      return wm;
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.WireMockServer;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;

              class Test {
                  public WireMockServer wm = new WireMockServer(8089);

                  @BeforeEach
                  void startWireMock() {
                      wm.start();
                  }

                  @AfterEach
                  void stopWireMock() {
                      wm.stop();
                  }

                  WireMockServer server() {
                      return wm;
                  }
              }
              """
          )
        );
    }

    @Test
    void ruleStoppedThroughMethodReferenceBecomesServer() {
        //language=java
        rewriteRun(
          java(
            """
              import com.github.tomakehurst.wiremock.junit.WireMockRule;
              import org.junit.Rule;

              class Test {
                  @Rule
                  public WireMockRule wm = new WireMockRule(8089);

                  Runnable outage() {
                      return wm::stop;
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.WireMockServer;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;

              class Test {
                  public WireMockServer wm = new WireMockServer(8089);

                  @BeforeEach
                  void startWireMock() {
                      wm.start();
                  }

                  @AfterEach
                  void stopWireMock() {
                      wm.stop();
                  }

                  Runnable outage() {
                      return wm::stop;
                  }
              }
              """
          )
        );
    }

    @Test
    void ruleReturnedFromLambdaUnchanged() {
        //language=java
        rewriteRun(
          java(
            """
              import com.github.tomakehurst.wiremock.WireMockServer;
              import com.github.tomakehurst.wiremock.junit.WireMockRule;
              import org.junit.Rule;

              import java.util.function.Supplier;

              class Test {
                  @Rule
                  public WireMockRule wm = new WireMockRule(8089);

                  WireMockServer server() {
                      Supplier<WireMockRule> rule = () -> {
                          return wm;
                      };
                      return null;
                  }
              }
              """
          )
        );
    }
}
