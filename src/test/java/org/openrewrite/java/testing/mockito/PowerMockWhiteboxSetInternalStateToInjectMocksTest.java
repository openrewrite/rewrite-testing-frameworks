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

class PowerMockWhiteboxSetInternalStateToInjectMocksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-4",
              "mockito-core-3.12",
              "powermock-core-1",
              "powermock-reflect-1"
            ))
          .recipe(new PowerMockWhiteboxSetInternalStateToInjectMocks());
    }

    @DocumentExample
    @Test
    void redundantCallOnAnAlreadyInjectedObjectIsRemoved() {
        //language=java
        rewriteRun(
          java(
            """
              class Converter {
              }

              class Facade {
                  private Converter converter;
              }
              """
          ),
          java(
            """
              import org.junit.Before;
              import org.mockito.InjectMocks;
              import org.mockito.Mock;
              import org.powermock.reflect.Whitebox;

              public class FacadeTest {

                  @InjectMocks
                  private Facade testObj;

                  @Mock
                  private Converter converter;

                  @Before
                  public void setUp() {
                      Whitebox.setInternalState(testObj, "converter", converter);
                  }
              }
              """,
            """
              import org.junit.Before;
              import org.mockito.InjectMocks;
              import org.mockito.Mock;

              public class FacadeTest {

                  @InjectMocks
                  private Facade testObj;

                  @Mock
                  private Converter converter;

                  @Before
                  public void setUp() {
                  }
              }
              """
          )
        );
    }

    @Test
    void objectUnderTestConstructedInTheFieldGainsInjectMocks() {
        //language=java
        rewriteRun(
          java(
            """
              class Collaborator {
              }

              class Queue {
                  private Collaborator collaborator;
              }
              """
          ),
          java(
            """
              import org.junit.Before;
              import org.mockito.Mock;
              import org.powermock.reflect.Whitebox;

              public class QueueTest {

                  private Queue queue = new Queue();

                  @Mock
                  private Collaborator collaborator;

                  @Before
                  public void setUp() {
                      Whitebox.setInternalState(queue, "collaborator", collaborator);
                  }
              }
              """,
            """
              import org.junit.Before;
              import org.mockito.InjectMocks;
              import org.mockito.Mock;

              public class QueueTest {

                  @InjectMocks
                  private Queue queue;

                  @Mock
                  private Collaborator collaborator;

                  @Before
                  public void setUp() {
                  }
              }
              """
          )
        );
    }

    @Test
    void leavesCallsWhoseValueIsNotAMock() {
        //language=java
        rewriteRun(
          java(
            """
              class Facade {
                  private String name;
              }
              """
          ),
          java(
            """
              import org.junit.Before;
              import org.mockito.InjectMocks;
              import org.powermock.reflect.Whitebox;

              public class FacadeTest {

                  @InjectMocks
                  private Facade testObj;

                  private String name = "x";

                  @Before
                  public void setUp() {
                      Whitebox.setInternalState(testObj, "name", name);
                  }
              }
              """
          )
        );
    }

    @Test
    void leavesCallsWhoseTargetIsItselfAMock() {
        //language=java
        rewriteRun(
          java(
            """
              class Action {
                  private String identity;
              }
              """
          ),
          java(
            """
              import org.junit.Before;
              import org.mockito.Mock;
              import org.powermock.reflect.Whitebox;

              public class ActionTest {

                  @Mock
                  private Action action;

                  @Mock
                  private String identity;

                  @Before
                  public void setUp() {
                      Whitebox.setInternalState(action, "identity", identity);
                  }
              }
              """
          )
        );
    }

    @Test
    void leavesCallsWhereTheMockNameDoesNotMatchTheField() {
        //language=java
        rewriteRun(
          java(
            """
              class Collaborator {
              }

              class Facade {
                  private Collaborator first;
                  private Collaborator second;
              }
              """
          ),
          java(
            """
              import org.junit.Before;
              import org.mockito.InjectMocks;
              import org.mockito.Mock;
              import org.powermock.reflect.Whitebox;

              public class FacadeTest {

                  @InjectMocks
                  private Facade testObj;

                  @Mock
                  private Collaborator collaboratorMock;

                  @Before
                  public void setUp() {
                      Whitebox.setInternalState(testObj, "second", collaboratorMock);
                  }
              }
              """
          )
        );
    }

    @Test
    void leavesObjectUnderTestThatIsReassignedInSetUp() {
        //language=java
        rewriteRun(
          java(
            """
              class Collaborator {
              }

              class Queue {
                  private Collaborator collaborator;
              }
              """
          ),
          java(
            """
              import org.junit.Before;
              import org.mockito.Mock;
              import org.powermock.reflect.Whitebox;

              public class QueueTest {

                  private Queue queue = new Queue();

                  @Mock
                  private Collaborator collaborator;

                  @Before
                  public void setUp() {
                      queue = new Queue();
                      Whitebox.setInternalState(queue, "collaborator", collaborator);
                  }
              }
              """
          )
        );
    }

    @Test
    void leavesObjectUnderTestMockitoWouldBuildWithAConstructor() {
        //language=java
        rewriteRun(
          java(
            """
              class Collaborator {
              }

              class Notifier {
              }

              class Service {
                  private Collaborator collaborator;

                  Service(Collaborator collaborator, Notifier notifier) {
                      this.collaborator = collaborator;
                  }
              }
              """
          ),
          java(
            """
              import org.junit.Before;
              import org.mockito.InjectMocks;
              import org.mockito.Mock;
              import org.powermock.reflect.Whitebox;

              public class ServiceTest {

                  @InjectMocks
                  private Service service;

                  @Mock
                  private Collaborator collaborator;

                  @Before
                  public void setUp() {
                      Whitebox.setInternalState(service, "collaborator", collaborator);
                  }
              }
              """
          )
        );
    }
}
