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
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class PreserveJUnit4ProductionApiTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.testing.junit5.JUnit4to5Migration")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-4"));
    }

    @Test
    void preservesJUnit4RuleLibraryAndItsCompileDependency() {
        rewriteRun(
          mavenProject("adapter",
            srcMainJava(
              java(
                """
                  import org.junit.rules.TestRule;
                  import org.junit.runner.Description;
                  import org.junit.runners.model.Statement;
                  public class Adapter implements TestRule {
                      @Override
                      public Statement apply(Statement base, Description description) {
                          return base;
                      }
                  }
                  """
              )
            ),
            srcTestJava(java("class AdapterTest { @org.junit.Test public void test() {} }")),
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>example</groupId>
                    <artifactId>adapter</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>junit</groupId>
                            <artifactId>junit</artifactId>
                            <version>4.13.2</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }
}
