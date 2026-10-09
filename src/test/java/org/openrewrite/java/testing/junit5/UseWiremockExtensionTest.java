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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

@Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/170")
@SuppressWarnings("JUnitMalformedDeclaration")
class UseWiremockExtensionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "wiremock-jre8-2.35"))
          .recipe(new UseWiremockExtension());
    }

    @DocumentExample
    @Test
    void optionsArg() {
        //language=java
        rewriteRun(
          java(
            """
              import com.github.tomakehurst.wiremock.junit.WireMockRule;
              import org.junit.Rule;

              import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

              class Test {
                  @Rule
                  public WireMockRule wm = new WireMockRule(options().dynamicHttpsPort());
              }
              """,
            """
              import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
              import org.junit.jupiter.api.extension.RegisterExtension;

              import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

              class Test {
                  @RegisterExtension
                  public WireMockExtension wm = WireMockExtension.newInstance().options(options().dynamicHttpsPort()).build();
              }
              """
          )
        );
    }

    @Test
    void failOnUnmatchedRequests() {
        //language=java
        rewriteRun(
          java(
            """
              import com.github.tomakehurst.wiremock.junit.WireMockRule;
              import org.junit.Rule;

              import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

              class Test {
                  @Rule
                  public WireMockRule wm = new WireMockRule(options().dynamicHttpsPort(), false);
              }
              """,
            """
              import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
              import org.junit.jupiter.api.extension.RegisterExtension;

              import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

              class Test {
                  @RegisterExtension
                  public WireMockExtension wm = WireMockExtension.newInstance().options(options().dynamicHttpsPort()).failOnUnmatchedRequests(false).build();
              }
              """
          )
        );
    }

    @Test
    void port() {
        //language=java
        rewriteRun(
          java(
            """
              import com.github.tomakehurst.wiremock.junit.WireMockRule;
              import org.junit.Rule;

              class Test {
                  @Rule
                  public WireMockRule wm = new WireMockRule(7001);
              }
              """,
            """
              import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
              import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
              import org.junit.jupiter.api.extension.RegisterExtension;

              class Test {
                  @RegisterExtension
                  public WireMockExtension wm = WireMockExtension.newInstance().options(WireMockConfiguration.options().port(7001)).build();
              }
              """
          )
        );
    }

    @Test
    void portAndHttpsPort() {
        //language=java
        rewriteRun(
          java(
            """
              import com.github.tomakehurst.wiremock.junit.WireMockRule;
              import org.junit.Rule;

              class Test {
                  @Rule
                  public WireMockRule wm = new WireMockRule(7001, 7002);
              }
              """,
            """
              import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
              import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
              import org.junit.jupiter.api.extension.RegisterExtension;

              class Test {
                  @RegisterExtension
                  public WireMockExtension wm = WireMockExtension.newInstance().options(WireMockConfiguration.options().port(7001).httpsPort(7002)).build();
              }
              """
          )
        );
    }

    @Nested
    class InJUnit4to5Migration implements RewriteTest {

        //language=java
        private static final String RULE = """
          import com.github.tomakehurst.wiremock.junit.WireMockRule;
          import org.junit.Rule;

          class WireMockTest {
              @Rule
              public WireMockRule wm = new WireMockRule(7001);
          }
          """;

        //language=java
        private static final String EXTENSION = """
          import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
          import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
          import org.junit.jupiter.api.extension.RegisterExtension;

          class WireMockTest {
              @RegisterExtension
              public WireMockExtension wm = WireMockExtension.newInstance().options(WireMockConfiguration.options().port(7001)).build();
          }
          """;

        //language=xml
        private static final String WIREMOCK_JRE8_2_35 = """
                  <dependency>
                      <groupId>com.github.tomakehurst</groupId>
                      <artifactId>wiremock-jre8</artifactId>
                      <version>2.35.2</version>
                      <scope>test</scope>
                  </dependency>
          """;

        @Override
        public void defaults(RecipeSpec spec) {
            spec
              .parser(JavaParser.fromJavaVersion()
                .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "wiremock-jre8-2.35"))
              .recipeFromResources("org.openrewrite.java.testing.junit5.JUnit4to5Migration");
        }

        @Test
        void upgradeWiremockJre8From227() {
            rewriteRun(
              mavenProject("project",
                srcTestJava(
                  java(RULE, EXTENSION)
                ),
                //language=xml
                pomXml(
                  """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>com.example</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                        <dependencies>
                            <dependency>
                                <groupId>com.github.tomakehurst</groupId>
                                <artifactId>wiremock-jre8</artifactId>
                                <version>2.27.2</version>
                                <scope>test</scope>
                            </dependency>
                        </dependencies>
                    </project>
                    """,
                  spec -> spec.after(actual -> assertThat(actual).contains(WIREMOCK_JRE8_2_35).actual())
                )
              )
            );
        }

        @Test
        void moveWiremock227ToWiremockJre8() {
            rewriteRun(
              mavenProject("project",
                srcTestJava(
                  java(RULE, EXTENSION)
                ),
                //language=xml
                pomXml(
                  """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>com.example</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                        <dependencies>
                            <dependency>
                                <groupId>com.github.tomakehurst</groupId>
                                <artifactId>wiremock</artifactId>
                                <version>2.27.2</version>
                                <scope>test</scope>
                            </dependency>
                        </dependencies>
                    </project>
                    """,
                  spec -> spec.after(actual -> assertThat(actual).contains(WIREMOCK_JRE8_2_35).doesNotContain("<artifactId>wiremock</artifactId>").actual())
                )
              )
            );
        }

        @Test
        void moveWiremockStandalone227ToWiremockJre8Standalone() {
            rewriteRun(
              mavenProject("project",
                srcTestJava(
                  java(RULE, EXTENSION)
                ),
                //language=xml
                pomXml(
                  """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>com.example</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                        <dependencies>
                            <dependency>
                                <groupId>com.github.tomakehurst</groupId>
                                <artifactId>wiremock-standalone</artifactId>
                                <version>2.27.2</version>
                                <scope>test</scope>
                            </dependency>
                        </dependencies>
                    </project>
                    """,
                  spec -> spec.after(actual -> assertThat(actual).containsSubsequence("<artifactId>wiremock-jre8-standalone</artifactId>", "<version>2.35.2</version>").doesNotContain("<artifactId>wiremock-standalone</artifactId>").actual())
                )
              )
            );
        }

        @Test
        void upgradeWiremockJre8From231() {
            rewriteRun(
              mavenProject("project",
                srcTestJava(
                  java(RULE, EXTENSION)
                ),
                //language=xml
                pomXml(
                  """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>com.example</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                        <dependencies>
                            <dependency>
                                <groupId>com.github.tomakehurst</groupId>
                                <artifactId>wiremock-jre8</artifactId>
                                <version>2.31.0</version>
                                <scope>test</scope>
                            </dependency>
                        </dependencies>
                    </project>
                    """,
                  spec -> spec.after(actual -> assertThat(actual).contains(WIREMOCK_JRE8_2_35).actual())
                )
              )
            );
        }

        @Test
        void upgradeVersionManagedByParent() {
            rewriteRun(
              //language=xml
              pomXml(
                """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>com.example</groupId>
                      <artifactId>parent</artifactId>
                      <version>1</version>
                      <packaging>pom</packaging>
                      <modules>
                          <module>child</module>
                      </modules>
                      <dependencyManagement>
                          <dependencies>
                              <dependency>
                                  <groupId>com.github.tomakehurst</groupId>
                                  <artifactId>wiremock-jre8</artifactId>
                                  <version>2.27.2</version>
                              </dependency>
                          </dependencies>
                      </dependencyManagement>
                  </project>
                  """,
                """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>com.example</groupId>
                      <artifactId>parent</artifactId>
                      <version>1</version>
                      <packaging>pom</packaging>
                      <modules>
                          <module>child</module>
                      </modules>
                      <dependencyManagement>
                          <dependencies>
                              <dependency>
                                  <groupId>com.github.tomakehurst</groupId>
                                  <artifactId>wiremock-jre8</artifactId>
                                  <version>2.35.2</version>
                              </dependency>
                          </dependencies>
                      </dependencyManagement>
                  </project>
                  """
              ),
              mavenProject("child",
                srcTestJava(
                  java(RULE, EXTENSION)
                ),
                //language=xml
                pomXml(
                  """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <parent>
                            <groupId>com.example</groupId>
                            <artifactId>parent</artifactId>
                            <version>1</version>
                        </parent>
                        <artifactId>child</artifactId>
                        <dependencies>
                            <dependency>
                                <groupId>com.github.tomakehurst</groupId>
                                <artifactId>wiremock-jre8</artifactId>
                                <scope>test</scope>
                            </dependency>
                        </dependencies>
                    </project>
                    """,
                  spec -> spec.after(actual -> assertThat(actual).doesNotContain("<version>2.").actual())
                )
              )
            );
        }

        @Test
        void overrideVersionPropertyOfExternalParent() {
            rewriteRun(
              mavenProject("project",
                srcTestJava(
                  java(RULE, EXTENSION)
                ),
                //language=xml
                pomXml(
                  """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <parent>
                            <groupId>org.springframework.cloud</groupId>
                            <artifactId>spring-cloud-contract-dependencies</artifactId>
                            <version>3.0.0</version>
                        </parent>
                        <groupId>com.example</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                        <dependencies>
                            <dependency>
                                <groupId>com.github.tomakehurst</groupId>
                                <artifactId>wiremock-jre8-standalone</artifactId>
                                <scope>test</scope>
                            </dependency>
                        </dependencies>
                    </project>
                    """,
                  spec -> spec.after(actual -> assertThat(actual).contains("<wiremock.version>2.35.2</wiremock.version>").actual())
                )
              )
            );
        }
    }
}
