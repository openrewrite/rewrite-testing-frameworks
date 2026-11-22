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

class RemovePowerMockObjectFactoryTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .dependsOn(
              //language=java
              """
                package org.powermock.modules.testng;
                public class PowerMockObjectFactory implements org.testng.IObjectFactory {
                    public Object newInstance(java.lang.reflect.Constructor constructor, Object... params) {
                        return null;
                    }
                }
                """,
              //language=java
              """
                package org.testng;
                public interface IObjectFactory {
                    Object newInstance(java.lang.reflect.Constructor constructor, Object... params);
                }
                """
            )
            .classpathFromResources(new InMemoryExecutionContext(), "testng-7"))
          .recipe(new RemovePowerMockObjectFactory());
    }

    @DocumentExample
    @Test
    void removesObjectFactoryMethod() {
        //language=java
        rewriteRun(
          java(
            """
              import org.testng.IObjectFactory;
              import org.testng.annotations.BeforeClass;
              import org.testng.annotations.ObjectFactory;
              import org.testng.annotations.Test;

              public class PermissionManagerServiceTest {

                  @ObjectFactory
                  public IObjectFactory getObjectFactory() {
                      return new org.powermock.modules.testng.PowerMockObjectFactory();
                  }

                  @BeforeClass
                  public void init() {
                  }

                  @Test
                  public void testCreatePermission() {
                  }
              }
              """,
            """
              import org.testng.annotations.BeforeClass;
              import org.testng.annotations.Test;

              public class PermissionManagerServiceTest {

                  @BeforeClass
                  public void init() {
                  }

                  @Test
                  public void testCreatePermission() {
                  }
              }
              """
          )
        );
    }

    @Test
    void importedObjectFactoryLeavesNoImportsBehind() {
        //language=java
        rewriteRun(
          java(
            """
              import org.powermock.modules.testng.PowerMockObjectFactory;
              import org.testng.IObjectFactory;
              import org.testng.annotations.ObjectFactory;

              public class MyTest {
                  @ObjectFactory
                  public IObjectFactory objectFactory() {
                      return new PowerMockObjectFactory();
                  }
              }
              """,
            """
              public class MyTest {
              }
              """
          )
        );
    }

    @Test
    void keepsObjectFactoryThatDoesMore() {
        //language=java
        rewriteRun(
          java(
            """
              import org.powermock.modules.testng.PowerMockObjectFactory;
              import org.testng.IObjectFactory;
              import org.testng.annotations.ObjectFactory;

              public class MyTest {
                  @ObjectFactory
                  public IObjectFactory objectFactory() {
                      System.setProperty("a", "b");
                      return new PowerMockObjectFactory();
                  }
              }
              """
          )
        );
    }
}
