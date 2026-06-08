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
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class PowerMockWhiteboxToJavaReflectionTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),
              "powermock-core-1",
              "powermock-reflect-1"
            ))
          .recipe(new PowerMockWhiteboxToJavaReflection());
    }

    @DocumentExample
    @Test
    void setInternalStateReplacedWithReflection() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private String name;
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void testSetField() {
                      MyService service = new MyService();
                      Whitebox.setInternalState(service, "name", "expectedValue");
                  }
              }
              """,
            """
              import java.lang.reflect.Field;

              class MyServiceTest {
                  void testSetField() throws Exception {
                      MyService service = new MyService();
                      Field nameField = service.getClass().getDeclaredField("name");
                      nameField.setAccessible(true);
                      nameField.set(service, "expectedValue");
                  }
              }
              """
          )
        );
    }

    @Test
    void getInternalStateWithAssignment() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private String name = "hello";
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void testGetField() {
                      MyService service = new MyService();
                      String result = Whitebox.getInternalState(service, "name");
                  }
              }
              """,
            """
              import java.lang.reflect.Field;

              class MyServiceTest {
                  void testGetField() throws Exception {
                      MyService service = new MyService();
                      Field nameField = service.getClass().getDeclaredField("name");
                      nameField.setAccessible(true);
                      String result = (String) nameField.get(service);
                  }
              }
              """
          )
        );
    }

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
    void throwsExceptionNotDuplicatedWhenAlreadyPresent() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private String name;
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void testSetField() throws Exception {
                      MyService service = new MyService();
                      Whitebox.setInternalState(service, "name", "expectedValue");
                  }
              }
              """,
            """
              import java.lang.reflect.Field;

              class MyServiceTest {
                  void testSetField() throws Exception {
                      MyService service = new MyService();
                      Field nameField = service.getClass().getDeclaredField("name");
                      nameField.setAccessible(true);
                      nameField.set(service, "expectedValue");
                  }
              }
              """
          )
        );
    }

    @Test
    void whiteboxInsideIfBlock() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private String name;
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void testSetFieldConditionally(boolean condition) {
                      MyService service = new MyService();
                      if (condition) {
                          Whitebox.setInternalState(service, "name", "expectedValue");
                      }
                  }
              }
              """,
            """
              import java.lang.reflect.Field;

              class MyServiceTest {
                  void testSetFieldConditionally(boolean condition) throws Exception {
                      MyService service = new MyService();
                      if (condition) {
                          Field nameField = service.getClass().getDeclaredField("name");
                          nameField.setAccessible(true);
                          nameField.set(service, "expectedValue");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void multipleWhiteboxCallsSameFieldName() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private String name;
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void testSetFieldTwice() {
                      MyService svc1 = new MyService();
                      MyService svc2 = new MyService();
                      Whitebox.setInternalState(svc1, "name", "first");
                      Whitebox.setInternalState(svc2, "name", "second");
                  }
              }
              """,
            """
              import java.lang.reflect.Field;

              class MyServiceTest {
                  void testSetFieldTwice() throws Exception {
                      MyService svc1 = new MyService();
                      MyService svc2 = new MyService();
                      Field nameField1 = svc1.getClass().getDeclaredField("name");
                      nameField1.setAccessible(true);
                      nameField1.set(svc1, "first");
                      Field nameField = svc2.getClass().getDeclaredField("name");
                      nameField.setAccessible(true);
                      nameField.set(svc2, "second");
                  }
              }
              """
          )
        );
    }

    @Test
    void throwsNotAddedWhenThrowableAlreadyPresent() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private String name;
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void testSetField() throws Throwable {
                      MyService service = new MyService();
                      Whitebox.setInternalState(service, "name", "expectedValue");
                  }
              }
              """,
            """
              import java.lang.reflect.Field;

              class MyServiceTest {
                  void testSetField() throws Throwable {
                      MyService service = new MyService();
                      Field nameField = service.getClass().getDeclaredField("name");
                      nameField.setAccessible(true);
                      nameField.set(service, "expectedValue");
                  }
              }
              """
          )
        );
    }

    @Test
    void setInternalStateNonLiteralFieldName() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private String name;
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void testSetField(String fieldName) {
                      MyService service = new MyService();
                      Whitebox.setInternalState(service, fieldName, "expectedValue");
                  }
              }
              """,
            """
              import java.lang.reflect.Field;

              class MyServiceTest {
                  void testSetField(String fieldName) throws Exception {
                      MyService service = new MyService();
                      Field reflectField = service.getClass().getDeclaredField(fieldName);
                      reflectField.setAccessible(true);
                      reflectField.set(service, "expectedValue");
                  }
              }
              """
          )
        );
    }

    @Test
    void getInternalStateNonLiteralFieldName() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private String name = "hello";
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void testGetField(String fieldName) {
                      MyService service = new MyService();
                      String result = Whitebox.getInternalState(service, fieldName);
                  }
              }
              """,
            """
              import java.lang.reflect.Field;

              class MyServiceTest {
                  void testGetField(String fieldName) throws Exception {
                      MyService service = new MyService();
                      Field reflectField = service.getClass().getDeclaredField(fieldName);
                      reflectField.setAccessible(true);
                      String result = (String) reflectField.get(service);
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
    void twoNonLiteralGetInternalStateInSameBlock() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private String name = "hello";
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void test(String f1, String f2) {
                      MyService service = new MyService();
                      String a = Whitebox.getInternalState(service, f1);
                      String b = Whitebox.getInternalState(service, f2);
                  }
              }
              """,
            """
              import java.lang.reflect.Field;

              class MyServiceTest {
                  void test(String f1, String f2) throws Exception {
                      MyService service = new MyService();
                      Field reflectField1 = service.getClass().getDeclaredField(f1);
                      reflectField1.setAccessible(true);
                      String a = (String) reflectField1.get(service);
                      Field reflectField = service.getClass().getDeclaredField(f2);
                      reflectField.setAccessible(true);
                      String b = (String) reflectField.get(service);
                  }
              }
              """
          )
        );
    }

    @Test
    void getFieldReturnsFieldVariable() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private String name;
              }
              """
          ),
          java(
            """
              import java.lang.reflect.Field;
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void test() {
                      Field f = Whitebox.getField(MyService.class, "name");
                  }
              }
              """,
            """
              import java.lang.reflect.Field;

              class MyServiceTest {
                  void test() throws Exception {
                      Field f = MyService.class.getDeclaredField("name");
                      f.setAccessible(true);
                  }
              }
              """
          )
        );
    }

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

    @Test
    void getInternalStateWithWhereClass() {
        //language=java
        rewriteRun(
          java(
            """
              class Parent {
                  private String name = "hello";
              }
              """
          ),
          java(
            """
              class Child extends Parent {
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void test() {
                      Child child = new Child();
                      String n = Whitebox.getInternalState(child, "name", Parent.class);
                  }
              }
              """,
            """
              import java.lang.reflect.Field;

              class MyServiceTest {
                  void test() throws Exception {
                      Child child = new Child();
                      Field nameField = Parent.class.getDeclaredField("name");
                      nameField.setAccessible(true);
                      String n = (String) nameField.get(child);
                  }
              }
              """
          )
        );
    }

    @Test
    void setInternalStateWithWhereClass() {
        //language=java
        rewriteRun(
          java(
            """
              class Parent {
                  private String name = "hello";
              }
              """
          ),
          java(
            """
              class Child extends Parent {
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void test() {
                      Child child = new Child();
                      Whitebox.setInternalState(child, "name", "newValue", Parent.class);
                  }
              }
              """,
            """
              import java.lang.reflect.Field;

              class MyServiceTest {
                  void test() throws Exception {
                      Child child = new Child();
                      Field nameField = Parent.class.getDeclaredField("name");
                      nameField.setAccessible(true);
                      nameField.set(child, "newValue");
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
    void invokeConstructorNoArgs() {
        //language=java
        rewriteRun(
          // The user type used as a generic/result type is generated by the template and cannot be
          // attributed by the isolated template parser (a test-only artifact); the source is valid Java.
          spec -> spec.typeValidationOptions(TypeValidation.builder().identifiers(false).methodInvocations(false).build()),
          java(
            """
              class MyService {
                  private MyService() {
                  }
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void test() {
                      MyService s = Whitebox.invokeConstructor(MyService.class);
                  }
              }
              """,
            """
              import java.lang.reflect.Constructor;

              class MyServiceTest {
                  void test() throws Exception {
                      Constructor<MyService> myServiceConstructor = MyService.class.getDeclaredConstructor();
                      myServiceConstructor.setAccessible(true);
                      MyService s = myServiceConstructor.newInstance();
                  }
              }
              """
          )
        );
    }

    @Test
    void invokeConstructorWithResolvedParamTypes() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.builder().identifiers(false).methodInvocations(false).build()),
          java(
            """
              class MyService {
                  private MyService(String name, int age) {
                  }
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void test() {
                      MyService s = Whitebox.invokeConstructor(MyService.class, "Alice", 42);
                  }
              }
              """,
            """
              import java.lang.reflect.Constructor;

              class MyServiceTest {
                  void test() throws Exception {
                      Constructor<MyService> myServiceConstructor = MyService.class.getDeclaredConstructor(String.class, int.class);
                      myServiceConstructor.setAccessible(true);
                      MyService s = myServiceConstructor.newInstance("Alice", 42);
                  }
              }
              """
          )
        );
    }

    @Test
    void invokeConstructorExplicitParamTypes() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.builder().identifiers(false).methodInvocations(false).build()),
          java(
            """
              class MyService {
                  private MyService(String name, int age) {
                  }
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void test() {
                      MyService s = Whitebox.invokeConstructor(MyService.class, new Class[]{String.class, int.class}, new Object[]{"Alice", 42});
                  }
              }
              """,
            """
              import java.lang.reflect.Constructor;

              class MyServiceTest {
                  void test() throws Exception {
                      Constructor<MyService> myServiceConstructor = MyService.class.getDeclaredConstructor(String.class, int.class);
                      myServiceConstructor.setAccessible(true);
                      MyService s = myServiceConstructor.newInstance("Alice", 42);
                  }
              }
              """
          )
        );
    }

    @Test
    void invokeConstructorAsStatement() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.builder().identifiers(false).methodInvocations(false).build()),
          java(
            """
              class MyService {
                  private MyService(String name) {
                  }
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void test() {
                      Whitebox.invokeConstructor(MyService.class, "Alice");
                  }
              }
              """,
            """
              import java.lang.reflect.Constructor;

              class MyServiceTest {
                  void test() throws Exception {
                      Constructor<MyService> myServiceConstructor = MyService.class.getDeclaredConstructor(String.class);
                      myServiceConstructor.setAccessible(true);
                      myServiceConstructor.newInstance("Alice");
                  }
              }
              """
          )
        );
    }

    @Test
    void getInternalStatePrimitiveResultUsesBoxedCast() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private int count = 3;
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void test() {
                      MyService service = new MyService();
                      int count = Whitebox.getInternalState(service, "count");
                  }
              }
              """,
            """
              import java.lang.reflect.Field;

              class MyServiceTest {
                  void test() throws Exception {
                      MyService service = new MyService();
                      Field countField = service.getClass().getDeclaredField("count");
                      countField.setAccessible(true);
                      int count = (Integer) countField.get(service);
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

    @Test
    void invokeConstructorVarargsArrayNotMigrated() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private MyService(String a, String b) {
                  }
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void test() throws Exception {
                      MyService s = Whitebox.invokeConstructor(MyService.class, new Object[]{"a", "b"});
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenWhiteboxNotUsed() {
        //language=java
        rewriteRun(
          java(
            """
              class MyServiceTest {
                  void test() {
                  }
              }
              """
          )
        );
    }
}
