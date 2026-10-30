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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemovePowerMockRuleTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .dependsOn(
              //language=java
              """
                package org.powermock.modules.junit4.rule;
                public class PowerMockRule implements org.junit.rules.MethodRule {
                    public org.junit.runners.model.Statement apply(org.junit.runners.model.Statement base, org.junit.runners.model.FrameworkMethod method, Object target) {
                        return base;
                    }
                }
                """
            )
            .classpathFromResources(new org.openrewrite.InMemoryExecutionContext(), "junit-4"))
          .recipe(new RemovePowerMockRule());
    }

    @DocumentExample
    @Test
    void removesPowerMockRuleField() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.Test;
              import org.powermock.modules.junit4.rule.PowerMockRule;

              public class MyTest {
                  @Rule
                  public PowerMockRule rule = new PowerMockRule();

                  @Test
                  public void test() {
                  }
              }
              """,
            """
              import org.junit.Test;

              public class MyTest {

                  @Test
                  public void test() {
                  }
              }
              """
          )
        );
    }
}
