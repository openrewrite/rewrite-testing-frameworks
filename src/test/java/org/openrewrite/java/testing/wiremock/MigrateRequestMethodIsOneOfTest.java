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

class MigrateRequestMethodIsOneOfTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateRequestMethodIsOneOf())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "wiremock-3.13"));
    }

    @DocumentExample
    @Test
    void becomesAMatcherQuestion() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.http.RequestMethod;

              class Methods {
                  boolean readOnly(RequestMethod method) {
                      return method.isOneOf(RequestMethod.GET, RequestMethod.HEAD);
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.http.RequestMethod;

              class Methods {
                  boolean readOnly(RequestMethod method) {
                      return RequestMethod.isOneOf(RequestMethod.GET, RequestMethod.HEAD).match(method).isExactMatch();
                  }
              }
              """
          )
        );
    }

    @Test
    void insideAnIfCondition() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.github.tomakehurst.wiremock.http.RequestMethod.GET;
              import static com.github.tomakehurst.wiremock.http.RequestMethod.POST;

              import com.github.tomakehurst.wiremock.http.Request;

              class Methods {
                  String describe(Request request) {
                      if (request.getMethod().isOneOf(GET, POST)) {
                          return "common";
                      }
                      return "other";
                  }
              }
              """,
            """
              import static com.github.tomakehurst.wiremock.http.RequestMethod.GET;
              import static com.github.tomakehurst.wiremock.http.RequestMethod.POST;

              import com.github.tomakehurst.wiremock.http.Request;
              import com.github.tomakehurst.wiremock.http.RequestMethod;

              class Methods {
                  String describe(Request request) {
                      if (RequestMethod.isOneOf(GET, POST).match(request.getMethod()).isExactMatch()) {
                          return "common";
                      }
                      return "other";
                  }
              }
              """
          )
        );
    }

    @Test
    void negatedCondition() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.github.tomakehurst.wiremock.http.RequestMethod.GET;

              import com.github.tomakehurst.wiremock.http.RequestMethod;

              class Methods {
                  boolean writes(RequestMethod method) {
                      return !method.isOneOf(GET);
                  }
              }
              """,
            """
              import static com.github.tomakehurst.wiremock.http.RequestMethod.GET;

              import com.github.tomakehurst.wiremock.http.RequestMethod;

              class Methods {
                  boolean writes(RequestMethod method) {
                      return !RequestMethod.isOneOf(GET).match(method).isExactMatch();
                  }
              }
              """
          )
        );
    }

    @Test
    void singleArgument() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.http.RequestMethod;

              class Methods {
                  boolean isGet(RequestMethod method) {
                      return method.isOneOf(RequestMethod.GET);
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.http.RequestMethod;

              class Methods {
                  boolean isGet(RequestMethod method) {
                      return RequestMethod.isOneOf(RequestMethod.GET).match(method).isExactMatch();
                  }
              }
              """
          )
        );
    }

    @Test
    void importIsAddedWhenOnlyConstantsWereStaticallyImported() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.github.tomakehurst.wiremock.http.RequestMethod.DELETE;
              import static com.github.tomakehurst.wiremock.http.RequestMethod.PUT;

              import com.github.tomakehurst.wiremock.http.Request;

              class Methods {
                  boolean mutates(Request request) {
                      return request.getMethod().isOneOf(PUT, DELETE);
                  }
              }
              """,
            """
              import static com.github.tomakehurst.wiremock.http.RequestMethod.DELETE;
              import static com.github.tomakehurst.wiremock.http.RequestMethod.PUT;

              import com.github.tomakehurst.wiremock.http.Request;
              import com.github.tomakehurst.wiremock.http.RequestMethod;

              class Methods {
                  boolean mutates(Request request) {
                      return RequestMethod.isOneOf(PUT, DELETE).match(request.getMethod()).isExactMatch();
                  }
              }
              """
          )
        );
    }

    @Test
    void isOneOfOnAnUnrelatedTypeIsUntouched() {
        rewriteRun(
          //language=java
          java(
            """
              class Methods {
                  static class Token {
                      boolean isOneOf(String... values) {
                          return false;
                      }
                  }

                  boolean check(Token token) {
                      return token.isOneOf("a", "b");
                  }
              }
              """
          )
        );
    }

    @Test
    void matchIsUntouched() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.http.RequestMethod;
              import com.github.tomakehurst.wiremock.matching.MatchResult;

              class Methods {
                  MatchResult check(RequestMethod pattern, RequestMethod actual) {
                      return pattern.match(actual);
                  }
              }
              """
          )
        );
    }
}
