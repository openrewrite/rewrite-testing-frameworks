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
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

class CleanupJUnitImportsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4"))
          .parser(KotlinParser.builder()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4"))
          .recipe(new CleanupJUnitImports());
    }

    @DocumentExample
    @Test
    void removesUnusedImport() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Test;

              public class MyTest {}
              """,
            """
              public class MyTest {}
              """
          ),
          //language=kotlin
          kotlin(
            """
              import org.junit.Test

              class MyTest {}
              """,
            """
              class MyTest {}
              """
          )
        );
    }

    @Test
    void leavesOtherImportsAlone() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Arrays;
              import java.util.Collections;
              import java.util.HashSet;

              public class MyTest {
              }
              """
          ),
          //language=kotlin
          kotlin(
            """
              import java.util.Arrays
              import java.util.Collections
              import java.util.HashSet

              class MyTest {
              }
              """
          )
        );
    }

    @Test
    void leavesUsedJUnitImportAlone() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Test;

              public class MyTest {
                  @Test
                  public void foo() {}
              }
              """
          ),
          //language=kotlin
          kotlin(
            """
              import org.junit.Test

              class MyTest {
                  @Test
                  fun foo() {}
              }
              """
          )
        );

    }
}
