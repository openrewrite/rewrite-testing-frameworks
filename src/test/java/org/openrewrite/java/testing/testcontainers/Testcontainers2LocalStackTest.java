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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

class Testcontainers2LocalStackTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipeFromResources("org.openrewrite.java.testing.testcontainers.Testcontainers2LocalStack")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "testcontainers-localstack", "testcontainers-2"))
          .parser(KotlinParser.builder()
            .classpathFromResources(new InMemoryExecutionContext(), "testcontainers-localstack", "testcontainers-2"));
    }

    @DocumentExample
    @Test
    void getEndpointOverrideToGetEndpoint() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testcontainers.containers.localstack.LocalStackContainer;

              import java.net.URI;

              class A {
                  URI endpoint(LocalStackContainer localStackContainer) {
                      return localStackContainer.getEndpointOverride(LocalStackContainer.Service.SQS);
                  }
              }
              """,
            """
              import org.testcontainers.containers.localstack.LocalStackContainer;

              import java.net.URI;

              class A {
                  URI endpoint(LocalStackContainer localStackContainer) {
                      return localStackContainer.getEndpoint();
                  }
              }
              """
          )
        );
    }

    @Test
    void getEndpointOverrideToGetEndpointKotlin() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              import org.testcontainers.containers.localstack.LocalStackContainer

              class A {
                  fun endpoint(localStackContainer: LocalStackContainer) =
                      localStackContainer.getEndpointOverride(LocalStackContainer.Service.SQS)
              }
              """,
            """
              import org.testcontainers.containers.localstack.LocalStackContainer

              class A {
                  fun endpoint(localStackContainer: LocalStackContainer) =
                      localStackContainer.getEndpoint()
              }
              """
          )
        );
    }

    @Test
    void servicesAndEndpointTogether() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testcontainers.containers.localstack.LocalStackContainer;

              import java.net.URI;

              class A {
                  LocalStackContainer localstack = new LocalStackContainer()
                          .withServices(LocalStackContainer.Service.SQS);

                  URI endpoint() {
                      return localstack.getEndpointOverride(LocalStackContainer.Service.SQS);
                  }
              }
              """,
            """
              import org.testcontainers.containers.localstack.LocalStackContainer;

              import java.net.URI;

              class A {
                  LocalStackContainer localstack = new LocalStackContainer()
                          .withServices("sqs");

                  URI endpoint() {
                      return localstack.getEndpoint();
                  }
              }
              """
          )
        );
    }

    @Test
    void nonTrivialServiceNames() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testcontainers.containers.localstack.LocalStackContainer;

              class A {
                  LocalStackContainer localstack = new LocalStackContainer()
                          .withServices(LocalStackContainer.Service.API_GATEWAY,
                                  LocalStackContainer.Service.DYNAMODB_STREAMS,
                                  LocalStackContainer.Service.CLOUDWATCHLOGS);
              }
              """,
            """
              import org.testcontainers.containers.localstack.LocalStackContainer;

              class A {
                  LocalStackContainer localstack = new LocalStackContainer()
                          .withServices("apigateway",
                                  "dynamodbstreams",
                                  "logs");
              }
              """
          )
        );
    }

    @Test
    void withServicesKotlin() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              import org.testcontainers.containers.localstack.LocalStackContainer

              class A {
                  val localstack: LocalStackContainer = LocalStackContainer()
                      .withServices(LocalStackContainer.Service.SQS, LocalStackContainer.Service.S3)
              }
              """,
            """
              import org.testcontainers.containers.localstack.LocalStackContainer

              class A {
                  val localstack: LocalStackContainer = LocalStackContainer()
                      .withServices("sqs", "s3")
              }
              """
          )
        );
    }

    /// End-to-end reproduction of the customer-reported Kotlin files (issue #2437) run through the full
    /// `Testcontainers2Migration`, asserting the LocalStack member references are migrated alongside
    /// the type rename so the result compiles against Testcontainers 2.x.
    @Nested
    class FullMigration implements RewriteTest {

        @Override
        public void defaults(RecipeSpec spec) {
            spec
              .recipeFromResources("org.openrewrite.java.testing.testcontainers.Testcontainers2Migration")
              .parser(KotlinParser.builder()
                .classpathFromResources(new InMemoryExecutionContext(), "testcontainers-localstack", "testcontainers-2"));
        }

        @Test
        void awsConfig() {
            rewriteRun(
              //language=kotlin
              kotlin(
                """
                  import org.testcontainers.containers.localstack.LocalStackContainer

                  class AwsConfig {
                      fun endpoint(localStackContainer: LocalStackContainer) =
                          localStackContainer.getEndpointOverride(LocalStackContainer.Service.SQS)
                  }
                  """,
                """
                  import org.testcontainers.localstack.LocalStackContainer

                  class AwsConfig {
                      fun endpoint(localStackContainer: LocalStackContainer) =
                          localStackContainer.getEndpoint()
                  }
                  """
              )
            );
        }

        @Test
        void baseTestcontainersConfig() {
            rewriteRun(
              //language=kotlin
              kotlin(
                """
                  import org.testcontainers.containers.localstack.LocalStackContainer
                  import org.testcontainers.utility.DockerImageName

                  class BaseTestcontainersConfig {
                      val localStackContainer: LocalStackContainer = LocalStackContainer(DockerImageName.parse("localstack/localstack:3.4.0"))
                          .withServices(LocalStackContainer.Service.SQS)
                  }
                  """,
                """
                  import org.testcontainers.localstack.LocalStackContainer
                  import org.testcontainers.utility.DockerImageName

                  class BaseTestcontainersConfig {
                      val localStackContainer: LocalStackContainer = LocalStackContainer(DockerImageName.parse("localstack/localstack:3.4.0"))
                          .withServices("sqs")
                  }
                  """
              )
            );
        }
    }
}
