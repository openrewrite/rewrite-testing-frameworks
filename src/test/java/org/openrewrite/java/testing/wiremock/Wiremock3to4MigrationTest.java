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

import java.util.regex.Pattern;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcTestJava;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.maven.Assertions.pomXml;

class Wiremock3to4MigrationTest implements RewriteTest {
    // `wiremock4.yml` pins an exact version, as every dynamic selector in rewrite's semver support
    // rejects `-beta.N`; keep this in step with the `newVersion` arguments over there. Only the
    // recipe *input* below needs the exact version; expected output is matched against
    // `WIREMOCK_4_REGEX_VERSION_STRING` instead, so a version bump over there lands here for free.
    private static final String WIREMOCK_4_VERSION = "4.0.0-beta.38";

    private static final String WIREMOCK_4_REGEX_STRING = "4\\.\\d+\\.\\d+(?:-[\\w.]+)?";

    private static final Pattern POM_WIREMOCK_4_VERSION = Pattern.compile("<version>(" + WIREMOCK_4_REGEX_STRING + ")</version>");

    private static final Pattern GRADLE_WIREMOCK_4_VERSION = Pattern.compile("wiremock:(" + WIREMOCK_4_REGEX_STRING + ")");

    private static final String WIREMOCK_3_VERSION = "3.3.1";

    // `wiremock-jetty12` was only published later in the 3.x line, so it cannot reuse WIREMOCK_3_VERSION.
    private static final String WIREMOCK_3_JETTY12_VERSION = "3.13.1";

