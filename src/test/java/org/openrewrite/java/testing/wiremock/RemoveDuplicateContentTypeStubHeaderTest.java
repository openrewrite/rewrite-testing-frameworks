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
import org.openrewrite.test.SourceSpecs;

import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.maven.Assertions.pomXml;

class RemoveDuplicateContentTypeStubHeaderTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveDuplicateContentTypeStubHeader());
    }

    @DocumentExample
    @Test
    void keepsOnlyTheLastValue() {
        rewriteRun(
          mavenProject("project",
            wiremockPom("3.13.2"),
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
                      "Content-Type": [ "application/json" ]
                    },
                    "body": "{}"
                  }
                }
                """,
              spec -> spec.path("src/test/resources/mappings/get-user.json")
            )
          )
        );
    }

    @Test
    void keepsTheLastOfThreeValuesAcrossLines() {
        rewriteRun(
          mavenProject("project",
            wiremockPom("3.13.2"),
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
                      "Content-Type": [
                        "application/json"
                      ]
                    }
                  }
                }
                """,
              spec -> spec.path("src/test/resources/mappings/get-user.json")
            )
          )
        );
    }

    @Test
    void headerNameIsMatchedCaseInsensitively() {
        rewriteRun(
          mavenProject("project",
            wiremockPom("3.13.2"),
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
                      "content-type": [ "application/json" ]
                    }
                  }
                }
                """,
              spec -> spec.path("src/test/resources/mappings/get-user.json")
            )
          )
        );
    }

    @Test
    void singleValueIsUntouched() {
        rewriteRun(
          mavenProject("project",
            wiremockPom("3.13.2"),
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
          )
        );
    }

    @Test
    void otherHeadersKeepEveryValue() {
        rewriteRun(
          mavenProject("project",
            wiremockPom("3.13.2"),
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
          )
        );
    }

    @Test
    void requestHeaderMatchersAreUntouched() {
        rewriteRun(
          mavenProject("project",
            wiremockPom("3.13.2"),
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
          )
        );
    }

    @Test
    void contentTypeArrayOutsideHeadersIsUntouched() {
        rewriteRun(
          mavenProject("project",
            wiremockPom("3.13.2"),
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
          )
        );
    }

    @Test
    void stubShapedJsonOutsideMappingsIsUntouched() {
        rewriteRun(
          mavenProject("project",
            wiremockPom("3.13.2"),
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
          )
        );
    }

    @Test
    void leavesMultipleValuesAloneOnWiremock4() {
        rewriteRun(
          mavenProject("project",
            wiremockPom("4.0.0-beta.38"),
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
              spec -> spec.path("src/test/resources/mappings/get-user.json")
            )
          )
        );
    }

    private static SourceSpecs wiremockPom(String version) {
        return pomXml(
          //language=xml
          """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>org.wiremock</groupId>
                        <artifactId>wiremock</artifactId>
                        <version>%s</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
            </project>
            """.formatted(version)
        );
    }
}
