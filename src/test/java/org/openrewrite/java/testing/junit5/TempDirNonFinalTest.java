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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class TempDirNonFinalTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5"))
          .recipe(new TempDirNonFinal());
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/241")
    @Test
    void tempDirStaticFinalFile() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.io.TempDir;

              import java.io.File;

              class MyTest {
                  @TempDir
                  static final File tempDir;
              }
              """,
            """
              import org.junit.jupiter.api.io.TempDir;

              import java.io.File;

              class MyTest {
                  @TempDir
                  static File tempDir;
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/241")
    @Test
    void tempDirStaticFinalPath() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.io.TempDir;

              import java.nio.file.Path;

              class MyTest {
                  @TempDir
                  static final Path tempDir;
              }
              """,
            """
              import org.junit.jupiter.api.io.TempDir;

              import java.nio.file.Path;

              class MyTest {
                  @TempDir
                  static Path tempDir;
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/241, https://github.com/openrewrite/rewrite-testing-frameworks/issues/483")
    @Test
    void tempDirFileParameter() {
        //language=java
        rewriteRun(
          java(
            """
              import java.io.File;
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.io.TempDir;

              import java.nio.file.Path;

              class MyTest {
                  @Test
                  void fileTest(@TempDir final File tempDir) {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/241")
    @Test
    void tempDirStaticFile() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.io.TempDir;

              import java.io.File;

              class MyTest {
                  @TempDir
                  static File tempDir;
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/241")
    @Test
    void tempDirStaticPath() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.io.TempDir;

              import java.nio.file.Path;

              class MyTest {
                  @TempDir
                  static Path tempDir;
              }
              """
          )
        );
    }
}
