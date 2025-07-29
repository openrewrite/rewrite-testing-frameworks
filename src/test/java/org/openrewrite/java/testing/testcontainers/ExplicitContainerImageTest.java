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
package org.openrewrite.java.testing.testcontainers;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ExplicitContainerImageTest implements RewriteTest {
    @DocumentExample
    @Test
    void explicitContainerImage() {
        rewriteRun(
          spec -> spec
            .recipe(new ExplicitContainerImage("org.testcontainers.containers.NginxContainer", "nginx:1.9.4", null))
            .parser(JavaParser.fromJavaVersion().classpath("nginx")),
          //language=java
          java(
            """
              import org.testcontainers.containers.NginxContainer;
              class Foo {
                  NginxContainer container = new NginxContainer();
              }
              """,
            """
              import org.testcontainers.containers.NginxContainer;
              class Foo {
                  NginxContainer container = new NginxContainer("nginx:1.9.4");
              }
              """
          )
        );
    }

    @Test
    void explicitContainerImageParsed() {
        rewriteRun(
          spec -> spec
            .recipe(new ExplicitContainerImage("org.testcontainers.containers.NginxContainer", "nginx:1.9.4", true))
            .parser(JavaParser.fromJavaVersion().classpath("nginx")),
          //language=java
          java(
            """
              import org.testcontainers.containers.NginxContainer;
              
              class Foo {
                  NginxContainer container = new NginxContainer();
              }
              """,
            """
              import org.testcontainers.containers.NginxContainer;
              import org.testcontainers.utility.DockerImageName;
              
              class Foo {
                  NginxContainer container = new NginxContainer(DockerImageName.parse("nginx:1.9.4"));
              }
              """
          )
        );
    }

    @Test
    void explicitContainerImages() {
        rewriteRun(
          spec -> spec
            .recipeFromResource("/META-INF/rewrite/testcontainers.yml", "org.openrewrite.java.testing.testcontainers.ExplicitContainerImages")
            .parser(JavaParser.fromJavaVersion().classpath("nginx")),
          //language=java
          java(
            """
              import org.testcontainers.containers.NginxContainer;
              class Foo {
                  NginxContainer container = new NginxContainer();
              }
              """,
            """
              import org.testcontainers.containers.NginxContainer;
              class Foo {
                  NginxContainer container = new NginxContainer("nginx:1.9.4");
              }
              """
          )
        );
    }
}
