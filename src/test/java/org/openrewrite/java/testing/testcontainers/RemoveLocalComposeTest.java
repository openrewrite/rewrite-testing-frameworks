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
package org.openrewrite.java.testing.testcontainers;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveLocalComposeTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new RemoveLocalCompose())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "testcontainers-1", "junit-4"));
    }

    @DocumentExample
    @Test
    void removeFromChain() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testcontainers.containers.DockerComposeContainer;

              import java.io.File;

              class A {
                  void start() {
                      DockerComposeContainer environment = new DockerComposeContainer(new File("docker-compose.yml"))
                              .withLocalCompose(true)
                              .withTailChildContainers(true);
                      environment.start();
                  }
              }
              """,
            """
              import org.testcontainers.containers.DockerComposeContainer;

              import java.io.File;

              class A {
                  void start() {
                      DockerComposeContainer environment = new DockerComposeContainer(new File("docker-compose.yml"))
                              .withTailChildContainers(true);
                      environment.start();
                  }
              }
              """
          )
        );
    }

    @Test
    void removeStatementOnComposeContainer() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testcontainers.containers.ComposeContainer;

              class A {
                  void start(ComposeContainer environment) {
                      environment.withLocalCompose(true);
                      environment.start();
                  }
              }
              """,
            """
              import org.testcontainers.containers.ComposeContainer;

              class A {
                  void start(ComposeContainer environment) {
                      environment.start();
                  }
              }
              """
          )
        );
    }

    @Test
    void keepLocalComposeFalse() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testcontainers.containers.DockerComposeContainer;

              import java.io.File;

              class A {
                  void start(boolean local) {
                      DockerComposeContainer containerized = new DockerComposeContainer(new File("docker-compose.yml"))
                              .withLocalCompose(false);
                      DockerComposeContainer configured = new DockerComposeContainer(new File("docker-compose.yml"))
                              .withLocalCompose(local);
                  }
              }
              """
          )
        );
    }
}
