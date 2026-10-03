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
import org.openrewrite.Tree;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class JUnit4MigrationEligibilityTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.testing.junit5.JUnit4to5Migration")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-4"));
    }

    @Test
    void preservesCustomRunnerAndLifecycle() {
        rewriteRun(
          java(
            """
              import org.junit.Before;
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.junit.runners.BlockJUnit4ClassRunner;
              import org.junit.runners.model.InitializationError;

              @RunWith(CustomRunner.class)
              class Example {
                  @Before public void setUp() {}
                  @Test public void test() {}
              }

              class CustomRunner extends BlockJUnit4ClassRunner {
                  CustomRunner(Class<?> type) throws InitializationError {
                      super(type);
                  }
              }
              """
          )
        );
    }
    @Test
    void guardsOnlyTheAffectedModuleIncludingItsPom() {
        JavaProject legacy = new JavaProject(Tree.randomId(), "legacy", null);
        JavaProject modern = new JavaProject(Tree.randomId(), "modern", null);
        rewriteRun(
          java(
            """
              import org.junit.runner.RunWith;
              import org.junit.runners.BlockJUnit4ClassRunner;
              import org.junit.runners.model.InitializationError;
              @RunWith(CustomRunner.class)
              class LegacyTest { @org.junit.Test public void test() {} }
              class CustomRunner extends BlockJUnit4ClassRunner {
                  CustomRunner(Class<?> type) throws InitializationError { super(type); }
              }
              """,
            spec -> spec.markers(legacy)
          ),
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>legacy</artifactId>
                  <version>1</version>
                  <dependencies>
                      <dependency>
                          <groupId>junit</groupId>
                          <artifactId>junit</artifactId>
                          <version>4.13.2</version>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.markers(legacy)
          ),
          java(
            """
              class ModernTest { @org.junit.Test public void test() {} }
              """,
            """
              import org.junit.jupiter.api.Test;

              class ModernTest { @Test public void test() {} }
              """,
            spec -> spec.markers(modern)
          )
        );
    }

}
