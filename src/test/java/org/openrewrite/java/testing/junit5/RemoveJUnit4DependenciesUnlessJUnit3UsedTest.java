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
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcTestJava;
import static org.openrewrite.maven.Assertions.pomXml;

class RemoveJUnit4DependenciesUnlessJUnit3UsedTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-4"))
          .recipe(new RemoveJUnit4DependenciesUnlessJUnit3Used());
    }

    private static final String POM_WITH_JUNIT = """
      <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>org.example</groupId>
          <artifactId>project</artifactId>
          <version>0.0.1</version>
          <dependencyManagement>
              <dependencies>
                  <dependency>
                      <groupId>junit</groupId>
                      <artifactId>junit</artifactId>
                      <version>4.13.2</version>
                  </dependency>
              </dependencies>
          </dependencyManagement>
          <dependencies>
              <dependency>
                  <groupId>junit</groupId>
                  <artifactId>junit</artifactId>
                  <scope>test</scope>
              </dependency>
          </dependencies>
      </project>
      """;

    @Test
    void keptWhileATestSuiteIsUsed() {
        rewriteRun(
          mavenProject("project",
            //language=java
            srcTestJava(java(
              """
                import junit.framework.Test;
                import junit.framework.TestSuite;

                class AllTests {
                    static Test suite() {
                        return new TestSuite();
                    }
                }
                """
            )),
            pomXml(POM_WITH_JUNIT, spec -> spec.after(pom -> assertThat(pom)
              .contains("<artifactId>junit</artifactId>")
              .contains("<artifactId>junit-vintage-engine</artifactId>").actual()))
          )
        );
    }

    @Test
    void removedOtherwise() {
        rewriteRun(
          //language=java
          srcTestJava(java(
            """
              class Plain {
              }
              """
          )),
          pomXml(POM_WITH_JUNIT, """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>org.example</groupId>
                <artifactId>project</artifactId>
                <version>0.0.1</version>
            </project>
            """)
        );
    }
}
