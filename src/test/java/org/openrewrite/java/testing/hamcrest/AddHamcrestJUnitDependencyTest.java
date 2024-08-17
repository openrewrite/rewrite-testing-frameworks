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
package org.openrewrite.java.testing.hamcrest;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class AddHamcrestJUnitDependencyTest implements RewriteTest {

    @Language("xml")
    private static final String POM_BEFORE = """
      <project>
          <groupId>org.example</groupId>
          <artifactId>project</artifactId>
          <version>1.0-SNAPSHOT</version>
          <dependencies>
              <dependency>
                  <groupId>junit</groupId>
                  <artifactId>junit</artifactId>
                  <version>4.13.2</version>
                  <scope>test</scope>
              </dependency>
          </dependencies>
      </project>
      """;
    @Language("xml")
    private static final String POM_AFTER = """
      <project>
          <groupId>org.example</groupId>
          <artifactId>project</artifactId>
          <version>1.0-SNAPSHOT</version>
          <dependencies>
              <dependency>
                  <groupId>junit</groupId>
                  <artifactId>junit</artifactId>
                  <version>4.13.2</version>
                  <scope>test</scope>
              </dependency>
              <dependency>
                  <groupId>org.hamcrest</groupId>
                  <artifactId>hamcrest-junit</artifactId>
                  <version>2.0.0.0</version>
                  <scope>test</scope>
              </dependency>
          </dependencies>
      </project>
      """;

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddHamcrestJUnitDependency())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "hamcrest",
            "junit-4"));

    }

    @Test
    @DocumentExample
    void shouldAddWhenUsingAssertThat() {
        rewriteRun(
          //language=java
          java(
            """
              class FooTest {
                  void bar() {
                      org.junit.Assert.assertThat("a", org.hamcrest.Matchers.is("a"));
                  }
              }
              """
          ),
          pomXml(POM_BEFORE, POM_AFTER)
        );
    }

    @Test
    void shouldAddWhenUsingAssumeThat() {
        rewriteRun(
          //language=java
          java(
            """
              class FooTest {
                  void bar() {
                      org.junit.Assume.assumeThat("a", org.hamcrest.Matchers.is("a"));
                  }
              }
              """
          ),
          pomXml(POM_BEFORE, POM_AFTER)
        );
    }

    @Test
    void shouldNotAddWhenUsingAssertTrue() {
        rewriteRun(
          //language=java
          java(
            """
              class FooTest {
                  void bar() {
                      org.junit.Assume.assumeTrue(true);
                      org.junit.Assert.assertTrue(true);
                  }
              }
              """
          ),
          pomXml(POM_BEFORE)
        );
    }
}