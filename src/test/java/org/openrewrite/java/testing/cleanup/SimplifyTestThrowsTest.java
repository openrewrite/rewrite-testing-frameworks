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
package org.openrewrite.java.testing.cleanup;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SimplifyTestThrowsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "junit-jupiter-api-5.9", "junit-jupiter-params-5.9"))
          .recipe(new SimplifyTestThrows());
    }

    @DocumentExample
    @Test
    void simplifyExceptions() {
        //language=java
        rewriteRun(
          java(
            """
              import java.io.IOException;
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void throwsMultiple() throws IOException, ArrayIndexOutOfBoundsException, ClassCastException {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void throwsMultiple() throws Exception {
                  }
              }
              """
          )
        );
    }

    @Test
    void normalMethodNoChanges() {
        //language=java
        rewriteRun(
          java(
            """
              import java.io.IOException;
              import org.junit.jupiter.api.Test;

              class ATest {
                  void noTest() throws IOException {
                  }
              }
              """
          )
        );
    }

    @Test
    void noThrowsDeclaration() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
    
              class ATest {
                  @Test
                  void noThrows() {
                  }
              }
              """
          )
        );
    }

    @Test
    void usesGeneralExceptionAlready() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
    
              class ATest {
                  @Test
                  void throwsEx() throws Exception {
                  }
              }
              """
          )
        );
    }
}
