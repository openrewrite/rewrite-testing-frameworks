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
}
