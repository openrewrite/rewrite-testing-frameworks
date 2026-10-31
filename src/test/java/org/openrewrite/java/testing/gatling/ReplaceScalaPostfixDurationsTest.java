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

class ReplaceScalaPostfixDurationsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceScalaPostfixDurations());
    }

    @DocumentExample
    @Test
    void postfixDurations() {
        rewriteRun(
          scala(
            """
              import io.gatling.core.Predef._
              import scala.concurrent.duration._

              class BasicSimulation extends Simulation {
                val scn = scenario("Basic")
                  .pause(3 seconds, 5 seconds)
                  .during(2 minutes) { exec(pause(500 milliseconds)) }

                setUp(scn.inject(rampUsers(10).during(20 seconds))).maxDuration(1 hour)
              }
              """,
            """
              import io.gatling.core.Predef._
              import scala.concurrent.duration._

              class BasicSimulation extends Simulation {
                val scn = scenario("Basic")
                  .pause(3.seconds, 5.seconds)
                  .during(2.minutes) { exec(pause(500.milliseconds)) }

                setUp(scn.inject(rampUsers(10).during(20.seconds))).maxDuration(1.hour)
              }
              """
          )
        );
    }

    @Test
    void expressionsAndFractions() {
        rewriteRun(
          scala(
            """
              import io.gatling.core.Predef._
              import scala.concurrent.duration._

              class Pauses {
                val base = 2
                val sum = (base + 1) seconds
                val half = 1.5 seconds
                val named = base minutes
              }
              """,
            """
              import io.gatling.core.Predef._
              import scala.concurrent.duration._

              class Pauses {
                val base = 2
                val sum = (base + 1).seconds
                val half = 1.5.seconds
                val named = base.minutes
              }
              """
          )
        );
    }

    @Test
    void keepDotNotationAndOtherInfixCalls() {
        rewriteRun(
          scala(
            """
              import io.gatling.core.Predef._
              import scala.concurrent.duration._

              class Untouched {
                val dotted = 5.seconds
                val larger = 3 max 4
                val total = 5.seconds plus 2.seconds
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

              class Timeouts {
                val timeout = 5 seconds
              }
              """
          )
        );
    }
}
