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

class RemoveInterceptDynamicTestTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api"))
          .recipe(new RemoveInterceptDynamicTest());
    }

    @DocumentExample
    @Test
    void removeInterceptDynamicTestMethod() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.ExtensionContext;
              import org.junit.jupiter.api.extension.InvocationInterceptor;
              import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

              class MyInterceptor implements InvocationInterceptor {
                  @Override
                  public void interceptDynamicTest(Invocation<Void> invocation, ExtensionContext extensionContext) throws Throwable {
                      System.out.println("Before dynamic test");
                      invocation.proceed();
                      System.out.println("After dynamic test");
                  }

                  @Override
                  public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
                      invocation.proceed();
                  }

                  private static class Method {}
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtensionContext;
              import org.junit.jupiter.api.extension.InvocationInterceptor;
              import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

              class MyInterceptor implements InvocationInterceptor {

                  @Override
                  public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
                      invocation.proceed();
                  }

                  private static class Method {}
              }
              """
          )
        );
    }

    @Test
    void removeMultipleInterceptDynamicTestMethods() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.ExtensionContext;
              import org.junit.jupiter.api.extension.InvocationInterceptor;

              class MyInterceptor implements InvocationInterceptor {
                  @Override
                  public void interceptDynamicTest(Invocation<Void> invocation, ExtensionContext context) throws Throwable {
                      invocation.proceed();
                  }

                  public void keepThisMethod() {
                      System.out.println("This stays");
                  }
              }

              class AnotherInterceptor implements InvocationInterceptor {
                  @Override
                  public void interceptDynamicTest(Invocation<Void> invocation, ExtensionContext extensionContext) throws Throwable {
                      // Different implementation
                      invocation.proceed();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtensionContext;
              import org.junit.jupiter.api.extension.InvocationInterceptor;

              class MyInterceptor implements InvocationInterceptor {

                  public void keepThisMethod() {
                      System.out.println("This stays");
                  }
              }

              class AnotherInterceptor implements InvocationInterceptor {
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveOtherMethods() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.ExtensionContext;
              import org.junit.jupiter.api.extension.InvocationInterceptor;
              import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

              class MyInterceptor implements InvocationInterceptor {
                  @Override
                  public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
                      invocation.proceed();
                  }

                  @Override
                  public void interceptTestTemplateMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
                      invocation.proceed();
                  }

                  private static class Method {}
              }
              """
          )
        );
    }

    @Test
    void handleExtendingClass() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.ExtensionContext;
              import org.junit.jupiter.api.extension.InvocationInterceptor;

              abstract class BaseInterceptor implements InvocationInterceptor {
              }

              class MyInterceptor extends BaseInterceptor {
                  @Override
                  public void interceptDynamicTest(Invocation<Void> invocation, ExtensionContext extensionContext) throws Throwable {
                      invocation.proceed();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtensionContext;
              import org.junit.jupiter.api.extension.InvocationInterceptor;

              abstract class BaseInterceptor implements InvocationInterceptor {
              }

              class MyInterceptor extends BaseInterceptor {
              }
              """
          )
        );
    }

    @Test
    void doNotChangeIfNotImplementingInvocationInterceptor() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.ExtensionContext;

              class MyClass {
                  // This is not implementing InvocationInterceptor, so method should not be removed
                  public void interceptDynamicTest(Object invocation, ExtensionContext extensionContext) throws Throwable {
                      System.out.println("This is a different method");
                  }
              }
              """
          )
        );
    }
}