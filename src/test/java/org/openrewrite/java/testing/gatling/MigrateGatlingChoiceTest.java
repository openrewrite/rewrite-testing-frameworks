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

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

class MigrateGatlingChoiceTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateGatlingChoice())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "gatling-core-3.9",
              "gatling-core-java-3.9",
              "gatling-http-3.9",
              "gatling-http-java-3.9"));
    }

    @DocumentExample
    @Test
    void withKeyAndWithWeight() {
        rewriteRun(
          //language=java
          java(
            """
              import io.gatling.javaapi.core.*;

              import static io.gatling.javaapi.core.CoreDsl.*;
              import static io.gatling.javaapi.http.HttpDsl.*;

              public class SwitchSimulation extends Simulation {
                  ScenarioBuilder scn = scenario("Switches")
                    .doSwitch("#{page}").on(
                      Choice.withKey("home", exec(http("home").get("/"))),
                      Choice.withKey("about", exec(http("about").get("/about")))
                    )
                    .randomSwitch().on(
                      Choice.withWeight(60.0, exec(http("search").get("/search"))),
                      Choice.withWeight(40.0, exec(http("browse").get("/browse")))
                    );
              }
              """,
            """
              import io.gatling.javaapi.core.*;

              import static io.gatling.javaapi.core.CoreDsl.*;
              import static io.gatling.javaapi.http.HttpDsl.*;

              public class SwitchSimulation extends Simulation {
                  ScenarioBuilder scn = scenario("Switches")
                    .doSwitch("#{page}").on(
                      onCase("home").then(exec(http("home").get("/"))),
                      onCase("about").then(exec(http("about").get("/about")))
                    )
                    .randomSwitch().on(
                      percent(60.0).then(exec(http("search").get("/search"))),
                      percent(40.0).then(exec(http("browse").get("/browse")))
                    );
              }
              """
          )
        );
    }

    @Test
    void addsStaticImportWhereNotCoveredByWildcard() {
        rewriteRun(
          //language=java
          java(
            """
              import io.gatling.javaapi.core.ChainBuilder;
              import io.gatling.javaapi.core.Choice;

              class Choices {
                  Choice.WithKey home(ChainBuilder chain) {
                      return Choice.withKey("home", chain);
                  }
              }
              """,
            """
              import io.gatling.javaapi.core.ChainBuilder;
              import io.gatling.javaapi.core.Choice;

              import static io.gatling.javaapi.core.CoreDsl.onCase;

              class Choices {
                  Choice.WithKey home(ChainBuilder chain) {
                      return onCase("home").then(chain);
                  }
              }
              """
          )
        );
    }

    @Test
    void kotlinSimulation() {
        rewriteRun(
          spec -> spec.parser(kotlinParser()),
          //language=kotlin
          kotlin(
            """
              import io.gatling.javaapi.core.Choice
              import io.gatling.javaapi.core.CoreDsl.exec
              import io.gatling.javaapi.core.CoreDsl.pause
              import io.gatling.javaapi.core.CoreDsl.scenario
              import io.gatling.javaapi.core.Simulation

              class SwitchSimulation : Simulation() {
                  val scn = scenario("Switches")
                    .doSwitch("#{page}").on(Choice.withKey("home", exec(pause(1))))
              }
              """,
            """
              import io.gatling.javaapi.core.CoreDsl.*
              import io.gatling.javaapi.core.Simulation

              class SwitchSimulation : Simulation() {
                  val scn = scenario("Switches")
                    .doSwitch("#{page}").on(onCase("home").then(exec(pause(1))))
              }
              """
          )
        );
    }

    private static KotlinParser.Builder kotlinParser() {
        return KotlinParser.builder()
          .classpathFromResources(new InMemoryExecutionContext(), "gatling-core-3.9", "gatling-core-java-3.9", "gatling-http-3.9", "gatling-http-java-3.9");
    }
}
