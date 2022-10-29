package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UseTestMethodOrderTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit"))
          .recipe(new UseTestMethodOrder());
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/62")
    @Test
    void nameAscending() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.FixMethodOrder;
              import org.junit.runners.MethodSorters;
              
              @FixMethodOrder(MethodSorters.NAME_ASCENDING)
              class Test {
              }
              """,
            """
              import org.junit.jupiter.api.MethodOrderer.MethodName;
              import org.junit.jupiter.api.TestMethodOrder;
              
              @TestMethodOrder(MethodName.class)
              class Test {
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/62")
    @Test
    void defaultAndOmitted() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.FixMethodOrder;
              import org.junit.runners.MethodSorters;
              
              @FixMethodOrder(MethodSorters.DEFAULT)
              class Test {
              }
              
              @FixMethodOrder
              class Test2 {
              }
              """,
            """
              import org.junit.jupiter.api.MethodOrderer.MethodName;
              import org.junit.jupiter.api.TestMethodOrder;
              
              @TestMethodOrder(MethodName.class)
              class Test {
              }
              
              @TestMethodOrder(MethodName.class)
              class Test2 {
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/80")
    @Test
    void jvmOrder() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.FixMethodOrder;
              import org.junit.runners.MethodSorters;
              
              @FixMethodOrder(MethodSorters.JVM)
              class Test {
              }
              """,
            """
              import org.junit.jupiter.api.MethodOrderer.MethodName;
              import org.junit.jupiter.api.TestMethodOrder;
              
              @TestMethodOrder(MethodName.class)
              class Test {
              }
              """
          )
        );
    }
}
