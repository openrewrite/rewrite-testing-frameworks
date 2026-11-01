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

class MigrateKafkaContainerTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateKafkaContainer())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "kafka-1.20.6", "testcontainers-1.20.6", "junit-4"));
    }

    @DocumentExample
    @Test
    void confluentImage() {
        rewriteRun(
          java(
            """
              import org.testcontainers.containers.KafkaContainer;
              import org.testcontainers.utility.DockerImageName;

              class A {
                  KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
              }
              """,
            """
              import org.testcontainers.kafka.ConfluentKafkaContainer;
              import org.testcontainers.utility.DockerImageName;

              class A {
                  ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
              }
              """
          )
        );
    }

    @Test
    void apacheImage() {
        rewriteRun(
          java(
            """
              import org.testcontainers.containers.KafkaContainer;
              import org.testcontainers.utility.DockerImageName;

              class A {
                  KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));
              }
              """,
            """
              import org.testcontainers.kafka.KafkaContainer;
              import org.testcontainers.utility.DockerImageName;

              class A {
                  KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));
              }
              """
          )
        );
    }

    @Test
    void apacheNativeImage() {
        rewriteRun(
          java(
            """
              import org.testcontainers.containers.KafkaContainer;
              import org.testcontainers.utility.DockerImageName;

              class A {
                  KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));
              }
              """,
            """
              import org.testcontainers.kafka.KafkaContainer;
              import org.testcontainers.utility.DockerImageName;

              class A {
                  KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));
              }
              """
          )
        );
    }

    @Test
    void unsupportedConfluentVersion() {
        rewriteRun(
          java(
            """
              import org.testcontainers.containers.KafkaContainer;
              import org.testcontainers.utility.DockerImageName;

              class A {
                  KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"));
              }
              """
          )
        );
    }

    @Test
    void customImage() {
        rewriteRun(
          java(
            """
              import org.testcontainers.containers.KafkaContainer;
              import org.testcontainers.utility.DockerImageName;

              class A {
                  KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("registry.example/cp-kafka:7.6.1"));
              }
              """
          )
        );
    }

    @Test
    void similarImageName() {
        rewriteRun(
          java(
            """
              import org.testcontainers.containers.KafkaContainer;
              import org.testcontainers.utility.DockerImageName;

              class A {
                  KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka-custom:7.6.1"));
              }
              """
          )
        );
    }

    @Test
    void unresolvedImage() {
        rewriteRun(
          java(
            """
              import org.testcontainers.containers.KafkaContainer;
              import org.testcontainers.utility.DockerImageName;

              class A {
                  KafkaContainer kafka(DockerImageName image) {
                      return new KafkaContainer(image);
                  }
              }
              """
          )
        );
    }

    @Test
    void defaultConstructor() {
        rewriteRun(
          java(
            """
              import org.testcontainers.containers.KafkaContainer;
              import org.testcontainers.utility.DockerImageName;

              class A {
                  KafkaContainer kafka = new KafkaContainer();
              }
              """
          )
        );
    }

    @Test
    void legacyVersionConstructor() {
        rewriteRun(
          java(
            """
              import org.testcontainers.containers.KafkaContainer;
              import org.testcontainers.utility.DockerImageName;

              class A {
                  KafkaContainer kafka = new KafkaContainer("7.6.1");
              }
              """
          )
        );
    }

    @Test
    void legacyApi() {
        rewriteRun(
          java(
            """
              import org.testcontainers.containers.KafkaContainer;
              import org.testcontainers.utility.DockerImageName;

              class A {
                  KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1")).withKraft();
              }
              """
          )
        );
    }

    @Test
    void mixedImages() {
        rewriteRun(
          java(
            """
              import org.testcontainers.containers.KafkaContainer;
              import org.testcontainers.utility.DockerImageName;

              class A {
                  KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
                  KafkaContainer other = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));
              }
              """
          )
        );
    }

    @Test
    void legacyConstant() {
        rewriteRun(
          java(
            """
              import org.testcontainers.containers.KafkaContainer;
              import org.testcontainers.utility.DockerImageName;

              class A {
                  KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
                  int port = KafkaContainer.KAFKA_PORT;
              }
              """
          )
        );
    }

    @Test
    void subclass() {
        rewriteRun(
          java(
            """
              import org.testcontainers.containers.KafkaContainer;
              import org.testcontainers.utility.DockerImageName;

              class A extends KafkaContainer {
                  A() { super(DockerImageName.parse("confluentinc/cp-kafka:7.6.1")); }
              }
              """
          )
        );
    }

    @Test
    void declarationsAcrossFiles() {
        rewriteRun(
          java(
            """
              import org.testcontainers.containers.KafkaContainer;
              import org.testcontainers.utility.DockerImageName;

              class A {
                  KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));
              }
              """,
            """
              import org.testcontainers.kafka.ConfluentKafkaContainer;
              import org.testcontainers.utility.DockerImageName;

              class A {
                  ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));
              }
              """
          ),
          java(
            """
              import org.testcontainers.containers.KafkaContainer;

              class B {
                  KafkaContainer kafka(A a) { return a.kafka; }
              }
              """,
            """
              import org.testcontainers.kafka.ConfluentKafkaContainer;

              class B {
                  ConfluentKafkaContainer kafka(A a) { return a.kafka; }
              }
              """
          )
        );
    }

    @Test
    void unresolvedConstructionInAnotherFile() {
        rewriteRun(
          java(
            """
              import org.testcontainers.containers.KafkaContainer;
              import org.testcontainers.utility.DockerImageName;

              class A {
                  KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
              }
              """
          ),
          java(
            """
              import org.testcontainers.containers.KafkaContainer;
              import org.testcontainers.utility.DockerImageName;

              class B {
                  KafkaContainer kafka(DockerImageName image) { return new KafkaContainer(image); }
              }
              """
          )
        );
    }

    @Test
    void anonymousSubclass() {
        rewriteRun(
          java(
            """
              import org.testcontainers.containers.KafkaContainer;
              import org.testcontainers.utility.DockerImageName;

              class A {
                  KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1")) {};
              }
              """
          )
        );
    }

    @Test
    void constructorReference() {
        rewriteRun(
          java(
            """
              import org.testcontainers.containers.KafkaContainer;
              import org.testcontainers.utility.DockerImageName;
              import java.util.function.Supplier;

              class A {
                  KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
                  Supplier<KafkaContainer> factory = KafkaContainer::new;
              }
              """
          )
        );
    }

    @Test
    void containerClassesComposite() {
        rewriteRun(
          spec -> spec.recipeFromResource("/META-INF/rewrite/testcontainers.yml",
            "org.openrewrite.java.testing.testcontainers.Testcontainers2ContainerClasses"),
          java(
            """
              import org.testcontainers.containers.KafkaContainer;
              import org.testcontainers.utility.DockerImageName;
              import java.util.function.Supplier;

              class A {
                  static final KafkaContainer kafka = new KafkaContainer(
                      DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
                  Supplier<String> bootstrapServers = kafka::getBootstrapServers;
              }
              """,
            """
              import org.testcontainers.kafka.ConfluentKafkaContainer;
              import org.testcontainers.utility.DockerImageName;
              import java.util.function.Supplier;

              class A {
                  static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
                      DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
                  Supplier<String> bootstrapServers = kafka::getBootstrapServers;
              }
              """
          )
        );
    }

}
