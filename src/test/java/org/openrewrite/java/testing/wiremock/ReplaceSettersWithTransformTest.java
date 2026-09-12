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

class ReplaceSettersWithTransformTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceSettersWithTransform())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "wiremock-3.13"));
    }

    @DocumentExample
    @Test
    void singleSetter() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.matching.RequestPattern;
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              class Stubs {
                  StubMapping retarget(StubMapping mapping, RequestPattern pattern) {
                      mapping.setRequest(pattern);
                      return mapping;
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.matching.RequestPattern;
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              class Stubs {
                  StubMapping retarget(StubMapping mapping, RequestPattern pattern) {
                      mapping = mapping.transform(builder -> builder.setRequest(pattern));
                      return mapping;
                  }
              }
              """
          )
        );
    }

    @Test
    void consecutiveSettersCollapseIntoOneTransform() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              class Stubs {
                  void describe(StubMapping mapping) {
                      mapping.setName("get user");
                      mapping.setPriority(3);
                      mapping.setScenarioName("users");
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              class Stubs {
                  void describe(StubMapping mapping) {
                      mapping = mapping.transform(builder -> builder.setName("get user").setPriority(3).setScenarioName("users"));
                  }
              }
              """
          )
        );
    }

    @Test
    void settersSeparatedByOtherStatementsStaySeparate() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              class Stubs {
                  void describe(StubMapping mapping) {
                      mapping.setName("get user");
                      System.out.println(mapping);
                      mapping.setPriority(3);
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              class Stubs {
                  void describe(StubMapping mapping) {
                      mapping = mapping.transform(builder -> builder.setName("get user"));
                      System.out.println(mapping);
                      mapping = mapping.transform(builder -> builder.setPriority(3));
                  }
              }
              """
          )
        );
    }

    @Test
    void distinctReceiversAreNotCollapsed() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              class Stubs {
                  void describe(StubMapping first, StubMapping second) {
                      first.setName("a");
                      second.setName("b");
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              class Stubs {
                  void describe(StubMapping first, StubMapping second) {
                      first = first.transform(builder -> builder.setName("a"));
                      second = second.transform(builder -> builder.setName("b"));
                  }
              }
              """
          )
        );
    }

    @Test
    void setUuidBecomesSetIdOnTheBuilder() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              import java.util.UUID;

              class Stubs {
                  void identify(StubMapping mapping) {
                      mapping.setUuid(UUID.randomUUID());
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              import java.util.UUID;

              class Stubs {
                  void identify(StubMapping mapping) {
                      mapping = mapping.transform(builder -> builder.setId(UUID.randomUUID()));
                  }
              }
              """
          )
        );
    }

    @Test
    void fieldReceiver() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              class Stubs {
                  private StubMapping mapping;

                  void describe() {
                      this.mapping.setName("get user");
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              class Stubs {
                  private StubMapping mapping;

                  void describe() {
                      this.mapping = this.mapping.transform(builder -> builder.setName("get user"));
                  }
              }
              """
          )
        );
    }

    @Test
    void responseDefinitionOriginalRequest() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.http.Request;
              import com.github.tomakehurst.wiremock.http.ResponseDefinition;

              class Responses {
                  void attach(ResponseDefinition response, Request request) {
                      response.setOriginalRequest(request);
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.http.Request;
              import com.github.tomakehurst.wiremock.http.ResponseDefinition;

              class Responses {
                  void attach(ResponseDefinition response, Request request) {
                      response = response.transform(builder -> builder.setOriginalRequest(request));
                  }
              }
              """
          )
        );
    }

    @Test
    void lambdaParameterNameAvoidsShadowing() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              class Stubs {
                  void describe(StubMapping mapping) {
                      String builder = "not a builder";
                      mapping.setName(builder);
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              class Stubs {
                  void describe(StubMapping mapping) {
                      String builder = "not a builder";
                      mapping = mapping.transform(builder1 -> builder1.setName(builder));
                  }
              }
              """
          )
        );
    }

    @Test
    void setterWithoutBuilderEquivalentIsLeftForTheCompiler() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              class Stubs {
                  void soil(StubMapping mapping) {
                      mapping.setDirty(true);
                  }
              }
              """
          )
        );
    }

    @Test
    void finalReceiverIsLeftAlone() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              class Stubs {
                  void describe(final StubMapping mapping) {
                      mapping.setName("get user");
                  }
              }
              """
          )
        );
    }

    @Test
    void capturedLocalInsideLambdaIsLeftAlone() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              import java.util.List;

              class Stubs {
                  void describe(List<String> names, StubMapping mapping) {
                      names.forEach(name -> mapping.setName(name));
                  }
              }
              """
          )
        );
    }

    @Test
    void nonAssignableReceiverIsLeftAlone() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              class Stubs {
                  StubMapping current() {
                      return null;
                  }

                  void describe() {
                      current().setName("get user");
                  }
              }
              """
          )
        );
    }

    @Test
    void argumentReadingTheReceiverStartsANewTransform() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              class Stubs {
                  void describe(StubMapping mapping) {
                      mapping.setName("get user");
                      mapping.setScenarioName(mapping.getName());
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              class Stubs {
                  void describe(StubMapping mapping) {
                      mapping = mapping.transform(builder -> builder.setName("get user"));
                      mapping = mapping.transform(builder -> builder.setScenarioName(mapping.getName()));
                  }
              }
              """
          )
        );
    }
}
