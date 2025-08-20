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

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeOkHttpMockWebServerTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "mockwebserver-4.10"))
          .recipeFromResource("/META-INF/rewrite/junit5.yml", "org.openrewrite.java.testing.junit5.UpgradeOkHttpMockWebServer");
    }

    @Test
    void shouldUpgradeMavenDependency() {
        rewriteRun(
          mavenProject("project",
            //language=java
            java(
              """
                import okhttp3.mockwebserver.MockWebServer;

                class Test {
                    void test() {
                        MockWebServer server = new MockWebServer();
                    }
                }
                """,
              """
                import mockwebserver3.MockWebServer;

                class Test {
                    void test() {
                        MockWebServer server = new MockWebServer();
                    }
                }
                """
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
                      <groupId>com.squareup.okhttp3</groupId>
                      <artifactId>mockwebserver</artifactId>
                      <version>4.10.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """,
              """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.squareup.okhttp3</groupId>
                      <artifactId>mockwebserver3-junit5</artifactId>
                      <version>5.1.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """
            )
          )
        );
    }
}
