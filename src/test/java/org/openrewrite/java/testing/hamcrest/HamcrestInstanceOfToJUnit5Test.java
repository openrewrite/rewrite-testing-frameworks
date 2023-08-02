package org.openrewrite.java.testing.hamcrest;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class HamcrestInstanceOfToJUnit5Test implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9", "hamcrest-2.2"))
          .recipe(new HamcrestInstanceOfToJUnit5());
    }


    @Test
    void instanceOf() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.util.List;
              
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.instanceOf;
              import static org.hamcrest.Matchers.isA;
              import static org.hamcrest.Matchers.not;
              
              class ATest {
                  private static final List<Integer> list = List.of();
                  @Test
                  void testInstance() {
                      assertThat(list, instanceOf(Iterable.class));
                      assertThat(list, not(instanceOf(Integer.class)));
                      assertThat(list, isA(Iterable.class));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.util.List;
              
              import static org.junit.jupiter.api.Assertions.assertFalse;
              import static org.junit.jupiter.api.Assertions.assertInstanceOf;
              
              class ATest {
                  private static final List<Integer> list = List.of();
                  @Test
                  void testInstance() {
                      assertInstanceOf(Iterable.class, list);
                      assertFalse(Integer.class.isAssignableFrom(list.getClass()));
                      assertInstanceOf(Iterable.class, list);
                  }
              }
              """
          ));
    }

    @Test
    void assertionsWithReason() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              import org.junit.jupiter.api.Test;
              
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.instanceOf;
              import static org.hamcrest.Matchers.isA;
              import static org.hamcrest.Matchers.not;
              
              class ATest {
                  private static final List<Integer> list = List.of();
                  
                  @Test
                  void testInstance() {
                      assertThat("Examined object is not instance of Iterable", list, instanceOf(Iterable.class));
                      assertThat("Examined object is not instance of Iterable", list, isA(Iterable.class));
                      assertThat("Examined object must not be instance of Integer", list, not(instanceOf(Integer.class)));
                  }
              }
              """,
            """
              import java.util.List;
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertFalse;
              import static org.junit.jupiter.api.Assertions.assertInstanceOf;
              
              class ATest {
                  private static final List<Integer> list = List.of();
                  
                  @Test
                  void testInstance() {
                      assertInstanceOf(Iterable.class, list, "Examined object is not instance of Iterable");
                      assertInstanceOf(Iterable.class, list, "Examined object is not instance of Iterable");
                      assertFalse(Integer.class.isAssignableFrom(list.getClass()), "Examined object must not be instance of Integer");
                  }
              }
              """
          ));
    }
}
