/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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

import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class MockitoJUnitAddMockitoSettingsLenientStrictnessTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "mockito-core", "mockito-junit-jupiter", "junit-jupiter-api", "junit"))
          .recipe(new MockitoJUnitAddMockitoSettingsLenientStrictness());
    }

    @Test
    @DocumentExample
    void shouldAddMockitoSettingsWithLenientStubbing() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
              }
              """,
            """
              import org.mockito.junit.jupiter.MockitoSettings;
              import org.mockito.quality.Strictness;

              @MockitoSettings(strictness = Strictness.LENIENT)
              class MyTest {
              }
              """
          )
        );
    }

    @Test
    void shouldLeaveExisting() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.junit.jupiter.MockitoSettings;
              import org.mockito.quality.Strictness;

              @MockitoSettings(strictness = Strictness.STRICT_STUBS)
              class MyTest {
              }
              """
          )
        );
    }

    @Test
    void shouldRunBeforeMockitoCore2_17() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.java.testing.mockito.MockitoRetainLenientStubbing"),
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://maven.apache.org/POM/4.0.0" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>bla.bla</groupId>
                  <artifactId>bla-bla</artifactId>
                  <version>1.0.0</version>
                  <name>project</name>
                  <dependencies>
                    <dependency>
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-core</artifactId>
                        <version>2.1.0</version>
                        <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """
            ),
            //language=java
            srcMainJava(
              java(
                """
                  import org.junit.jupiter.api.extension.ExtendWith;
                  import org.mockito.junit.jupiter.MockitoExtension;
    
                  @ExtendWith(MockitoExtension.class)
                  class MyTest {
                  }
                  """,
                """
                  import org.mockito.junit.jupiter.MockitoSettings;
                  import org.mockito.quality.Strictness;
    
                  @MockitoSettings(strictness = Strictness.LENIENT)
                  class MyTest {
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void shouldNotRunAfterMockitoCore2_17() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.java.testing.mockito.MockitoRetainLenientStubbing"),
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://maven.apache.org/POM/4.0.0" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>bla.bla</groupId>
                  <artifactId>bla-bla</artifactId>
                  <version>1.0.0</version>
                  <name>project</name>
                  <dependencies>
                    <dependency>
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-core</artifactId>
                        <version>2.17.0</version>
                        <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """
            ),
            //language=java
            srcMainJava(
              java(
                """
                  import org.junit.jupiter.api.extension.ExtendWith;
                  import org.mockito.junit.jupiter.MockitoExtension;
    
                  @ExtendWith(MockitoExtension.class)
                  class MyTest {
                  }
                  """
              )
            )
          )
        );
    }

}
