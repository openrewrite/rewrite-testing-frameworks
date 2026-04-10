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
package org.openrewrite.java.testing.testcontainers;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class MigrateToOracleFreeTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipeFromResource("/META-INF/rewrite/testcontainers.yml",
            "org.openrewrite.java.testing.testcontainers.MigrateToOracleFree")
          .parser(JavaParser.fromJavaVersion()
            //language=java
            .dependsOn(
              """
                package org.testcontainers.containers;
                public class OracleContainer {
                    public OracleContainer(String image) {}
                }
                """,
              """
                package org.testcontainers.oracle;
                public class OracleContainer {
                    public OracleContainer(String image) {}
                }
                """));
    }

    @DocumentExample
    @Test
    void changeTypeAndImport() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testcontainers.containers.OracleContainer;

              class A {
                  private OracleContainer oracle = null;
              }
              """,
            """
              import org.testcontainers.oracle.OracleContainer;

              class A {
                  private OracleContainer oracle = null;
              }
              """
          )
        );
    }

    @Test
    void changeDependency() {
        rewriteRun(
          pomXml(
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.openrewrite.example</groupId>
                  <artifactId>testcontainers</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.testcontainers</groupId>
                          <artifactId>oracle-xe</artifactId>
                          <version>1.20.6</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.openrewrite.example</groupId>
                  <artifactId>testcontainers</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.testcontainers</groupId>
                          <artifactId>oracle-free</artifactId>
                          <version>1.20.6</version>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }
}
