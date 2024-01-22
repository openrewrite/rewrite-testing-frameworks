/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.mockito;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Pattern;

import static org.openrewrite.maven.Assertions.pomXml;

class MockitoInlineToCoreTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.testing.mockito.Mockito1to5Migration");
    }

    @Test
    void inlineToCore() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(2),
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
            sourceSpecs -> sourceSpecs.after(after -> {
                String version = Pattern.compile("<version>(5.+)</version>").matcher(after).results().reduce((a, b) -> b).orElseThrow().group(1);
                return """
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
                  """.formatted(version);
            })
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
            sourceSpecs -> sourceSpecs.after(after -> {
                String version = Pattern.compile("<version>(5.+)</version>").matcher(after).results().reduce((a, b) -> b).orElseThrow().group(1);
                return """
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
                  """.formatted(version);
            })
          )
        );
    }
}
