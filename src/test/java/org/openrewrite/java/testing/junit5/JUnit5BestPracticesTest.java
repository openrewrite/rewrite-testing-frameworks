package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class JUnit5BestPracticesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing.junit5")
            .build()
            .activateRecipes("org.openrewrite.java.testing.junit5.JUnit5BestPractices"));
    }

    @Test
    void changeBeforeToBeforeEach() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Before;

              public class Example {
                  @Before
                  public void initialize() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;

              public class Example {
                  @BeforeEach
                  public void initialize() {
                  }
              }
              """
          )
        );
    }

    @Test
    void changeAfterToAfterEach() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.After;

              public class Example {
                  @After
                  public void initialize() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterEach;

              public class Example {
                  @AfterEach
                  public void initialize() {
                  }
              }
              """
          )
        );
    }

    @Test
    void changeBeforeClassToBeforeAll() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.BeforeClass;

              public class Example {
                  @BeforeClass
                  public static void initialize() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeAll;

              public class Example {
                  @BeforeAll
                  public static void initialize() {
                  }
              }
              """
          )
        );
    }

    @Test
    void changeAfterClassToAfterAll() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.AfterClass;

              public class Example {
                  @AfterClass
                  public static void initialize() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterAll;

              public class Example {
                  @AfterAll
                  public static void initialize() {
                  }
              }
              """
          )
        );
    }

    @Test
    void changeIgnoreToDisabled() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.junit.Ignore;

              public class Example {
                  @Ignore @Test public void something() {}
                            
                  @Ignore("not ready yet") @Test public void somethingElse() {}
              }
              """,
            """
              import org.junit.jupiter.api.Disabled;

              public class Example {
                  @Disabled @Test public void something() {}
                            
                  @Disabled("not ready yet") @Test public void somethingElse() {}
              }
              """
          )
        );
    }
}
