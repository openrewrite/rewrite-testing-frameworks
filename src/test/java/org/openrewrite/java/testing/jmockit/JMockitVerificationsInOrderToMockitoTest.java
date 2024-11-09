/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.testing.jmockit;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.testing.jmockit.JMockitTestUtils.setDefaultParserSettings;

/**
 * Not doing full testing of VerificationsInOrder as it is covered in JMockitVerificationsToMockitoTest
 */
class JMockitVerificationsInOrderToMockitoTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        setDefaultParserSettings(spec);
    }

    @DocumentExample
    @Test
    void whenMultipleMocks() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().identifiers(false).build()),
          java(
            """
              import mockit.VerificationsInOrder;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object obj;

                  @Mocked
                  String str;

                  void test() {
                      obj.wait(10L, 10);
                      str.notify();
                      new VerificationsInOrder() {{
                          obj.wait(anyLong, anyInt);
                          str.notify();
                      }};
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.InOrder;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.mockito.Mockito.*;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object obj;

                  @Mock
                  String str;

                  void test() {
                      obj.wait(10L, 10);
                      str.notify();
                      InOrder inOrder = inOrder(obj, str);
                      inOrder.verify(obj).wait(anyLong(), anyInt());
                      inOrder.verify(str).notify();
                  }
              }
              """
          )
        );
    }

    @Test
    void whenTimes() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().identifiers(false).build()),
          java(
            """
              import mockit.VerificationsInOrder;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object obj;

                  void test() {
                      obj.wait(10L, 10);
                      obj.wait(10L, 10);
                      obj.wait();
                      new VerificationsInOrder() {{
                          obj.wait(anyLong, anyInt);
                          times = 2;
                          obj.wait();
                      }};
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.InOrder;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.mockito.Mockito.*;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object obj;

                  void test() {
                      obj.wait(10L, 10);
                      obj.wait(10L, 10);
                      obj.wait();
                      InOrder inOrder = inOrder(obj);
                      inOrder.verify(obj, times(2)).wait(anyLong(), anyInt());
                      inOrder.verify(obj).wait();
                  }
              }
              """
          )
        );
    }

    @Test
    void whenThreeBlocks() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().identifiers(false).build()),
          java(
            """
              import mockit.VerificationsInOrder;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object obj;

                  void test() {
                      obj.wait(10L, 10);
                      obj.wait();
                      new VerificationsInOrder() {{
                          obj.wait(anyLong, anyInt);
                          obj.wait();
                      }};

                      obj.wait();
                      new VerificationsInOrder() {{
                          obj.wait();
                      }};

                      obj.wait(10L, 10);
                      new VerificationsInOrder() {{
                          obj.wait(anyLong, anyInt);
                      }};
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.InOrder;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.mockito.Mockito.*;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object obj;

                  void test() {
                      obj.wait(10L, 10);
                      obj.wait();
                      InOrder inOrder = inOrder(obj);
                      inOrder.verify(obj).wait(anyLong(), anyInt());
                      inOrder.verify(obj).wait();

                      obj.wait();
                      InOrder inOrder1 = inOrder(obj);
                      inOrder1.verify(obj).wait();

                      obj.wait(10L, 10);
                      InOrder inOrder2 = inOrder(obj);
                      inOrder2.verify(obj).wait(anyLong(), anyInt());
                  }
              }
              """
          )
        );
    }

    @Test
    void whenTwoBlocks() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().identifiers(false).build()),
          java(
            """
              import mockit.VerificationsInOrder;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object obj;

                  void test() {
                      obj.wait(10L, 10);
                      obj.wait();
                      new VerificationsInOrder() {{
                          obj.wait(anyLong, anyInt);
                          obj.wait();
                      }};

                      obj.wait();
                      obj.notify();
                      new VerificationsInOrder() {{
                          obj.wait();
                          obj.notify();
                      }};
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.InOrder;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.mockito.Mockito.*;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object obj;

                  void test() {
                      obj.wait(10L, 10);
                      obj.wait();
                      InOrder inOrder = inOrder(obj);
                      inOrder.verify(obj).wait(anyLong(), anyInt());
                      inOrder.verify(obj).wait();

                      obj.wait();
                      obj.notify();
                      InOrder inOrder1 = inOrder(obj);
                      inOrder1.verify(obj).wait();
                      inOrder1.verify(obj).notify();
                  }
              }
              """
          )
        );
    }
}
