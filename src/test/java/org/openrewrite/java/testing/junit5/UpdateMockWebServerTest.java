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
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4.13", "junit-jupiter-api-5.9", "mockwebserver-3.14"))
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
}
