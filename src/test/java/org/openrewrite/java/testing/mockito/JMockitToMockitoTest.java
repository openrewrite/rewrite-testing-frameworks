package org.openrewrite.java.testing.mockito;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class JMockitToMockitoTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-jupiter-api-5.9",
              "jmockit-1.49",
              "mockito-core-3.12"
            ))
          .recipeFromResource(
            "/META-INF/rewrite/mockito.yml",
          "org.openrewrite.java.testing.mockito.JMockitToMockito"
          );
    }

    @Test
    void rewrite() {
        //language=java
        rewriteRun(
          java(
            """                  
              import static org.junit.jupiter.api.Assertions.assertNull;
              
              import mockit.Expectations;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;
              
              @ExtendWith(JMockitExtension.class)
              class MyTest {
                @Mocked
                MyObject myObject;
              
                void test() {
                  new Expectations() {{
                    myObject.getSomeField();
                    result = null;
                  }};
                  assertNull(myObject.getSomeField());
                }
              }
              """,
            """                  
              import static org.junit.jupiter.api.Assertions.assertNull;
              import static org.mockito.Mockito.when;
              
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
              
              @ExtendWith(MockitoExtension.class)
              class MyTest {
                @Mock
                MyObject myObject;
              
                void test() {
                  when(myObject.getSomeField()).thenReturn(null);
                  assertNull(myObject.getSomeField());
                }
              }
              """
          )
        );
    }
}
