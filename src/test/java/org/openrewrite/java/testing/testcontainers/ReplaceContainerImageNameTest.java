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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ReplaceContainerImageNameTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new ReplaceContainerImageName(
            "org.testcontainers.containers.KafkaContainer",
            "confluentinc/cp-kafka",
            "apache/kafka-native:3.8.0"))
          .parser(JavaParser.fromJavaVersion()
            //language=java
            .dependsOn(
              """
                package org.testcontainers.utility;
                public class DockerImageName {
                    public DockerImageName(String image) {}
                    public static DockerImageName parse(String image) { return new DockerImageName(image); }
                }
                """,
              """
                package org.testcontainers.containers;
                import org.testcontainers.utility.DockerImageName;
                public class KafkaContainer {
                    public KafkaContainer(DockerImageName image) {}
                }
                """));
    }

    @DocumentExample
    @Test
    void replaceConfluentWithKafkaNative() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testcontainers.containers.KafkaContainer;
              import org.testcontainers.utility.DockerImageName;

              class A {
                  KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.3"));
              }
              """,
            """
              import org.testcontainers.containers.KafkaContainer;
              import org.testcontainers.utility.DockerImageName;

              class A {
                  KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));
              }
              """
          )
        );
    }

    @Test
    void doesNotChangeNonMatchingImage() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testcontainers.containers.KafkaContainer;
              import org.testcontainers.utility.DockerImageName;

              class A {
                  KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));
              }
              """
          )
        );
    }

    @Test
    void replaceInDockerImageNameConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testcontainers.containers.KafkaContainer;
              import org.testcontainers.utility.DockerImageName;

              class A {
                  KafkaContainer kafka = new KafkaContainer(new DockerImageName("confluentinc/cp-kafka:7.5.3"));
              }
              """,
            """
              import org.testcontainers.containers.KafkaContainer;
              import org.testcontainers.utility.DockerImageName;

              class A {
                  KafkaContainer kafka = new KafkaContainer(new DockerImageName("apache/kafka-native:3.8.0"));
              }
              """
          )
        );
    }

    @Test
    void doesNotChangeImageOutsideDockerImageNameParse() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testcontainers.containers.KafkaContainer;

              class A {
                  String image = "confluentinc/cp-kafka:7.5.3";
              }
              """
          )
        );
    }
}
