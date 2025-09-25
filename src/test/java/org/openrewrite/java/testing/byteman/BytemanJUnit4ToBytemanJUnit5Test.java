/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.testing.byteman;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class BytemanJUnit4ToBytemanJUnit5Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "junit-4", "junit-jupiter-api-5",
            "byteman-bmunit-4", "byteman-bmunit5-4"))
          .recipeFromResource(
            "/META-INF/rewrite/byteman.yml",
            "org.openrewrite.java.testing.byteman.BytemanJUnit4ToBytemanJUnit5");
    }

    @DocumentExample
    @Test
    void convertRunWithToWithByteman() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
              import org.jboss.byteman.contrib.bmunit.BMRule;

              @RunWith(BMUnitRunner.class)
              public class BytemanTest {

                  @Test
                  @BMRule(name = "test rule",
                      targetClass = "java.lang.String",
                      targetMethod = "length()",
                      action = "return 42")
                  public void testWithByteman() {
                      String test = "hello";
                      assert test.length() == 42;
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.jboss.byteman.contrib.bmunit.WithByteman;
              import org.jboss.byteman.contrib.bmunit.BMRule;

              @WithByteman
              public class BytemanTest {

                  @Test
                  @BMRule(name = "test rule",
                      targetClass = "java.lang.String",
                      targetMethod = "length()",
                      action = "return 42")
                  public void testWithByteman() {
                      String test = "hello";
                      assert test.length() == 42;
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenAlreadyUsingJUnit5() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.jboss.byteman.contrib.bmunit.WithByteman;

              @WithByteman
              public class BytemanTest {
                  @Test
                  public void testMethod() {
                      // test code
                  }
              }
              """
          )
        );
    }
}
