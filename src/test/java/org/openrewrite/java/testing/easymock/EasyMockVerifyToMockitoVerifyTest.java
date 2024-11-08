/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.easymock;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class EasyMockVerifyToMockitoVerifyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(
            JavaParser.fromJavaVersion()
              .classpathFromResources(new InMemoryExecutionContext(), "junit-4.13.2", "junit-jupiter-api-5.9", "easymock-5.4.0", "mockito-core-5.*"))
          .recipe(new EasyMockVerifyToMockitoVerify());
    }

    @Test
    @DocumentExample
    void replaceEasyMockByMockito() {
        //language=java
        rewriteRun(
          java("""
              import static org.easymock.EasyMock.*;
              import static org.easymock.EasyMock.verify;

              public class ExampleTest {
                  public void testServiceMethod() {
                      Dependency dependency = createMock(Dependency.class);
                      expect(dependency.performAction());
                      expect(dependency.performAction2()).andReturn(3);
                      replay(dependency);
                      verify(dependency);
                      expect(dependency.performAction3()).andReturn("Mocked Result");
                      verify(dependency);
                  }

                  interface Dependency {
                      String performAction();
                      int performAction2();
                      String performAction3();
                  }
              }
              """,
            """
              import static org.easymock.EasyMock.*;
              import static org.mockito.Mockito.verify;

              public class ExampleTest {
                  public void testServiceMethod() {
                      Dependency dependency = createMock(Dependency.class);
                      expect(dependency.performAction());
                      expect(dependency.performAction2()).andReturn(3);
                      replay(dependency);
                      verify(dependency).performAction();
                      verify(dependency).performAction2();
                      expect(dependency.performAction3()).andReturn("Mocked Result");
                      verify(dependency).performAction3();
                  }

                  interface Dependency {
                      String performAction();
                      int performAction2();
                      String performAction3();
                  }
              }""")
        );
    }
}
