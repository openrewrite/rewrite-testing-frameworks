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
package org.openrewrite.java.testing.junit;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.javaVersion;
import static org.openrewrite.maven.Assertions.pomXml;

class JUnit6BestPracticesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5"))
          .recipeFromResources("org.openrewrite.java.testing.junit.JUnit6BestPractices");
    }

    @DocumentExample
    @Test
    void junit5ToJunit6() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.MethodOrderer;
              import org.junit.jupiter.api.TestMethodOrder;
              import org.junit.jupiter.api.Test;

              @TestMethodOrder(MethodOrderer.Alphanumeric.class)
              public class MyTest {
                  @Test
                  public void test1() {
                  }

                  @Test
                  public void test2() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.MethodOrderer;
              import org.junit.jupiter.api.TestMethodOrder;
              import org.junit.jupiter.api.Test;

              @TestMethodOrder(MethodOrderer.MethodName.class)
              class MyTest {
                  @Test
                  void test1() {
                  }

                  @Test
                  void test2() {
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
                return assertThat(actual)
                        .containsPattern("<junit-jupiter\\.version>6\\.\\d+\\.\\d+</junit-jupiter\\.version>").actual();
            })
          )
        );
    }

}
