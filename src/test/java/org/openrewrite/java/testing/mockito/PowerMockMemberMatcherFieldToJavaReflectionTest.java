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

class PowerMockMemberMatcherFieldToJavaReflectionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-4",
              "mockito-core-3",
              "powermock-api-mockito-1",
              "powermock-api-support-1",
              "powermock-core-1",
              "powermock-reflect-1"
            ))
          .recipe(new PowerMockMemberMatcherFieldToJavaReflection());
    }

    @DocumentExample
    @Test
    void chainedSetOnStaticallyImportedField() {
        //language=java
        rewriteRun(
          java(
            """
              class ServiceManager {
                  private Object delegate;
              }
              """
          ),
          java(
            """
              import org.junit.Test;

              import static org.powermock.api.support.membermodification.MemberMatcher.field;

              class ServiceManagerTest {
                  ServiceManager serviceManager = new ServiceManager();

                  @Test
                  public void replacesDelegate() throws Exception {
                      field(ServiceManager.class, "delegate").set(serviceManager, new Object());
                  }
              }
              """,
            """
              import org.junit.Test;

              import java.lang.reflect.Field;

              class ServiceManagerTest {
                  ServiceManager serviceManager = new ServiceManager();

                  @Test
                  public void replacesDelegate() throws Exception {
                      Field delegateField = ServiceManager.class.getDeclaredField("delegate");
                      delegateField.setAccessible(true);
                      delegateField.set(serviceManager, new Object());
                  }
              }
              """
          )
        );
    }

    @Test
    void chainedSetThroughMemberModifierAddsThrows() {
        //language=java
        rewriteRun(
          java(
            """
              class Task {
                  private String policy;
              }
              """
          ),
          java(
            """
              import org.junit.Test;
              import org.powermock.api.support.membermodification.MemberModifier;

              class TaskTest {
                  @Test
                  public void poll() throws IllegalAccessException {
                      Task task = new Task();
                      MemberModifier.field(Task.class, "policy").set(task, "fixed");
                  }
              }
              """,
            """
              import org.junit.Test;

              import java.lang.reflect.Field;

              class TaskTest {
                  @Test
                  public void poll() throws IllegalAccessException, Exception {
                      Task task = new Task();
                      Field policyField = Task.class.getDeclaredField("policy");
                      policyField.setAccessible(true);
                      policyField.set(task, "fixed");
                  }
              }
              """
          )
        );
    }

    @Test
    void fieldAssignedToVariable() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.concurrent.atomic.AtomicBoolean;

              class Provider {
                  private AtomicBoolean initialised = new AtomicBoolean();
              }
              """
          ),
          java(
            """
              import java.lang.reflect.Field;
              import java.util.concurrent.atomic.AtomicBoolean;

              import org.junit.After;
              import org.powermock.api.support.membermodification.MemberModifier;

              class ProviderTest {
                  Provider provider = new Provider();

                  @After
                  public void destroy() throws Exception {
                      Field field = MemberModifier.field(Provider.class, "initialised");
                      field.set(provider, new AtomicBoolean(false));
                  }
              }
              """,
            """
              import java.lang.reflect.Field;
              import java.util.concurrent.atomic.AtomicBoolean;

              import org.junit.After;

              class ProviderTest {
                  Provider provider = new Provider();

                  @After
                  public void destroy() throws Exception {
                      Field field = Provider.class.getDeclaredField("initialised");
                      field.setAccessible(true);
                      field.set(provider, new AtomicBoolean(false));
                  }
              }
              """
          )
        );
    }

    @Test
    void nestedGetOnSpyLooksUpDeclaringClass() {
        //language=java
        rewriteRun(
          java(
            """
              class Factory {
                  private int timeout = 5;
              }
              """
          ),
          java(
            """
              import org.junit.Test;
              import org.powermock.api.mockito.PowerMockito;

              import static org.junit.Assert.assertEquals;
              import static org.mockito.Mockito.spy;

              class FactoryTest {
                  @Test
                  public void timeout() throws Exception {
                      Factory factory = spy(new Factory());
                      assertEquals(5, PowerMockito.field(factory.getClass(), "timeout").get(factory));
                  }
              }
              """,
            """
              import org.junit.Test;

              import java.lang.reflect.Field;

              import static org.junit.Assert.assertEquals;
              import static org.mockito.Mockito.spy;

              class FactoryTest {
                  @Test
                  public void timeout() throws Exception {
                      Factory factory = spy(new Factory());
                      Field timeoutField = Factory.class.getDeclaredField("timeout");
                      timeoutField.setAccessible(true);
                      assertEquals(5, timeoutField.get(factory));
                  }
              }
              """
          )
        );
    }

    @Test
    void fieldDeclaredInSuperclass() {
        //language=java
        rewriteRun(
          java(
            """
              class Base {
                  private String name;
              }
              """
          ),
          java(
            """
              class Child extends Base {
              }
              """
          ),
          java(
            """
              import org.junit.Test;

              import static org.powermock.api.support.membermodification.MemberMatcher.field;

              class ChildTest {
                  @Test
                  public void name() throws Exception {
                      Child child = new Child();
                      field(Child.class, "name").set(child, "a");
                  }
              }
              """,
            """
              import org.junit.Test;

              import java.lang.reflect.Field;

              class ChildTest {
                  @Test
                  public void name() throws Exception {
                      Child child = new Child();
                      Field nameField = Base.class.getDeclaredField("name");
                      nameField.setAccessible(true);
                      nameField.set(child, "a");
                  }
              }
              """
          )
        );
    }

    @Test
    void runtimeClassOfInterfaceWalksHierarchy() {
        //language=java
        rewriteRun(
          java(
            """
              interface Offset {
              }
              """
          ),
          java(
            """
              import org.junit.Test;
              import org.powermock.api.support.membermodification.MemberModifier;

              class OffsetTest {
                  Offset offset;

                  @Test
                  public void partition() throws Exception {
                      MemberModifier.field(offset.getClass(), "partition").set(offset, 42);
                  }
              }
              """,
            """
              import org.junit.Test;

              import java.lang.reflect.Field;

              class OffsetTest {
                  Offset offset;

                  @Test
                  public void partition() throws Exception {
                      Field partitionField = declaredFieldInHierarchy(offset.getClass(), "partition");
                      partitionField.setAccessible(true);
                      partitionField.set(offset, 42);
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
    void otherMemberMatcherMethodsAreLeftAlone() {
        //language=java
        rewriteRun(
          java(
            """
              class Service {
                  void init() {
                  }
              }
              """
          ),
          java(
            """
              import static org.powermock.api.support.membermodification.MemberMatcher.method;
              import static org.powermock.api.support.membermodification.MemberModifier.suppress;

              class ServiceTest {
                  void test() {
                      suppress(method(Service.class, "init"));
                  }
              }
              """
          )
        );
    }
}
