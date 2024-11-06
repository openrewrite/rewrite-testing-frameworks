package org.openrewrite.java.testing.easymock;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class EasyMockToMockitoTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(
            JavaParser.fromJavaVersion()
              .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9", "easymock-5.4.0", "mockito-core-3.12"))
          .recipeFromResources("org.openrewrite.java.testing.easymock.EasyMockToMockito");
    }

    @Test
    @DocumentExample
    void replaceEasyMockByMockito() {
        //language=java
        rewriteRun(
          java("""
              import org.easymock.EasyMock;
              import org.easymock.EasyMockRunner;
              import org.junit.Before;
              import org.junit.Test;
              import org.junit.runner.RunWith;

              import static org.junit.Assert.assertEquals;

              @RunWith(EasyMockRunner.class)
              public class ExampleTest {

                  private Service service;
                  private Dependency dependency;

                  @Before
                  public void setUp() {
                      dependency = EasyMock.createNiceMock(Dependency.class);
                      service = new Service(dependency);
                  }

                  @Test
                  public void testServiceMethod() {
                      EasyMock.expect(dependency.performAction()).andReturn("Mocked Result");
                      EasyMock.replay(dependency);
                      assertEquals("Mocked Result", service.useDependency());
                      EasyMock.verify(dependency);
                  }

                  class Service {
                      private final Dependency dependency;

                      Service(Dependency dependency) {
                          this.dependency = dependency;
                      }

                      String useDependency() {
                          return dependency.performAction();
                      }
                  }

                  interface Dependency {
                      String performAction();
                  }
              }
              """,
            """
              import org.junit.Before;
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.InjectMocks;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;

              import static org.junit.Assert.assertEquals;
              import static org.mockito.Mockito.mock;
              import static org.mockito.Mockito.when;
              import static org.mockito.Mockito.verify;

              @RunWith(MockitoJUnitRunner.class)
              public class ExampleTest {

                  private Service service;
                  private Dependency dependency;

                  @Before
                  public void setUp() {
                      dependency = mock(Dependency.class);
                      service = new Service(dependency);
                  }

                  @Test
                  public void testServiceMethod() {
                      when(dependency.performAction()).thenReturn("Mocked Result");
                      assertEquals("Mocked Result", service.useDependency());
                      verify(dependency).performAction();
                  }

                  class Service {
                      private final Dependency dependency;

                      Service(Dependency dependency) {
                          this.dependency = dependency;
                      }

                      String useDependency() {
                          return dependency.performAction();
                      }
                  }

                  interface Dependency {
                      String performAction();
                  }
              }
              """)
        );
    }
}


