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
package org.openrewrite.java.testing.junit6;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.javaVersion;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcTestJava;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeToJUnitPioneer3Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-jupiter-api-6", "junit-pioneer-2", "testng-7"))
          .recipeFromResources("org.openrewrite.java.testing.junit6.UpgradeToJUnitPioneer3");
    }

    @DocumentExample
    @Test
    void migratesRemovedExtensionsAndUpgradesJUnit() {
        rewriteRun(
          mavenProject("project",
            //language=java
            srcTestJava(java(
              """
                import org.junit.jupiter.api.Test;
                import org.junitpioneer.jupiter.DefaultTimeZone;
                import org.junitpioneer.jupiter.RetryingTest;
                import org.junitpioneer.jupiter.WritesSystemProperty;

                class MyTest {
                    @RetryingTest(3)
                    @DefaultTimeZone("UTC")
                    @WritesSystemProperty
                    void flaky() {
                    }
                }
                """,
              """
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.api.util.DefaultTimeZone;
                import org.junit.jupiter.api.util.WritesSystemProperty;
                import org.junitpioneer.jupiter.RetryingTest;

                class MyTest {
                    @RetryingTest(3)
                    @DefaultTimeZone("UTC")
                    @WritesSystemProperty
                    void flaky() {
                    }
                }
                """,
              spec -> spec.markers(javaVersion(17))
            )),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.14.0</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.junit-pioneer</groupId>
                            <artifactId>junit-pioneer</artifactId>
                            <version>2.3.0</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
              // junit-pioneer only moves once 3.0.0 is released, as 3.x does not select release candidates
              spec -> spec.after(actual -> assertThat(actual)
                .containsPattern("<artifactId>junit-jupiter</artifactId>\\s*<version>6\\.\\d+\\.\\d+</version>")
                .containsPattern("<artifactId>junit-pioneer</artifactId>\\s*<version>(2\\.3\\.0|3\\.\\d+\\.\\d+)</version>")
                .actual())
            )
          )
        );
    }

    @Test
    void leavesModuleWithoutPioneerAlone() {
        rewriteRun(
          mavenProject("project",
            //language=java
            srcTestJava(java(
              """
                import org.junit.jupiter.api.Test;

                class MyTest {
                    @Test
                    void test() {
                    }
                }
                """,
              spec -> spec.markers(javaVersion(17))
            )),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.14.0</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void leavesModuleBelowJava17Alone() {
        rewriteRun(
          mavenProject("project",
            //language=java
            srcTestJava(java(
              """
                import org.junitpioneer.jupiter.DefaultTimeZone;
                import org.junitpioneer.jupiter.RetryingTest;

                class MyTest {
                    @RetryingTest(3)
                    @DefaultTimeZone("UTC")
                    void flaky() {
                    }
                }
                """,
              spec -> spec.markers(javaVersion(11))
            )),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit-pioneer</groupId>
                            <artifactId>junit-pioneer</artifactId>
                            <version>2.3.0</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void leavesModuleThatAlsoUsesTestNgAlone() {
        rewriteRun(
          mavenProject("project",
            //language=java
            srcTestJava(java(
              """
                import org.junitpioneer.jupiter.DefaultTimeZone;
                import org.testng.annotations.Test;

                class MyTest {
                    @Test
                    @DefaultTimeZone("UTC")
                    void test() {
                    }
                }
                """,
              spec -> spec.markers(javaVersion(17))
            )),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit-pioneer</groupId>
                            <artifactId>junit-pioneer</artifactId>
                            <version>2.3.0</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.testng</groupId>
                            <artifactId>testng</artifactId>
                            <version>7.10.2</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }
}
