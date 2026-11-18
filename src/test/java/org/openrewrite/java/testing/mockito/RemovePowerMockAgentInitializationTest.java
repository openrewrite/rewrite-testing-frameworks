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
package org.openrewrite.java.testing.mockito;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemovePowerMockAgentInitializationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .dependsOn(
              //language=java
              """
                package org.powermock.modules.agent;
                public class PowerMockAgent {
                    public static void initializeIfNeeded() {
                    }
                }
                """
            )
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4"))
          .recipe(new RemovePowerMockAgentInitialization());
    }

    @DocumentExample
    @Test
    void removesStaticInitializerThatOnlyStartsTheAgent() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              import org.powermock.modules.agent.PowerMockAgent;

              public class TranslationKeyTest {

                  static {
                      PowerMockAgent.initializeIfNeeded();
                  }

                  private String name;

                  @Test
                  public void test() {
                  }
              }
              """,
            """
              import org.junit.Test;

              public class TranslationKeyTest {

                  private String name;

                  @Test
                  public void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsRestOfStaticInitializer() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.ArrayList;
              import java.util.List;
              import org.powermock.modules.agent.PowerMockAgent;

              public class MyTest {
                  static final List<String> NAMES = new ArrayList<>();

                  static {
                      PowerMockAgent.initializeIfNeeded();
                      NAMES.add("a");
                  }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.List;

              public class MyTest {
                  static final List<String> NAMES = new ArrayList<>();

                  static {
                      NAMES.add("a");
                  }
              }
              """
          )
        );
    }

    @Test
    void leavesAnAlreadyEmptyStaticInitializer() {
        //language=java
        rewriteRun(
          java(
            """
              import org.powermock.modules.agent.PowerMockAgent;

              public class MyTest {
                  static {
                  }

                  void test() {
                      PowerMockAgent.initializeIfNeeded();
                  }
              }
              """,
            """
              public class MyTest {
                  static {
                  }

                  void test() {
                  }
              }
              """
          )
        );
    }
}
