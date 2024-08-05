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

class AssertFalseLiteralTrueToFailTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9"))
          .recipe(new AssertLiteralBooleanToFailRecipe());
    }

    @Test
    @DocumentExample
    void assertFalseToFail() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertFalse;
                            
              public class Test {
                  void test() {
                      assertFalse(true, "message");
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
    void assertFalseToFailNonStatic() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.*;
                            
              public class Test {
                  void test() {
                      Assertions.assertFalse(true, "message");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.*;
                            
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
    void assertFalseNonLiteralNoChange() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.*;
                            
              public class Test {
                  void test() {
                      String a1 = "a";
                      String a2 = "a";
                      assertFalse(a1.equals(a2), "message");
                  }
              }
              """
          )
        );
    }
}