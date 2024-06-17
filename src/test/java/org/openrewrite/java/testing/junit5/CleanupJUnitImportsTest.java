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
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4.13"))
          .parser(KotlinParser.builder()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4.13"))
          .recipe(new CleanupJUnitImports());
    }

    @DocumentExample
    @Test
    void removesUnusedImport() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              
              public class MyTest {}
              """,
            """
              public class MyTest {}
              """
          )
        );

        //language=kotlin
        rewriteRun(
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
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Arrays;
              import java.util.Collections;
              import java.util.HashSet;
              
              public class MyTest {
              }
              """
          )
        );

        //language=kotlin
        rewriteRun(
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
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              
              public class MyTest {
                  @Test
                  public void foo() {}
              }
              """
          )
        );

        //language=kotlin
        rewriteRun(
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
