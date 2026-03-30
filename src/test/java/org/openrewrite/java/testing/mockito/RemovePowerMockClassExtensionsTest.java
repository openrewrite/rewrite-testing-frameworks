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

class RemovePowerMockClassExtensionsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "powermock-core-1"
            ))
          .recipe(new RemovePowerMockClassExtensions());
    }

    @DocumentExample
    @Test
    void extensionOfPowerMockTestCaseGetsRemoved() {
        //language=java
        rewriteRun(java(
            """
              package org.powermock.modules.testng;

              public class PowerMockTestCase {}
              """
          ),
          java(
            """
              import org.powermock.modules.testng.PowerMockTestCase;

              public class MyPowerMockTestCase extends PowerMockTestCase {}
              """,
            """
              public class MyPowerMockTestCase {}
              """
          )
        );
    }

    @Test
    void extensionOfPowerMockConfigurationGetsRemoved() {
        //language=java
        rewriteRun(
          java(
            """
              package org.powermock.configuration;

              public class PowerMockConfiguration {}
              """
          ),
          java(
            """
              import org.powermock.configuration.PowerMockConfiguration;

              public class MyPowerMockConfiguration extends PowerMockConfiguration {}
              """,
            """
              public class MyPowerMockConfiguration {}
              """
          ));
    }
}
