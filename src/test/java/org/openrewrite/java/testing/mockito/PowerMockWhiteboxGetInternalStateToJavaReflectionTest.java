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

class PowerMockWhiteboxGetInternalStateToJavaReflectionTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),
              "powermock-core-1",
              "powermock-reflect-1"
            ))
          .recipe(new PowerMockWhiteboxGetInternalStateToJavaReflection());
    }

    @DocumentExample
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
}
