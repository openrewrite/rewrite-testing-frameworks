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
package org.openrewrite.java.testing.mockito;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Pattern;

import static org.openrewrite.maven.Assertions.pomXml;

class MockitoInlineToCoreTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.testing.mockito.Mockito1to5Migration");
    }

    @DocumentExample
    @Test
    void inlineToCore() {
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
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-inline</artifactId>
                        <version>3.11.2</version>
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
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-core</artifactId>
                        <version>%s</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
              </project>
              """.formatted(Pattern.compile("<version>(5.+)</version>").matcher(after).results().findFirst().orElseThrow().group(1)))
          )
        );
    }

    @Test
    void noDuplicates() {
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
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-core</artifactId>
                        <version>3.11.2</version>
                        <scope>test</scope>
                    </dependency>
                    <dependency>
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-inline</artifactId>
                        <version>3.11.2</version>
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
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-core</artifactId>
                        <version>%s</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
              </project>
              """.formatted(Pattern.compile("<version>(5.+)</version>").matcher(after).results().findFirst().orElseThrow().group(1)))
          )
        );
    }

    @Test
    void shouldUpdateByteBuddy() {
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
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-core</artifactId>
                        <version>5.13.0</version>
                        <scope>test</scope>
                    </dependency>
                    <dependency>
                        <groupId>net.bytebuddy</groupId>
                        <artifactId>byte-buddy</artifactId>
                        <version>1.12.19</version>
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
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-core</artifactId>
                        <version>%s</version>
                        <scope>test</scope>
                    </dependency>
                    <dependency>
                        <groupId>net.bytebuddy</groupId>
                        <artifactId>byte-buddy</artifactId>
                        <version>%s</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
              </project>
              """.formatted(
              Pattern.compile("<version>(5.+)</version>").matcher(after).results().findFirst().orElseThrow().group(1),
              Pattern.compile("<version>(1.17.+)</version>").matcher(after).results().findFirst().orElseThrow().group(1)))
          )
        );
    }
}
