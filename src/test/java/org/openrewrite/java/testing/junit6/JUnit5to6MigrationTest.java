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
package org.openrewrite.java.testing.junit6;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.javaVersion;
import static org.openrewrite.maven.Assertions.pomXml;

class JUnit5to6MigrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api"))
          .recipeFromResources("org.openrewrite.java.testing.junit6.JUnit5to6Migration");
    }

    @Test
    void upgradesJunitBomViaVersionProperty() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              class FooTest {
                  @Test
                  void bar() {
                  }
              }
              """,
            spec -> spec.markers(javaVersion(17))
          ),
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>example</artifactId>
                  <version>1.0.0</version>
                  <properties>
                      <junit-jupiter.version>5.14.4</junit-jupiter.version>
                  </properties>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>org.junit</groupId>
                              <artifactId>junit-bom</artifactId>
                              <version>${junit-jupiter.version}</version>
                              <type>pom</type>
                              <scope>import</scope>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
              </project>
              """,
            spec -> spec.after(actual -> {
                assertThat(Pattern.compile("<junit-jupiter\\.version>6\\.\\d+\\.\\d+</junit-jupiter\\.version>")
                  .matcher(actual).find())
                  .as("junit-jupiter.version property should be upgraded to 6.x; actual:\n%s", actual)
                  .isTrue();
                return actual;
            })
          )
        );
    }
}
