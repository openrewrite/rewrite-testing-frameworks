/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
            "junit-jupiter-api-5"));
    }

    @DocumentExample
    @Test
    void dataTable() {
        rewriteRun(
          spec -> spec.dataTable(FindUnitTestTable.Row.class, rows -> assertThat(rows)
            .extracting(FindUnitTestTable.Row::getFullyQualifiedMethodName)
            .containsExactly("bar", "baz")),
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
