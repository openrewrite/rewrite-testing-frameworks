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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ConvertToRawTypeTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipeFromResources("org.openrewrite.java.testing.testcontainers.Testcontainers2ContainerClasses")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "testcontainers-1", "nginx"));
    }

    @DocumentExample
    @Test
    void variableDeclaration() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testcontainers.containers.NginxContainer;

              class Foo {
                  NginxContainer<?> container = new NginxContainer<>();
              }
              """,
            """
              import org.testcontainers.nginx.NginxContainer;

              class Foo {
                  NginxContainer container = new NginxContainer();
              }
              """
          )
        );
    }

    @Test
    void variableDeclarationWithModifier() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testcontainers.containers.NginxContainer;

              class Foo {
                  private static final NginxContainer<?> container = new NginxContainer<>();
              }
              """,
            """
              import org.testcontainers.nginx.NginxContainer;

              class Foo {
                  private static final NginxContainer container = new NginxContainer();
              }
              """
          )
        );
    }

    @Test
    void methodReturnType() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testcontainers.containers.NginxContainer;

              class Foo {
                  NginxContainer<?> createContainer() {
                      return new NginxContainer<>();
                  }
              }
              """,
            """
              import org.testcontainers.nginx.NginxContainer;

              class Foo {
                  NginxContainer createContainer() {
                      return new NginxContainer();
                  }
              }
              """
          )
        );
    }

    @Test
    void newClassExpression() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testcontainers.containers.NginxContainer;

              class Foo {
                  void test() {
                      var container = new NginxContainer<>();
                  }
              }
              """,
            """
              import org.testcontainers.nginx.NginxContainer;

              class Foo {
                  void test() {
                      var container = new NginxContainer();
                  }
              }
              """
          )
        );
    }

    @Test
    void onlyChangeImportWhenAlreadyRaw() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testcontainers.containers.NginxContainer;

              class Foo {
                  NginxContainer container = new NginxContainer();
              }
              """,
            """
              import org.testcontainers.nginx.NginxContainer;

              class Foo {
                  NginxContainer container = new NginxContainer();
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenDifferentType() {
        rewriteRun(
          spec -> spec.recipe(new ConvertToRawType("org.testcontainers.containers.MySQLContainer")),
          //language=java
          java(
            """
              import org.testcontainers.containers.NginxContainer;
              class Foo {
                  NginxContainer<?> container = new NginxContainer<>();
              }
              """
          )
        );
    }
}
