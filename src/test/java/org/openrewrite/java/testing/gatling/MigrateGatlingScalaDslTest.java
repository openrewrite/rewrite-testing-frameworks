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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.scala.Assertions.scala;

class MigrateGatlingScalaDslTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateGatlingScalaDsl());
    }

    @DocumentExample
    @Test
    void renamesRemovedMethods() {
        rewriteRun(
          scala(
            """
              import io.gatling.core.Predef._
              import io.gatling.http.Predef._
              import scala.concurrent.duration._

              class BasicSimulation extends Simulation {
                val httpProtocol = http
                  .baseUrl("https://computer-database.gatling.io")
                  .inferHtmlResources(WhiteList(), BlackList(".*\\\\.css"))
                  .ignoreDefaultChecks
                  .ignoreDefaultHeaders

                val scn = scenario("Basic")
                  .exec(polling.every(10.seconds).exec(http("poll").get("/")))
                  .exec(stopInjector("enough"))
                  .exec(stopInjectorIf("too slow", session => true))

                setUp(scn.inject(heavisideUsers(1000).during(20.seconds))).protocols(httpProtocol)
              }
              """,
            """
              import io.gatling.core.Predef._
              import io.gatling.http.Predef._
              import scala.concurrent.duration._

              class BasicSimulation extends Simulation {
                val httpProtocol = http
                  .baseUrl("https://computer-database.gatling.io")
                  .inferHtmlResources(AllowList(), DenyList(".*\\\\.css"))
                  .ignoreProtocolChecks
                  .ignoreProtocolHeaders

                val scn = scenario("Basic")
                  .exec(poll.every(10.seconds).exec(http("poll").get("/")))
                  .exec(stopLoadGenerator("enough"))
                  .exec(stopLoadGeneratorIf("too slow", session => true))

                setUp(scn.inject(stressPeakUsers(1000).during(20.seconds))).protocols(httpProtocol)
              }
              """
          )
        );
    }

    @Test
    void browserPresetsBecomeConnectionCounts() {
        rewriteRun(
          scala(
            """
              import io.gatling.http.Predef._

              class Protocols {
                val chrome = http.maxConnectionsPerHostLikeChrome
                val safari = http
                  .baseUrl("https://example.com")
                  .maxConnectionsPerHostLikeSafari
                  .acceptHeader("*/*")
                val ie = http.maxConnectionsPerHostLikeIE10
              }
              """,
            """
              import io.gatling.http.Predef._

              class Protocols {
                val chrome = http.maxConnectionsPerHost(6)
                val safari = http
                  .baseUrl("https://example.com")
                  .maxConnectionsPerHost(6)
                  .acceptHeader("*/*")
                val ie = http.maxConnectionsPerHost(8)
              }
              """
          )
        );
    }

    @Test
    void feederConvertBecomesTransform() {
        rewriteRun(
          scala(
            """
              import io.gatling.core.Predef._

              class Feeders {
                val feeder = csv("users.csv").convert { case ("age", age) => age.toInt }
              }
              """,
            """
              import io.gatling.core.Predef._

              class Feeders {
                val feeder = csv("users.csv").transform { case ("age", age) => age.toInt }
              }
              """
          )
        );
    }

    @Test
    void checkTransformTakingTheSession() {
        rewriteRun(
          scala(
            """
              import io.gatling.core.Predef._
              import io.gatling.http.Predef._

              class Checks {
                val withSession = jsonPath("$.id").transform((id, session) => id + session.userId)
                val optionWithSession = jsonPath("$.id").transformOption((id, session) => id)
                val withoutSession = jsonPath("$.id").transform(id => id.toUpperCase)
              }
              """,
            """
              import io.gatling.core.Predef._
              import io.gatling.http.Predef._

              class Checks {
                val withSession = jsonPath("$.id").transformWithSession((id, session) => id + session.userId)
                val optionWithSession = jsonPath("$.id").transformOptionWithSession((id, session) => id)
                val withoutSession = jsonPath("$.id").transform(id => id.toUpperCase)
              }
              """
          )
        );
    }

    @Test
    void keepScalaCollectionTransform() {
        rewriteRun(
          scala(
            """
              import io.gatling.core.Predef._

              class Collections {
                val incremented = Map("a" -> 1).transform((key, value) => value + 1)
              }
              """
          )
        );
    }

    @Test
    void keepNamesDeclaredInTheSource() {
        rewriteRun(
          scala(
            """
              import io.gatling.core.Predef._

              class Own {
                def polling(n: Int): Int = n
                val heavisideUsers = 3
                val p = polling(heavisideUsers)
              }
              """
          )
        );
    }

    @Test
    void ignoreSourcesNotImportingGatling() {
        rewriteRun(
          scala(
            """
              import scala.concurrent.duration._

              class Elsewhere {
                def run(polling: Seq[Int]): Seq[Int] = polling.map(_ * 2)
              }
              """
          )
        );
    }
}
