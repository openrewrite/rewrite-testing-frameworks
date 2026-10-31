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
package org.openrewrite.java.testing.gatling;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.scala.Assertions.scala;

class UpgradeToGatling3_16Test implements RewriteTest {

    private static final String[] GATLING_3_9 = {"gatling-core-3.9", "gatling-core-java-3.9", "gatling-http-3.9", "gatling-http-java-3.9"};

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.testing.gatling.UpgradeToGatling3_16")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), GATLING_3_9))
          .parser(KotlinParser.builder().classpathFromResources(new InMemoryExecutionContext(), GATLING_3_9));
    }

    @DocumentExample
    @Test
    void scalaSimulation() {
        rewriteRun(
          scala(
            """
              import io.gatling.core.Predef._
              import io.gatling.http.Predef._
              import scala.concurrent.duration._

              class ComputerSimulation extends Simulation {
                val httpProtocol = http
                  .baseUrl("https://computer-database.gatling.io")
                  .inferHtmlResources(BlackList(".*\\\\.css"))
                  .maxConnectionsPerHostLikeChrome

                val feeder = csv("computers.csv").convert { case ("id", id) => id.toInt }

                val scn = scenario("Computers")
                  .feed(feeder)
                  .exec(http("computer").get("/computers/${id}"))
                  .exec(polling.every(10.seconds).exec(http("poll").get("/computers")))

                setUp(scn.inject(heavisideUsers(100).during(20 seconds))).protocols(httpProtocol)
              }
              """,
            """
              import io.gatling.core.Predef._
              import io.gatling.http.Predef._
              import scala.concurrent.duration._

              class ComputerSimulation extends Simulation {
                val httpProtocol = http
                  .baseUrl("https://computer-database.gatling.io")
                  .inferHtmlResources(DenyList(".*\\\\.css"))
                  .maxConnectionsPerHost(6)

                val feeder = csv("computers.csv").transform { case ("id", id) => id.toInt }

                val scn = scenario("Computers")
                  .feed(feeder)
                  .exec(http("computer").get("/computers/#{id}"))
                  .exec(poll.every(10.seconds).exec(http("poll").get("/computers")))

                setUp(scn.inject(stressPeakUsers(100).during(20.seconds))).protocols(httpProtocol)
              }
              """
          )
        );
    }

    @Test
    void javaSimulation() {
        rewriteRun(
          //language=java
          java(
            """
              import io.gatling.javaapi.core.*;
              import io.gatling.javaapi.http.*;

              import static io.gatling.javaapi.core.CoreDsl.*;
              import static io.gatling.javaapi.http.HttpDsl.*;

              public class ComputerSimulation extends Simulation {
                  HttpProtocolBuilder httpProtocol = http.baseUrl("https://computer-database.gatling.io");

                  ScenarioBuilder scn = scenario("Computers")
                    .exec(exitBlockOnFail(exec(http("computer").get("/computers/${id}"))))
                    .doSwitch("#{kind}").on(Choice.withKey("laptop", exec(http("laptops").get("/laptops"))))
                    .exec(stopInjector("done"));

                  {
                      setUp(scn.injectOpen(stressPeakUsers(100).during(20))).protocols(httpProtocol);
                  }
              }
              """,
            """
              import io.gatling.javaapi.core.*;
              import io.gatling.javaapi.http.*;

              import static io.gatling.javaapi.core.CoreDsl.*;
              import static io.gatling.javaapi.http.HttpDsl.*;

              public class ComputerSimulation extends Simulation {
                  HttpProtocolBuilder httpProtocol = http.baseUrl("https://computer-database.gatling.io");

                  ScenarioBuilder scn = scenario("Computers")
                    .exec(exitBlockOnFail().on(exec(http("computer").get("/computers/#{id}"))))
                    .doSwitch("#{kind}").on(onCase("laptop").then(exec(http("laptops").get("/laptops"))))
                    .exec(stopLoadGenerator("done"));

                  {
                      setUp(scn.injectOpen(stressPeakUsers(100).during(20))).protocols(httpProtocol);
                  }
              }
              """
          )
        );
    }

    @Test
    void kotlinSimulation() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              import io.gatling.javaapi.core.*
              import io.gatling.javaapi.core.CoreDsl.*
              import io.gatling.javaapi.http.HttpDsl.*

              class ComputerSimulation : Simulation() {
                  val httpProtocol = http.baseUrl("https://computer-database.gatling.io")

                  val scn = scenario("Computers")
                    .exec(exitBlockOnFail(exec(http("computer").get("/computers/\\${id}"))))
                    .randomSwitch().on(Choice.withWeight(50.0, exec(http("laptops").get("/laptops"))))
                    .exec(stopInjector("done"))

                  init {
                      setUp(scn.injectOpen(atOnceUsers(1))).protocols(httpProtocol)
                  }
              }
              """,
            """
              import io.gatling.javaapi.core.*
              import io.gatling.javaapi.core.CoreDsl.*
              import io.gatling.javaapi.http.HttpDsl.*

              class ComputerSimulation : Simulation() {
                  val httpProtocol = http.baseUrl("https://computer-database.gatling.io")

                  val scn = scenario("Computers")
                    .exec(exitBlockOnFail().on(exec(http("computer").get("/computers/#{id}"))))
                    .randomSwitch().on(percent(50.0).then(exec(http("laptops").get("/laptops"))))
                    .exec(stopLoadGenerator("done"))

                  init {
                      setUp(scn.injectOpen(atOnceUsers(1))).protocols(httpProtocol)
                  }
              }
              """
          )
        );
    }

    @Test
    void mavenDependenciesAndPlugin() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>load-tests</artifactId>
                <version>1.0.0</version>
                <properties>
                  <gatling.version>3.0.0</gatling.version>
                </properties>
                <dependencies>
                  <dependency>
                    <groupId>io.gatling.highcharts</groupId>
                    <artifactId>gatling-charts-highcharts</artifactId>
                    <version>${gatling.version}</version>
                    <scope>test</scope>
                  </dependency>
                  <dependency>
                    <groupId>io.gatling</groupId>
                    <artifactId>gatling-app</artifactId>
                    <version>${gatling.version}</version>
                    <scope>test</scope>
                  </dependency>
                </dependencies>
                <build>
                  <plugins>
                    <plugin>
                      <groupId>io.gatling</groupId>
                      <artifactId>gatling-maven-plugin</artifactId>
                      <version>3.0.0</version>
                    </plugin>
                  </plugins>
                </build>
              </project>
              """,
            spec -> spec.after(after -> assertThat(after)
              .containsPattern("<gatling.version>3\\.16\\.\\d+</gatling.version>")
              .containsPattern("<artifactId>gatling-maven-plugin</artifactId>\\s*<version>4\\.\\d+\\.\\d+</version>")
              .actual())
          )
        );
    }

    @Test
    void gradlePlugin() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'scala'
                  id 'io.gatling.gradle' version '3.9.5'
              }
              """,
            spec -> spec.after(after -> assertThat(after)
              .containsPattern("id 'io\\.gatling\\.gradle' version '3\\.16\\.[\\d.]+'")
              .actual())
          )
        );
    }
}
