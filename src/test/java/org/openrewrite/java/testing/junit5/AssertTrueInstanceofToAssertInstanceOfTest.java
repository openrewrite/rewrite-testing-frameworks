package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class AssertTrueInstanceofToAssertInstanceOfTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9", "junit-4.13"))
          .recipe(new AssertTrueInstanceofToAssertInstanceOf());
    }

    @Test
    void testJUnit5() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;
              
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertTrue(list instanceof Iterable);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;
              
              import static org.junit.jupiter.api.Assertions.assertInstanceOf;
              
              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertInstanceOf(Iterable.class, list);
                  }
              }
              """
          ));
    }

    @Test
    void testJUnit5WithReason() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;
              
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertTrue(list instanceof Iterable, "Not instance of Iterable");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;
              
              import static org.junit.jupiter.api.Assertions.assertInstanceOf;
              
              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertInstanceOf(Iterable.class, list, "Not instance of Iterable");
                  }
              }
              """
          ));
    }

    @Test
    void testJUnit4() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;
              
              import static org.junit.Assert.assertTrue;
              
              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertTrue(list instanceof Iterable);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;
              
              import static org.junit.jupiter.api.Assertions.assertInstanceOf;
              
              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertInstanceOf(Iterable.class, list);
                  }
              }
              """
          ));
    }

    @Test
    void testJUnit4WithReason() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;
              
              import static org.junit.Assert.assertTrue;
              
              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertTrue("Not instance of Iterable", list instanceof Iterable);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;
              
              import static org.junit.jupiter.api.Assertions.assertInstanceOf;
              
              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertInstanceOf(Iterable.class, list, "Not instance of Iterable");
                  }
              }
              """
          ));
    }
}
