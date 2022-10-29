package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class TempDirNonFinalTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit"))
          .recipe(new TempDirNonFinal());
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/241")
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

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/241")
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

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/241")
    void tempDirFileParameter() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.io.TempDir;
              
              import java.nio.file.Path;
              
              class MyTest {
                  @Test
                  void fileTest(@TempDir File tempDir) {
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/241")
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

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/241")
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
