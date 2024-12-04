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
    void replaceEasyMockVerifyByMockitoVerify() {
        //language=java
        rewriteRun(
          java(
                """
              import static org.easymock.EasyMock.*;

              public class ExampleTest {
                  public void testServiceMethod() {
                      Dependency dependency = createNiceMock(Dependency.class);
                      expect(dependency.action("", 2)).andReturn("result");

                      Dependency dependency2 = createNiceMock(Dependency.class);
                      expect(dependency2.action("", 2)).andReturn("result");
                      expect(dependency2.action2());

                      Dependency dependency3 = createNiceMock(Dependency.class);
                      expect(dependency3.action("A", 1)).andReturn("result");
                      expect(dependency3.action2()).andReturn("result");
                      expect(dependency3.action3(3.3)).andReturn("result");

                      verify(dependency);
                      verify(dependency2);
                      verify(dependency3);
                  }

                  interface Dependency {
                      String action(String s, int i);
                      String action2();
                      String action3(double d);
                  }
              }
              """,
            """
              import static org.easymock.EasyMock.expect;
              import static org.easymock.EasyMock.createNiceMock;
              import static org.mockito.Mockito.verify;

              public class ExampleTest {
                  public void testServiceMethod() {
                      Dependency dependency = createNiceMock(Dependency.class);
                      expect(dependency.action("", 2)).andReturn("result");

                      Dependency dependency2 = createNiceMock(Dependency.class);
                      expect(dependency2.action("", 2)).andReturn("result");
                      expect(dependency2.action2());

                      Dependency dependency3 = createNiceMock(Dependency.class);
                      expect(dependency3.action("A", 1)).andReturn("result");
                      expect(dependency3.action2()).andReturn("result");
                      expect(dependency3.action3(3.3)).andReturn("result");

                      verify(dependency).action("", 2);
                      verify(dependency2).action("", 2);
                      verify(dependency2).action2();
                      verify(dependency3).action("A", 1);
                      verify(dependency3).action2();
                      verify(dependency3).action3(3.3);
                  }

                  interface Dependency {
                      String action(String s, int i);
                      String action2();
                      String action3(double d);
                  }
              }
              """)
        );
    }

    @Test
    void simpleReplacement() {
        //language=java
        rewriteRun(
          java(
                """
              import static org.easymock.EasyMock.*;

              public class ExampleTest {
                  public void testServiceMethod() {
                      Dependency dependency = createNiceMock(Dependency.class);
                      expect(dependency.action()).andReturn("result");
                      verify(dependency);
                  }

                  interface Dependency {
                      String action();
                  }
              }
              """,
            """
              import static org.easymock.EasyMock.expect;
              import static org.easymock.EasyMock.createNiceMock;
              import static org.mockito.Mockito.verify;

              public class ExampleTest {
                  public void testServiceMethod() {
                      Dependency dependency = createNiceMock(Dependency.class);
                      expect(dependency.action()).andReturn("result");
                      verify(dependency).action();
                  }

                  interface Dependency {
                      String action();
                  }
              }
              """)
        );
    }

    @Test
    void simpleReplacementWithArguments() {
        //language=java
        rewriteRun(
          java(
                """
              import static org.easymock.EasyMock.*;

              public class ExampleTest {
                  public void testServiceMethod() {
                      Dependency dependency = createNiceMock(Dependency.class);
                      expect(dependency.action("", 2)).andReturn("result");
                      verify(dependency);
                  }

                  interface Dependency {
                      String action(String s, int i);
                  }
              }
              """,
            """
              import static org.easymock.EasyMock.expect;
              import static org.easymock.EasyMock.createNiceMock;
              import static org.mockito.Mockito.verify;

              public class ExampleTest {
                  public void testServiceMethod() {
                      Dependency dependency = createNiceMock(Dependency.class);
                      expect(dependency.action("", 2)).andReturn("result");
                      verify(dependency).action("", 2);
                  }

                  interface Dependency {
                      String action(String s, int i);
                  }
              }
              """)
        );
    }

    @Test
    void replacementWithMultipleMethods() {
        //language=java
        rewriteRun(
          java(
                """
              import static org.easymock.EasyMock.*;

              public class ExampleTest {
                  public void testServiceMethod() {
                      Dependency dependency = createNiceMock(Dependency.class);
                      expect(dependency.action("", 2)).andReturn("result");
                      expect(dependency.action2()).andReturn("result");
                      verify(dependency);
                  }

                  interface Dependency {
                      String action(String s, int i);
                      String action2();
                  }
              }
              """,
            """
              import static org.easymock.EasyMock.expect;
              import static org.easymock.EasyMock.createNiceMock;
              import static org.mockito.Mockito.verify;

              public class ExampleTest {
                  public void testServiceMethod() {
                      Dependency dependency = createNiceMock(Dependency.class);
                      expect(dependency.action("", 2)).andReturn("result");
                      expect(dependency.action2()).andReturn("result");
                      verify(dependency).action("", 2);
                      verify(dependency).action2();
                  }

                  interface Dependency {
                      String action(String s, int i);
                      String action2();
                  }
              }
              """)
        );
    }

    @Test
    void verifyWithoutExpect() {
        //language=java
        rewriteRun(
          java(
            """
          import static org.easymock.EasyMock.*;

          public class ExampleTest {
              public void testServiceMethod() {
                  Dependency dependency = createNiceMock(Dependency.class);
                  verify(dependency);
              }

              interface Dependency {}
          }
          """,
            """
              import static org.easymock.EasyMock.createNiceMock;
              import static org.easymock.EasyMock.verify;

              public class ExampleTest {
                  public void testServiceMethod() {
                      Dependency dependency = createNiceMock(Dependency.class);
                      verify(dependency);
                  }

                  interface Dependency {}
              }
              """)
        );
    }

    @Test
    void verifyAndDependenciesAllOverThePlace() {
        //language=java
        rewriteRun(
          java(
                """
              import static org.easymock.EasyMock.*;

              public class ExampleTest {
                  public void testServiceMethod() {
                      verify(null);

                      Dependency dependency = createNiceMock(Dependency.class);
                      expect(dependency.action()).andReturn("result");

                      Dependency dependency3 = createNiceMock(Dependency.class);
                      verify(dependency3);

                      Dependency dependency2 = createNiceMock(Dependency.class);
                      expect(dependency2.action()).andReturn("result");

                      verify(dependency2);
                      verify(dependency);
                  }

                  interface Dependency {
                      String action();
                  }
              }
              """,
            """
              import static org.easymock.EasyMock.*;
              import static org.mockito.Mockito.verify;

              public class ExampleTest {
                  public void testServiceMethod() {
                      verify(null);

                      Dependency dependency = createNiceMock(Dependency.class);
                      expect(dependency.action()).andReturn("result");

                      Dependency dependency3 = createNiceMock(Dependency.class);
                      verify(dependency3);

                      Dependency dependency2 = createNiceMock(Dependency.class);
                      expect(dependency2.action()).andReturn("result");

                      verify(dependency2).action();
                      verify(dependency).action();
                  }

                  interface Dependency {
                      String action();
                  }
              }
              """)
        );
    }
}
