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

class FlagUnsupportedPowerMockUsageTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-4",
              "mockito-core-3.12",
              "powermock-api-mockito2-2",
              "powermock-api-support-2",
              "powermock-core-2",
              "powermock-module-junit4",
              "powermock-reflect-2"))
          .recipe(new FlagUnsupportedPowerMockUsage());
    }

    @DocumentExample
    @Test
    void flagsMemberModificationAndPrivateMethodStubbing() {
        //language=java
        rewriteRun(
          java(
            """
              class Service {
                  private String secret() {
                      return "real";
                  }

                  static void init() {
                  }
              }
              """
          ),
          java(
            """
              import org.junit.Test;
              import org.powermock.api.mockito.PowerMockito;

              import static org.powermock.api.support.membermodification.MemberMatcher.method;
              import static org.powermock.api.support.membermodification.MemberModifier.suppress;

              public class ServiceTest {
                  @Test
                  public void test() throws Exception {
                      suppress(method(Service.class, "init"));
                      Service service = PowerMockito.spy(new Service());
                      PowerMockito.when(service, "secret").thenReturn("mock");
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.powermock.api.mockito.PowerMockito;

              import static org.powermock.api.support.membermodification.MemberMatcher.method;
              import static org.powermock.api.support.membermodification.MemberModifier.suppress;

              public class ServiceTest {
                  @Test
                  public void test() throws Exception {
                      /* `MemberModifier.suppress` could not be migrated automatically; migrate it manually to replace PowerMock */
                      suppress(method(Service.class, "init"));
                      Service service = PowerMockito.spy(new Service());
                      /* `PowerMockito.when` with more than one argument could not be migrated automatically; migrate it manually to replace PowerMock */
                      PowerMockito.when(service, "secret").thenReturn("mock");
                  }
              }
              """
          )
        );
    }

    @Test
    void supportedUsageIsNotFlagged() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;

              class Request<T> {
                  static <T> Request<T> build(List<T> items) {
                      return new Request<>();
                  }
              }
              """
          ),
          java(
            """
              import java.util.Collections;

              import org.junit.Test;
              import org.powermock.api.mockito.PowerMockito;

              public class GenericTest {
                  @Test
                  public void test() {
                      PowerMockito.mockStatic(Request.class);
                      PowerMockito.when(Request.build(Collections.emptyList())).thenReturn(null);
                  }
              }
              """
          ),
          java(
            """
              import java.util.Calendar;

              import org.junit.Test;
              import org.powermock.api.mockito.PowerMockito;
              import org.powermock.reflect.Whitebox;

              public class MyTest {
                  private int field;

                  @Test
                  public void test() {
                      PowerMockito.mockStatic(Calendar.class);
                      PowerMockito.verifyStatic(Calendar.class);
                      Calendar.getInstance();
                      Whitebox.setInternalState(this, "field", 1);
                  }
              }
              """
          )
        );
    }

    @Test
    void fluentCallIsFlaggedOnceThroughTheCallStartingTheChain() {
        //language=java
        rewriteRun(
          java(
            """
              import java.io.File;

              import org.junit.Test;
              import org.powermock.api.mockito.PowerMockito;

              public class MyTest {
                  @Test
                  public void test() throws Exception {
                      PowerMockito.verifyNew(File.class).withArguments("a.txt");
                  }
              }
              """,
            """
              import java.io.File;

              import org.junit.Test;
              import org.powermock.api.mockito.PowerMockito;

              public class MyTest {
                  @Test
                  public void test() throws Exception {
                      /* `PowerMockito.verifyNew` could not be migrated automatically; migrate it manually to replace PowerMock */
                      PowerMockito.verifyNew(File.class).withArguments("a.txt");
                  }
              }
              """
          )
        );
    }

    @Test
    void staticMethodInheritedByMockedClass() {
        //language=java
        rewriteRun(
          java(
            """
              import java.net.Inet4Address;

              import org.junit.Test;
              import org.powermock.api.mockito.PowerMockito;

              public class MyTest {
                  @Test
                  public void test() throws Exception {
                      PowerMockito.mockStatic(Inet4Address.class);
                      PowerMockito.when(Inet4Address.getLocalHost()).thenReturn(null);
                  }
              }
              """,
            """
              import java.net.Inet4Address;

              import org.junit.Test;
              import org.powermock.api.mockito.PowerMockito;

              public class MyTest {
                  @Test
                  public void test() throws Exception {
                      PowerMockito.mockStatic(Inet4Address.class);
                      /* Static mocking of `Inet4Address.getLocalHost()` cannot be migrated, as `Mockito.mockStatic` does not intercept static methods inherited from `InetAddress`; migrate it manually to replace PowerMock */
                      PowerMockito.when(Inet4Address.getLocalHost()).thenReturn(null);
                  }
              }
              """
          )
        );
    }

    @Test
    void staticMethodsOfClassesMockitoRefusesToMock() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              import org.powermock.api.mockito.PowerMockito;

              public class MyTest {
                  @Test
                  public void test() {
                      PowerMockito.mockStatic(System.class);
                      PowerMockito.when(System.currentTimeMillis()).thenReturn(1L);
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.powermock.api.mockito.PowerMockito;

              public class MyTest {
                  @Test
                  public void test() {
                      /* `mockStatic(System.class)` cannot be migrated, as Mockito does not mock the static methods of `System`; migrate it manually to replace PowerMock */
                      PowerMockito.mockStatic(System.class);
                      PowerMockito.when(System.currentTimeMillis()).thenReturn(1L);
                  }
              }
              """
          )
        );
    }

    @Test
    void whiteboxOnFieldOfRuntimeClassIsNotFlagged() {
        //language=java
        rewriteRun(
          java(
            """
              interface Service {
              }
              """
          ),
          java(
            """
              class ServiceImpl implements Service {
                  private Object repository;
              }
              """
          ),
          java(
            """
              import org.junit.Test;
              import org.powermock.reflect.Whitebox;

              public class MyTest {
                  @Test
                  public void test() {
                      Service service = new ServiceImpl();
                      Whitebox.setInternalState(service, "repository", new Object());
                  }
              }
              """
          )
        );
    }

    @Test
    void whiteboxOnFieldOfUnreferenceableSuperclass() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              import org.powermock.reflect.Whitebox;

              public class MyTest {
                  private static class Base {
                      private int count;
                  }

                  private static class Sub extends Base {
                  }

                  @Test
                  public void test() {
                      Sub target = new Sub();
                      Object value = Whitebox.getInternalState(target, "count");
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.powermock.reflect.Whitebox;

              public class MyTest {
                  private static class Base {
                      private int count;
                  }

                  private static class Sub extends Base {
                  }

                  @Test
                  public void test() {
                      Sub target = new Sub();
                      /* `Whitebox.getInternalState` cannot be migrated, as the member it accesses is declared in a superclass that the test cannot reference; migrate it manually to replace PowerMock */
                      Object value = Whitebox.getInternalState(target, "count");
                  }
              }
              """
          )
        );
    }

    @Test
    void whiteboxOnSpyOfUnknownClass() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              import org.mockito.Mockito;
              import org.powermock.reflect.Whitebox;

              public class MyTest {
                  @Test
                  public void test() {
                      Object target = Mockito.spy(new Object());
                      Object value = Whitebox.getInternalState(target, "field");
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.mockito.Mockito;
              import org.powermock.reflect.Whitebox;

              public class MyTest {
                  @Test
                  public void test() {
                      Object target = Mockito.spy(new Object());
                      /* `Whitebox.getInternalState` cannot be migrated, as the runtime class of a Mockito mock or spy does not declare the member it accesses; migrate it manually to replace PowerMock */
                      Object value = Whitebox.getInternalState(target, "field");
                  }
              }
              """
          )
        );
    }

    @Test
    void annotationWithoutMockitoEquivalent() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.powermock.core.classloader.annotations.MockPolicy;
              import org.powermock.modules.junit4.PowerMockRunner;

              @RunWith(PowerMockRunner.class)
              @MockPolicy({})
              public class MyTest {
                  @Test
                  public void test() {
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.powermock.core.classloader.annotations.MockPolicy;
              import org.powermock.modules.junit4.PowerMockRunner;

              @RunWith(PowerMockRunner.class)
              /* `@MockPolicy` could not be migrated automatically; migrate it manually to replace PowerMock */
              @MockPolicy({})
              public class MyTest {
                  @Test
                  public void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void annotationOnNestedClassIsFlaggedOnce() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              import org.powermock.core.classloader.annotations.MockPolicy;

              public class MyTest {
                  @MockPolicy({})
                  public static class Nested {
                      @Test
                      public void test() {
                      }
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.powermock.core.classloader.annotations.MockPolicy;

              public class MyTest {
                  /* `@MockPolicy` could not be migrated automatically; migrate it manually to replace PowerMock */ @MockPolicy({})
                  public static class Nested {
                      @Test
                      public void test() {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void commentIsPlacedOnTheLineBeforeTheStatement() {
        //language=java
        rewriteRun(
          java(
            """
              class Service {
                  private String secret(String input) {
                      return input;
                  }
              }
              """
          ),
          java(
            """
              import org.junit.Test;
              import org.powermock.api.mockito.PowerMockito;

              import static org.powermock.api.support.membermodification.MemberMatcher.method;

              public class ServiceTest {
                  @Test
                  public void test() throws Exception {
                      Service service = PowerMockito.spy(new Service());

                      // Given
                      PowerMockito.when(service, method(Service.class, "secret")).withArguments("in").thenReturn("out");
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.powermock.api.mockito.PowerMockito;

              import static org.powermock.api.support.membermodification.MemberMatcher.method;

              public class ServiceTest {
                  @Test
                  public void test() throws Exception {
                      Service service = PowerMockito.spy(new Service());

                      // Given
                      /* `PowerMockito.when` with more than one argument could not be migrated automatically; migrate it manually to replace PowerMock */
                      PowerMockito.when(service, method(Service.class, "secret")).withArguments("in").thenReturn("out");
                  }
              }
              """
          )
        );
    }
}
