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

class MigrateGatlingExitBlockOnFailTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateGatlingExitBlockOnFail())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "gatling-core-3.9",
              "gatling-core-java-3.9",
              "gatling-http-3.9",
              "gatling-http-java-3.9"));
    }

    @DocumentExample
    @Test
    void staticMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import io.gatling.javaapi.core.*;

              import static io.gatling.javaapi.core.CoreDsl.*;
              import static io.gatling.javaapi.http.HttpDsl.*;

              public class CheckoutSimulation extends Simulation {
                  ChainBuilder checkout = exec(exitBlockOnFail(exec(http("cart").get("/cart"))));
              }
              """,
            """
              import io.gatling.javaapi.core.*;

              import static io.gatling.javaapi.core.CoreDsl.*;
              import static io.gatling.javaapi.http.HttpDsl.*;

              public class CheckoutSimulation extends Simulation {
                  ChainBuilder checkout = exec(exitBlockOnFail().on(exec(http("cart").get("/cart"))));
              }
              """
          )
        );
    }

    @Test
    void qualifiedStaticMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import io.gatling.javaapi.core.ChainBuilder;
              import io.gatling.javaapi.core.CoreDsl;

              class Chains {
                  ChainBuilder guarded(ChainBuilder chain) {
                      return CoreDsl.exitBlockOnFail(chain);
                  }
              }
              """,
            """
              import io.gatling.javaapi.core.ChainBuilder;
              import io.gatling.javaapi.core.CoreDsl;

              class Chains {
                  ChainBuilder guarded(ChainBuilder chain) {
                      return CoreDsl.exitBlockOnFail().on(chain);
                  }
              }
              """
          )
        );
    }

    @Test
    void instanceMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import io.gatling.javaapi.core.*;

              import static io.gatling.javaapi.core.CoreDsl.*;

              public class CheckoutSimulation extends Simulation {
                  ChainBuilder browse = exec(pause(1));
                  ScenarioBuilder scn = scenario("Checkout").exitBlockOnFail(browse);
              }
              """,
            """
              import io.gatling.javaapi.core.*;

              import static io.gatling.javaapi.core.CoreDsl.*;

              public class CheckoutSimulation extends Simulation {
                  ChainBuilder browse = exec(pause(1));
                  ScenarioBuilder scn = scenario("Checkout").exitBlockOnFail().on(browse);
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

              class CheckoutSimulation : Simulation() {
                  val browse = exec(pause(1))
                  val checkout = exec(exitBlockOnFail(browse))
                  val scn = scenario("Checkout").exitBlockOnFail(browse)
              }
              """,
            """
              import io.gatling.javaapi.core.*
              import io.gatling.javaapi.core.CoreDsl.*

              class CheckoutSimulation : Simulation() {
                  val browse = exec(pause(1))
                  val checkout = exec(exitBlockOnFail().on(browse))
                  val scn = scenario("Checkout").exitBlockOnFail().on(browse)
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
