package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class EnclosedToNestedTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit"))
          .recipe(new EnclosedToNested());
    }

    @Test
    void oneInnerClass() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              import org.junit.experimental.runners.Enclosed;
              import org.junit.runner.RunWith;
              
              @RunWith(Enclosed.class)
              public class RootTest {
                  public static class InnerTest {
                      @Test
                      public void test() {}
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.jupiter.api.Nested;
              
             \s
             \s
              public class RootTest {
                  @Nested
                  public class InnerTest {
                      @Test
                      public void test() {}
                  }
              }
              """
          )
        );
    }

    @Test
    void multipleInnerClasses() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              import org.junit.experimental.runners.Enclosed;
              import org.junit.runner.RunWith;
              
              @RunWith(Enclosed.class)
              public class RootTest {
                  public static class InnerTest {
                      @Test
                      public void test() {}
                  }
                  
                  public static class Inner2Test {
                      @Test
                      public void test() {}
              
                      public static class InnermostTest {
                          @Test
                          public void test() {}
                      }
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.jupiter.api.Nested;
              
             \s
             \s
              public class RootTest {
                  @Nested
                  public class InnerTest {
                      @Test
                      public void test() {}
                  }
              
                  @Nested
                  public class Inner2Test {
                      @Test
                      public void test() {}
              
                      @Nested
                      public class InnermostTest {
                          @Test
                          public void test() {}
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void recognizesTestAnnotationWithArguments() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              import org.junit.experimental.runners.Enclosed;
              import org.junit.runner.RunWith;
              
              @RunWith(Enclosed.class)
              public class RootTest {
                  public static class InnerTest {
                      @Test(timeout = 10)
                      public void test() {}
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.jupiter.api.Nested;
              
              
              
              public class RootTest {
                  @Nested
                  public class InnerTest {
                      @Test(timeout = 10)
                      public void test() {}
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotAnnotateNonTestInnerClasses() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              import org.junit.experimental.runners.Enclosed;
              import org.junit.runner.RunWith;
              
              @RunWith(Enclosed.class)
              public class RootTest {
                  public static class InnerTest {
                      @Test
                      public void test() {}
                  }
                  
                  public static class Foo {
                      public void bar() {}
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.jupiter.api.Nested;
              
              
              
              public class RootTest {
                  @Nested
                  public class InnerTest {
                      @Test
                      public void test() {}
                  }
                  
                  public static class Foo {
                      public void bar() {}
                  }
              }
              """
          )
        );
    }
}
