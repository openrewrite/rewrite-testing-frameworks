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

package org.openrewrite.java.testing.mockito;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AddMockitoExtensionIfAnnotationsUsedTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddMockitoExtensionIfAnnotationsUsed())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api", "mockito-junit-jupiter", "mockito-core")
            .dependsOn("public class Service {}"));
    }

    @DocumentExample
    @Test
    void addForMock() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.mockito.Mock;

              class Test {
                  @Mock
                  Service service;
                  @Test
                  void test() {}
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class Test {
                  @Mock
                  Service service;
                  @Test
                  void test() {}
              }
              """
          )
        );
    }

    @Test
    void addForCaptor() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.mockito.Captor;

              class Test {
                  @Captor
                  Service service;
                  @Test
                  void test() {}
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Captor;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class Test {
                  @Captor
                  Service service;
                  @Test
                  void test() {}
              }
              """
          )
        );
    }

    @Test
    void dontAddIfPresent() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Captor;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class Test {
                  @Captor
                  Service service;
                  @Mock
                  Service service;
              }
              """
          )
        );
    }

    @Test
    void dontAddIfJunit4() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              class Test {
                  @Mock
                  Service service;
                  @Test
                  void test() {}
              }
              """
          )
        );
    }

    @Test
    void notInferWithExistingAnnotations() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Disabled;
              import org.mockito.Captor;
              import org.mockito.Mock;

              @Disabled
              class Test {
                  @Mock
                  Service service;
                  @Test
                  void test() {}
              }
              """,
            """
              import org.junit.jupiter.api.Disabled;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Captor;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              @Disabled
              @ExtendWith(MockitoExtension.class)
              class Test {
                  @Mock
                  Service service;
                  @Test
                  void test() {}
              }
              """
          )
        );
    }
}
