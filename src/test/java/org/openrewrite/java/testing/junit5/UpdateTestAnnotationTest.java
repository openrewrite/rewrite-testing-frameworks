/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UpdateTestAnnotationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit"))
          .recipe(new UpdateTestAnnotation());
    }

    @Test
    void expectedNoneToAssertDoesNotThrow() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              
              public class MyTest {
              
                  @Test(expected = Test.None.class)
                  public void test_printLine() {
                      int arr = new int[]{0}[0];
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
              
              public class MyTest {
              
                  @Test
                  void test_printLine() {
                      assertDoesNotThrow(() -> {
                          int arr = new int[]{0}[0];
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void assertThrowsSingleLine() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              
              public class MyTest {
              
                  @Test(expected = IllegalArgumentException.class)
                  public void test() {
                      throw new IllegalArgumentException("boom");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertThrows;
              
              public class MyTest {
              
                  @Test
                  void test() {
                      assertThrows(IllegalArgumentException.class, () -> {
                          throw new IllegalArgumentException("boom");
                      });
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void assertThrowsSingleStatement() {
        //language=java
        rewriteRun(
          java(
            """
                  import org.junit.Test;
                  
                  public class MyTest {
                  
                      @Test(expected = IndexOutOfBoundsException.class)
                      public void test() {
                          int arr = new int[]{}[0];
                      }
                  }
                  """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertThrows;
              
              public class MyTest {
              
                  @Test
                  void test() {
                      assertThrows(IndexOutOfBoundsException.class, () -> {
                          int arr = new int[]{}[0];
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void assertThrowsMultiLine() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              
              public class MyTest {
              
                  @Test(expected = IllegalArgumentException.class)
                  public void test() {
                      String foo = "foo";
                      throw new IllegalArgumentException("boom");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertThrows;
              
              public class MyTest {
              
                  @Test
                  void test() {
                      assertThrows(IllegalArgumentException.class, () -> {
                          String foo = "foo";
                          throw new IllegalArgumentException("boom");
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void noTestAnnotationValues() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              
              public class MyTest {
              
                  @Test
                  public void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              public class MyTest {
              
                  @Test
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void preservesComments() {
        //language=java
        rewriteRun(
          java(
            """
              package org.openrewrite;
              public @interface Issue {
                  String value();
              }
              """
          ),
          java(
            """
              import org.junit.Test;
              import org.openrewrite.Issue;
              
              public class MyTest {
              
                  // some comments
                  @Issue("some issue")
                  @Test
                  public void test() { }
                  
                  // some comments
                  @Test
                  public void test1() { }
                  
                  @Test
                  // some comments
                  public void test2() { }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.Issue;
              
              public class MyTest {
              
                  // some comments
                  @Issue("some issue")
                  @Test
                  void test() { }
              
                  // some comments
                  @Test
                  void test1() { }
              
                  // some comments
                  @Test
                  void test2() { }
              }
              """
          )
        );
    }

    @Test
    void testAnnotationWithTimeout() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              
              public class MyTest {
              
                  @Test(timeout = 500)
                  public void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.Timeout;
              
              public class MyTest {
              
                  @Test
                  @Timeout(500)
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void testAnnotationWithImportedException() {
        //language=java
        rewriteRun(
          java(
            """
              package com.abc;
              public class MyException extends Exception {
                    public MyException(String message) {
                        super(message);
                    }
              }
              """
          ),
          java(
            """
              import com.abc.MyException;
              import org.junit.Test;
              
              public class MyTest {
              
                  @Test(expected = MyException.class)
                  public void test() {
                      throw new MyException("my exception");
                  }
              }
              """,
            """
              import com.abc.MyException;
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertThrows;
              
              public class MyTest {
              
                  @Test
                  void test() {
                      assertThrows(MyException.class, () -> {
                          throw new MyException("my exception");
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void testAnnotationWithTimeoutAndException() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              
              public class MyTest {
              
                  @Test(expected = IllegalArgumentException.class, timeout = 500)
                  public void test() {
                      throw new IllegalArgumentException("boom");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.Timeout;
              
              import static org.junit.jupiter.api.Assertions.assertThrows;
              
              public class MyTest {
              
                  @Test
                  @Timeout(500)
                  void test() {
                      assertThrows(IllegalArgumentException.class, () -> {
                          throw new IllegalArgumentException("boom");
                      });
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/150")
    @Test
    void protectedToPackageVisibility() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;

              public class MyTest {
              
                  @Test
                  protected void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              public class MyTest {

                  @Test
                  void test() {
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("JUnitMalformedDeclaration")
    @Test
    void privateToPackageVisibility() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              
              public class MyTest {
              
                  @Test
                  private void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              public class MyTest {
              
                  @Test
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void preservesVisibilityOnTestMethodThatIsAnOverride() {
        //language=java
        rewriteRun(
          java(
            """
              package com.test;
              
              public interface Foo {
                  void foo();
              }
              """
          ),
          java(
            """
              package com.test;
              
              import org.junit.Test;
              
              public class FooTest implements Foo {
                  
                  @Test
                  public void foo() {
                  }
              }
              """,
            """
              package com.test;
              
              import org.junit.jupiter.api.Test;
              
              public class FooTest implements Foo {
                  
                  @Test
                  public void foo() {
                  }
              }
              """
          )
        );
    }


    @Test
    void migrateDotClass() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              public class MyTest {
                  Object o = Test.class;
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              public class MyTest {
                  Object o = Test.class;
              }
              """
          )
        );
    }

    @Test
    void usedInJavadoc() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              
              /** @see org.junit.Test */
              public class MyTest {
                  @Test
                  public void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              /** @see org.junit.jupiter.api.Test */
              public class MyTest {
                  @Test
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void fullyQualified() {
        rewriteRun(
          //language=java
          java(
            """
              public class MyTest {
                  @org.junit.Test
                  public void feature1() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              public class MyTest {
                  @org.junit.jupiter.api.Test
                  void feature1() {
                  }
              }
              """
          )
        );
    }

    @Test
    void mixedFullyQualifiedAndNot() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Test;
              public class MyTest {
                  @org.junit.Test
                  public void feature1() {
                  }
                  
                  @Test
                  void feature2() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
                            
              public class MyTest {
                  @org.junit.jupiter.api.Test
                  void feature1() {
                  }
                  
                  @Test
                  void feature2() {
                  }
              }
              """
          )
        );
    }
}
