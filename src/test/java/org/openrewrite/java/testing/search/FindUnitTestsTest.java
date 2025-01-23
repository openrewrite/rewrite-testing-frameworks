package org.openrewrite.java.testing.search;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

class FindUnitTestsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindUnitTests());
    }

    @Test
    void junit4() {
        //language=java
        rewriteRun(
          java(
            """
              package org.openrewrite.test;

              import org.junit.Test;
              import java.util.ArrayList;
              import java.util.List;

              public class MyTest {
                 @Test
                 public void test() {
                     List<String> list = new ArrayList<>();
                     list.add("Hello");
                     list.add("World");
                 }

                 void notAT_es_t(){
                     List<String> list = new ArrayList<>();
                     list.add("Good");
                     list.add("Bye");
                 }
              }
              """
          )
        );
    }

    @Test
    void junit5() {
        //language=java
        rewriteRun(
          spec -> spec.recipe(new FindUnitTests())
            .dataTable(FindUnitTestTable.Row.class, rows -> assertThat(rows).hasSize(1)),
          java(
            """
              import org.junit.jupiter.api.Test;

              class MyTest {
                  @Test
                  void method() {
                      // comment
                      method2();
                  }

                  public void method2() {
                      // comment
                  }
              }
              """
          )
        );
    }

    @Test
    void testng() {
        //language=java
        rewriteRun(
          spec -> spec.recipe(new FindUnitTests())
            .dataTable(FindUnitTestTable.Row.class, rows -> assertThat(rows).hasSize(1)),
          java(
            """
              import org.testng.annotations.Test;

              class MyTest {
                  @Test
                  public void method() {
                      // comment
                      method2();
                  }

                  public void method2() {
                      // comment
                  }
              }
              """));

    }

    @Test
    void dataTable() {
        rewriteRun(
          spec -> spec.recipe(new FindUnitTests())
            .dataTable(FindUnitTestTable.Row.class, rows -> assertThat(rows).hasSize(2)),
          //language=java
          java(
            """
              package org.openrewrite.test;
              import org.junit.Test;
              import java.util.ArrayList;
              import java.util.List;

              public class MyTest {

                 @Test
                 public void test() {
                     String a = "Hello";
                     String b = "World";
                     String c = append(a, b);
                     String d = MyClass.anotherAppend(a, b);

                 }

                 public String append(String a, String b) {
                     return a + b;
                 }

              }
              """
          ),
          //language=java
          java(
            """
              package org.openrewrite.test;

              public static class MyClass {
                  public String anotherAppend(String a, String b) {
                      return a + b;
                  }
              }
              """
          )
        );
    }

    @Test
    void noTest() {
        //language=java
        rewriteRun(
          java(
            """
              package org.openrewrite.a;

              import java.util.ArrayList;
              import java.util.List;

              public class SomeClass {


                 void notATest(){
                     List<String> list = new ArrayList<>();
                     list.add("Good");
                     list.add("Bye");
                 }
              }
              """
          )
        );
    }
}
