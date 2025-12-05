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
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeOkHttpMockWebServerTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "mockwebserver-4.10", "okhttp-4.10", "junit-4"))
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
//          .recipe(new UpdateMockWebServerMockResponse());
          //.recipeFromResource("/META-INF/rewrite/junit5.yml", "org.openrewrite.java.testing.junit5.UpgradeOkHttpMockWebServer");
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
                  {
                      mockResponse.status("b");
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
              import mockwebserver3.MockWebServer;
              import okhttp3.Headers;

              class A {
                  private Headers.Builder headersBuilder = new Headers.Builder();
                  private MockWebServer mockWebServer = new MockWebServer();
                  private MockResponse.Builder mockResponse = new MockResponse.Builder()
                      .status("a")
                      .headers(headersBuilder.build())
                      .setHeader("headerA", "someValue");
                  {
                      mockResponse.status("b");
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
                          .build()
                      );
                  }
              }
              """
          )
        );
    }

//    @Test
//    void wip() {
//        rewriteRun(
//          //language=java
//          java(
//            """
//              import okhttp3.Headers;
//              import okhttp3.mockwebserver.MockResponse;
//
//              class A {
//                  void someMethod() {
//                      Headers headers = new Headers.Builder().build();
//                      MockResponse a = new MockResponse();
//                      // .status(String): void
//                      // .getStatus(): String
//                      // --
//                      // .setStatus(String): MockResponse[this]
//                      // ---
//                      // .headers(Headers): void
//                      // .setHeaders(Headers): MockResponse
//                      // .getHeaders(): Headers
//                      // ---
//                      // .addHeader(String): MockResponse
//                      // .addHeader(String,Object): MockResponse
//                      // .addHeaderLenient(String,Object): MockResponse
//                      // ---
//                      // .setHeader(String,Object): MockResponse
//                      // .removeHeader(String): MockResponse
//                      // .clearHeaders(): MockResponse
//                      a.header
//                      a.trailers(headers);
//                  }
//              }
//              """
//          )
//        );
//    }

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
}
