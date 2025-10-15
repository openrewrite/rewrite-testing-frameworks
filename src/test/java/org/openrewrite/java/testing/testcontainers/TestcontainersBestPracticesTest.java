/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.testing.testcontainers;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class TestcontainersBestPracticesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipeFromResource("/META-INF/rewrite/testcontainers.yml",
            "org.openrewrite.java.testing.testcontainers.TestContainersBestPractices")
          .parser(JavaParser.fromJavaVersion().classpath(
              "testcontainers",
              "testcontainers-cassandra",
              "testcontainers-kafka",
              "testcontainers-junit-jupiter",
              "testcontainers-localstack",
              "testcontainers-mysql",
              "testcontainers-nginx"));
    }

    @DocumentExample
    @Test
    void getHost() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testcontainers.containers.ContainerState;
              class Foo {
                  String method(ContainerState container) {
                      return container.getContainerIpAddress();
                  }
              }
              """,
            """
              import org.testcontainers.containers.ContainerState;
              class Foo {
                  String method(ContainerState container) {
                      return container.getHost();
                  }
              }
              """
          )
        );
    }

    @Test
    void dependencyUpdate() {
        rewriteRun(
          pomXml(
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.openrewrite.example</groupId>
                  <artifactId>testcontainers</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.testcontainers</groupId>
                          <artifactId>testcontainers</artifactId>
                          <version>1.21.3</version>
                      </dependency>
                      <dependency>
                          <groupId>org.testcontainers</groupId>
                          <artifactId>mysql</artifactId>
                          <version>1.21.3</version>
                      </dependency>
                      <dependency>
                          <groupId>org.testcontainers</groupId>
                          <artifactId>nginx</artifactId>
                          <version>1.21.3</version>
                      </dependency>
                      <dependency>
                          <groupId>org.testcontainers</groupId>
                          <artifactId>junit-jupiter</artifactId>
                          <version>1.21.3</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(after -> {
                Matcher matcher = Pattern.compile("<version>(2\\.\\d+\\.\\d+)</version>").matcher(after);
                assertTrue(matcher.find());
                String afterVersion = matcher.group(1);
                //language=xml
                return """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>org.openrewrite.example</groupId>
                      <artifactId>testcontainers</artifactId>
                      <version>1.0-SNAPSHOT</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.testcontainers</groupId>
                              <artifactId>testcontainers</artifactId>
                              <version>%s</version>
                          </dependency>
                          <dependency>
                              <groupId>org.testcontainers</groupId>
                              <artifactId>testcontainers-mysql</artifactId>
                              <version>%s</version>
                          </dependency>
                          <dependency>
                              <groupId>org.testcontainers</groupId>
                              <artifactId>testcontainers-nginx</artifactId>
                              <version>%s</version>
                          </dependency>
                          <dependency>
                              <groupId>org.testcontainers</groupId>
                              <artifactId>testcontainers-junit-jupiter</artifactId>
                              <version>%s</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """.formatted(afterVersion,
                                afterVersion,
                                afterVersion,
                                afterVersion);
            })
          )
        );
    }

    @Test
    void composeContainer() {
        rewriteRun(
          java(
            """
              import org.testcontainers.containers.DockerComposeContainer;

              class A {
                  void foo(String bar) {
                      DockerComposeContainer compose = new DockerComposeContainer();
                  }
              }
              """,
            """
              import org.testcontainers.containers.ComposeContainer;

              class A {
                  void foo(String bar) {
                      ComposeContainer compose = new ComposeContainer();
                  }
              }
              """
          )
        );
    }

    @Test
    void changeTypes() {
        rewriteRun(
          java(
            """
              import org.testcontainers.containers.CassandraContainer;
              import org.testcontainers.containers.KafkaContainer;
              import org.testcontainers.containers.localstack.LocalStackContainer;
              import org.testcontainers.containers.MySQLContainer;

              class A {
                  private CassandraContainer cassandra = null;
                  private KafkaContainer kafka = null;
                  private MySQLContainer mysql = null;
                  private LocalStackContainer localstack = null;
              }
              """,
            """
              import org.testcontainers.cassandra.CassandraContainer;
              import org.testcontainers.kafka.KafkaContainer;
              import org.testcontainers.localstack.LocalStackContainer;
              import org.testcontainers.mysql.MySQLContainer;

              class A {
                  private CassandraContainer cassandra = null;
                  private KafkaContainer kafka = null;
                  private MySQLContainer mysql = null;
                  private LocalStackContainer localstack = null;
              }
              """
          )
        );
    }
}
