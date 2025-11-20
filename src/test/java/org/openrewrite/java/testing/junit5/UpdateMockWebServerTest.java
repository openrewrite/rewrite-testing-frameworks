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

@SuppressWarnings("RedundantThrows")
class UpdateMockWebServerTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "junit-jupiter-api-5", "mockwebserver-4.10", "okio-jvm-3"))
          .recipe(new UpdateMockWebServer());
    }

    @DocumentExample
    @Test
    void mockWebServerRuleUpdated() {
        //language=java
        rewriteRun(
          java(
            """
              import okhttp3.mockwebserver.MockWebServer;
              import org.junit.Rule;
              class MyTest {
                  @Rule
                  public MockWebServer server = new MockWebServer();
              }
              """,
            """
              import okhttp3.mockwebserver.MockWebServer;
              import org.junit.jupiter.api.AfterEach;

              import java.io.IOException;

              class MyTest {
                  public MockWebServer server = new MockWebServer();

                  @AfterEach
                  void afterEachTest() throws IOException {
                      server.close();
                  }
              }
              """
          )
        );
    }

    @Test
    void mockWebServerRuleUpdatedExistingAfterEachStatement() {
        //language=java
        rewriteRun(
          java(
            """
              import okhttp3.mockwebserver.MockWebServer;
              import org.junit.Rule;
              import org.junit.jupiter.api.AfterEach;

              class MyTest {
                  @Rule
                  public MockWebServer server = new MockWebServer();

                  @AfterEach
                  void afterEachTest() { }
              }
              """,
            """
              import okhttp3.mockwebserver.MockWebServer;
              import org.junit.jupiter.api.AfterEach;

              import java.io.IOException;

              class MyTest {
                  public MockWebServer server = new MockWebServer();

                  @AfterEach
                  void afterEachTest() throws IOException {
                      server.close();
                  }
              }
              """
          )
        );
    }

    @Test
    void mockWebServerMigratesCode() {
        //language=java
        rewriteRun(
          java(
            """
              import okhttp3.mockwebserver.MockResponse;
              import okhttp3.mockwebserver.MockWebServer;
              import okhttp3.mockwebserver.RecordedRequest;

              import java.util.concurrent.TimeUnit;

              public class MyTest {
                  public void test() throws Exception {
                      MockWebServer server = new MockWebServer();
                      server.enqueue(new MockResponse()
                              .setBody("hello, world!")
                              .setResponseCode(100)
                              .addHeader("My-Header", "value")
                              .setBodyDelay(100, TimeUnit.MILLISECONDS)
                              .setStatus("Something")
                      );
                      server.start();

                      RecordedRequest request1 = server.takeRequest();
                      String path = request1.getPath();
                      String header = request1.getHeader("Authorization");
                      String body = request1.getBody().readUtf8();

                      server.shutdown();
                  }
              }
              """,
            """
              import mockwebserver3.MockResponse;
              import mockwebserver3.MockWebServer;
              import mockwebserver3.RecordedRequest;

              import java.nio.charset.StandardCharsets;
              import java.util.concurrent.TimeUnit;

              public class MyTest {
                  public void test() throws Exception {
                      MockWebServer server = new MockWebServer();
                      server.enqueue(new MockResponse.Builder()
                              .body("hello, world!")
                              .code(100)
                              .addHeader("My-Header", "value")
                              .bodyDelay(100, TimeUnit.MILLISECONDS)
                              .status("Something")
                              .build()
                      );
                      server.start();

                      RecordedRequest request1 = server.takeRequest();
                      String path = request1.getUrl().encodedPath();
                      String header = request1.getHeaders().get("Authorization");
                      String body = request1.getBody().string(StandardCharsets.UTF_8);

                      server.close();
                  }
              }
              """
          )
        );
    }
}
