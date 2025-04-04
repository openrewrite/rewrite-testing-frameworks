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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UseTestMethodOrderTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4"))
          .recipe(new UseTestMethodOrder());
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/62")
    @Test
    void nameAscending() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.FixMethodOrder;
              import org.junit.runners.MethodSorters;

              @FixMethodOrder(MethodSorters.NAME_ASCENDING)
              class Test {
              }
              """,
            """
              import org.junit.jupiter.api.MethodOrderer.MethodName;
              import org.junit.jupiter.api.TestMethodOrder;

              @TestMethodOrder(MethodName.class)
              class Test {
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/62")
    @Test
    void defaultAndOmitted() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.FixMethodOrder;
              import org.junit.runners.MethodSorters;

              @FixMethodOrder(MethodSorters.DEFAULT)
              class Test {
              }

              @FixMethodOrder
              class Test2 {
              }
              """,
            """
              import org.junit.jupiter.api.MethodOrderer.MethodName;
              import org.junit.jupiter.api.TestMethodOrder;

              @TestMethodOrder(MethodName.class)
              class Test {
              }

              @TestMethodOrder(MethodName.class)
              class Test2 {
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/80")
    @Test
    void jvmOrder() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.FixMethodOrder;
              import org.junit.runners.MethodSorters;

              @FixMethodOrder(MethodSorters.JVM)
              class Test {
              }
              """,
            """
              import org.junit.jupiter.api.MethodOrderer.MethodName;
              import org.junit.jupiter.api.TestMethodOrder;

              @TestMethodOrder(MethodName.class)
              class Test {
              }
              """
          )
        );
    }
}
