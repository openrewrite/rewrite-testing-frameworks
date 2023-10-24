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
package org.openrewrite.java.testing.cleanup;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AssertNotEqualsBooleanToAssertBooleanTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9"))
          .recipe(new AssertNotEqualsBooleanToAssertBoolean());
    }

    @SuppressWarnings({"ConstantConditions"})
    @Test
    void assertNotEqualsFalseToToAssertTrue() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertNotEquals;
              
              public class Test {
                  void test() {
                      String a = "a";
                      String c = "c";
                      assertNotEquals(false, a.equals(c));
                      assertNotEquals(false, a.equals(c), "message");
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              public class Test {
                  void test() {
                      String a = "a";
                      String c = "c";
                      assertTrue(a.equals(c));
                      assertTrue(a.equals(c), "message");
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/206")
    @SuppressWarnings({"ConstantConditions", "SimplifiableAssertion"})
    @Test
    void preserveStyleOfStaticImportOrNot() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Assertions;
              
              public class Test {
                  void test() {
                      String a = "a";
                      String c = "c";
                      Assertions.assertNotEquals(false, a.equals(c), "message");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions;
              
              public class Test {
                  void test() {
                      String a = "a";
                      String c = "c";
                      Assertions.assertTrue(a.equals(c), "message");
                  }
              }
              """
          )
        );
    }
}
