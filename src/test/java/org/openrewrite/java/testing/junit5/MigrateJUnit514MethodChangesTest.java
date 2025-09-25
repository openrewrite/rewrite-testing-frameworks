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

class MigrateJUnit514MethodChangesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .typeValidationOptions(TypeValidation.none())
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing.junit5")
            .build()
            .activateRecipes("org.openrewrite.java.testing.junit5.MigrateJUnit514MethodChanges"));
    }

    @DocumentExample
    @Test
    void migrateSelectClasspathResource() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.platform.engine.discovery.DiscoverySelectors;
              import java.util.Set;

              public class TestClass {
                  public void selectResource() {
                      Set<String> paths = Set.of("/test.xml");
                      DiscoverySelectors.selectClasspathResource(paths);
                  }
              }
              """,
            """
              import org.junit.platform.engine.discovery.DiscoverySelectors;
              import java.util.Set;

              public class TestClass {
                  public void selectResource() {
                      Set<String> paths = Set.of("/test.xml");
                      DiscoverySelectors.selectClasspathResourceByName(paths);
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateReflectionSupportToResourceSupport() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.platform.commons.support.ReflectionSupport;

              public class TestClass {
                  public void findRoots() {
                      var roots = ReflectionSupport.findAllClasspathRootDirectories();
                  }
              }
              """,
            """
              import org.junit.platform.commons.support.ResourceSupport;

              public class TestClass {
                  public void findRoots() {
                      var roots = ResourceSupport.findClasspathRootDirectories();
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateStaticImportOfSelectClasspathResource() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Set;
              import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathResource;

              public class TestClass {
                  public void selectResource() {
                      Set<String> paths = Set.of("/test.xml");
                      selectClasspathResource(paths);
                  }
              }
              """,
            """
              import java.util.Set;
              import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathResourceByName;

              public class TestClass {
                  public void selectResource() {
                      Set<String> paths = Set.of("/test.xml");
                      selectClasspathResourceByName(paths);
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateStaticImportOfReflectionSupport() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.junit.platform.commons.support.ReflectionSupport.findAllClasspathRootDirectories;

              public class TestClass {
                  public void findRoots() {
                      var roots = findAllClasspathRootDirectories();
                  }
              }
              """,
            """
              import static org.junit.platform.commons.support.ResourceSupport.findClasspathRootDirectories;

              public class TestClass {
                  public void findRoots() {
                      var roots = findClasspathRootDirectories();
                  }
              }
              """
          )
        );
    }
}
