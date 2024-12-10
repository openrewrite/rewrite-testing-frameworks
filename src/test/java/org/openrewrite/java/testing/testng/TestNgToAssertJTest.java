/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.testing.testng;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class TestNgToAssertJTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("testng"))
          .recipeFromResources("org.openrewrite.java.testing.assertj.Assertj");
    }

    /**
     * Verify that we can throw new Exception from the JavaTemplate in the generated recipe.
     */
    @DocumentExample
    @Test
    void failWithMessage() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.testng.Assert.fail;

              class Test {
                  void test() {
                      fail("foo");
                      fail("foo", new IllegalStateException());
                      fail();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.fail;

              class Test {
                  void test() {
                      fail("foo");
                      fail("foo", new IllegalStateException());
                      fail();
                  }
              }
              """
          )
        );
    }

    /**
     * Verify some assertions as implemented through Refaster rules converted to Recipes. No need to test all variants.
     */
    @Test
    void assertTrueFalse() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.testng.Assert.assertFalse;
              import static org.testng.Assert.assertTrue;

              class Test {
                  void test() {
                      assertTrue(true);
                      assertTrue(true, "foo");
                      assertFalse(false);
                      assertFalse(false, "foo");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test() {
                      assertThat(true).isTrue();
                      assertThat(true).withFailMessage("foo").isTrue();
                      assertThat(false).isFalse();
                      assertThat(false).withFailMessage("foo").isFalse();
                  }
              }
              """
          )
        );
    }
}
