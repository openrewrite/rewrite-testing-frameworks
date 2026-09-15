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

class MigrateStubMappingUuidToIdTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateStubMappingUuidToId());
    }

    @DocumentExample
    @Test
    void uuidOnlyIsRenamedToId() {
        rewriteRun(
          //language=json
          json(
            """
              {
                "uuid": "8f4c3b1e-5d2a-4f6b-9c8d-1a2b3c4d5e6f",
                "name": "get user",
                "request": {
                  "method": "GET",
                  "url": "/user"
                },
                "response": {
                  "status": 200
                }
              }
              """,
            """
              {
                "id": "8f4c3b1e-5d2a-4f6b-9c8d-1a2b3c4d5e6f",
                "name": "get user",
                "request": {
                  "method": "GET",
                  "url": "/user"
                },
                "response": {
                  "status": 200
                }
              }
              """,
            spec -> spec.path("src/test/resources/mappings/get-user.json")
          )
        );
    }

    @Test
    void redundantUuidIsDroppedWhenIdIsPresent() {
        rewriteRun(
          //language=json
          json(
            """
              {
                "id": "8f4c3b1e-5d2a-4f6b-9c8d-1a2b3c4d5e6f",
                "name": "get user",
                "request": {
                  "method": "GET",
                  "url": "/user"
                },
                "response": {
                  "status": 200
                },
                "uuid": "8f4c3b1e-5d2a-4f6b-9c8d-1a2b3c4d5e6f"
              }
              """,
            """
              {
                "id": "8f4c3b1e-5d2a-4f6b-9c8d-1a2b3c4d5e6f",
                "name": "get user",
                "request": {
                  "method": "GET",
                  "url": "/user"
                },
                "response": {
                  "status": 200
                }
              }
              """,
            spec -> spec.path("src/test/resources/mappings/get-user.json")
          )
        );
    }

    @Test
    void uuidFirstWithIdPresentKeepsIndentation() {
        rewriteRun(
          //language=json
          json(
            """
              {
                "uuid": "8f4c3b1e-5d2a-4f6b-9c8d-1a2b3c4d5e6f",
                "id": "8f4c3b1e-5d2a-4f6b-9c8d-1a2b3c4d5e6f",
                "request": {
                  "method": "GET"
                }
              }
              """,
            """
              {
                "id": "8f4c3b1e-5d2a-4f6b-9c8d-1a2b3c4d5e6f",
                "request": {
                  "method": "GET"
                }
              }
              """,
            spec -> spec.path("src/test/resources/mappings/get-user.json")
          )
        );
    }

    @Test
    void multiStubMappingsFile() {
        rewriteRun(
          //language=json
          json(
            """
              {
                "mappings": [
                  {
                    "uuid": "8f4c3b1e-5d2a-4f6b-9c8d-1a2b3c4d5e6f",
                    "request": {
                      "method": "GET",
                      "url": "/one"
                    },
                    "response": {
                      "status": 200
                    }
                  },
                  {
                    "id": "11111111-2222-3333-4444-555555555555",
                    "uuid": "11111111-2222-3333-4444-555555555555",
                    "request": {
                      "method": "GET",
                      "url": "/two"
                    },
                    "response": {
                      "status": 204
                    }
                  }
                ]
              }
              """,
            """
              {
                "mappings": [
                  {
                    "id": "8f4c3b1e-5d2a-4f6b-9c8d-1a2b3c4d5e6f",
                    "request": {
                      "method": "GET",
                      "url": "/one"
                    },
                    "response": {
                      "status": 200
                    }
                  },
                  {
                    "id": "11111111-2222-3333-4444-555555555555",
                    "request": {
                      "method": "GET",
                      "url": "/two"
                    },
                    "response": {
                      "status": 204
                    }
                  }
                ]
              }
              """,
            spec -> spec.path("src/test/resources/mappings/all.json")
          )
        );
    }

    @Test
    void nestedUuidOutsideAStubMappingIsUntouched() {
        rewriteRun(
          //language=json
          json(
            """
              {
                "uuid": "8f4c3b1e-5d2a-4f6b-9c8d-1a2b3c4d5e6f",
                "request": {
                  "method": "POST",
                  "bodyPatterns": [
                    {
                      "equalToJson": "{\\"uuid\\": \\"11111111-2222-3333-4444-555555555555\\"}"
                    }
                  ]
                }
              }
              """,
            """
              {
                "id": "8f4c3b1e-5d2a-4f6b-9c8d-1a2b3c4d5e6f",
                "request": {
                  "method": "POST",
                  "bodyPatterns": [
                    {
                      "equalToJson": "{\\"uuid\\": \\"11111111-2222-3333-4444-555555555555\\"}"
                    }
                  ]
                }
              }
              """,
            spec -> spec.path("src/test/resources/mappings/post-user.json")
          )
        );
    }

    @Test
    void unrelatedJsonIsUntouched() {
        rewriteRun(
          //language=json
          json(
            """
              {
                "uuid": "8f4c3b1e-5d2a-4f6b-9c8d-1a2b3c4d5e6f",
                "name": "some domain object",
                "enabled": true
              }
              """,
            spec -> spec.path("src/test/resources/fixtures/thing.json")
          )
        );
    }

    @Test
    void uuidThatIsNotAUuidIsUntouched() {
        rewriteRun(
          //language=json
          json(
            """
              {
                "uuid": "not-a-uuid",
                "request": {
                  "method": "GET"
                },
                "response": {
                  "status": 200
                }
              }
              """,
            spec -> spec.path("src/test/resources/mappings/odd.json")
          )
        );
    }

    @Test
    void stubMappingAlreadyOnIdOnly() {
        rewriteRun(
          //language=json
          json(
            """
              {
                "id": "8f4c3b1e-5d2a-4f6b-9c8d-1a2b3c4d5e6f",
                "request": {
                  "method": "GET"
                },
                "response": {
                  "status": 200
                }
              }
              """,
            spec -> spec.path("src/test/resources/mappings/get-user.json")
          )
        );
    }
}
