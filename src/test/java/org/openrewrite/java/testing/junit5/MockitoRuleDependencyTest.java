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

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class MockitoRuleDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.testing.junit5.UseMockitoExtension")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "junit-4", "mockito-core-5", "mockito-junit-jupiter-5", "junit-jupiter-api-5"));
    }

    @Test
    void addsMatchingJupiterDependencyForModernMockitoRule() {
        rewriteRun(
          mavenProject("test",
            srcTestJava(
              java(
                """
                  import org.junit.Rule;
                  import org.mockito.junit.MockitoJUnit;
                  import org.mockito.junit.MockitoRule;
                  class Example {
                      @Rule public MockitoRule mocks = MockitoJUnit.rule();
                  }
                  """,
                spec -> spec.after(source -> assertThat(source)
                  .contains("@ExtendWith(MockitoExtension.class)")
                  .doesNotContain("public MockitoRule mocks").actual())
              )
            ),
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>example</groupId>
                    <artifactId>test</artifactId>
                    <version>1</version>
                    <properties>
                        <mockito.version>5.23.0</mockito.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.mockito</groupId>
                            <artifactId>mockito-core</artifactId>
                            <version>${mockito.version}</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>example</groupId>
                    <artifactId>test</artifactId>
                    <version>1</version>
                    <properties>
                        <mockito.version>5.23.0</mockito.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.mockito</groupId>
                            <artifactId>mockito-core</artifactId>
                            <version>${mockito.version}</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.mockito</groupId>
                            <artifactId>mockito-junit-jupiter</artifactId>
                            <version>${mockito.version}</version>
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
    void addsMatchingJupiterDependencyForModernMockitoRunner() {
        rewriteRun(
          mavenProject("test",
            srcTestJava(
              java(
                """
                  import org.junit.runner.RunWith;
                  import org.mockito.junit.MockitoJUnitRunner;
                  @RunWith(MockitoJUnitRunner.class)
                  class Example {}
                  """,
                spec -> spec.after(source -> assertThat(source)
                  .contains("@ExtendWith(MockitoExtension.class)")
                  .doesNotContain("public MockitoRule mocks").actual())
              )
            ),
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>example</groupId>
                    <artifactId>test</artifactId>
                    <version>1</version>
                    <properties>
                        <mockito.version>5.23.0</mockito.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.mockito</groupId>
                            <artifactId>mockito-core</artifactId>
                            <version>${mockito.version}</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>example</groupId>
                    <artifactId>test</artifactId>
                    <version>1</version>
                    <properties>
                        <mockito.version>5.23.0</mockito.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.mockito</groupId>
                            <artifactId>mockito-core</artifactId>
                            <version>${mockito.version}</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.mockito</groupId>
                            <artifactId>mockito-junit-jupiter</artifactId>
                            <version>${mockito.version}</version>
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
    void upgradesLegacyMockitoBeforeAligningJupiter() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "junit-4", "mockito-all-1.10", "mockito-core-3.12", "mockito-junit-jupiter-3.12", "junit-jupiter-api-5")),
          mavenProject("test",
            srcTestJava(
              java(
                """
                  import org.junit.runner.RunWith;
                  import org.mockito.runners.MockitoJUnitRunner;
                  @RunWith(MockitoJUnitRunner.class)
                  class Example {}
                  """,
                spec -> spec.after(source -> assertThat(source)
                  .contains("@ExtendWith(MockitoExtension.class)")
                  .doesNotContain("public MockitoRule mocks").actual())
              )
            ),
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>example</groupId>
                    <artifactId>test</artifactId>
                    <version>1</version>
                    <properties>
                        <mockito.version>1.10.19</mockito.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.mockito</groupId>
                            <artifactId>mockito-core</artifactId>
                            <version>${mockito.version}</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>example</groupId>
                    <artifactId>test</artifactId>
                    <version>1</version>
                    <properties>
                        <mockito.version>4.11.0</mockito.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.mockito</groupId>
                            <artifactId>mockito-core</artifactId>
                            <version>${mockito.version}</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.mockito</groupId>
                            <artifactId>mockito-junit-jupiter</artifactId>
                            <version>${mockito.version}</version>
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
    void doesNotAddJupiterForAnUnannotatedDelegate() {
        rewriteRun(
          mavenProject("adapter",
            srcTestJava(
              java(
                """
                  import org.mockito.junit.MockitoRule;
                  class Adapter {
                      MockitoRule delegate;
                      void configure() { delegate.silent(); }
                  }
                  """
              )
            ),
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>example</groupId>
                    <artifactId>adapter</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.mockito</groupId>
                            <artifactId>mockito-core</artifactId>
                            <version>5.23.0</version>
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
