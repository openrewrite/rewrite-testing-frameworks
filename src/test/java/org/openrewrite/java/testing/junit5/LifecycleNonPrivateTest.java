package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("JUnitMalformedDeclaration")
class LifecycleNonPrivateTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit"))
          .recipe(new LifecycleNonPrivate());
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/241")
    void beforeEachPrivate() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.BeforeEach;
                            
              class MyTest {
                  @BeforeEach
                  private void beforeEach() {
                  }
                  private void unaffected() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;
                            
              class MyTest {
                  @BeforeEach
                  void beforeEach() {
                  }
                  private void unaffected() {
                  }
              }
              """)
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/241")
    void afterAllPrivate() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.AfterAll;
                            
              class MyTest {
                  @AfterAll
                  private static void afterAll() {
                  }
                  private void unaffected() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterAll;
                            
              class MyTest {
                  @AfterAll
                  static void afterAll() {
                  }
                  private void unaffected() {
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/241")
    void beforeEachAfterAllUnchanged() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.AfterAll;
              import org.junit.jupiter.api.BeforeEach;
                          
              class MyTest {
                  @BeforeEach
                  void beforeEach() {
                  }
                  @AfterAll
                  static void afterAll() {
                  }
                  private void unaffected() {
                  }
              }
              """
          )
        );
    }
}
