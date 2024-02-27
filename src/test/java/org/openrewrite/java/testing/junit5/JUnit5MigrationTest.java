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
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4.13", "hamcrest-2.2"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing.junit5")
            .build()
            .activateRecipes("org.openrewrite.java.testing.junit5.JUnit4to5Migration"));
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/284")
    @Test
    void classReference() {
        rewriteRun(
          //language=java
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
                  public void filterShouldRemoveUnusedConfig() {
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
          //language=xml
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

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/429")
    void dontExcludeJunit4DependencyfromTestcontainers() {
        //language=xml
        String before = """
          <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example.jackson</groupId>
              <artifactId>test-plugins</artifactId>
              <version>1.0.0</version>
              <dependencies>
                  <dependency>
                      <groupId>org.testcontainers</groupId>
                      <artifactId>testcontainers</artifactId>
                      <version>1.18.3</version>
                      <scope>test</scope>
                  </dependency>
              </dependencies>
          </project>
          """;
        // Output identical, but we want to make sure we don't exclude junit4 from testcontainers
        rewriteRun(pomXml(before, before));
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/477")
    void dontExcludeJunit4DependencyfromSpringBootTestcontainers() {
        //language=xml
        String before = """
          <project>
              <modelVersion>4.0.0</modelVersion>
              <parent>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-starter-parent</artifactId>
                  <version>3.2.1</version>
                  <relativePath/> <!-- lookup parent from repository -->
              </parent>
              <groupId>dev.ted</groupId>
              <artifactId>testcontainer-migrate</artifactId>
              <version>0.0.1</version>
              <dependencies>
                  <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter</artifactId>
                  </dependency>
                  <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-test</artifactId>
                      <scope>test</scope>
                  </dependency>
                  <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-testcontainers</artifactId>
                      <scope>test</scope>
                  </dependency>
                  <dependency>
                      <groupId>org.testcontainers</groupId>
                      <artifactId>junit-jupiter</artifactId>
                      <scope>test</scope>
                  </dependency>
              </dependencies>
          </project>
          """;
        // Output identical, but we want to make sure we don't exclude junit4 from testcontainers
        rewriteRun(pomXml(before, before));
    }

    // edge case for deprecated use of assertEquals
    // https://junit.org/junit4/javadoc/4.13/org/junit/Assert.html#assertEquals(java.lang.Object%5B%5D,%20java.lang.Object%5B%5D)
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/pull/384")
    @Test
    void assertEqualsWithArrayArgumentToAssertArrayEquals() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Assert;
                            
              class MyTest {
                  void test() {
                       Assert.assertEquals(new Object[1], new Object[1]);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions;
                            
              class MyTest {
                  void test() {
                       Assertions.assertArrayEquals(new Object[1], new Object[1]);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/443")
    @Test
    void migrateInheritedTestBeforeAfterAnnotations() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.After;
              import org.junit.Before;
              import org.junit.Test;
                            
              public class AbstractTest {
                  @Before
                  public void before() {
                  }

                  @After
                  public void after() {
                  }

                  @Test
                  public void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;
                            
              public class AbstractTest {
                  @BeforeEach
                  public void before() {
                  }

                  @AfterEach
                  public void after() {
                  }

                  @Test
                  public void test() {
                  }
              }
              """
          ),
          java(
            """
              public class A extends AbstractTest {
                  public void before() {
                  }

                  public void after() {
                  }

                  public void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;
                            
              public class A extends AbstractTest {
                  @BeforeEach
                  public void before() {
                  }

                  @AfterEach
                  public void after() {
                  }

                  @Test
                  public void test() {
                  }
              }
              """
          )
        );
    }

}
