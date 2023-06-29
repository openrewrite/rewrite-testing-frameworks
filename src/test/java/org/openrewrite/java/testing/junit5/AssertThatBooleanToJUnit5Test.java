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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class AssertThatBooleanToJUnit5Test implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9", "hamcrest-2.2"))
          .recipe(new AssertThatBooleanToJUnit5());
    }

    @Test
    void assertWithLogicOp() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              
              import static org.hamcrest.MatcherAssert.assertThat;
              
              class Test {
                  @Test
                  void testEquals() {
                      int a = 7;
                      int b = 29;
                      assertThat("Not equal", a == b);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              class Test {
                  @Test
                  void testEquals() {
                      int a = 7;
                      int b = 29;
                      assertTrue(a == b, "Not equal");
                  }
              }
              """
          ));
    }

    @Test
    void assertWithMethodCall() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              
              import static org.hamcrest.MatcherAssert.assertThat;
              
              class Test {
                  @Test
                  void testContains() {
                      String string = "Hello world";
                      assertThat("Does not contain", string.contains("llo wor"));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              class Test {
                  @Test
                  void testContains() {
                      String string = "Hello world";
                      assertTrue(string.contains("llo wor"), "Does not contain");
                  }
              }
              """
          ));
    }
}
