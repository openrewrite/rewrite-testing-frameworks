package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("OptionalIsPresent")
class TestRuleToTestInfoTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit"))
          .recipe(new TestRuleToTestInfo());
    }

    @Test
    void testRuleToTestInfo() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.rules.TestName;

              public class SomeTest {
                  @Rule
                  public TestName name = new TestName();
                  protected String randomName() {
                      return name.getMethodName();
                  }

                  private static class SomeInnerClass {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.TestInfo;
              
              import java.lang.reflect.Method;
              import java.util.Optional;

              public class SomeTest {
                 \s
                  public String name;
                  protected String randomName() {
                      return name;
                  }

                  private static class SomeInnerClass {
                  }

                  @BeforeEach
                  public void setup(TestInfo testInfo) {
                      Optional<Method> testMethod = testInfo.getTestMethod();
                      if (testMethod.isPresent()) {
                          this.name = testMethod.get().getName();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void testRuleHasBeforeMethodToTestInfo() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Before;
              import org.junit.Rule;
              import org.junit.rules.TestName;

              public class SomeTest {
                  protected int count;
                  @Rule
                  public TestName name = new TestName();
                  protected String randomName() {
                      return name.getMethodName();
                  }

                  @Before
                  public void setup() {
                      count++;
                  }

                  private static class SomeInnerClass {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.TestInfo;
              
              import java.lang.reflect.Method;
              import java.util.Optional;

              public class SomeTest {
                  protected int count;
                 \s
                  public String name;
                  protected String randomName() {
                      return name;
                  }

                  @BeforeEach
                  public void setup(TestInfo testInfo) {
                      Optional<Method> testMethod = testInfo.getTestMethod();
                      if (testMethod.isPresent()) {
                          this.name = testMethod.get().getName();
                      }
                      count++;
                  }

                  private static class SomeInnerClass {
                  }
              }
              """
          )
        );
    }
}
