/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.testing.junit6;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveJreOtherTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-6"))
          .recipe(new RemoveJreOther());
    }

    @DocumentExample
    @Test
    void flagJreOtherNextToOtherConstants() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.condition.EnabledOnJre;
              import org.junit.jupiter.api.condition.JRE;

              class MyTest {
                  @Test
                  @EnabledOnJre({JRE.JAVA_21, JRE.OTHER})
                  void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.condition.EnabledOnJre;
              import org.junit.jupiter.api.condition.JRE;

              class MyTest {
                  @Test
                  /*~~(JRE.OTHER is deprecated and has no mechanical replacement; removing it would change which JREs match)~~>*/@EnabledOnJre({JRE.JAVA_21, JRE.OTHER})
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void flagJreOtherInDisabledOnJreValueAttribute() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.condition.DisabledOnJre;
              import org.junit.jupiter.api.condition.JRE;

              class MyTest {
                  @Test
                  @DisabledOnJre(value = {JRE.JAVA_17, JRE.OTHER})
                  void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.condition.DisabledOnJre;
              import org.junit.jupiter.api.condition.JRE;

              class MyTest {
                  @Test
                  /*~~(JRE.OTHER is deprecated and has no mechanical replacement; removing it would change which JREs match)~~>*/@DisabledOnJre(value = {JRE.JAVA_17, JRE.OTHER})
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void flagLoneBracedJreOther() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.condition.EnabledOnJre;
              import org.junit.jupiter.api.condition.JRE;

              class MyTest {
                  @Test
                  @EnabledOnJre({JRE.OTHER})
                  void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.condition.EnabledOnJre;
              import org.junit.jupiter.api.condition.JRE;

              class MyTest {
                  @Test
                  /*~~(JRE.OTHER is deprecated and has no mechanical replacement; removing it would change which JREs match)~~>*/@EnabledOnJre({JRE.OTHER})
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void flagLoneJreOther() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.condition.EnabledOnJre;
              import org.junit.jupiter.api.condition.JRE;

              class MyTest {
                  @Test
                  @EnabledOnJre(JRE.OTHER)
                  void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.condition.EnabledOnJre;
              import org.junit.jupiter.api.condition.JRE;

              class MyTest {
                  @Test
                  /*~~(JRE.OTHER is deprecated and has no mechanical replacement; removing it would change which JREs match)~~>*/@EnabledOnJre(JRE.OTHER)
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void leaveAnnotationsWithoutJreOtherAlone() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.condition.EnabledOnJre;
              import org.junit.jupiter.api.condition.JRE;

              class MyTest {
                  @Test
                  @EnabledOnJre({JRE.JAVA_17, JRE.JAVA_21})
                  void test() {
                  }
              }
              """
          )
        );
    }
}
