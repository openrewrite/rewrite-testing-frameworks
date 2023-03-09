/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class JUnit5MigrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4.13.+", "hamcrest-2.2"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing.junit5")
            .build()
            .activateRecipes("org.openrewrite.java.testing.junit5.JUnit4to5Migration"));
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/284")
    @Test
    void classReference() {
        rewriteRun(
          java(
            """
            import org.junit.Test;
            
            public class Sample {
                void method() {
                    Class<Test> c = Test.class;
                }
            }
            """,
            """
            import org.junit.jupiter.api.Test;
            
            public class Sample {
                void method() {
                    Class<Test> c = Test.class;
                }
            }
            """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/145")
    void assertThatReceiver() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Assert;
              import org.junit.Test;

              import static java.util.Arrays.asList;
              import static org.hamcrest.Matchers.containsInAnyOrder;

              public class SampleTest {
                  @SuppressWarnings("ALL")
                  @Test
                  public void filterShouldRemoveUnusedConfig() {
                      Assert.assertThat(asList("1", "2", "3"),
                              containsInAnyOrder("3", "2", "1"));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static java.util.Arrays.asList;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.containsInAnyOrder;

              public class SampleTest {
                  @SuppressWarnings("ALL")
                  @Test
                  void filterShouldRemoveUnusedConfig() {
                      assertThat(asList("1", "2", "3"),
                              containsInAnyOrder("3", "2", "1"));
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/279")
    void upgradeMavenPluginVersions() {
        rewriteRun(
          pomXml(
            """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example.jackson</groupId>
                <artifactId>test-plugins</artifactId>
                <version>1.0.0</version>
                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-surefire-plugin</artifactId>
                            <version>2.20.1</version>
                        </plugin>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-failsafe-plugin</artifactId>
                            <version>2.20.1</version>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """,
            """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example.jackson</groupId>
                <artifactId>test-plugins</artifactId>
                <version>1.0.0</version>
                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-surefire-plugin</artifactId>
                            <version>2.22.2</version>
                        </plugin>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-failsafe-plugin</artifactId>
                            <version>2.22.2</version>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """
          )
        );
    }


}
