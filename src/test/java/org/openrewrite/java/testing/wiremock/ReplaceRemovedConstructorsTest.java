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

class ReplaceRemovedConstructorsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceRemovedConstructors())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "wiremock-3.13"));
    }

    @DocumentExample
    @Test
    void wireMockHostAndPort() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.client.WireMock;

              class Client {
                  WireMock remote() {
                      return new WireMock("localhost", 8080);
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.client.WireMock;

              class Client {
                  WireMock remote() {
                      return WireMock.create().host("localhost").port(8080).build();
                  }
              }
              """
          )
        );
    }

    @Test
    void wireMockNoArgs() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.client.WireMock;

              class Client {
                  WireMock remote = new WireMock();
              }
              """,
            """
              import com.github.tomakehurst.wiremock.client.WireMock;

              class Client {
                  WireMock remote = WireMock.create().build();
              }
              """
          )
        );
    }

    @Test
    void wireMockSchemeHostAndPort() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.client.WireMock;

              class Client {
                  WireMock remote = new WireMock("https", "example.org", 443);
              }
              """,
            """
              import com.github.tomakehurst.wiremock.client.WireMock;

              class Client {
                  WireMock remote = WireMock.create().scheme("https").host("example.org").port(443).build();
              }
              """
          )
        );
    }

    @Test
    void wireMockHostPortAndUrlPathPrefix() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.client.WireMock;

              class Client {
                  WireMock remote = new WireMock("localhost", 8080, "/wm");
              }
              """,
            """
              import com.github.tomakehurst.wiremock.client.WireMock;

              class Client {
                  WireMock remote = WireMock.create().host("localhost").port(8080).urlPathPrefix("/wm").build();
              }
              """
          )
        );
    }

    @Test
    void wireMockPortOnlyIsStillSupportedIn4x() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.client.WireMock;

              class Client {
                  WireMock remote = new WireMock(8080);
              }
              """
          )
        );
    }

    @Test
    void wireMockSchemeAndHostIsLeftAloneBecauseThePortWouldChange() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.client.WireMock;

              class Client {
                  WireMock remote = new WireMock("https", "example.org");
              }
              """
          )
        );
    }

    @Test
    void stubMappingNoArgs() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              class Stubs {
                  StubMapping mapping = new StubMapping();
              }
              """,
            """
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              class Stubs {
                  StubMapping mapping = StubMapping.builder().build();
              }
              """
          )
        );
    }

    @Test
    void stubMappingRequestAndResponse() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.http.ResponseDefinition;
              import com.github.tomakehurst.wiremock.matching.RequestPattern;
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              class Stubs {
                  StubMapping build(RequestPattern request, ResponseDefinition response) {
                      return new StubMapping(request, response);
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.http.ResponseDefinition;
              import com.github.tomakehurst.wiremock.matching.RequestPattern;
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              class Stubs {
                  StubMapping build(RequestPattern request, ResponseDefinition response) {
                      return StubMapping.builder().setRequest(request).setResponse(response).build();
                  }
              }
              """
          )
        );
    }

    @Test
    void responseDefinitionStatusAndBody() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.http.ResponseDefinition;

              class Responses {
                  ResponseDefinition ok = new ResponseDefinition(200, "hello");
              }
              """,
            """
              import com.github.tomakehurst.wiremock.http.ResponseDefinition;

              class Responses {
                  ResponseDefinition ok = new ResponseDefinition.Builder().setStatus(200).setBody("hello").build();
              }
              """
          )
        );
    }

    @Test
    void responseDefinitionStatusAndByteBody() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.http.ResponseDefinition;

              class Responses {
                  ResponseDefinition binary(byte[] content) {
                      return new ResponseDefinition(201, content);
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.http.ResponseDefinition;

              class Responses {
                  ResponseDefinition binary(byte[] content) {
                      return new ResponseDefinition.Builder().setStatus(201).setBody(content).build();
                  }
              }
              """
          )
        );
    }

    @Test
    void responseDefinitionNoArgs() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.http.ResponseDefinition;

              class Responses {
                  ResponseDefinition empty = new ResponseDefinition();
              }
              """,
            """
              import com.github.tomakehurst.wiremock.http.ResponseDefinition;

              class Responses {
                  ResponseDefinition empty = new ResponseDefinition.Builder().build();
              }
              """
          )
        );
    }

    @Test
    void requestPatternEverythingBecomesAnything() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.matching.RequestPattern;

              class Patterns {
                  RequestPattern all = RequestPattern.everything();
              }
              """,
            """
              import com.github.tomakehurst.wiremock.matching.RequestPattern;

              class Patterns {
                  RequestPattern all = RequestPattern.ANYTHING;
              }
              """
          )
        );
    }

    @Test
    void requestPatternFromCustomMatcherDefinition() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.matching.CustomMatcherDefinition;
              import com.github.tomakehurst.wiremock.matching.RequestPattern;

              class Patterns {
                  RequestPattern of(CustomMatcherDefinition definition) {
                      return new RequestPattern(definition);
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.matching.CustomMatcherDefinition;
              import com.github.tomakehurst.wiremock.matching.RequestPattern;

              class Patterns {
                  RequestPattern of(CustomMatcherDefinition definition) {
                      return new RequestPattern.Builder().setCustomMatcherDefinition(definition).build();
                  }
              }
              """
          )
        );
    }

    @Test
    void anonymousSubclassIsLeftAlone() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.http.ResponseDefinition;

              class Responses {
                  ResponseDefinition custom = new ResponseDefinition(200, "hello") {
                      @Override
                      public String toString() {
                          return "custom";
                      }
                  };
              }
              """
          )
        );
    }

    @Test
    void constructorsRetainedIn4xAreLeftAlone() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.client.WireMock;
              import com.github.tomakehurst.wiremock.core.Admin;

              class Client {
                  WireMock remote(Admin admin) {
                      return new WireMock(admin);
                  }
              }
              """
          )
        );
    }

    @Test
    void wrappedAdminClientFoldsIntoBuilder() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.client.HttpAdminClient;
              import com.github.tomakehurst.wiremock.client.WireMock;

              class Client {
                  WireMock remote = new WireMock(new HttpAdminClient("localhost", 8080));
              }
              """,
            """
              import com.github.tomakehurst.wiremock.client.WireMock;

              class Client {
                  WireMock remote = WireMock.create().host("localhost").port(8080).build();
              }
              """
          )
        );
    }

    @Test
    void wrappedAdminClientWithSchemeAndPrefix() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.client.HttpAdminClient;
              import com.github.tomakehurst.wiremock.client.WireMock;

              class Client {
                  WireMock remote = new WireMock(new HttpAdminClient("https", "example.org", 443, "/wm"));
              }
              """,
            """
              import com.github.tomakehurst.wiremock.client.WireMock;

              class Client {
                  WireMock remote = WireMock.create().scheme("https").host("example.org").port(443).urlPathPrefix("/wm").build();
              }
              """
          )
        );
    }

    @Test
    void wrappedAdminClientWithProxyAndAuthenticator() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.client.HttpAdminClient;
              import com.github.tomakehurst.wiremock.client.WireMock;
              import com.github.tomakehurst.wiremock.security.ClientAuthenticator;

              class Client {
                  WireMock remote(ClientAuthenticator authenticator) {
                      return new WireMock(new HttpAdminClient(
                              "https", "example.org", 443, "/wm", "example.org", "proxy.internal", 3128, authenticator));
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.client.WireMock;
              import com.github.tomakehurst.wiremock.security.ClientAuthenticator;

              class Client {
                  WireMock remote(ClientAuthenticator authenticator) {
                      return WireMock.create().scheme("https").host("example.org").port(443).urlPathPrefix("/wm").hostHeader("example.org").proxyHost("proxy.internal").proxyPort(3128).authenticator(authenticator).build();
                  }
              }
              """
          )
        );
    }

    @Test
    void wrappedAdminClientKeepsTheImportWhenStillUsedElsewhere() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.client.HttpAdminClient;
              import com.github.tomakehurst.wiremock.client.WireMock;

              class Client {
                  WireMock remote = new WireMock(new HttpAdminClient("localhost", 8080));

                  void accept(HttpAdminClient client) {
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.client.HttpAdminClient;
              import com.github.tomakehurst.wiremock.client.WireMock;

              class Client {
                  WireMock remote = WireMock.create().host("localhost").port(8080).build();

                  void accept(HttpAdminClient client) {
                  }
              }
              """
          )
        );
    }

    @Test
    void wrappedAdminClientSchemeAndHostIsLeftAloneBecauseThePortWouldChange() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.client.HttpAdminClient;
              import com.github.tomakehurst.wiremock.client.WireMock;

              class Client {
                  WireMock remote = new WireMock(new HttpAdminClient("https", "example.org"));
              }
              """
          )
        );
    }

    @Test
    void bareAdminClientIsLeftForTheDeveloper() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.client.HttpAdminClient;

              class Client {
                  HttpAdminClient admin = new HttpAdminClient("localhost", 8080);
              }
              """
          )
        );
    }

    @Test
    void wireMockFromAnArbitraryAdminIsLeftAlone() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.client.WireMock;
              import com.github.tomakehurst.wiremock.core.Admin;

              class Client {
                  WireMock remote(Admin admin) {
                      return new WireMock(admin);
                  }
              }
              """
          )
        );
    }
}
