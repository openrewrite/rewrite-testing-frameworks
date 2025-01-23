package org.openrewrite.java.testing.search;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

class FindUnitTestsTest implements RewriteTest {

    @Language("java")
    private static final String CLASS_FOO = """
      package foo;

      public class Foo {
          public void bar() {
          }
          public void baz() {
          }
      }
      """;

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindUnitTests())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "junit-jupiter-api-5.9"));
    }

    @DocumentExample
    @Test
    void junit5() {
        //language=java
        rewriteRun(
          spec -> spec.dataTable(FindUnitTestTable.Row.class, rows -> assertThat(rows).hasSize(1)),
          java(CLASS_FOO),
          java(
            """
              import foo.Foo;
              import org.junit.jupiter.api.Test;

              public class FooTest {
                 @Test
                 public void test() {
                     new Foo().bar();
                 }
              }
              """
          )
        );
    }

    @Test
    void dataTable() {
        rewriteRun(
          spec -> spec.dataTable(FindUnitTestTable.Row.class, rows -> assertThat(rows).hasSize(2)),
          java(CLASS_FOO),
          //language=java
          java(
            """
              import foo.Foo;
              import org.junit.jupiter.api.Test;

              public class FooTest {
                 @Test
                 public void test() {
                     Foo foo = new Foo();
                     foo.bar();
                     foo.baz();
                 }
              }
              """
          )
        );
    }

    @Nested
    class NotFound {

        @Test
        void notATest() {
            //language=java
            rewriteRun(
              spec -> spec.afterRecipe(run -> assertThat(run.getDataTables()).hasSize(1)), // stats table
              java(CLASS_FOO),
              java(
                """
                  import foo.Foo;

                  public class FooTest {
                     public void test() {
                         new Foo().bar();
                     }
                  }
                  """
              )
            );
        }

        @Test
        void methodFromTest() {
            //language=java
            rewriteRun(
              spec -> spec.afterRecipe(run -> assertThat(run.getDataTables()).hasSize(1)), // stats table
              java(CLASS_FOO),
              java(
                """
                  import org.junit.jupiter.api.Test;

                  public class FooTest {
                     @Test
                     public void test() {
                         beep();
                     }

                     public void beep() {
                     }
                  }
                  """
              )
            );
        }
    }
}
