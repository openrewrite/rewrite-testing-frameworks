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
import org.openrewrite.DocumentExample;
package org.openrewrite.java.testing.jmockit;

import org.junit.Ignore;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.testing.jmockit.JMockitTestUtils.setDefaultParserSettings;

class JMockitBlockToMockitoTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        setDefaultParserSettings(spec);
    }

    @DocumentExample
    @Test
    void whenTimesAndResult() {
        //language=java
        rewriteRun(
                java(
                        """
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  new Expectations() {{
                                      myObject.toString();
                                      result = "foo";
                                      times = 2;
                                  }};
                                  assertEquals("foo", myObject.toString());
                                  assertEquals("foo", myObject.toString());
                              }
                          }
                          """,
                        """
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          import static org.mockito.Mockito.*;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              void test() {
                                  doReturn("foo").when(myObject).toString();
                                  assertEquals("foo", myObject.toString());
                                  assertEquals("foo", myObject.toString());
                                  verify(myObject, times(2)).toString();
                              }
                          }
                          """
                )
        );
    }

    @Test
    void whenTimesAndResultWithInThrow() {
        //language=java
        rewriteRun(
                java(
                        """
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertThrows;
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  assertThrows(IllegalStateException.class, () -> {
                                      new Expectations() {{
                                          myObject.toString();
                                          result = "foo";
                                          times = 2;
                                      }};
                                      assertEquals("foo", myObject.toString());
                                      assertEquals("foo", myObject.toString());
                                  });
                              }
                          }
                          """,
                        """
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertThrows;
                          import static org.mockito.Mockito.*;
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              void test() {
                                  assertThrows(IllegalStateException.class, () -> {
                                      doReturn("foo").when(myObject).toString();
                                      assertEquals("foo", myObject.toString());
                                      assertEquals("foo", myObject.toString());
                                      verify(myObject, times(2)).toString();
                                  });
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
                java(
                        """
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          import static org.junit.jupiter.api.Assertions.assertNull;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  new Expectations() {{
                                      myObject.toString();
                                      times = 2;
                                  }};
                                  assertNull(myObject.toString());
                                  assertNull(myObject.toString());
                              }
                          }
                          """,
                        """
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          import static org.junit.jupiter.api.Assertions.assertNull;
                          import static org.mockito.Mockito.*;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              void test() {
                                  doReturn(null).when(myObject).toString();
                                  assertNull(myObject.toString());
                                  assertNull(myObject.toString());
                                  verify(myObject, times(2)).toString();
                              }
                          }
                          """
                )
        );
    }

    @Test
    void whenNoTimesNoResult() {
        //language=java
        rewriteRun(
                java(
                        """
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          import static org.junit.jupiter.api.Assertions.assertNull;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  new Expectations() {{
                                      myObject.toString();
                                  }};
                                  assertNull(myObject.toString());
                              }
                          }
                          """,
                        """
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          import static org.junit.jupiter.api.Assertions.assertNull;
                          import static org.mockito.Mockito.*;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              void test() {
                                  doReturn(null).when(myObject).toString();
                                  assertNull(myObject.toString());
                                  verify(myObject, atLeast(1)).toString();
                              }
                          }
                          """
                )
        );
    }

    @Test
    void whenResult() {
        //language=java
        rewriteRun(
                java(
                        """
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertThrows;
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  assertThrows(IllegalStateException.class, () -> {
                                      new Expectations() {{
                                          myObject.toString();
                                          result = "foo";
                                      }};
                                      assertEquals("foo", myObject.toString());
                                  });
                              }
                          }
                          """,
                        """
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertThrows;
                          import static org.mockito.Mockito.*;
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              void test() {
                                  assertThrows(IllegalStateException.class, () -> {
                                      doReturn("foo").when(myObject).toString();
                                      assertEquals("foo", myObject.toString());
                                      verify(myObject, atLeast(1)).toString();
                                  });
                              }
                          }
                          """
                )
        );
    }

    @Test
    void whenThrow() {
        //language=java
        rewriteRun(
                java(
                        """
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertThrows;
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  assertThrows(IllegalStateException.class, () -> {
                                      new Expectations() {{
                                          myObject.toString();
                                          result = new IllegalStateException("foo");
                                      }};
                                      assertEquals("foo", myObject.toString());
                                  });
                              }
                          }
                          """,
                        """
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertThrows;
                          import static org.mockito.Mockito.*;
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              void test() {
                                  assertThrows(IllegalStateException.class, () -> {
                                      doThrow(new IllegalStateException("foo")).when(myObject).toString();
                                      assertEquals("foo", myObject.toString());
                                      verify(myObject, atLeast(1)).toString();
                                  });
                              }
                          }
                          """
                )
        );
    }

    @Test
    void whenMultiResults() {
        //language=java
        rewriteRun(
                java(
                        """
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertThrows;
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  assertThrows(IllegalStateException.class, () -> {
                                      new Expectations() {{
                                          myObject.toString();
                                          result = "foo";
                                          result = "foo1";
                                          result = "foo2";
                                          times = 3;
                                      }};
                                      assertEquals("foo", myObject.toString());
                                      assertEquals("foo1", myObject.toString());
                                      assertEquals("foo2", myObject.toString());
                                  });
                              }
                          }
                          """,
                        """
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertThrows;
                          import static org.mockito.Mockito.*;
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              void test() {
                                  assertThrows(IllegalStateException.class, () -> {
                                      doReturn("foo", "foo1", "foo2").when(myObject).toString();
                                      assertEquals("foo", myObject.toString());
                                      assertEquals("foo1", myObject.toString());
                                      assertEquals("foo2", myObject.toString());
                                      verify(myObject, times(3)).toString();
                                  });
                              }
                          }
                          """
                )
        );
    }

    @Test
    void whenMultiNonLiteralResults() {
        //language=java
        rewriteRun(
                java(
                        """
                          import java.math.BigInteger;
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertThrows;
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  assertThrows(IllegalStateException.class, () -> {
                                      new Expectations() {{
                                          myObject.toString();
                                          result = BigInteger.valueOf(30 * 3).add(BigInteger.ONE).toString();
                                          result = BigInteger.valueOf(30 * 4).add(BigInteger.ONE).toString();
                                          result = BigInteger.valueOf(30 * 5).add(BigInteger.ONE).toString();
                                          times = 3;
                                      }};
                                      assertEquals("foo", myObject.toString());
                                      assertEquals("foo1", myObject.toString());
                                      assertEquals("foo2", myObject.toString());
                                  });
                              }
                          }
                          """,
                        """
                          import java.math.BigInteger;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertThrows;
                          import static org.mockito.Mockito.*;
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              void test() {
                                  assertThrows(IllegalStateException.class, () -> {
                                      doReturn(BigInteger.valueOf(30 * 3).add(BigInteger.ONE).toString(), BigInteger.valueOf(30 * 4).add(BigInteger.ONE).toString(), BigInteger.valueOf(30 * 5).add(BigInteger.ONE).toString()).when(myObject).toString();
                                      assertEquals("foo", myObject.toString());
                                      assertEquals("foo1", myObject.toString());
                                      assertEquals("foo2", myObject.toString());
                                      verify(myObject, times(3)).toString();
                                  });
                              }
                          }
                          """
                )
        );
    }

    @Test
    void whenTimesAndResultComplexType() {
        //language=java
        rewriteRun(
                java(
                        """
                          import java.math.BigInteger;
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  new Expectations() {{
                                      myObject.toString();
                                      result = BigInteger.valueOf(1000 * 3).add(BigInteger.ONE);
                                      times = 2;
                                  }};
                                  assertEquals("foo", myObject.toString());
                                  assertEquals("foo", myObject.toString());
                              }
                          }
                          """,
                        """
                          import java.math.BigInteger;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          import static org.mockito.Mockito.*;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              void test() {
                                  doReturn(BigInteger.valueOf(1000 * 3).add(BigInteger.ONE)).when(myObject).toString();
                                  assertEquals("foo", myObject.toString());
                                  assertEquals("foo", myObject.toString());
                                  verify(myObject, times(2)).toString();
                              }
                          }
                          """
                )
        );
    }

    @Test
    void whenIntResult() {
        //language=java
        rewriteRun(
                java(
                        """
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  new Expectations() {{
                                      myObject.toString();
                                      result = 10;
                                  }};
                                  assertEquals(10, myObject.toString());
                                  new Expectations() {{
                                      myObject.toString();
                                      this.result = 100;
                                  }};
                                  assertEquals(100, myObject.toString());
                              }
                          }
                          """,
                        """
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          import static org.mockito.Mockito.*;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              void test() {
                                  doReturn(10).when(myObject).toString();
                                  assertEquals(10, myObject.toString());
                                  verify(myObject, atLeast(1)).toString();
                                  doReturn(100).when(myObject).toString();
                                  assertEquals(100, myObject.toString());
                                  verify(myObject, atLeast(1)).toString();
                              }
                          }
                          """
                )
        );
    }

    @Test
    void whenMultiExpectationsForm1() {
        //language=java
        rewriteRun(
                java(
                        """
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import static org.junit.jupiter.api.Assertions.assertEquals;

                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  new Expectations() {{
                                      myObject.toString();
                                      result = 10;
                                  }};
                                  new Expectations() {{
                                      myObject.hashCode();
                                      this.result = 100;
                                  }};
                                  assertEquals(10, myObject.toString());
                                  assertEquals(100, myObject.hashCode());
                              }
                          }
                          """,
                        """
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          import static org.mockito.Mockito.*;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              void test() {
                                  doReturn(10).when(myObject).toString();
                                  doReturn(100).when(myObject).hashCode();
                                  assertEquals(10, myObject.toString());
                                  assertEquals(100, myObject.hashCode());
                                  verify(myObject, atLeast(1)).toString();
                                  verify(myObject, atLeast(1)).hashCode();
                              }
                          }
                          """
                )
        );
    }

    @Test
    void whenMultiExpectationsForm2() {
        //language=java
        rewriteRun(
                java(
                        """
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;

                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  new Expectations() {{
                                      myObject.toString();
                                      result = 10;
                                  }
                                  {
                                      myObject.hashCode();
                                      this.result = 100;
                                  }};
                                  assertEquals(10, myObject.toString());
                                  assertEquals(100, myObject.hashCode());
                              }
                          }
                          """,
                        """
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          import static org.mockito.Mockito.*;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              void test() {
                                  doReturn(10).when(myObject).toString();
                                  doReturn(100).when(myObject).hashCode();
                                  assertEquals(10, myObject.toString());
                                  assertEquals(100, myObject.hashCode());
                                  verify(myObject, atLeast(1)).toString();
                                  verify(myObject, atLeast(1)).hashCode();
                              }
                          }
                          """
                )
        );
    }

    @Test
    void whenMultiExpectationsForm3() {
        //language=java
        rewriteRun(
                java(
                        """
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;

                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  new Expectations() {{
                                      myObject.toString();
                                      result = 10;
                                      myObject.hashCode();
                                      this.result = 100;
                                  }};
                                  assertEquals(10, myObject.toString());
                                  assertEquals(100, myObject.hashCode());
                              }
                          }
                          """,
                        """
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          import static org.mockito.Mockito.*;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              void test() {
                                  doReturn(10).when(myObject).toString();
                                  doReturn(100).when(myObject).hashCode();
                                  assertEquals(10, myObject.toString());
                                  assertEquals(100, myObject.hashCode());
                                  verify(myObject, atLeast(1)).toString();
                                  verify(myObject, atLeast(1)).hashCode();
                              }
                          }
                          """
                )
        );
    }


    // my case don't have returns, not handle it now.
    @Ignore
    void whenReturnsInOneLine() {
        //language=java
        rewriteRun(
                java(
                        """
                          class MyObject {
                              public String getSomeField() {
                                  return "X";
                              }
                          }
                          """
                ),
                java(
                        """
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              MyObject myObject;
                          
                              void test() throws RuntimeException {
                                  new Expectations() {{
                                      myObject.getSomeField();
                                      returns("foo", "bar");
                                  }};
                                  assertEquals("foo", myObject.getSomeField());
                                  assertEquals("bar", myObject.getSomeField());
                              }
                          }
                          """,
                        """
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          import static org.mockito.Mockito.when;
                          import static org.mockito.Mockito.atLeast;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              MyObject myObject;
                          
                              void test() throws RuntimeException {
                                  doReturn("foo", "bar").when(myObject).getSomeField();
                                  assertEquals("foo", myObject.getSomeField());
                                  assertEquals("bar", myObject.getSomeField());
                                  verify(myObject, atLeast(1)).getSomeField();
                              }
                          }
                          """
                )
        );
    }

    @Test
    void whenClassArgumentMatcher() {
        //language=java
        rewriteRun(
                java(
                        """
                          import java.util.List;
                          
                          class MyObject {
                              public String getSomeField(List<String> input) {
                                  return "X";
                              }
                              public String getSomeOtherField(Object input) {
                                  return "Y";
                              }
                              public String getSomeArrayField(Object input) {
                                  return "Z";
                              }
                          }
                          """
                ),
                java(
                        """
                          import java.util.ArrayList;
                          import java.util.List;
                          
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertNull;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              MyObject myObject;
                          
                              void test() {
                                  new Expectations() {{
                                      myObject.getSomeField((List<String>) any);
                                      result = null;
                                      myObject.getSomeOtherField((Object) any);
                                      result = null;
                                      myObject.getSomeArrayField((byte[]) any);
                                      result = null;
                                  }};
                                  assertNull(myObject.getSomeField(new ArrayList<>()));
                                  assertNull(myObject.getSomeOtherField(new Object()));
                                  assertNull(myObject.getSomeArrayField(new byte[0]));
                              }
                          }
                          """,
                        """
                          import java.util.ArrayList;
                          import java.util.List;
                          
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertNull;
                          import static org.mockito.ArgumentMatchers.*;
                          import static org.mockito.Mockito.*;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              MyObject myObject;
                          
                              void test() {
                                  doReturn(null).when(myObject).getSomeField(anyList());
                                  doReturn(null).when(myObject).getSomeOtherField(any(Object.class));
                                  doReturn(null).when(myObject).getSomeArrayField(any(byte[].class));
                                  assertNull(myObject.getSomeField(new ArrayList<>()));
                                  assertNull(myObject.getSomeOtherField(new Object()));
                                  assertNull(myObject.getSomeArrayField(new byte[0]));
                                  verify(myObject, atLeast(1)).getSomeField(anyList());
                                  verify(myObject, atLeast(1)).getSomeOtherField(any(Object.class));
                                  verify(myObject, atLeast(1)).getSomeArrayField(any(byte[].class));
                              }
                          }
                          """
                )
        );
    }

    @Test
    void whenMultipleStatements() {
        //language=java
        rewriteRun(
                java(
                        """
                          class MyObject {
                              public String getSomeStringField(String input, long otherInput) {
                                  return "X";
                              }
                              public int getSomeIntField() {
                                  return 0;
                              }
                              public Object getSomeObjectField() {
                                  return new Object();
                              }
                              public void doSomething() {}
                          }
                          """
                ),
                java(
                        """
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          import static org.junit.jupiter.api.Assertions.assertNull;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              @Mocked
                              MyObject myOtherObject;
                          
                              void test() {
                                  new Expectations() {{
                                      myObject.hashCode();
                                      result = 10;
                                      myOtherObject.getSomeObjectField();
                                      result = null;
                                      myObject.wait(anyLong, anyInt);
                                      myOtherObject.getSomeStringField(anyString, anyLong);
                                      result = "foo";
                                  }};
                                  assertEquals(10, myObject.hashCode());
                                  assertNull(myOtherObject.getSomeObjectField());
                                  myObject.wait(10L, 10);
                                  assertEquals("foo", myOtherObject.getSomeStringField("bar", 10L));
                              }
                          }
                          """,
                        """
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          import static org.junit.jupiter.api.Assertions.assertNull;
                          import static org.mockito.ArgumentMatchers.*;
                          import static org.mockito.Mockito.*;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              @Mock
                              MyObject myOtherObject;
                          
                              void test() {
                                  doReturn(10).when(myObject).hashCode();
                                  doReturn(null).when(myOtherObject).getSomeObjectField();
                                  doAnswer(invocation -> null).when(myObject).wait(anyLong(), anyInt());
                                  doReturn("foo").when(myOtherObject).getSomeStringField(anyString(), anyLong());
                                  assertEquals(10, myObject.hashCode());
                                  assertNull(myOtherObject.getSomeObjectField());
                                  myObject.wait(10L, 10);
                                  assertEquals("foo", myOtherObject.getSomeStringField("bar", 10L));
                                  verify(myObject, atLeast(1)).hashCode();
                                  verify(myOtherObject, atLeast(1)).getSomeObjectField();
                                  verify(myObject, atLeast(1)).wait(anyLong(), anyInt());
                                  verify(myOtherObject, atLeast(1)).getSomeStringField(anyString(), anyLong());
                              }
                          }
                          """
                )
        );
    }

    @Test
    void whenEmptyBlock() {
        //language=java
        rewriteRun(
                java(
                        """
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  new Expectations() {{
                                  }};
                                  myObject.wait(1L);
                              }
                          }
                          """,
                        """
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              void test() {
                                  myObject.wait(1L);
                              }
                          }
                          """
                )
        );
    }

    @Test
    void whenNoBody() {
        //language=java
        rewriteRun(
                java(
                        """
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  new Expectations() {{
                                      myObject.toString();
                                      result = "foo";
                                      times = 2;
                                  }};
                              }
                          }
                          """,
                        """
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          import static org.mockito.Mockito.*;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              void test() {
                                  doReturn("foo").when(myObject).toString();
                                  //TODO: testing
                                  verify(myObject, times(2)).toString();
                              }
                          }
                          """
                )
        );
    }

    @Test
    void whenResultIsComplex() {
        //language=java
        rewriteRun(
                java(
                        """
                          import java.util.ArrayList;
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  new Expectations() {{
                                      myObject.toString();
                                      ArrayList<String> list = new ArrayList<String>();
                                      list.add("foo");
                                      list.add(new String("bar"));
                                      list.add("baz");
                                      result = list;
                                      times = 2;
                                  }};
                                  assertEquals("foo", myObject.toString());
                                  assertEquals("foo", myObject.toString());
                              }
                          }
                          """,
                        """
                          import java.util.ArrayList;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          import static org.mockito.Mockito.*;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              void test() {
                                  ArrayList<String> list = new ArrayList<String>();
                                  list.add("foo");
                                  list.add(new String("bar"));
                                  list.add("baz");
                                  doReturn(list).when(myObject).toString();
                                  assertEquals("foo", myObject.toString());
                                  assertEquals("foo", myObject.toString());
                                  verify(myObject, times(2)).toString();
                              }
                          }
                          """
                )
        );
    }

    @Test
    void whenResultIsComplexForm1() {
        //language=java
        rewriteRun(
                java(
                        """
                          import java.util.ArrayList;
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  ArrayList<String> list = new ArrayList<String>();
                                  list.add("foo");
                                  list.add(new String("bar"));
                                  list.add("baz");
                                  new Expectations() {{
                                      myObject.toString();
                                      result = list;
                                      times = 2;
                                  }};
                                  assertEquals("foo", myObject.toString());
                                  assertEquals("foo", myObject.toString());
                              }
                          }
                          """,
                        """
                          import java.util.ArrayList;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          import static org.mockito.Mockito.*;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              void test() {
                                  ArrayList<String> list = new ArrayList<String>();
                                  list.add("foo");
                                  list.add(new String("bar"));
                                  list.add("baz");
                                  doReturn(list).when(myObject).toString();
                                  assertEquals("foo", myObject.toString());
                                  assertEquals("foo", myObject.toString());
                                  verify(myObject, times(2)).toString();
                              }
                          }
                          """
                )
        );
    }

}
