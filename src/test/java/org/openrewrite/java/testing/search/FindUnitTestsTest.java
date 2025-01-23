package org.openrewrite.java.testing.search;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class FindUnitTestsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindUnitTests());
    }

    @Test
    void findUnitTests() {
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
                 void test() {
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
              """));
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

                 void notAT_es_t(){
                     List<String> list = new ArrayList<>();
                     list.add("Good");
                     list.add("Bye");
                 }
              }
              """));
    }

//    @Test
//    void emptyTestWithComments() {
//        //language=java
//        rewriteRun(
//          java(
//            """
//              import org.junit.Test;
//              class MyTest {
//                  @Test
//                  public void method() {
//                      // comment
//                  }
//              }
//              """
//          )
//        );
//    }

}
