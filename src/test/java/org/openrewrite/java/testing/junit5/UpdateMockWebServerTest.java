package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
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
            .classpath("junit")
            .dependsOn("package okhttp3.mockwebserver; public class MockWebServer implements Closeable {}")
          )
          .recipe(new UpdateMockWebServer());
    }

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
