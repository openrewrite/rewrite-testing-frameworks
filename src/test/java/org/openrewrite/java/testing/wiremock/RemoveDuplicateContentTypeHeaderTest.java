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
package org.openrewrite.java.testing.wiremock;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveDuplicateContentTypeHeaderTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveDuplicateContentTypeHeader())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "wiremock-3.13"));
    }

    @DocumentExample
    @Test
    void keepsOnlyTheLastValue() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

              import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

              class Stubs {
                  ResponseDefinitionBuilder response() {
                      return aResponse()
                              .withStatus(200)
                              .withHeader("Content-Type", "text/plain", "application/json")
                              .withBody("{}");
                  }
              }
              """,
            """
              import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

              import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

              class Stubs {
                  ResponseDefinitionBuilder response() {
                      return aResponse()
                              .withStatus(200)
                              .withHeader("Content-Type", "application/json")
                              .withBody("{}");
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsTheLastOfThreeValues() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

              import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

              class Stubs {
                  ResponseDefinitionBuilder response = aResponse()
                          .withHeader("Content-Type", "text/plain", "text/html", "application/json");
              }
              """,
            """
              import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

              import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

              class Stubs {
                  ResponseDefinitionBuilder response = aResponse()
                          .withHeader("Content-Type", "application/json");
              }
              """
          )
        );
    }

    @Test
    void headerNameIsMatchedCaseInsensitively() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

              import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

              class Stubs {
                  ResponseDefinitionBuilder response = aResponse()
                          .withHeader("content-type", "text/plain", "application/json");
              }
              """,
            """
              import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

              import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

              class Stubs {
                  ResponseDefinitionBuilder response = aResponse()
                          .withHeader("content-type", "application/json");
              }
              """
          )
        );
    }

    @Test
    void httpHeaderFactory() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.http.HttpHeader;

              class Stubs {
                  HttpHeader contentType = HttpHeader.httpHeader("Content-Type", "text/plain", "application/json");
              }
              """,
            """
              import com.github.tomakehurst.wiremock.http.HttpHeader;

              class Stubs {
                  HttpHeader contentType = HttpHeader.httpHeader("Content-Type", "application/json");
              }
              """
          )
        );
    }

    @Test
    void httpHeaderConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.http.HttpHeader;

              class Stubs {
                  HttpHeader contentType = new HttpHeader("Content-Type", "text/plain", "application/json");
              }
              """,
            """
              import com.github.tomakehurst.wiremock.http.HttpHeader;

              class Stubs {
                  HttpHeader contentType = new HttpHeader("Content-Type", "application/json");
              }
              """
          )
        );
    }

    @Test
    void singleValueIsUntouched() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

              import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

              class Stubs {
                  ResponseDefinitionBuilder response = aResponse()
                          .withHeader("Content-Type", "application/json");
              }
              """
          )
        );
    }

    @Test
    void otherHeadersKeepEveryValue() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

              import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

              class Stubs {
                  ResponseDefinitionBuilder response = aResponse()
                          .withHeader("Set-Cookie", "a=1", "b=2");
              }
              """
          )
        );
    }

    @Test
    void valuesHeldInAnArrayCannotBeSplitApart() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

              import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

              class Stubs {
                  ResponseDefinitionBuilder response(String[] values) {
                      return aResponse().withHeader("Content-Type", values);
                  }
              }
              """
          )
        );
    }

    @Test
    void requestHeaderMatchersAreUntouched() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
              import static com.github.tomakehurst.wiremock.client.WireMock.get;
              import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

              import com.github.tomakehurst.wiremock.client.MappingBuilder;

              class Stubs {
                  MappingBuilder mapping = get(urlEqualTo("/x"))
                          .withHeader("Content-Type", equalTo("application/json"));
              }
              """
          )
        );
    }

    @Test
    void appliesWithoutAnExplicitResponseDefinitionBuilderImport() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
              import static com.github.tomakehurst.wiremock.client.WireMock.get;
              import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
              import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

              class Stubs {
                  void stub() {
                      stubFor(get(urlEqualTo("/user")).willReturn(aResponse()
                              .withStatus(200)
                              .withHeader("Content-Type", "text/plain", "application/json")));
                  }
              }
              """,
            """
              import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
              import static com.github.tomakehurst.wiremock.client.WireMock.get;
              import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
              import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

              class Stubs {
                  void stub() {
                      stubFor(get(urlEqualTo("/user")).willReturn(aResponse()
                              .withStatus(200)
                              .withHeader("Content-Type", "application/json")));
                  }
              }
              """
          )
        );
    }
}
