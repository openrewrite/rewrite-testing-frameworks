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

class MigrateJUnit514DeprecationsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .typeValidationOptions(TypeValidation.none())
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing.junit5")
            .build()
            .activateRecipes("org.openrewrite.java.testing.junit5.MigrateJUnit514Deprecations"));
    }

    @DocumentExample
    @Test
    void migrateOutputDirectoryProvider() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.platform.commons.support.OutputDirectoryProvider;

              public class TestClass {
                  private OutputDirectoryProvider provider;

                  public void useProvider(OutputDirectoryProvider p) {
                      this.provider = p;
                  }
              }
              """,
            """
              import org.junit.platform.commons.support.OutputDirectoryCreator;

              public class TestClass {
                  private OutputDirectoryCreator provider;

                  public void useProvider(OutputDirectoryCreator p) {
                      this.provider = p;
                  }
              }
              """
          )
        );
    }

    @Test
    void migratePlatformResource() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.platform.commons.support.Resource;

              public class TestClass {
                  private Resource resource;

                  public Resource getResource() {
                      return resource;
                  }
              }
              """,
            """
              import org.junit.platform.commons.io.Resource;

              public class TestClass {
                  private Resource resource;

                  public Resource getResource() {
                      return resource;
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateJupiterMediaType() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.extension.MediaType;

              public class TestClass {
                  private MediaType mediaType = MediaType.APPLICATION_JSON;

                  public void setMediaType(MediaType type) {
                      this.mediaType = type;
                  }
              }
              """,
            """
              import org.junit.jupiter.api.MediaType;

              public class TestClass {
                  private MediaType mediaType = MediaType.APPLICATION_JSON;

                  public void setMediaType(MediaType type) {
                      this.mediaType = type;
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateParameterInfo() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.params.support.ParameterInfo;

              public class TestClass {
                  private ParameterInfo info;

                  public ParameterInfo getInfo() {
                      return info;
                  }
              }
              """,
            """
              import org.junit.jupiter.params.ParameterInfo;

              public class TestClass {
                  private ParameterInfo info;

                  public ParameterInfo getInfo() {
                      return info;
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateMultipleTypes() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.platform.commons.support.OutputDirectoryProvider;
              import org.junit.platform.commons.support.Resource;
              import org.junit.jupiter.api.extension.MediaType;
              import org.junit.jupiter.params.support.ParameterInfo;

              public class TestClass {
                  private OutputDirectoryProvider provider;
                  private Resource resource;
                  private MediaType mediaType;
                  private ParameterInfo info;
              }
              """,
            """
              import org.junit.platform.commons.support.OutputDirectoryCreator;
              import org.junit.platform.commons.io.Resource;
              import org.junit.jupiter.api.MediaType;
              import org.junit.jupiter.params.ParameterInfo;

              public class TestClass {
                  private OutputDirectoryCreator provider;
                  private Resource resource;
                  private MediaType mediaType;
                  private ParameterInfo info;
              }
              """
          )
        );
    }
}