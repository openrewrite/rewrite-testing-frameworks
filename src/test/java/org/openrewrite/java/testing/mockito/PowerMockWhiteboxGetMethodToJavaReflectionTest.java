/*
 * Copyright 2026 the original author or authors.
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

class PowerMockWhiteboxGetMethodToJavaReflectionTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),
              "powermock-core-1",
              "powermock-reflect-1"
            ))
          .recipe(new PowerMockWhiteboxGetMethodToJavaReflection());
    }

    @DocumentExample
    @Test
    void getMethodWithParamType() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private String greet(String name) { return "Hello " + name; }
              }
              """
          ),
          java(
            """
              import java.lang.reflect.Method;
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void test() {
                      Method m = Whitebox.getMethod(MyService.class, "greet", String.class);
                  }
              }
              """,
            """
              import java.lang.reflect.Method;

              class MyServiceTest {
                  void test() throws Exception {
                      Method m = MyService.class.getDeclaredMethod("greet", String.class);
                      m.setAccessible(true);
                  }
              }
              """
          )
        );
    }

    @Test
    void getMethodNoParamTypes() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private String compute() { return "result"; }
              }
              """
          ),
          java(
            """
              import java.lang.reflect.Method;
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void test() {
                      Method m = Whitebox.getMethod(MyService.class, "compute");
                  }
              }
              """,
            """
              import java.lang.reflect.Method;

              class MyServiceTest {
                  void test() throws Exception {
                      Method m = MyService.class.getDeclaredMethod("compute");
                      m.setAccessible(true);
                  }
              }
              """
          )
        );
    }

    @Test
    void getMethodPrimitiveParamType() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private int doubleIt(int value) { return value * 2; }
              }
              """
          ),
          java(
            """
              import java.lang.reflect.Method;
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void test() {
                      Method m = Whitebox.getMethod(MyService.class, "doubleIt", int.class);
                  }
              }
              """,
            """
              import java.lang.reflect.Method;

              class MyServiceTest {
                  void test() throws Exception {
                      Method m = MyService.class.getDeclaredMethod("doubleIt", int.class);
                      m.setAccessible(true);
                  }
              }
              """
          )
        );
    }
}
