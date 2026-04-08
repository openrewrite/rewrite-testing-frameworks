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
package org.openrewrite.java.testing.wiremock;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Pattern;

import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeWiremockDependencyVersionTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.testing.wiremock.UpgradeWiremockDependencyVersion");
    }

    @DocumentExample
    @Test
    void wiremockJre8ToWiremock() {
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
                        <version>2.35.1</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
              </project>
              """,
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
              """.formatted(Pattern.compile("<version>(3\\.\\d+\\.\\d+)</version>").matcher(after).results().findFirst().orElseThrow().group(1)))
          )
        );
    }

    @Test
    void wiremockToWiremock() {
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
                        <artifactId>wiremock</artifactId>
                        <version>2.27.2</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
              </project>
              """,
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
              """.formatted(Pattern.compile("<version>(3\\.\\d+\\.\\d+)</version>").matcher(after).results().findFirst().orElseThrow().group(1)))
          )
        );
    }

    @Test
    void wiremockStandaloneToWiremockStandalone() {
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
                        <artifactId>wiremock-standalone</artifactId>
                        <version>2.27.2</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
              </project>
              """,
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
              """.formatted(Pattern.compile("<version>(3\\.\\d+\\.\\d+)</version>").matcher(after).results().findFirst().orElseThrow().group(1)))
          )
        );
    }

    @Test
    void wiremockJre8StandaloneToWiremockStandalone() {
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
                        <artifactId>wiremock-jre8-standalone</artifactId>
                        <version>2.35.1</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
              </project>
              """,
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
              """.formatted(Pattern.compile("<version>(3\\.\\d+\\.\\d+)</version>").matcher(after).results().findFirst().orElseThrow().group(1)))
          )
        );
    }

    @Test
    void alreadyMigratedNoChange() {
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
                        <version>3.3.1</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }
}
