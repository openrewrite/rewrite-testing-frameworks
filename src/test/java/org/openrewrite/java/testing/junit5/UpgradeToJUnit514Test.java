/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UpgradeToJUnit514Test implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipeFromResources("org.openrewrite.java.testing.junit5.UpgradeToJUnit514");
    }

    @DocumentExample
    @Test
    void migrateOutputDirectoryProvider() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.platform.engine.reporting.OutputDirectoryProvider;

              public class TestClass {
                  private OutputDirectoryProvider provider;

                  public void useProvider(OutputDirectoryProvider p) {
                      this.provider = p;
                  }
              }
              """,
            """
              import org.junit.platform.engine.OutputDirectoryCreator;

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
}
