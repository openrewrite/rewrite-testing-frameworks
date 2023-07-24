package org.openrewrite.java.testing.hamcrest;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class HamcrestAnyOfToAssertJTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "junit-jupiter-api-5.9",
            "hamcrest-2.2",
            "assertj-core-3.24"))
          .recipe(new HamcrestAnyOfToAssertJ());
    }

    @Test
    void anyOfMigrate() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.anyOf;
              import static org.hamcrest.Matchers.equalTo;
              import static org.hamcrest.Matchers.hasLength;
              
              class MyTest {
                  @Test
                  void testMethod() {
                      String str1 = "hello world";
                      String str2 = "hello world";
                      assertThat(str1, anyOf(equalTo(str2), hasLength(12)));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.assertj.core.api.AbstractAssert;
              
              import static org.assertj.core.api.Assertions.assertThat;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.anyOf;
              import static org.hamcrest.Matchers.equalTo;
              import static org.hamcrest.Matchers.hasLength;
              
              class MyTest {
                  @Test
                  void testMethod() {
                      String str1 = "hello world";
                      String str2 = "hello world";
                      assertThat(str1).satisfiesAnyOf(
                              () -> assertThat(str1, equalTo(str2)),
                              () -> assertThat(str1, hasLength(12))
                      );
                  }
              }
              """
          )
        );
    }
}
