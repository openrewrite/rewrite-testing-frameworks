package org.openrewrite.java.testing.hamcrest;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class HamcrestArrayMatcherToAssertJTest implements RewriteTest {
    public void defaults(RecipeSpec spec) {
        spec.recipe(new HamcrestArrayMatcherToAssertJ());
    }

    @Test
    void replaceArrayWithContainsExactly() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.array;
              import static org.hamcrest.Matchers.equalTo;
              
              public class MyTest {
                  public static void main(String[] args) {
                      String[] fruits = { "Apple", "Orange", "Banana" };
              
                      assertThat(fruits, array(equalTo("Apple"), equalTo("Orange"), equalTo("Banana")));
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;
              
              public class MyTest {
                  public static void main(String[] args) {
                      String[] fruits = { "Apple", "Orange", "Banana" };
              
                      Assertions.assertThat(fruits).containsExactly("Apple", "Orange", "Banana");
                  }
              }
              """
          )
        );
    }
}
