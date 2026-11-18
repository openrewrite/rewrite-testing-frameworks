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

class PowerMockRepositoryRemoveControlToCloseStaticMockTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "powermock-core-1", "mockito-core-3.12", "junit-4"))
          .recipe(new PowerMockRepositoryRemoveControlToCloseStaticMock());
    }

    @DocumentExample
    @Test
    void closesTheMockedStatic() {
        //language=java
        rewriteRun(
          java(
            """
              enum Platform {
                  UNIX, WINDOWS;
                  static Platform current() {
                      return UNIX;
                  }
              }
              """
          ),
          java(
            """
              import org.junit.Test;
              import org.mockito.MockedStatic;
              import org.mockito.Mockito;
              import org.powermock.core.MockRepository;

              public class JobTest {
                  private MockedStatic<Platform> mockedPlatform;

                  @Test
                  public void usesAgentPlatform() {
                      mockedPlatform = Mockito.mockStatic(Platform.class);
                      mockedPlatform.when(Platform::current).thenReturn(Platform.WINDOWS);

                      // reset mock of Platform class
                      MockRepository.removeClassMethodInvocationControl(Platform.class);
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.mockito.MockedStatic;
              import org.mockito.Mockito;

              public class JobTest {
                  private MockedStatic<Platform> mockedPlatform;

                  @Test
                  public void usesAgentPlatform() {
                      mockedPlatform = Mockito.mockStatic(Platform.class);
                      mockedPlatform.when(Platform::current).thenReturn(Platform.WINDOWS);

                      // reset mock of Platform class
                      if (mockedPlatform != null) {
                          mockedPlatform.closeOnDemand();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void leavesTypesThatAreNotMockedStatically() {
        //language=java
        rewriteRun(
          java(
            """
              import org.powermock.core.MockRepository;

              public class JobTest {
                  void test() {
                      MockRepository.removeClassMethodInvocationControl(String.class);
                  }
              }
              """
          )
        );
    }
}
