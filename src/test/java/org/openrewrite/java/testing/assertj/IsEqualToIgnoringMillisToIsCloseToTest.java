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
package org.openrewrite.java.testing.assertj;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class IsEqualToIgnoringMillisToIsCloseToTest implements RewriteTest {

    @DocumentExample
    @Test
    void replaceDeprecation() {
        rewriteRun(spec -> spec.recipe(new IsEqualToIgnoringMillisToIsCloseToRecipe()),
          //language=java
          java(
            """
              import org.assertj.core.api.Assertions;

              import java.util.Date;

              class A {
                  public void foo(Date date1, Date date2) {
                      Assertions.assertThat(date1).isEqualToIgnoringMillis(date2);
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              import java.util.Date;

              class A {
                  public void foo(Date date1, Date date2) {
                      Assertions.assertThat(date1).isCloseTo(date2, 1000L);
                  }
              }
              """
          )
        );
    }
}
