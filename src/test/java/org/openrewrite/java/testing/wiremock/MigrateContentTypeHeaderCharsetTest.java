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

class MigrateContentTypeHeaderCharsetTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateContentTypeHeaderCharset())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "wiremock-3.13"));
    }

    @DocumentExample
    @Test
    void assignmentKeepsTheUtf8Default() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.http.ContentTypeHeader;

              import java.nio.charset.Charset;

              class Headers {
                  Charset of(ContentTypeHeader header) {
                      return header.charset();
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.http.ContentTypeHeader;

              import java.nio.charset.Charset;
              import java.nio.charset.StandardCharsets;

              class Headers {
                  Charset of(ContentTypeHeader header) {
                      return header.charset().orElse(StandardCharsets.UTF_8);
                  }
              }
              """
          )
        );
    }

    @Test
    void chainedCallAppliesToTheResolvedCharset() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.http.ContentTypeHeader;

              class Headers {
                  String name(ContentTypeHeader header) {
                      return header.charset().name();
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.http.ContentTypeHeader;

              import java.nio.charset.StandardCharsets;

              class Headers {
                  String name(ContentTypeHeader header) {
                      return header.charset().orElse(StandardCharsets.UTF_8).name();
                  }
              }
              """
          )
        );
    }

    @Test
    void usedAsAnArgument() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.http.ContentTypeHeader;

              class Headers {
                  String decode(byte[] body, ContentTypeHeader header) {
                      return new String(body, header.charset());
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.http.ContentTypeHeader;

              import java.nio.charset.StandardCharsets;

              class Headers {
                  String decode(byte[] body, ContentTypeHeader header) {
                      return new String(body, header.charset().orElse(StandardCharsets.UTF_8));
                  }
              }
              """
          )
        );
    }

    @Test
    void receiverCanBeAnyExpression() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.http.HttpHeaders;

              import java.nio.charset.Charset;

              class Headers {
                  Charset of(HttpHeaders headers) {
                      return headers.getContentTypeHeader().charset();
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.http.HttpHeaders;

              import java.nio.charset.Charset;
              import java.nio.charset.StandardCharsets;

              class Headers {
                  Charset of(HttpHeaders headers) {
                      return headers.getContentTypeHeader().charset().orElse(StandardCharsets.UTF_8);
                  }
              }
              """
          )
        );
    }

    @Test
    void existingStandardCharsetsImportIsReused() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.http.ContentTypeHeader;

              import java.nio.charset.Charset;
              import java.nio.charset.StandardCharsets;

              class Headers {
                  Charset fallback = StandardCharsets.ISO_8859_1;

                  Charset of(ContentTypeHeader header) {
                      return header.charset();
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.http.ContentTypeHeader;

              import java.nio.charset.Charset;
              import java.nio.charset.StandardCharsets;

              class Headers {
                  Charset fallback = StandardCharsets.ISO_8859_1;

                  Charset of(ContentTypeHeader header) {
                      return header.charset().orElse(StandardCharsets.UTF_8);
                  }
              }
              """
          )
        );
    }

    @Test
    void charsetOnAnUnrelatedTypeIsUntouched() {
        rewriteRun(
          //language=java
          java(
            """
              import java.nio.charset.Charset;

              class Headers {
                  static class Other {
                      Charset charset() {
                          return null;
                      }
                  }

                  Charset of(Other other) {
                      return other.charset();
                  }
              }
              """
          )
        );
    }

    @Test
    void encodingPartIsUntouched() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.http.ContentTypeHeader;

              import java.util.Optional;

              class Headers {
                  Optional<String> of(ContentTypeHeader header) {
                      return header.encodingPart();
                  }
              }
              """
          )
        );
    }
}
