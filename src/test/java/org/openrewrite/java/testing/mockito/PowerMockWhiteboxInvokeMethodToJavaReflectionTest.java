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

class PowerMockWhiteboxInvokeMethodToJavaReflectionTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),
              "powermock-core-1",
              "powermock-reflect-1"
            ))
          .recipe(new PowerMockWhiteboxInvokeMethodToJavaReflection());
    }

    @DocumentExample
    @Test
    void invokeMethodNoArgs() {
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
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void testInvoke() {
                      MyService service = new MyService();
                      String result = Whitebox.invokeMethod(service, "compute");
                  }
              }
              """,
            """
              import java.lang.reflect.Method;

              class MyServiceTest {
                  void testInvoke() throws Exception {
                      MyService service = new MyService();
                      Method computeMethod = service.getClass().getDeclaredMethod("compute");
                      computeMethod.setAccessible(true);
                      String result = (String) computeMethod.invoke(service);
                  }
              }
              """
          )
        );
    }

    @Test
    void invokeMethodWithArgs() {
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
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void testInvokeWithArgs() {
                      MyService service = new MyService();
                      String result = Whitebox.invokeMethod(service, "greet", "World");
                  }
              }
              """,
            """
              import java.lang.reflect.Method;

              class MyServiceTest {
                  void testInvokeWithArgs() throws Exception {
                      MyService service = new MyService();
                      Method greetMethod = service.getClass().getDeclaredMethod("greet", String.class);
                      greetMethod.setAccessible(true);
                      String result = (String) greetMethod.invoke(service, "World");
                  }
              }
              """
          )
        );
    }

    @Test
    void invokeMethodWithMultipleArgs() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private String combine(String a, String b) { return a + b; }
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void testInvokeWithMultipleArgs() {
                      MyService service = new MyService();
                      String result = Whitebox.invokeMethod(service, "combine", "Hello", "World");
                  }
              }
              """,
            """
              import java.lang.reflect.Method;

              class MyServiceTest {
                  void testInvokeWithMultipleArgs() throws Exception {
                      MyService service = new MyService();
                      Method combineMethod = service.getClass().getDeclaredMethod("combine", String.class, String.class);
                      combineMethod.setAccessible(true);
                      String result = (String) combineMethod.invoke(service, "Hello", "World");
                  }
              }
              """
          )
        );
    }

    @Test
    void invokeMethodWithPrimitiveArg() {
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
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void testInvokeWithPrimitive() {
                      MyService service = new MyService();
                      Whitebox.invokeMethod(service, "doubleIt", 5);
                  }
              }
              """,
            """
              import java.lang.reflect.Method;

              class MyServiceTest {
                  void testInvokeWithPrimitive() throws Exception {
                      MyService service = new MyService();
                      Method doubleItMethod = service.getClass().getDeclaredMethod("doubleIt", int.class);
                      doubleItMethod.setAccessible(true);
                      doubleItMethod.invoke(service, 5);
                  }
              }
              """
          )
        );
    }

    @Test
    void invokeMethodWithConcreteArgButInterfaceParam() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;

              class MyService {
                  private String process(List<String> items) { return items.toString(); }
              }
              """
          ),
          java(
            """
              import java.util.ArrayList;
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void testInvokeWithConcreteArg() {
                      MyService service = new MyService();
                      ArrayList<String> items = new ArrayList<>();
                      String result = Whitebox.invokeMethod(service, "process", items);
                  }
              }
              """,
            """
              import java.lang.reflect.Method;
              import java.util.ArrayList;
              import java.util.List;

              class MyServiceTest {
                  void testInvokeWithConcreteArg() throws Exception {
                      MyService service = new MyService();
                      ArrayList<String> items = new ArrayList<>();
                      Method processMethod = service.getClass().getDeclaredMethod("process", List.class);
                      processMethod.setAccessible(true);
                      String result = (String) processMethod.invoke(service, items);
                  }
              }
              """
          )
        );
    }

    @Test
    void invokeMethodWithInterfaceTypedArg() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;

              class MyService {
                  private String process(List<String> items) { return items.toString(); }
              }
              """
          ),
          java(
            """
              import java.util.ArrayList;
              import java.util.List;
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void testInvokeWithInterfaceArg() {
                      MyService service = new MyService();
                      List<String> items = new ArrayList<>();
                      String result = Whitebox.invokeMethod(service, "process", items);
                  }
              }
              """,
            """
              import java.lang.reflect.Method;
              import java.util.ArrayList;
              import java.util.List;

              class MyServiceTest {
                  void testInvokeWithInterfaceArg() throws Exception {
                      MyService service = new MyService();
                      List<String> items = new ArrayList<>();
                      Method processMethod = service.getClass().getDeclaredMethod("process", List.class);
                      processMethod.setAccessible(true);
                      String result = (String) processMethod.invoke(service, items);
                  }
              }
              """
          )
        );
    }

    @Test
    void invokeMethodNonLiteralMethodName() {
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
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void testInvoke(String methodName) {
                      MyService service = new MyService();
                      Whitebox.invokeMethod(service, methodName);
                  }
              }
              """,
            """
              import java.lang.reflect.Method;

              class MyServiceTest {
                  void testInvoke(String methodName) throws Exception {
                      MyService service = new MyService();
                      Method reflectMethod = service.getClass().getDeclaredMethod(methodName);
                      reflectMethod.setAccessible(true);
                      reflectMethod.invoke(service);
                  }
              }
              """
          )
        );
    }

    @Test
    void invokeStaticMethodNoArgs() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private static String compute() { return "result"; }
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void test() {
                      String r = Whitebox.invokeMethod(MyService.class, "compute");
                  }
              }
              """,
            """
              import java.lang.reflect.Method;

              class MyServiceTest {
                  void test() throws Exception {
                      Method computeMethod = MyService.class.getDeclaredMethod("compute");
                      computeMethod.setAccessible(true);
                      String r = (String) computeMethod.invoke(null);
                  }
              }
              """
          )
        );
    }

    @Test
    void invokeStaticMethodWithPrimitiveArg() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private static String compute(int value) { return "" + value; }
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void test() {
                      String r = Whitebox.invokeMethod(MyService.class, "compute", 5);
                  }
              }
              """,
            """
              import java.lang.reflect.Method;

              class MyServiceTest {
                  void test() throws Exception {
                      Method computeMethod = MyService.class.getDeclaredMethod("compute", int.class);
                      computeMethod.setAccessible(true);
                      String r = (String) computeMethod.invoke(null, 5);
                  }
              }
              """
          )
        );
    }

    @Test
    void invokeStaticMethodAsStatement() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private static void doStuff(int value) { }
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void test() {
                      Whitebox.invokeMethod(MyService.class, "doStuff", 5);
                  }
              }
              """,
            """
              import java.lang.reflect.Method;

              class MyServiceTest {
                  void test() throws Exception {
                      Method doStuffMethod = MyService.class.getDeclaredMethod("doStuff", int.class);
                      doStuffMethod.setAccessible(true);
                      doStuffMethod.invoke(null, 5);
                  }
              }
              """
          )
        );
    }

    @Test
    void instanceAndStaticInvokeMethodInSameBlock() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private String instanceCompute() { return "i"; }
                  private static String staticCompute() { return "s"; }
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void test() {
                      MyService service = new MyService();
                      String a = Whitebox.invokeMethod(service, "instanceCompute");
                      String b = Whitebox.invokeMethod(MyService.class, "staticCompute");
                  }
              }
              """,
            """
              import java.lang.reflect.Method;

              class MyServiceTest {
                  void test() throws Exception {
                      MyService service = new MyService();
                      Method instanceComputeMethod = service.getClass().getDeclaredMethod("instanceCompute");
                      instanceComputeMethod.setAccessible(true);
                      String a = (String) instanceComputeMethod.invoke(service);
                      Method staticComputeMethod = MyService.class.getDeclaredMethod("staticCompute");
                      staticComputeMethod.setAccessible(true);
                      String b = (String) staticComputeMethod.invoke(null);
                  }
              }
              """
          )
        );
    }

    @Test
    void invokeMethodPrimitiveResultUsesBoxedCast() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private int compute() { return 42; }
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void test() {
                      MyService service = new MyService();
                      int r = Whitebox.invokeMethod(service, "compute");
                  }
              }
              """,
            """
              import java.lang.reflect.Method;

              class MyServiceTest {
                  void test() throws Exception {
                      MyService service = new MyService();
                      Method computeMethod = service.getClass().getDeclaredMethod("compute");
                      computeMethod.setAccessible(true);
                      int r = (Integer) computeMethod.invoke(service);
                  }
              }
              """
          )
        );
    }

    @Test
    void invokeMethodExplicitParamTypesNotMigrated() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private String greet(String name) { return name; }
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void test() throws Exception {
                      MyService service = new MyService();
                      String r = Whitebox.invokeMethod(service, "greet", new Class[]{String.class}, new Object[]{"World"});
                  }
              }
              """
          )
        );
    }
}
