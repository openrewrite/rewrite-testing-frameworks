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
package org.openrewrite.java.testing.arquillian;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ReplaceArquillianInSequenceAnnotationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("arquillian-junit-core"))
          .recipe(new ReplaceArquillianInSequenceAnnotation());
    }

    @DocumentExample
    @Test
    void replaceInSequenceAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.jboss.arquillian.junit.InSequence;

              class A {
                  @InSequence(2)
                  void second() {}

                  @InSequence(1)
                  void first() {}
              }
              """,
            """
              import org.junit.jupiter.api.MethodOrderer;
              import org.junit.jupiter.api.Order;
              import org.junit.jupiter.api.TestMethodOrder;

              @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
              class A {
                  @Order(2)
                  void second() {}

                  @Order(1)
                  void first() {}
              }
              """
          )
        );
    }
}