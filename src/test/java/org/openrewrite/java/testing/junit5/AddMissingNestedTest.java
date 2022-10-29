package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("JUnit3StyleTestMethodInJUnit4Class")
class AddMissingNestedTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddMissingNested())
          .parser(JavaParser.fromJavaVersion().classpath("junit"));
    }

    @Test
    void oneInnerClass() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
                            
              public class RootTest {
                  public class InnerTest {
                      @Test
                      public void test() {}
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
                            
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
              import org.junit.jupiter.api.Test;
                            
              public class RootTest {
                  public class InnerTest {
                      @Test
                      public void test() {}
                  }
                            
                  public class Inner2Test {
                      @Test
                      public void test() {}
                            
                      public class InnermostTest {
                          @Test
                          public void test() {}
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
              
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
    void doesNotAnnotationNonTestInnerClass() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              
              public class RootTest {
                  public class InnerTest {
                      @Test
                      public void test() {}
                  }
                  
                  public static class Foo {
                      public void bar() {}
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
              
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

    @Test
    void removesStatic() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              
              public class RootTest {
                  public static class InnerTest {
                      @Test
                      public void test() {}
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
              
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
}
