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
import org.openrewrite.DocumentExample;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeToJUnit514Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .typeValidationOptions(TypeValidation.none())
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing.junit5")
            .build()
            .activateRecipes("org.openrewrite.java.testing.junit5.UpgradeToJUnit514"));
    }

    @DocumentExample
    @Test
    void upgradeJUnitDependenciesAndMigrateCode() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test-project</artifactId>
                <version>1.0.0</version>
                <dependencies>
                  <dependency>
                    <groupId>org.junit.jupiter</groupId>
                    <artifactId>junit-jupiter-engine</artifactId>
                    <version>5.10.0</version>
                    <scope>test</scope>
                  </dependency>
                  <dependency>
                    <groupId>org.junit.platform</groupId>
                    <artifactId>junit-platform-commons</artifactId>
                    <version>1.10.0</version>
                    <scope>test</scope>
                  </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test-project</artifactId>
                <version>1.0.0</version>
                <dependencies>
                  <dependency>
                    <groupId>org.junit.jupiter</groupId>
                    <artifactId>junit-jupiter-engine</artifactId>
                    <version>5.14.0</version>
                    <scope>test</scope>
                  </dependency>
                  <dependency>
                    <groupId>org.junit.platform</groupId>
                    <artifactId>junit-platform-commons</artifactId>
                    <version>1.14.0</version>
                    <scope>test</scope>
                  </dependency>
                </dependencies>
              </project>
              """
          ),
          //language=java
          java(
            """
              import org.junit.platform.commons.support.OutputDirectoryProvider;
              import org.junit.jupiter.api.extension.MediaType;
              import org.junit.jupiter.params.support.ParameterInfo;

              public class TestConfig {
                  private OutputDirectoryProvider provider;
                  private MediaType mediaType = MediaType.APPLICATION_JSON_UTF_8;
                  private ParameterInfo info;
              }
              """,
            """
              import org.junit.platform.commons.support.OutputDirectoryCreator;
              import org.junit.jupiter.api.MediaType;
              import org.junit.jupiter.params.ParameterInfo;

              public class TestConfig {
                  private OutputDirectoryCreator provider;
                  private MediaType mediaType = MediaType.APPLICATION_JSON;
                  private ParameterInfo info;
              }
              """
          )
        );
    }

    @Test
    void upgradeMavenBom() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test-project</artifactId>
                <version>1.0.0</version>
                <dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>org.junit</groupId>
                      <artifactId>junit-bom</artifactId>
                      <version>5.10.0</version>
                      <type>pom</type>
                      <scope>import</scope>
                    </dependency>
                  </dependencies>
                </dependencyManagement>
                <dependencies>
                  <dependency>
                    <groupId>org.junit.jupiter</groupId>
                    <artifactId>junit-jupiter-engine</artifactId>
                    <scope>test</scope>
                  </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test-project</artifactId>
                <version>1.0.0</version>
                <dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>org.junit</groupId>
                      <artifactId>junit-bom</artifactId>
                      <version>5.14.0</version>
                      <type>pom</type>
                      <scope>import</scope>
                    </dependency>
                  </dependencies>
                </dependencyManagement>
                <dependencies>
                  <dependency>
                    <groupId>org.junit.jupiter</groupId>
                    <artifactId>junit-jupiter-engine</artifactId>
                    <scope>test</scope>
                  </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }
}