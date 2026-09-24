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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.json.Assertions.json;

class RemoveDuplicateContentTypeStubHeaderTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveDuplicateContentTypeStubHeader());
    }

    @DocumentExample
    @Test
    void keepsOnlyTheLastValue() {
        rewriteRun(
          //language=json
          json(
            """
              {
                "request": {
                  "method": "GET",
                  "url": "/user"
                },
                "response": {
                  "status": 200,
                  "headers": {
                    "Content-Type": [ "text/plain", "application/json" ]
                  },
                  "body": "{}"
                }
              }
              """,
            """
              {
                "request": {
                  "method": "GET",
                  "url": "/user"
                },
                "response": {
                  "status": 200,
                  "headers": {
                    "Content-Type": "application/json"
                  },
                  "body": "{}"
                }
              }
              """,
            spec -> spec.path("src/test/resources/mappings/get-user.json")
          )
        );
    }

    @Test
    void keepsTheLastOfThreeValuesAcrossLines() {
        rewriteRun(
          //language=json
          json(
            """
              {
                "response": {
                  "headers": {
                    "Content-Type": [
                      "text/plain",
                      "text/html",
                      "application/json"
                    ]
                  }
                }
              }
              """,
            """
              {
                "response": {
                  "headers": {
                    "Content-Type": "application/json"
                  }
                }
              }
              """,
            spec -> spec.path("src/test/resources/mappings/get-user.json")
          )
        );
    }

    @Test
    void headerNameIsMatchedCaseInsensitively() {
        rewriteRun(
          //language=json
          json(
            """
              {
                "response": {
                  "headers": {
                    "content-type": [ "text/plain", "application/json" ]
                  }
                }
              }
              """,
            """
              {
                "response": {
                  "headers": {
                    "content-type": "application/json"
                  }
                }
              }
              """,
            spec -> spec.path("src/test/resources/mappings/get-user.json")
          )
        );
    }

    @Test
    void singleValueIsUntouched() {
        rewriteRun(
          //language=json
          json(
            """
              {
                "response": {
                  "headers": {
                    "Content-Type": "application/json"
                  }
                }
              }
              """,
            spec -> spec.path("src/test/resources/mappings/get-user.json")
          )
        );
    }

    @Test
    void otherHeadersKeepEveryValue() {
        rewriteRun(
          //language=json
          json(
            """
              {
                "response": {
                  "headers": {
                    "Set-Cookie": [ "a=1", "b=2" ]
                  }
                }
              }
              """,
            spec -> spec.path("src/test/resources/mappings/get-user.json")
          )
        );
    }

    @Test
    void requestHeaderMatchersAreUntouched() {
        rewriteRun(
          //language=json
          json(
            """
              {
                "request": {
                  "method": "GET",
                  "headers": {
                    "Content-Type": {
                      "equalTo": "application/json"
                    }
                  }
                }
              }
              """,
            spec -> spec.path("src/test/resources/mappings/get-user.json")
          )
        );
    }

    @Test
    void contentTypeArrayOutsideHeadersIsUntouched() {
        rewriteRun(
          //language=json
          json(
            """
              {
                "supported": {
                  "Content-Type": [ "text/plain", "application/json" ]
                }
              }
              """,
            spec -> spec.path("src/test/resources/mappings/get-user.json")
          )
        );
    }

    @Test
    void stubShapedJsonOutsideMappingsIsUntouched() {
        rewriteRun(
          //language=json
          json(
            """
              {
                "response": {
                  "headers": {
                    "Content-Type": [ "text/plain", "application/json" ]
                  }
                }
              }
              """,
            spec -> spec.path("src/test/resources/fixtures/config.json")
          )
        );
    }
}
