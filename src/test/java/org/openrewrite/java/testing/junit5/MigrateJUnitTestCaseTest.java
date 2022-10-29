package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateJUnitTestCaseTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit", "hamcrest"))
          .recipe(new MigrateJUnitTestCase());
    }

    @Test
    void convertTestCase() {
        //language=java
        rewriteRun(
          java(
            """
              import junit.framework.TestCase;
                            
              public class MathTest extends TestCase {
                  protected long value1;
                  protected long value2;
                            
                  @Override
                  protected void setUp() {
                      super.setUp();
                      value1 = 2;
                      value2 = 3;
                  }
                            
                  public void testAdd() {
                      setName("primitive test");
                      long result = value1 + value2;
                      assertEquals(5, result);
                      fail("some Failure message");
                  }
                            
                  @Override
                  protected void tearDown() {
                      super.tearDown();
                      value1 = 0;
                      value2 = 0;
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;
                            
              import static org.junit.jupiter.api.Assertions.*;

              public class MathTest {
                  protected long value1;
                  protected long value2;
                            
                  @BeforeEach
                  public void setUp() {
                      value1 = 2;
                      value2 = 3;
                  }
                            
                  @Test
                  public void testAdd() {
                      //setName("primitive test");
                      long result = value1 + value2;
                      assertEquals(5, result);
                      fail("some Failure message");
                  }
                            
                  @AfterEach
                  public void tearDown() {
                      value1 = 0;
                      value2 = 0;
                  }
              }
              """
          )
        );
    }

    @Test
    void convertExtendedTestCase() {
        //language=java
        rewriteRun(
          java(
            """
              package com.abc;
              import junit.framework.TestCase;
              public abstract class CTest extends TestCase {
                  @Override
                  public void setUp() {}
                  
                  @Override
                  public void tearDown() {}
              }
              """,
            """
              package com.abc;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
                            
              public abstract class CTest {
                  @BeforeEach
                  public void setUp() {}
                            
                  @AfterEach
                  public void tearDown() {}
              }
              """
          ),
          java(
            """
              package com.abc;
              import com.abc.CTest;
              import static org.junit.Assert.assertEquals;
              public class MathTest extends CTest {
                  protected long value1;
                  protected long value2;
                            
                  @Override
                  protected void setUp() {
                      value1 = 2;
                      value2 = 3;
                  }
                            
                  public void testAdd() {
                      long result = value1 + value2;
                      assertEquals(5, result);
                  }
                            
                  @Override
                  protected void tearDown() {
                      value1 = 0;
                      value2 = 0;
                  }
              }
              """,
            """
              package com.abc;
              import com.abc.CTest;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;
                            
              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class MathTest extends CTest {
                  protected long value1;
                  protected long value2;
                            
                  @BeforeEach
                  public void setUp() {
                      value1 = 2;
                      value2 = 3;
                  }
                            
                  @Test
                  public void testAdd() {
                      long result = value1 + value2;
                      assertEquals(5, result);
                  }
                            
                  @AfterEach
                  public void tearDown() {
                      value1 = 0;
                      value2 = 0;
                  }
              }
              """
          )
        );
    }

    @Test
    void notTestCaseHasTestCaseAssertion() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
                            
              import static junit.framework.TestCase.assertTrue;
                            
              class AaTest {
                  @Test
                  public void someTest() {
                      assertTrue("assert message", isSameStuff("stuff"));
                  }
                  private boolean isSameStuff(String stuff) {
                      return "stuff".equals(stuff);
                  }
              }
              """,
            """
              import org.junit.Test;
                            
              import static org.junit.jupiter.api.Assertions.assertTrue;
                            
              class AaTest {
                  @Test
                  public void someTest() {
                      assertTrue(isSameStuff("stuff"), "assert message");
                  }
                  private boolean isSameStuff(String stuff) {
                      return "stuff".equals(stuff);
                  }
              }
              """
          )
        );
    }

    @Test
    void notTestCaseHasAssertAssertion() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
                            
              import static junit.framework.Assert.assertTrue;
                            
              class AaTest {
                  @Test
                  public void someTest() {
                      assertTrue("assert message", isSameStuff("stuff"));
                  }
                  private boolean isSameStuff(String stuff) {
                      return "stuff".equals(stuff);
                  }
              }
              """,
            """
              import org.junit.Test;
                            
              import static org.junit.jupiter.api.Assertions.assertTrue;
                            
              class AaTest {
                  @Test
                  public void someTest() {
                      assertTrue(isSameStuff("stuff"), "assert message");
                  }
                  private boolean isSameStuff(String stuff) {
                      return "stuff".equals(stuff);
                  }
              }
              """
          )
        );
    }

    @Test
    void notTestCase() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              class AaTest {
                  @Test(expected = NumberFormatException.class)
                  public void testSomeNumberStuff() {
                      Double n = Double.valueOf("a");
                  }
              }
              """
          )
        );
    }
}
