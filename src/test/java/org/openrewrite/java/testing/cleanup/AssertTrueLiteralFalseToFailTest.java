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
package org.openrewrite.java.testing.cleanup;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AssertTrueLiteralFalseToFailTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9"))
          .recipe(new AssertLiteralBooleanToFailRecipe());
    }

    @Test
    @DocumentExample
    void assertTrueToFail() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertTrue;
                            
              public class Test {
                  void test() {
                      assertTrue(false, "message");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions;
                            
              public class Test {
                  void test() {
                      Assertions.fail("message");
                  }
              }
              """
          )
        );
    }

    @Test
    void assertTrueToFailNonStatic() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Assertions;
                            
              public class Test {
                  void test() {
                      Assertions.assertTrue(false, "message");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions;
                            
              public class Test {
                  void test() {
                      Assertions.fail("message");
                  }
              }
              """
          )
        );
    }

    @Test
    void assertTrueNonLiteralNoChange() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertTrue;
                            
              public class Test {
                  void test() {
                      String a = "a";
                      String b = "b";
                      assertTrue(a.equals(b), "message");
                  }
              }
              """
          )
        );
    }
}