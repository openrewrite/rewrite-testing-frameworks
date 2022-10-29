package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class CleanupJUnitImportsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit"))
          .recipe(new CleanupJUnitImports());
    }

    @Test
    void removesUnusedImport() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              
              public class MyTest {}
              """,
            """
              public class MyTest {}
              """
          )
        );
    }

    @Test
    void leavesOtherImportsAlone() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Arrays;
              import java.util.Collections;
              import java.util.HashSet;
              
              public class MyTest {
              }
              """
          )
        );
    }

    @Test
    void leavesUsedJUnitImportAlone() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              
              public class MyTest {
                  @Test
                  public void foo() {}
              }
              """
          )
        );
    }
}
