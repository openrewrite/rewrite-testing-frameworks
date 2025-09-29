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
package org.openrewrite.java.testing.junit6;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UpdateTestTemplateInvocationContextsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api"))
          .recipe(new UpdateTestTemplateInvocationContexts());
    }

    // TODO: Fix the recipe implementation to handle this case properly
    // @DocumentExample
    // @Test
    void updateReturnTypeToWildcard() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.ExtensionContext;
              import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
              import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

              import java.util.stream.Stream;

              class MyTestTemplateProvider implements TestTemplateInvocationContextProvider {
                  @Override
                  public boolean supportsTestTemplate(ExtensionContext context) {
                      return true;
                  }

                  @Override
                  public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
                      return Stream.of(new MyContext());
                  }

                  static class MyContext implements TestTemplateInvocationContext {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtensionContext;
              import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
              import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

              import java.util.stream.Stream;

              class MyTestTemplateProvider implements TestTemplateInvocationContextProvider {
                  @Override
                  public boolean supportsTestTemplate(ExtensionContext context) {
                      return true;
                  }

                  @Override
                  public Stream<? extends TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
                      return Stream.of(new MyContext());
                  }

                  static class MyContext implements TestTemplateInvocationContext {
                  }
              }
              """
          )
        );
    }

    // TODO: Fix the recipe implementation to handle this case properly
    // @Test
    void updateMultipleProviders() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.ExtensionContext;
              import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
              import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

              import java.util.stream.Stream;

              class Provider1 implements TestTemplateInvocationContextProvider {
                  @Override
                  public boolean supportsTestTemplate(ExtensionContext context) {
                      return true;
                  }

                  @Override
                  public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
                      return Stream.empty();
                  }
              }

              class Provider2 implements TestTemplateInvocationContextProvider {
                  @Override
                  public boolean supportsTestTemplate(ExtensionContext context) {
                      return false;
                  }

                  @Override
                  public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
                      return Stream.empty();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtensionContext;
              import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
              import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

              import java.util.stream.Stream;

              class Provider1 implements TestTemplateInvocationContextProvider {
                  @Override
                  public boolean supportsTestTemplate(ExtensionContext context) {
                      return true;
                  }

                  @Override
                  public Stream<? extends TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
                      return Stream.empty();
                  }
              }

              class Provider2 implements TestTemplateInvocationContextProvider {
                  @Override
                  public boolean supportsTestTemplate(ExtensionContext context) {
                      return false;
                  }

                  @Override
                  public Stream<? extends TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
                      return Stream.empty();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeIfAlreadyUsingWildcard() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.ExtensionContext;
              import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
              import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

              import java.util.stream.Stream;

              class MyTestTemplateProvider implements TestTemplateInvocationContextProvider {
                  @Override
                  public boolean supportsTestTemplate(ExtensionContext context) {
                      return true;
                  }

                  @Override
                  public Stream<? extends TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
                      return Stream.empty();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeOtherMethods() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.ExtensionContext;
              import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

              import java.util.stream.Stream;

              class MyClass {
                  // This is not implementing TestTemplateInvocationContextProvider
                  public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
                      return Stream.empty();
                  }

                  // Different method name
                  public Stream<TestTemplateInvocationContext> provideSomething(ExtensionContext context) {
                      return Stream.empty();
                  }
              }
              """
          )
        );
    }

    // TODO: Fix the recipe implementation to handle this case properly
    // @Test
    void handleFullyQualifiedTypes() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.ExtensionContext;
              import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

              class MyTestTemplateProvider implements TestTemplateInvocationContextProvider {
                  @Override
                  public boolean supportsTestTemplate(ExtensionContext context) {
                      return true;
                  }

                  @Override
                  public java.util.stream.Stream<org.junit.jupiter.api.extension.TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
                      return java.util.stream.Stream.empty();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtensionContext;
              import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

              class MyTestTemplateProvider implements TestTemplateInvocationContextProvider {
                  @Override
                  public boolean supportsTestTemplate(ExtensionContext context) {
                      return true;
                  }

                  @Override
                  public java.util.stream.Stream<? extends org.junit.jupiter.api.extension.TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
                      return java.util.stream.Stream.empty();
                  }
              }
              """
          )
        );
    }
}