    private static final String WIREMOCK_2_VERSION = "2.35.1";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.testing.wiremock.Wiremock3to4Migration")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "junit-jupiter-api-5", "wiremock-3.13"));
    }

    private static String pomWiremock4Version(String after) {
        return POM_WIREMOCK_4_VERSION.matcher(after).results().findFirst().orElseThrow().group(1);
    }

    private static String gradleWiremock4Version(String after) {
        return GRADLE_WIREMOCK_4_VERSION.matcher(after).results().findFirst().orElseThrow().group(1);
    }

    @DocumentExample
    @Test
    void wiremock3ToWiremock4() {
        rewriteRun(
          //language=xml
          pomXml(
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
              """.formatted(WIREMOCK_3_VERSION),
            spec -> spec.after(after -> """
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
              """.formatted(pomWiremock4Version(after)))
          )
        );
    }

    @Test
    void wiremockStandalone3ToWiremockStandalone4() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>org.wiremock</groupId>
                        <artifactId>wiremock-standalone</artifactId>
                        <version>%s</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
              </project>
              """.formatted(WIREMOCK_3_VERSION),
            spec -> spec.after(after -> """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>org.wiremock</groupId>
                        <artifactId>wiremock-standalone</artifactId>
                        <version>%s</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
              </project>
              """.formatted(pomWiremock4Version(after)))
          )
        );
    }

    @Test
    void legacyCoordinatesGoStraightToWiremock4() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>com.github.tomakehurst</groupId>
                        <artifactId>wiremock-jre8</artifactId>
                        <version>%s</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
              </project>
              """.formatted(WIREMOCK_2_VERSION),
            spec -> spec.after(after -> """
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
              """.formatted(pomWiremock4Version(after)))
          )
        );
    }

    @Test
    void gradle() {
        rewriteRun(
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java'
              }

              repositories {
                  mavenCentral()
              }

              dependencies {
                  testImplementation 'org.wiremock:wiremock:%s'
              }
              """.formatted(WIREMOCK_3_VERSION),
            spec -> spec.after(after -> """
              plugins {
                  id 'java'
              }

              repositories {
                  mavenCentral()
              }

              dependencies {
                  testImplementation 'org.wiremock:wiremock:%s'
              }
              """.formatted(gradleWiremock4Version(after)))
          )
        );
    }

    @Test
    void otherDependenciesAreUntouched() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>org.junit.jupiter</groupId>
                        <artifactId>junit-jupiter-api</artifactId>
                        <version>5.10.1</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void alreadyOnWiremock4NoChange() {
        rewriteRun(
          //language=xml
          pomXml(
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
              """.formatted(WIREMOCK_4_VERSION)
          )
        );
    }

    @Test
    void jetty12ModuleFoldedBackIntoWiremock() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>org.wiremock</groupId>
                        <artifactId>wiremock-jetty12</artifactId>
                        <version>%s</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
              </project>
              """.formatted(WIREMOCK_3_JETTY12_VERSION),
            spec -> spec.after(after -> """
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
              """.formatted(pomWiremock4Version(after)))
          )
        );
    }

    @Test
    void addsJUnit5ModuleWhenExtensionIsUsed() {
        rewriteRun(
          mavenProject("project",
            //language=java
            srcTestJava(
              java(
                """
                  import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
                  import org.junit.jupiter.api.extension.RegisterExtension;

                  class MyTest {
                      @RegisterExtension
                      static WireMockExtension wm = WireMockExtension.newInstance().build();
                  }
                  """
              )
            ),
            //language=xml
            pomXml(
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
                """.formatted(WIREMOCK_3_VERSION),
              spec -> spec.after(after -> """
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
                    <dependency>
                      <groupId>org.wiremock</groupId>
                      <artifactId>wiremock-junit5</artifactId>
                      <version>%s</version>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """.formatted(pomWiremock4Version(after), pomWiremock4Version(after)))
            )
          )
        );
    }

    @Test
    void addsJUnit4ModuleWhenRuleIsUsed() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          mavenProject("project",
            //language=java
            srcTestJava(
              java(
                """
                  import com.github.tomakehurst.wiremock.junit.WireMockRule;
                  import org.junit.Rule;

                  class MyTest {
                      @Rule
                      public WireMockRule wm = new WireMockRule(8080);
                  }
                  """
              )
            ),
            //language=groovy
            buildGradle(
              """
                plugins {
                    id 'java'
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    testImplementation 'org.wiremock:wiremock:%s'
                }
                """.formatted(WIREMOCK_3_VERSION),
              spec -> spec.after(after -> """
                plugins {
                    id 'java'
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    testImplementation 'org.wiremock:wiremock:%s'
                    testImplementation "org.wiremock:wiremock-junit4:%s"
                }
                """.formatted(gradleWiremock4Version(after), gradleWiremock4Version(after)))
            )
          )
        );
    }

    @Test
    void noJUnitModuleWhenOnlyTheCoreDslIsUsed() {
        rewriteRun(
          mavenProject("project",
            //language=java
            srcTestJava(
              java(
                """
                  import com.github.tomakehurst.wiremock.WireMockServer;

                  class MyTest {
                      WireMockServer server = new WireMockServer(8080);
                  }
                  """
              )
            ),
            //language=xml
            pomXml(
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
                """.formatted(WIREMOCK_3_VERSION),
              spec -> spec.after(after -> """
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
                """.formatted(pomWiremock4Version(after)))
            )
          )
        );
    }

    @Test
    void repackagedJettyAndServletTypes() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.common.JettySettings;
              import com.github.tomakehurst.wiremock.jetty11.NotFoundHandler;
              import com.github.tomakehurst.wiremock.servlet.WireMockWebContextListener;

              class MyTest {
                  NotFoundHandler handler;
                  JettySettings settings;
                  WireMockWebContextListener listener;
              }
              """,
            """
              import com.github.tomakehurst.wiremock.jetty.JettySettings;
              import com.github.tomakehurst.wiremock.jetty.NotFoundHandler;
              import com.github.tomakehurst.wiremock.jetty.servlet.WireMockWebContextListener;

              class MyTest {
                  NotFoundHandler handler;
                  JettySettings settings;
                  WireMockWebContextListener listener;
              }
              """
          )
        );
    }

    @Test
    void stubMappingGetUuidBecomesGetId() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              import java.util.UUID;

              class MyTest {
                  UUID id(StubMapping mapping) {
                      return mapping.getUuid();
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              import java.util.UUID;

              class MyTest {
                  UUID id(StubMapping mapping) {
                      return mapping.getId();
                  }
              }
              """
          )
        );
    }

    @Test
    void settersBecomeTransformCalls() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.matching.RequestPattern;
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              class Stubs {
                  void retarget(StubMapping mapping, RequestPattern pattern) {
                      mapping.setRequest(pattern);
                      mapping.setPriority(1);
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.matching.RequestPattern;
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              class Stubs {
                  void retarget(StubMapping mapping, RequestPattern pattern) {
                      mapping = mapping.transform(builder -> builder.setRequest(pattern).setPriority(1));
                  }
              }
              """
          )
        );
    }

    @Test
    void stubMappingFileUuidBecomesId() {
        rewriteRun(
          //language=json
          json(
            """
              {
                "uuid": "8f4c3b1e-5d2a-4f6b-9c8d-1a2b3c4d5e6f",
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
    void duplicateContentTypeHeadersCollapseWhileStillOnWiremock3() {
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
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
                """.formatted(WIREMOCK_3_VERSION),
              spec -> spec.after(after -> """
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
                """.formatted(pomWiremock4Version(after)))
            ),
            //language=json
            json(
              """
                {
                  "response": {
                    "status": 200,
                    "headers": {
                      "Content-Type": [ "text/plain", "application/json" ]
                    }
                  }
                }
                """,
              """
                {
                  "response": {
                    "status": 200,
                    "headers": {
                      "Content-Type": "application/json"
                    }
                  }
                }
                """,
              spec -> spec.path("src/test/resources/mappings/get-user.json")
            ),
            srcTestJava(
              //language=java
              java(
                """
                  import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

                  import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

                  class Stubs {
                      ResponseDefinitionBuilder response() {
                          return aResponse()
                                  .withHeader("Content-Type", "text/plain", "application/json");
                      }
                  }
                  """,
                """
                  import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

                  import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

                  class Stubs {
                      ResponseDefinitionBuilder response() {
                          return aResponse()
                                  .withHeader("Content-Type", "application/json");
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void duplicateContentTypeHeadersSurviveOnWiremock4() {
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
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
                """.formatted(WIREMOCK_4_VERSION)
            ),
            //language=json
            json(
              """
                {
                  "response": {
                    "status": 200,
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

    @Test
    void constructAndMutateBecomesBuilderAndTransform() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.http.ResponseDefinition;
              import com.github.tomakehurst.wiremock.matching.RequestPattern;
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              class Stubs {
                  StubMapping build(RequestPattern pattern) {
                      StubMapping mapping = new StubMapping();
                      mapping.setRequest(pattern);
                      mapping.setResponse(new ResponseDefinition(200, "hello"));
                      return mapping;
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.http.ResponseDefinition;
              import com.github.tomakehurst.wiremock.matching.RequestPattern;
              import com.github.tomakehurst.wiremock.stubbing.StubMapping;

              class Stubs {
                  StubMapping build(RequestPattern pattern) {
                      StubMapping mapping = StubMapping.builder().build();
                      mapping = mapping.transform(builder -> builder.setRequest(pattern).setResponse(new ResponseDefinition.Builder().setStatus(200).setBody("hello").build()));
                      return mapping;
                  }
              }
              """
          )
        );
    }

    @Test
    void requestMethodValueBecomesGetName() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.http.RequestMethod;

              class Methods {
                  String describe(RequestMethod method) {
                      return method.value();
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.http.RequestMethod;

              class Methods {
                  String describe(RequestMethod method) {
                      return method.getName();
                  }
              }
              """
          )
        );
    }

    @Test
    void requestMethodValueAsAMethodReference() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.http.RequestMethod;

              import java.util.List;
              import java.util.stream.Collectors;

              class Methods {
                  List<String> names(List<RequestMethod> methods) {
                      return methods.stream().map(RequestMethod::value).collect(Collectors.toList());
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.http.RequestMethod;

              import java.util.List;
              import java.util.stream.Collectors;

              class Methods {
                  List<String> names(List<RequestMethod> methods) {
                      return methods.stream().map(RequestMethod::getName).collect(Collectors.toList());
                  }
              }
              """
          )
        );
    }

    @Test
    void valueOnAnUnrelatedTypeIsUntouched() {
        rewriteRun(
          //language=java
          java(
            """
              class Methods {
                  static class Token {
                      String value() {
                          return "x";
                      }
                  }

                  String describe(Token token) {
                      return token.value();
                  }
              }
              """
          )
        );
    }
}
