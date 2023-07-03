/*
 * Copyright 2023 the original author or authors.
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
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"NumericOverflow", "divzero"})
public class RemoveTryCatchBlocksFromUnitTestsTest implements RewriteTest {
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
                                    "junit-jupiter-api-5.9",
                                    "junit-jupiter-params-5.9",
                                    "junit"))
          .recipe(new RemoveTryCatchBlocksFromUnitTests());
    }

    @Test
    @DocumentExample
    void removeTryCatchBlock() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.Assert;
              import org.junit.jupiter.api.Test;
              
              class Test {
                  @Test
                  public void testMethod() {
                      try {
                          int divide = 50/0;
                      }catch (ArithmeticException e) {
                          Assert.fail(e.getMessage());
                      }
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions;
              import org.junit.jupiter.api.Test;
              
              class Test {
                  @Test
                  public void testMethod() {
                      try {
                          int divide = 50/0;
                      }catch (ArithmeticException e) {
                          Assertions.assertDoesNotThrow(e);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void onlyAffectsUnitTests() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.Assert;
              
              class Test {
                  public void method() {
                      try {
                          int divide = 50/0;
                      }catch (ArithmeticException e) {
                          Assert.fail(e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }
}
