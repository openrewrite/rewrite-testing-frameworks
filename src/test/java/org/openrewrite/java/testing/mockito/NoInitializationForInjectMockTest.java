package org.openrewrite.java.testing.mockito;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class NoInitializationForInjectMockTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "mockito-core-3.12"))
          .recipe(new NoInitializationForInjectMock());
    }

    @Test
    @DocumentExample
    void removeInitializationOfInjectMocks() {
        //language=java
        rewriteRun(
          java(
            """
              class MyObject {
                  private String someField;

                  public MyObject(String someField) {
                      this.someField = someField;
                  }
              }
              """
          ),
          java(
            """
              import org.mockito.InjectMocks;

              class MyTest {
                  @InjectMocks
                  MyObject myObject = new MyObject("someField");
              }
              """,
              """
              import org.mockito.InjectMocks;

              class MyTest {
                  @InjectMocks
                  MyObject myObject;
              }
              """
          )
        );
    }
}
