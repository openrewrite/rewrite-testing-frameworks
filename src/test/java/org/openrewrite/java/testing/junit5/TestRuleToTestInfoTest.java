/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("OptionalIsPresent")
class TestRuleToTestInfoTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4"))
          .recipe(new TestRuleToTestInfo());
    }

    @DocumentExample
    @Test
    void ruleToTestInfo() {
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
    void ruleHasBeforeMethodToTestInfo() {
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

    @Test
    void ruleIsFinal() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.rules.TestName;

              public class SomeTest {
                  @Rule
                  public final TestName name = new TestName();
                  protected String randomName() {
                      return name.getMethodName();
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
    void onlyRenameBeforeOnMethodThatGetsTestInfo() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Before;
              import org.junit.Rule;
              import org.junit.rules.TestName;

              public class SomeTest {
                  @Rule
                  public TestName name = new TestName();
                  protected String randomName() {
                      return name.getMethodName();
                  }

                  static class Other {
                      int count;

                      @Before
                      public void setup() {
                          count++;
                      }
                  }
              }
              """,
            """
              import org.junit.Before;
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

                  static class Other {
                      int count;

                      @Before
                      public void setup() {
                          count++;
                      }
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
    void keepExistingBeforeEachParameters() {
        //language=java
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "junit-jupiter-api-5")),
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.RepetitionInfo;
              import org.junit.rules.TestName;

              public class SomeTest {
                  @Rule
                  public TestName name = new TestName();
                  protected String randomName() {
                      return name.getMethodName();
                  }

                  @BeforeEach
                  public void setup(RepetitionInfo repetitionInfo) {
                      System.out.println(repetitionInfo.getCurrentRepetition());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.RepetitionInfo;
              import org.junit.jupiter.api.TestInfo;

              import java.lang.reflect.Method;
              import java.util.Optional;

              public class SomeTest {
                 \s
                  public String name;
                  protected String randomName() {
                      return name;
                  }

                  @BeforeEach
                  public void setup(RepetitionInfo repetitionInfo, TestInfo testInfo) {
                      Optional<Method> testMethod = testInfo.getTestMethod();
                      if (testMethod.isPresent()) {
                          this.name = testMethod.get().getName();
                      }
                      System.out.println(repetitionInfo.getCurrentRepetition());
                  }
              }
              """
          )
        );
    }

    @Test
    void keepPrefixOfSelect() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.rules.TestName;

              public class SomeTest {
                  @Rule
                  public TestName name = new TestName();
                  void log(String s) {
                  }
                  void test() {
                      log(name.getMethodName());
                      String s =name.getMethodName();
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
                  void log(String s) {
                  }
                  void test() {
                      log(name);
                      String s =name;
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
}
