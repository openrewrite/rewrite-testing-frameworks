/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MockConstructionToTryWithResourcesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MockConstructionToTryWithResources())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5", "mockito-core-5")
            //language=java
            .dependsOn(
              """
                public class A {
                    public String method(Object arg) {
                        return "original";
                   }
                }
                """
            )
          );
    }

    @DocumentExample
    @Test
    void shouldWrapMockConstructionInTryWithResources() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.mockito.MockedConstruction;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.mockConstruction;
              import static org.mockito.Mockito.when;
              import static org.mockito.ArgumentMatchers.any;

              class TestClass {
                  @Test
                  void test() {
                      MockedConstruction<A> aMockedConstruction = mockConstruction(A.class, (mock, context) -> {
                          when(mock.method(any())).thenReturn("XYZ");
                      });
                      A instance = new A();
                      assertEquals("XYZ", instance.method("test"));
                      aMockedConstruction.close();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.mockito.MockedConstruction;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.mockConstruction;
              import static org.mockito.Mockito.when;
              import static org.mockito.ArgumentMatchers.any;

              class TestClass {
                  @Test
                  void test() {
                      try (MockedConstruction<A> aMockedConstruction = mockConstruction(A.class, (mock, context) -> {
                               when(mock.method(any())).thenReturn("XYZ");
                           })) {
                          A instance = new A();
                          assertEquals("XYZ", instance.method("test"));
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldNotChangeIfAlreadyInTryWithResources() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.mockito.MockedConstruction;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.mockConstruction;
              import static org.mockito.Mockito.when;
              import static org.mockito.ArgumentMatchers.any;

              class TestClass {
                  @Test
                  void test() {
                      try (MockedConstruction<A> aMockedConstruction = mockConstruction(A.class, (mock, context) -> {
                          when(mock.method(any())).thenReturn("XYZ");
                      })) {
                          A instance = new A();
                          assertEquals("XYZ", instance.method("test"));
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldNotChangeIfNoCloseCall() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.mockito.MockedConstruction;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.mockConstruction;
              import static org.mockito.Mockito.when;
              import static org.mockito.ArgumentMatchers.any;

              class TestClass {
                  @Test
                  void test() {
                      MockedConstruction<A> aMockedConstruction = mockConstruction(A.class, (mock, context) -> {
                          when(mock.method(any())).thenReturn("XYZ");
                      });
                      A instance = new A();
                      assertEquals("XYZ", instance.method("test"));
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldHandleMultipleStatementsBetweenDeclarationAndClose() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.mockito.MockedConstruction;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.junit.jupiter.api.Assertions.assertNotNull;
              import static org.mockito.Mockito.mockConstruction;
              import static org.mockito.Mockito.when;
              import static org.mockito.ArgumentMatchers.any;

              class TestClass {
                  @Test
                  void test() {
                      MockedConstruction<A> aMockedConstruction = mockConstruction(A.class, (mock, context) -> {
                          when(mock.method(any())).thenReturn("XYZ");
                      });
                      A instance1 = new A();
                      A instance2 = new A();
                      assertNotNull(instance1);
                      assertNotNull(instance2);
                      assertEquals("XYZ", instance1.method("test"));
                      assertEquals("XYZ", instance2.method("test"));
                      aMockedConstruction.close();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.mockito.MockedConstruction;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.junit.jupiter.api.Assertions.assertNotNull;
              import static org.mockito.Mockito.mockConstruction;
              import static org.mockito.Mockito.when;
              import static org.mockito.ArgumentMatchers.any;

              class TestClass {
                  @Test
                  void test() {
                      try (MockedConstruction<A> aMockedConstruction = mockConstruction(A.class, (mock, context) -> {
                               when(mock.method(any())).thenReturn("XYZ");
                           })) {
                          A instance1 = new A();
                          A instance2 = new A();
                          assertNotNull(instance1);
                          assertNotNull(instance2);
                          assertEquals("XYZ", instance1.method("test"));
                          assertEquals("XYZ", instance2.method("test"));
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldRemoveRedundantCloseInTryWithResources() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.mockito.MockedConstruction;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.mockConstruction;
              import static org.mockito.Mockito.when;
              import static org.mockito.ArgumentMatchers.any;

              class TestClass {
                  @Test
                  void test() {
                      try (MockedConstruction<A> aMockedConstruction = mockConstruction(A.class, (mock, context) -> {
                          when(mock.method(any())).thenReturn("XYZ");
                      })) {
                          A instance = new A();
                          assertEquals("XYZ", instance.method("test"));
                          aMockedConstruction.close();
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.mockito.MockedConstruction;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.mockConstruction;
              import static org.mockito.Mockito.when;
              import static org.mockito.ArgumentMatchers.any;

              class TestClass {
                  @Test
                  void test() {
                      try (MockedConstruction<A> aMockedConstruction = mockConstruction(A.class, (mock, context) -> {
                          when(mock.method(any())).thenReturn("XYZ");
                      })) {
                          A instance = new A();
                          assertEquals("XYZ", instance.method("test"));
                      }
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/moderneinc/customer-requests/issues/1731")
    @Test
    void shouldHandleFullyQualifiedMockitoMockConstruction() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.mockito.MockedConstruction;
              import org.mockito.Mockito;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.when;
              import static org.mockito.ArgumentMatchers.any;

              class TestClass {
                  @Test
                  void test() {
                      MockedConstruction<A> aMockedConstruction = Mockito.mockConstruction(A.class, (mock, context) -> {
                          when(mock.method(any())).thenReturn("XYZ");
                      });
                      A instance = new A();
                      assertEquals("XYZ", instance.method("test"));
                      aMockedConstruction.close();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.mockito.MockedConstruction;
              import org.mockito.Mockito;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.when;
              import static org.mockito.ArgumentMatchers.any;

              class TestClass {
                  @Test
                  void test() {
                      try (MockedConstruction<A> aMockedConstruction = Mockito.mockConstruction(A.class, (mock, context) -> {
                               when(mock.method(any())).thenReturn("XYZ");
                           })) {
                          A instance = new A();
                          assertEquals("XYZ", instance.method("test"));
                      }
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/moderneinc/customer-requests/issues/1731")
    @Test
    void shouldHandleMultipleWhensInLambda() {
        //language=java
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5", "mockito-core-5")
            //language=java
            .dependsOn(
              """
                import java.util.List;
                public class ItemInflator {
                    public List<String> inflateNodesWithItemStore(List<Object> items) {
                        return null;
                    }
                    public List<String> inflateWithIRO(List<Object> items) {
                        return null;
                    }
                }
                """
            )
          ),
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.mockito.MockedConstruction;
              import org.mockito.Mockito;

              import java.util.List;

              import static org.junit.jupiter.api.Assertions.assertNotNull;
              import static org.mockito.Mockito.when;
              import static org.mockito.ArgumentMatchers.anyList;

              class TestClass {
                  private static final List<String> productList = List.of("product1", "product2");

                  @Test
                  void test() {
                      MockedConstruction<ItemInflator> itemInflatorMockedConstruction = Mockito.mockConstruction(ItemInflator.class, (mock, context) -> {
                          when(mock.inflateNodesWithItemStore(anyList())).thenReturn(productList);
                          when(mock.inflateWithIRO(anyList())).thenReturn(productList);
                      });
                      ItemInflator inflator = new ItemInflator();
                      assertNotNull(inflator.inflateNodesWithItemStore(List.of()));
                      assertNotNull(inflator.inflateWithIRO(List.of()));
                      itemInflatorMockedConstruction.close();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.mockito.MockedConstruction;
              import org.mockito.Mockito;

              import java.util.List;

              import static org.junit.jupiter.api.Assertions.assertNotNull;
              import static org.mockito.Mockito.when;
              import static org.mockito.ArgumentMatchers.anyList;

              class TestClass {
                  private static final List<String> productList = List.of("product1", "product2");

                  @Test
                  void test() {
                      try (MockedConstruction<ItemInflator> itemInflatorMockedConstruction = Mockito.mockConstruction(ItemInflator.class, (mock, context) -> {
                               when(mock.inflateNodesWithItemStore(anyList())).thenReturn(productList);
                               when(mock.inflateWithIRO(anyList())).thenReturn(productList);
                           })) {
                          ItemInflator inflator = new ItemInflator();
                          assertNotNull(inflator.inflateNodesWithItemStore(List.of()));
                          assertNotNull(inflator.inflateWithIRO(List.of()));
                      }
                  }
              }
              """
          )
        );
    }
}
