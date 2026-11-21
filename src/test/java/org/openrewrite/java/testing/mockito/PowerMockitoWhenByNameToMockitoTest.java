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
package org.openrewrite.java.testing.mockito;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class PowerMockitoWhenByNameToMockitoTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-4",
              "mockito-core-3.12",
              "powermock-api-mockito-1",
              "powermock-api-support-1",
              "powermock-core-1",
              "powermock-reflect-1"))
          .recipe(new PowerMockitoWhenByNameToMockito());
    }

    private static final String HTTP_CLIENT = """
      package com.qcloud;

      public interface HttpClient {
          <X> X execute(String request, Object handler) throws java.io.IOException;
          int count(int limit);
          void sign(String request, java.util.Date expires);
          void close();
          String describe();
          String overloaded(String a);
          String overloaded(Integer a);
      }
      """;

    @DocumentExample
    @Test
    void returningMethodWithMatchers() {
        //language=java
        rewriteRun(
          java(HTTP_CLIENT),
          java(
            """
              import com.qcloud.HttpClient;
              import org.junit.Test;
              import org.powermock.api.mockito.PowerMockito;

              import static org.mockito.ArgumentMatchers.any;

              public class MyTest {
                  @Test
                  public void test() throws Exception {
                      HttpClient client = PowerMockito.mock(HttpClient.class);
                      PowerMockito.when(client, "execute", any(), any()).thenReturn("response");
                      PowerMockito.when(client, "describe").thenReturn("described");
                  }
              }
              """,
            """
              import com.qcloud.HttpClient;
              import org.junit.Test;
              import org.powermock.api.mockito.PowerMockito;

              import static org.mockito.ArgumentMatchers.any;

              public class MyTest {
                  @Test
                  public void test() throws Exception {
                      HttpClient client = PowerMockito.mock(HttpClient.class);
                      PowerMockito.when(client.execute(any(), any())).thenReturn("response");
                      PowerMockito.when(client.describe()).thenReturn("described");
                  }
              }
              """
          )
        );
    }

    @Test
    void voidMethodWithAnswer() {
        //language=java
        rewriteRun(
          java(HTTP_CLIENT),
          java(
            """
              import com.qcloud.HttpClient;
              import org.junit.Test;
              import org.powermock.api.mockito.PowerMockito;

              import static org.mockito.ArgumentMatchers.any;

              public class MyTest {
                  @Test
                  public void test() throws Exception {
                      HttpClient client = PowerMockito.mock(HttpClient.class);
                      PowerMockito.when(client, "sign", any(), any()).thenAnswer((m) -> {return null;});
                      PowerMockito.when(client, "close").thenThrow(new IllegalStateException());
                      PowerMockito.when(client, "close").thenCallRealMethod();
                  }
              }
              """,
            """
              import com.qcloud.HttpClient;
              import org.junit.Test;
              import org.mockito.Mockito;
              import org.powermock.api.mockito.PowerMockito;

              import static org.mockito.ArgumentMatchers.any;

              public class MyTest {
                  @Test
                  public void test() throws Exception {
                      HttpClient client = PowerMockito.mock(HttpClient.class);
                      Mockito.doAnswer((m) -> {
                          return null;
                      }).when(client).sign(any(), any());
                      Mockito.doThrow(new IllegalStateException()).when(client).close();
                      Mockito.doCallRealMethod().when(client).close();
                  }
              }
              """
          )
        );
    }

    @Test
    void unresolvedOrUnsafeStubbingIsLeftAlone() {
        //language=java
        rewriteRun(
          java(HTTP_CLIENT),
          java(
            """
              import com.qcloud.HttpClient;
              import org.junit.Test;
              import org.powermock.api.mockito.PowerMockito;

              import static org.mockito.ArgumentMatchers.any;

              public class MyTest {
                  @Test
                  public void test() throws Exception {
                      HttpClient client = PowerMockito.mock(HttpClient.class);
                      // no such method
                      PowerMockito.when(client, "missing").thenReturn("a");
                      // `any()` is null, which the primitive parameter can not take
                      PowerMockito.when(client, "count", any()).thenReturn(1);
                      PowerMockito.when(client, "close").thenReturn(null);
                  }
              }
              """
          )
        );
    }
}
