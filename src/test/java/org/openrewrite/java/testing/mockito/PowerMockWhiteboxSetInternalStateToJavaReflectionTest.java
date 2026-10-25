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

class PowerMockWhiteboxSetInternalStateToJavaReflectionTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),
              "powermock-core-1",
              "powermock-reflect-1"
            ))
          .recipe(new PowerMockWhiteboxSetInternalStateToJavaReflection());
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
                      Field nameField = MyService.class.getDeclaredField("name");
                      nameField.setAccessible(true);
                      nameField.set(service, "expectedValue");
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
                  private String name;
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
    void setInternalStateWithWhereClassVariable() {
        //language=java
        rewriteRun(
          java(
            """
              class Parent {
                  private String name;
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
                      Class<?> where = Parent.class;
                      Whitebox.setInternalState(child, "name", "newValue", where);
                  }
              }
              """,
            """
              import java.lang.reflect.Field;

              class MyServiceTest {
                  void test() throws Exception {
                      Child child = new Child();
                      Class<?> where = Parent.class;
                      Field nameField = where.getDeclaredField("name");
                      nameField.setAccessible(true);
                      nameField.set(child, "newValue");
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
                      Field nameField = MyService.class.getDeclaredField("name");
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
                          Field nameField = MyService.class.getDeclaredField("name");
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
                      Field nameField1 = MyService.class.getDeclaredField("name");
                      nameField1.setAccessible(true);
                      nameField1.set(svc1, "first");
                      Field nameField = MyService.class.getDeclaredField("name");
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
                      Field nameField = MyService.class.getDeclaredField("name");
                      nameField.setAccessible(true);
                      nameField.set(service, "expectedValue");
                  }
              }
              """
          )
        );
    }

    @Test
    void arrayValueSelectsTheObjectArrayOverload() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private String[] names;
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void testSetField() {
                      MyService service = new MyService();
                      Whitebox.setInternalState(service, "names", new String[]{"a", "b"});
                  }
              }
              """,
            """
              import java.lang.reflect.Field;

              class MyServiceTest {
                  void testSetField() throws Exception {
                      MyService service = new MyService();
                      Field namesField = MyService.class.getDeclaredField("names");
                      namesField.setAccessible(true);
                      namesField.set(service, new String[]{"a", "b"});
                  }
              }
              """
          )
        );
    }

    @Test
    void staticFieldIdentifiedByItsType() {
        //language=java
        rewriteRun(
          java(
            """
              class Logger {
              }

              class Writer {
                  private static Logger log;
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class WriterTest {
                  void testInjectLogger() {
                      Logger mockLog = new Logger();
                      Whitebox.setInternalState(Writer.class, Logger.class, mockLog);
                  }
              }
              """,
            """
              import java.lang.reflect.Field;

              class WriterTest {
                  void testInjectLogger() throws Exception {
                      Logger mockLog = new Logger();
                      Field logField = Writer.class.getDeclaredField("log");
                      logField.setAccessible(true);
                      logField.set(null, mockLog);
                  }
              }
              """
          )
        );
    }

    @Test
    void instanceFieldIdentifiedByItsType() {
        //language=java
        rewriteRun(
          java(
            """
              class Collaborator {
              }

              class Service {
                  private Collaborator collaborator;
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class ServiceTest {
                  void testInject() {
                      Service service = new Service();
                      Whitebox.setInternalState(service, Collaborator.class, new Collaborator());
                  }
              }
              """,
            """
              import java.lang.reflect.Field;

              class ServiceTest {
                  void testInject() throws Exception {
                      Service service = new Service();
                      Field collaboratorField = Service.class.getDeclaredField("collaborator");
                      collaboratorField.setAccessible(true);
                      collaboratorField.set(service, new Collaborator());
                  }
              }
              """
          )
        );
    }

    @Test
    void declinesWhenMoreThanOneFieldHasThatType() {
        //language=java
        rewriteRun(
          java(
            """
              class Collaborator {
              }

              class Service {
                  private Collaborator first;
                  private Collaborator second;
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class ServiceTest {
                  void testInject() {
                      Service service = new Service();
                      Whitebox.setInternalState(service, Collaborator.class, new Collaborator());
                  }
              }
              """
          )
        );
    }
}
