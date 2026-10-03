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
                      Field nameField = MyService.class.getDeclaredField("name");
                      nameField.setAccessible(true);
                      String result = (String) nameField.get(service);
                  }
              }
              """
          )
        );
    }

    @Test
    void primitiveResultUsesBoxedCast() {
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
                      Field countField = MyService.class.getDeclaredField("count");
                      countField.setAccessible(true);
                      int count = (Integer) countField.get(service);
                  }
              }
              """
          )
        );
    }

    @Test
    void fieldDeclaredInSuperclassIsLookedUpOnDeclaringClass() {
        //language=java
        rewriteRun(
          java(
            """
              class BaseService {
                  private String name = "hello";
              }
              """
          ),
          java(
            """
              class MyService extends BaseService {
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
                      Field nameField = BaseService.class.getDeclaredField("name");
                      nameField.setAccessible(true);
                      String result = (String) nameField.get(service);
                  }
              }
              """
          )
        );
    }

    @Test
    void fieldDeclaredInClassNestedInPrivateClassWalksTheHierarchy() {
        //language=java
        rewriteRun(
          java(
            """
              class Outer {
                  private static class Hidden {
                      static class Base {
                          private String name = "hello";
                      }
                  }

                  static class MyService extends Hidden.Base {
                  }
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void testGetField() {
                      Outer.MyService service = new Outer.MyService();
                      String result = Whitebox.getInternalState(service, "name");
                  }
              }
              """,
            """
              import java.lang.reflect.Field;

              class MyServiceTest {
                  void testGetField() throws Exception {
                      Outer.MyService service = new Outer.MyService();
                      Field nameField = declaredFieldInHierarchy(service.getClass(), "name");
                      nameField.setAccessible(true);
                      String result = (String) nameField.get(service);
                  }

                  private static Field declaredFieldInHierarchy(Class<?> type, String name) throws NoSuchFieldException {
                      for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                          try {
                              return c.getDeclaredField(name);
                          } catch (NoSuchFieldException e) {
                              // declared further up the hierarchy
                          }
                      }
                      throw new NoSuchFieldException(name);
                  }
              }
              """
          )
        );
    }

    @Test
    void getInternalStateNestedInExpression() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private String name = "hello";
                  private int count = 1;
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void testGetField() {
                      MyService service = new MyService();
                      check(Whitebox.getInternalState(service, "name"));
                      System.out.println(Whitebox.getInternalState(service, "count"));
                  }

                  void check(String value) {
                  }
              }
              """,
            """
              import java.lang.reflect.Field;

              class MyServiceTest {
                  void testGetField() throws Exception {
                      MyService service = new MyService();
                      Field nameField = MyService.class.getDeclaredField("name");
                      nameField.setAccessible(true);
                      check((String) nameField.get(service));
                      Field countField = MyService.class.getDeclaredField("count");
                      countField.setAccessible(true);
                      System.out.println(countField.get(service));
                  }

                  void check(String value) {
                  }
              }
              """
          )
        );
    }

    @Test
    void castInsertedForNestedCallIsImported() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.HashMap;

              class MyService {
                  private HashMap<String, String> params = new HashMap<>();
              }
              """
          ),
          java(
            """
              import java.util.Map;

              class Checks {
                  static void check(Map<?, ?> value) {
                  }
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void testGetField() {
                      MyService service = new MyService();
                      Checks.check(Whitebox.getInternalState(service, "params"));
                  }
              }
              """,
            """
              import java.lang.reflect.Field;
              import java.util.Map;

              class MyServiceTest {
                  void testGetField() throws Exception {
                      MyService service = new MyService();
                      Field paramsField = MyService.class.getDeclaredField("params");
                      paramsField.setAccessible(true);
                      Checks.check((Map) paramsField.get(service));
                  }
              }
              """
          )
        );
    }

    @Test
    void getInternalStateInLambdaIsLeftAlone() {
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
              import java.util.function.Supplier;

              class MyServiceTest {
                  void testGetField() {
                      MyService service = new MyService();
                      Supplier<Object> name = () -> Whitebox.getInternalState(service, "name");
                  }
              }
              """
          )
        );
    }

    @Test
    void fieldNamedByConstant() {
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
                  private static final String NAME = "name";

                  void testGetField() {
                      MyService service = new MyService();
                      System.out.println(Whitebox.getInternalState(service, NAME));
                  }
              }
              """,
            """
              import java.lang.reflect.Field;

              class MyServiceTest {
                  private static final String NAME = "name";

                  void testGetField() throws Exception {
                      MyService service = new MyService();
                      Field nameField = MyService.class.getDeclaredField(NAME);
                      nameField.setAccessible(true);
                      System.out.println(nameField.get(service));
                  }
              }
              """
          )
        );
    }

    @Test
    void getInternalStateInLambdaBlockIsLeftAlone() {
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
              import java.util.function.Supplier;

              class MyServiceTest {
                  void testGetField() {
                      MyService service = new MyService();
                      Supplier<Object> name = () -> {
                          System.out.println(Whitebox.getInternalState(service, "name"));
                          return null;
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void fieldOfASuperclassTheTestCannotReferenceWalksTheHierarchy() {
        //language=java
        rewriteRun(
          java(
            """
              import org.powermock.reflect.Whitebox;

              public class MyTest {
                  private static class Base {
                      private int count;
                  }

                  private static class Sub extends Base {
                  }

                  public void test() throws Exception {
                      Sub target = new Sub();
                      Object value = Whitebox.getInternalState(target, "count");
                  }
              }
              """,
            """
              import java.lang.reflect.Field;

              public class MyTest {
                  private static class Base {
                      private int count;
                  }

                  private static class Sub extends Base {
                  }

                  public void test() throws Exception {
                      Sub target = new Sub();
                      Field countField = declaredFieldInHierarchy(target.getClass(), "count");
                      countField.setAccessible(true);
                      Object value = countField.get(target);
                  }

                  private static Field declaredFieldInHierarchy(Class<?> type, String name) throws NoSuchFieldException {
                      for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                          try {
                              return c.getDeclaredField(name);
                          } catch (NoSuchFieldException e) {
                              // declared further up the hierarchy
                          }
                      }
                      throw new NoSuchFieldException(name);
                  }
              }
              """
          )
        );
    }

    @Test
    void aHelperWithCallersWrapsInsteadOfWideningItsSignature() {
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

                  void test() {
                      readName(new MyService());
                  }

                  private String readName(MyService service) {
                      return Whitebox.getInternalState(service, "name");
                  }
              }
              """,
            """
              import java.lang.reflect.Field;

              class MyServiceTest {

                  void test() {
                      readName(new MyService());
                  }

                  private String readName(MyService service) {
                      try {
                          Field nameField = MyService.class.getDeclaredField("name");
                          nameField.setAccessible(true);
                          return (String) nameField.get(service);
                      } catch (ReflectiveOperationException e) {
                          throw new RuntimeException(e);
                      }
                  }
              }
              """
          )
        );
    }
}
