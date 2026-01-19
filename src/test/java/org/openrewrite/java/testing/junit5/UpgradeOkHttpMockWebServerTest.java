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

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeOkHttpMockWebServerTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "mockwebserver-4.10", "okhttp-4.10", "okio-jvm-3.12", "junit-4"))
          .recipeFromYaml(
            """
              ---
              type: specs.openrewrite.org/v1beta/recipe
              name: org.openrewrite.test.MigrateMockResponse
              displayName: Test
              description: Test.
              recipeList:
                - org.openrewrite.java.testing.junit5.UpdateMockWebServerMockResponse
              """,
            "org.openrewrite.test.MigrateMockResponse"
          );
    }

    @DocumentExample
    @Test
    void shouldUpgradeMavenDependency() {
        rewriteRun(
          spec -> spec.recipeFromResource("/META-INF/rewrite/junit5.yml", "org.openrewrite.java.testing.junit5.UpgradeOkHttpMockWebServer"),
          mavenProject("project",
            // TODO: handle solely J.NewClass and update declarative recipe to include new one.
            //language=xml
            pomXml(
              """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.squareup.okhttp3</groupId>
                      <artifactId>mockwebserver</artifactId>
                      <version>4.10.0</version>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """,
              spec -> spec.after(pom ->
                assertThat(pom)
                  .doesNotContain("<artifactId>mockwebserver</artifactId>")
                  .contains("<artifactId>mockwebserver3</artifactId>")
                  .containsPattern("<version>5\\.(.*)</version>")
                  .actual()
              )
            )
          )
        );
    }

    // TODO: methods receiving MockResponse - maybe add comment instructing to double check?
    @Test
    void shouldMigrateMockResponseToBuilder() {
        rewriteRun(
          //language=java
          java(
            """
              import okhttp3.Headers;
              import okhttp3.mockwebserver.MockResponse;
              import okhttp3.mockwebserver.MockWebServer;

              class A {
                  private Headers.Builder headersBuilder = new Headers.Builder();
                  private MockWebServer mockWebServer = new MockWebServer();
                  private MockResponse mockResponse = new MockResponse()
                      .setStatus("a")
                      .setHeaders(headersBuilder.build())
                      .setHeader("headerA", "someValue");
                  private okhttp3.mockwebserver.MockResponse mockResponse2 = new okhttp3.mockwebserver.MockResponse();
                  {
                      mockResponse.status("b");
                      mockResponse2.setBody("Lorem ipsum");
                      mockResponse.headers(headersBuilder.build());
                      mockWebServer.enqueue(mockResponse);
                      mockResponse.setStatus("c");
                      mockResponse.setHeaders(headersBuilder.build());
                      mockResponse.removeHeader("headerA");
                      mockResponse.clearHeaders();
                      mockResponse.addHeaderLenient("headerB", "anotherValue");
                  }

                  void methodA() {
                      mockResponse.setStatus("d");
                      mockWebServer.enqueue(mockResponse);
                      mockResponse.status("e");
                      String status = mockResponse.getStatus();
                  }

                  void methodB() {
                      mockWebServer.enqueue(
                        new MockResponse()
                          .setStatus("hi")
                          .setHeaders(headersBuilder.build())
                      );
                  }
              }
              """,
            """
              import mockwebserver3.MockResponse;
              import okhttp3.Headers;
              import mockwebserver3.MockWebServer;

              class A {
                  private Headers.Builder headersBuilder = new Headers.Builder();
                  private MockWebServer mockWebServer = new MockWebServer();
                  private MockResponse.Builder mockResponse = new MockResponse.Builder()
                      .status("a")
                      .headers(headersBuilder.build())
                      .setHeader("headerA", "someValue");
                  private MockResponse.Builder mockResponse2 = new MockResponse.Builder();
                  {
                      mockResponse.status("b");
                      mockResponse2.body("Lorem ipsum");
                      mockResponse.headers(headersBuilder.build());
                      mockWebServer.enqueue(mockResponse.build());
                      mockResponse.status("c");
                      mockResponse.headers(headersBuilder.build());
                      mockResponse.removeHeader("headerA");
                      mockResponse.clearHeaders();
                      mockResponse.addHeaderLenient("headerB", "anotherValue");
                  }

                  void methodA() {
                      mockResponse.status("d");
                      mockWebServer.enqueue(mockResponse.build());
                      mockResponse.status("e");
                      String status = mockResponse.getStatus();
                  }

                  void methodB() {
                      mockWebServer.enqueue(
                        new MockResponse.Builder()
                                      .status("hi")
                                      .headers(headersBuilder.build())
                                      .build());
                  }
              }
              """
          )
        );
    }

    @Test
    void typicalUseCase() {
        rewriteRun(
          //language=java
          java(
            """
              import okhttp3.mockwebserver.MockResponse;
              import okhttp3.mockwebserver.MockWebServer;

              class ApiUnitTest {
                  private MockWebServer mockWebServer = new MockWebServer();
                  void testGet() {
                      String body = "{\\"message\\":\\"Hello, World!\\"}";
                      mockWebServer.enqueue(new MockResponse()
                              .setHeader("Content-Type", "application/json; charset=utf-8")
                              .setBody(body)
                              .setResponseCode(200));
                  }
              }
              """,
            """
              import mockwebserver3.MockResponse;
              import mockwebserver3.MockWebServer;

              class ApiUnitTest {
                  private MockWebServer mockWebServer = new MockWebServer();
                  void testGet() {
                      String body = "{\\"message\\":\\"Hello, World!\\"}";
                      mockWebServer.enqueue(new MockResponse.Builder()
                              .setHeader("Content-Type", "application/json; charset=utf-8")
                              .body(body)
                              .code(200)
                              .build());
                  }
              }
              """
          )
        );
    }

    @Test
    void verifyMockResponseToBuilderMethodCoverage() {
        rewriteRun(
          //language=java
          java(
            """
              import okhttp3.Headers;
              import okhttp3.mockwebserver.MockResponse;
              import java.util.concurrent.TimeUnit;
              import okhttp3.mockwebserver.MockWebServer;

              class A {
                  void configureFully(MockResponse mockResponse) {
                      mockResponse.setBody("Lorem ipsum");
                      mockResponse.setBodyDelay(30L, TimeUnit.SECONDS);
                      mockResponse.setChunkedBody("Lorem ipsum", 2048);
                      mockResponse.setHeaders(new Headers.Builder().add("accept:application/json").build());
                      mockResponse.setHeadersDelay(30L, TimeUnit.SECONDS);
                      mockResponse.setHttp2ErrorCode(500);
                      mockResponse.setResponseCode(200);
                      mockResponse.setStatus("OK");
                      mockResponse.setTrailers(new Headers.Builder().add("x-trailer:value").build());
                  }
              }
              """,
            """
              import mockwebserver3.MockResponse;
              import okhttp3.Headers;
              import java.util.concurrent.TimeUnit;
              import mockwebserver3.MockWebServer;

              class A {
                  void configureFully(MockResponse.Builder mockResponse) {
                      mockResponse.body("Lorem ipsum");
                      mockResponse.bodyDelay(30L, TimeUnit.SECONDS);
                      mockResponse.chunkedBody("Lorem ipsum", 2048);
                      mockResponse.headers(new Headers.Builder().add("accept:application/json").build());
                      mockResponse.headersDelay(30L, TimeUnit.SECONDS);
                      mockResponse.code(500);
                      mockResponse.code(200);
                      mockResponse.status("OK");
                      mockResponse.trailers(new Headers.Builder().add("x-trailer:value").build());
                  }
              }
              """
          )
        );
    }
}
