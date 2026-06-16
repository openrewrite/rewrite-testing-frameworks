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
          .recipeFromResources("org.openrewrite.java.testing.mockito.PowerMockWhiteboxToJavaReflection");
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
<<<<<<< HEAD
    void migratesSetGetAndInvokeInOneMethod() {
=======
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
    void getInternalStateWithAssignment() {
>>>>>>> origin/main
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private String name;
                  private String description = "d";
                  private String compute() { return name; }
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void test() {
                      MyService service = new MyService();
                      Whitebox.setInternalState(service, "name", "newValue");
                      String desc = Whitebox.getInternalState(service, "description");
                      String result = Whitebox.invokeMethod(service, "compute");
                  }
              }
              """,
            """
              import java.lang.reflect.Field;
              import java.lang.reflect.Method;

              class MyServiceTest {
                  void test() throws Exception {
                      MyService service = new MyService();
                      Field nameField = service.getClass().getDeclaredField("name");
                      nameField.setAccessible(true);
                      nameField.set(service, "newValue");
                      Field descriptionField = service.getClass().getDeclaredField("description");
                      descriptionField.setAccessible(true);
                      String desc = (String) descriptionField.get(service);
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
