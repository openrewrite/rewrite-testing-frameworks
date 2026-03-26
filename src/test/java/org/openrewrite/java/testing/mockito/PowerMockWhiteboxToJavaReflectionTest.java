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
          .recipe(new PowerMockWhiteboxToJavaReflection())
          .typeValidationOptions(TypeValidation.builder()
            .methodInvocations(false)
            .build());
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
                      Method greetMethod = service.getClass().getDeclaredMethod("greet", "World".getClass());
                      greetMethod.setAccessible(true);
                      String result = (String) greetMethod.invoke(service, "World");
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
