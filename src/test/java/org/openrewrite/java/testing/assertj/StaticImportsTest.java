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


import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class StaticImportsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing.junit5")
            .build()
            .activateRecipes("org.openrewrite.java.testing.assertj.StaticImports"));
    }

    @DocumentExample
    @Test
    void useStaticImports() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              import org.assertj.core.api.Assertions;
              import static org.assertj.core.api.Fail.fail;

              public class Test {
                  List<String> exampleList;
                  void method() {
                      Assertions.assertThat(true).isTrue();
                      Assertions.assertThat(exampleList).hasSize(0);
                      fail("This is a failure");
                  }
              }
              """,
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.assertj.core.api.Assertions.fail;

              public class Test {
                  List<String> exampleList;
                  void method() {
                      assertThat(true).isTrue();
                      assertThat(exampleList).hasSize(0);
                      fail("This is a failure");
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/664")
    @Test
    @Disabled("Requires changes in AssertJ to adopt `assertThatClass` and `assertThatInterface`")
    void assertionsForClassTypes() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              import org.assertj.core.api.AssertionsForClassTypes;
              import org.assertj.core.api.AssertionsForInterfaceTypes;
              import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
              import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

              public class Test {
                  List<String> exampleList;
                  void method() {
                      AssertionsForInterfaceTypes.assertThat(exampleList).hasSize(0);
                      AssertionsForClassTypes.assertThat(true).isTrue();
                      assertThat(true).isTrue();
                      assertThat(exampleList).hasSize(0);
                  }
              }
              """,
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              public class Test {
                  List<String> exampleList;
                  void method() {
                      assertThat(exampleList).hasSize(0);
                      assertThat(true).isTrue();
                      assertThat(true).isTrue();
                      assertThat(exampleList).hasSize(0);
                  }
              }
              """
          )
        );
    }
}
