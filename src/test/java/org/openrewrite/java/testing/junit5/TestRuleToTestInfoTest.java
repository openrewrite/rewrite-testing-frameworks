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
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4.13.+"))
          .recipe(new TestRuleToTestInfo());
    }

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
}
