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
import static org.openrewrite.scala.Assertions.scala;

class ReplaceGatlingDollarExpressionLanguageTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceGatlingDollarExpressionLanguage())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "gatling-core-3.9", "gatling-core-java-3.9", "gatling-http-3.9", "gatling-http-java-3.9"));
    }

    @DocumentExample
    @Test
    void scalaSimulation() {
        rewriteRun(
          scala(
            """
              import io.gatling.core.Predef._
              import io.gatling.http.Predef._

              class ComputerSimulation extends Simulation {
                val scn = scenario("Computers")
                  .exec(http("computer").get("/computers/${computerId}").check(jsonPath("$.name").is("${name}")))
                  .exec(http("search").post("/search").body(StringBody(\"\"\"{"q": "${query.random()}"}\"\"\")))
              }
              """,
            """
              import io.gatling.core.Predef._
              import io.gatling.http.Predef._

              class ComputerSimulation extends Simulation {
                val scn = scenario("Computers")
                  .exec(http("computer").get("/computers/#{computerId}").check(jsonPath("$.name").is("#{name}")))
                  .exec(http("search").post("/search").body(StringBody(\"\"\"{"q": "#{query.random()}"}\"\"\")))
              }
              """
          )
        );
    }

    @Test
    void scalaInterpolatedStringsAreLeftAlone() {
        rewriteRun(
          scala(
            """
              import io.gatling.core.Predef._
              import io.gatling.http.Predef._

              class InterpolatingSimulation extends Simulation {
                val baseUrl = "https://example.com"
                val scn = scenario("Interpolating").exec(http("home").get(s"${baseUrl}/home"))
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
                  ScenarioBuilder scn = scenario("Computers")
                    .exec(http("computer").get("/computers/${computerId}").check(jsonPath("$.name").is("${name}")))
                    .exec(http("search").post("/search").body(StringBody(\"\"\"
                      {"q": "${query}", "page": ${page}}
                      \"\"\")));
              }
              """,
            """
              import io.gatling.javaapi.core.*;
              import io.gatling.javaapi.http.*;

              import static io.gatling.javaapi.core.CoreDsl.*;
              import static io.gatling.javaapi.http.HttpDsl.*;

              public class ComputerSimulation extends Simulation {
                  ScenarioBuilder scn = scenario("Computers")
                    .exec(http("computer").get("/computers/#{computerId}").check(jsonPath("$.name").is("#{name}")))
                    .exec(http("search").post("/search").body(StringBody(\"\"\"
                      {"q": "#{query}", "page": #{page}}
                      \"\"\")));
              }
              """
          )
        );
    }

    @Test
    void ignoreSourcesNotImportingGatling() {
        rewriteRun(
          //language=java
          java(
            """
              class Placeholders {
                  String url = "${base.url}/home";
              }
              """
          ),
          scala(
            """
              class Placeholders {
                val url = "${base.url}/home"
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
              import io.gatling.javaapi.core.*
              import io.gatling.javaapi.core.CoreDsl.*
              import io.gatling.javaapi.http.HttpDsl.*

              class ComputerSimulation : Simulation() {
                  val baseUrl = "https://example.com"
                  val scn = scenario("Computers")
                    .exec(http("computer").get("/computers/\\${computerId}"))
                    .exec(http("home").get("$baseUrl/home"))
                    .exec(http("about").get("${baseUrl}/about"))
              }
              """,
            """
              import io.gatling.javaapi.core.*
              import io.gatling.javaapi.core.CoreDsl.*
              import io.gatling.javaapi.http.HttpDsl.*

              class ComputerSimulation : Simulation() {
                  val baseUrl = "https://example.com"
                  val scn = scenario("Computers")
                    .exec(http("computer").get("/computers/#{computerId}"))
                    .exec(http("home").get("$baseUrl/home"))
                    .exec(http("about").get("${baseUrl}/about"))
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
