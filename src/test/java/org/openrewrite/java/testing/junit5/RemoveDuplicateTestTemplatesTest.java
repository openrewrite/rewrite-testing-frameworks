package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class RemoveDuplicateTestTemplatesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion()
          .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9"))
          .recipe(new RemoveDuplicateTestTemplates());
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/314")
    @Test
    void removeDuplicate() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.RepeatedTest;
              import org.junit.jupiter.api.DisplayName;
              
              class MyTest {
                  @Test
                  @RepeatedTest(3)
                  @DisplayName("When an entry does not exist, it should be created and initialized to 0")
                  void TestMethod() {
                      System.out.println("foobar");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.RepeatedTest;
              import org.junit.jupiter.api.DisplayName;
                            
              class MyTest {
              
                  @RepeatedTest(3)
                  @DisplayName("When an entry does not exist, it should be created and initialized to 0")
                  void TestMethod() {
                      System.out.println("foobar");
                  }
              }
              """
          )
        );
    }

    @Test
    void removesWhenOutOfOrder() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.RepeatedTest;
              import org.junit.jupiter.api.DisplayName;
              
              class MyTest {
                  @DisplayName("When an entry does not exist, it should be created and initialized to 0")
                  @RepeatedTest(3)
                  @Test
                  void TestMethod() {
                      System.out.println("foobar");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.RepeatedTest;
              import org.junit.jupiter.api.DisplayName;
              
              class MyTest {
                  @DisplayName("When an entry does not exist, it should be created and initialized to 0")
                  @RepeatedTest(3)
                  void TestMethod() {
                      System.out.println("foobar");
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotRemoveWithOnlyTest() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              
              class MyTest {
                  @Test
                  void TestMethod() {
                      System.out.println("foobar");
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotRemoveWithOnlyRepeatedTest() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.RepeatedTest;
              
              class MyTest {
                  @RepeatedTest(3)
                  void TestMethod() {
                      System.out.println("foobar");
                  }
              }
              """
          )
        );
    }
}
