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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("JUnitMalformedDeclaration")
@Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/170")
class UseWiremockExtensionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "wiremock-jre8-2.35"))
          .recipe(new UseWiremockExtension());
    }

    @DocumentExample
    @Test
    void optionsArg() {
        //language=java
        rewriteRun(
          java(
            """
              import com.github.tomakehurst.wiremock.junit.WireMockRule;
              import org.junit.Rule;

              import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

              class Test {
                  @Rule
                  public WireMockRule wm = new WireMockRule(options().dynamicHttpsPort());
              }
              """,
            """
              import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
              import org.junit.jupiter.api.extension.RegisterExtension;

              import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

              class Test {
                  @RegisterExtension
                  public WireMockExtension wm = WireMockExtension.newInstance().options(options().dynamicHttpsPort()).build();
              }
              """
          )
        );
    }

    @Test
    void failOnUnmatchedRequests() {
        //language=java
        rewriteRun(
          java(
            """
              import com.github.tomakehurst.wiremock.junit.WireMockRule;
              import org.junit.Rule;

              import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

              class Test {
                  @Rule
                  public WireMockRule wm = new WireMockRule(options().dynamicHttpsPort(), false);
              }
              """,
            """
              import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
              import org.junit.jupiter.api.extension.RegisterExtension;

              import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

              class Test {
                  @RegisterExtension
                  public WireMockExtension wm = WireMockExtension.newInstance().options(options().dynamicHttpsPort()).failOnUnmatchedRequests(false).build();
              }
              """
          )
        );
    }

    @Test
    void port() {
        //language=java
        rewriteRun(
          java(
            """
              import com.github.tomakehurst.wiremock.junit.WireMockRule;
              import org.junit.Rule;

              class Test {
                  @Rule
                  public WireMockRule wm = new WireMockRule(7001);
              }
              """,
            """
              import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
              import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
              import org.junit.jupiter.api.extension.RegisterExtension;

              class Test {
                  @RegisterExtension
                  public WireMockExtension wm = WireMockExtension.newInstance().options(WireMockConfiguration.options().port(7001)).build();
              }
              """
          )
        );
    }

    @Test
    void portAndHttpsPort() {
        //language=java
        rewriteRun(
          java(
            """
              import com.github.tomakehurst.wiremock.junit.WireMockRule;
              import org.junit.Rule;

              class Test {
                  @Rule
                  public WireMockRule wm = new WireMockRule(7001, 7002);
              }
              """,
            """
              import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
              import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
              import org.junit.jupiter.api.extension.RegisterExtension;

              class Test {
                  @RegisterExtension
                  public WireMockExtension wm = WireMockExtension.newInstance().options(WireMockConfiguration.options().port(7001).httpsPort(7002)).build();
              }
              """
          )
        );
    }
}
