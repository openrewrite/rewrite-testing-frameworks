/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.testing.jmockit;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.testing.jmockit.JMockitTestUtils.*;

class JMockitNonStrictExpectationsToMockitoTest implements RewriteTest {
    private static final String LEGACY_JMOCKIT_DEPENDENCY = "jmockit-1.22";

    @Override
    public void defaults(RecipeSpec spec) {
        setParserSettings(spec, JUNIT_4_DEPENDENCY, LEGACY_JMOCKIT_DEPENDENCY, MOCKITO_CORE_DEPENDENCY);
    }

    @Test
    void whenNullResult() {
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
              import mockit.NonStrictExpectations;
              import mockit.Mocked;
              import mockit.integration.junit4.JMockit;
              import org.junit.runner.RunWith;

              import static org.junit.Assert.assertNull;

              @RunWith(JMockit.class)
              class MyTest {
                  @Mocked
                  MyObject myObject;

                  void test() {
                      new NonStrictExpectations() {{
                          myObject.getSomeField();
                          result = null;
                      }};
                      assertNull(myObject.getSomeField());
                  }
              }
              """,
            """
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;

              import static org.junit.Assert.assertNull;
              import static org.mockito.Mockito.lenient;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  MyObject myObject;

                  void test() {
                      lenient().when(myObject.getSomeField()).thenReturn(null);
                      assertNull(myObject.getSomeField());
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
              class MyObject {
                  public int getSomeField() {
                      return 0;
                  }
              }
              """
          ),
          java(
            """
              import mockit.NonStrictExpectations;
              import mockit.Mocked;
              import mockit.integration.junit4.JMockit;
              import org.junit.runner.RunWith;

              import static org.junit.Assert.assertEquals;

              @RunWith(JMockit.class)
              class MyTest {
                  @Mocked
                  MyObject myObject;

                  void test() {
                      new NonStrictExpectations() {{
                          myObject.getSomeField();
                          result = 10;
                      }};
                      assertEquals(10, myObject.getSomeField());
                      new NonStrictExpectations() {{
                          myObject.getSomeField();
                          this.result = 100;
                      }};
                      assertEquals(100, myObject.getSomeField());
                  }
              }
              """,
            """
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;

              import static org.junit.Assert.assertEquals;
              import static org.mockito.Mockito.lenient;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  MyObject myObject;

                  void test() {
                      lenient().when(myObject.getSomeField()).thenReturn(10);
                      assertEquals(10, myObject.getSomeField());
                      lenient().when(myObject.getSomeField()).thenReturn(100);
                      assertEquals(100, myObject.getSomeField());
                  }
              }
              """
          )
        );
    }

    @Test
    void whenStringResult() {
        //language=java
        rewriteRun(
          java(
            """
              class MyObject {
                  public String getSomeField(String s) {
                      return "X";
                  }
              }
              """
          ),
          java(
            """
              import mockit.NonStrictExpectations;
              import mockit.Mocked;
              import mockit.integration.junit4.JMockit;
              import org.junit.runner.RunWith;

              import static org.junit.Assert.assertEquals;

              @RunWith(JMockit.class)
              class MyTest {
                  @Mocked
                  MyObject myObject;

                  void test() {
                      new NonStrictExpectations() {{
                          myObject.getSomeField(anyString);
                          result = "foo";
                      }};
                      assertEquals("foo", myObject.getSomeField("bar"));
                  }
              }
              """,
            """
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;

              import static org.junit.Assert.assertEquals;
              import static org.mockito.Mockito.anyString;
              import static org.mockito.Mockito.lenient;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  MyObject myObject;

                  void test() {
                      lenient().when(myObject.getSomeField(anyString())).thenReturn("foo");
                      assertEquals("foo", myObject.getSomeField("bar"));
                  }
              }
              """
          )
        );
    }

    @Test
    void whenVariableResult() {
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
              import mockit.NonStrictExpectations;
              import mockit.Mocked;
              import mockit.integration.junit4.JMockit;
              import org.junit.runner.RunWith;

              import static org.junit.Assert.assertEquals;

              @RunWith(JMockit.class)
              class MyTest {
                  @Mocked
                  MyObject myObject;

                  String expected = "expected";

                  void test() {
                      new NonStrictExpectations() {{
                          myObject.getSomeField();
                          result = expected;
                      }};
                      assertEquals(expected, myObject.getSomeField());
                  }
              }
              """,
            """
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;

              import static org.junit.Assert.assertEquals;
              import static org.mockito.Mockito.lenient;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  MyObject myObject;

                  String expected = "expected";

                  void test() {
                      lenient().when(myObject.getSomeField()).thenReturn(expected);
                      assertEquals(expected, myObject.getSomeField());
                  }
              }
              """
          )
        );
    }

    @Test
    void whenNewClassResult() {
        //language=java
        rewriteRun(
          java(
            """
              class MyObject {
                  public Object getSomeField() {
                      return null;
                  }
              }
              """
          ),
          java(
            """
              import mockit.NonStrictExpectations;
              import mockit.Mocked;
              import mockit.integration.junit4.JMockit;
              import org.junit.runner.RunWith;

              import static org.junit.Assert.assertNotNull;

              @RunWith(JMockit.class)
              class MyTest {
                  @Mocked
                  MyObject myObject;

                  void test() {
                      new NonStrictExpectations() {{
                          myObject.getSomeField();
                          result = new Object();
                      }};
                      assertNotNull(myObject.getSomeField());
                  }
              }
              """,
            """
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;

              import static org.junit.Assert.assertNotNull;
              import static org.mockito.Mockito.lenient;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  MyObject myObject;

                  void test() {
                      lenient().when(myObject.getSomeField()).thenReturn(new Object());
                      assertNotNull(myObject.getSomeField());
                  }
              }
              """
          )
        );
    }

    @Test
    void whenExceptionResult() {
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
              import mockit.NonStrictExpectations;
              import mockit.Mocked;
              import mockit.integration.junit4.JMockit;
              import org.junit.runner.RunWith;

              @RunWith(JMockit.class)
              class MyTest {
                  @Mocked
                  MyObject myObject;

                  void test() throws RuntimeException {
                      new NonStrictExpectations() {{
                          myObject.getSomeField();
                          result = new RuntimeException();
                      }};
                      myObject.getSomeField();
                  }
              }
              """,
            """
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;

              import static org.mockito.Mockito.lenient;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  MyObject myObject;

                  void test() throws RuntimeException {
                      lenient().when(myObject.getSomeField()).thenThrow(new RuntimeException());
                      myObject.getSomeField();
                  }
              }
              """
          )
        );
    }

    @Test
    void whenReturns() {
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
              import mockit.NonStrictExpectations;
              import mockit.Mocked;
              import mockit.integration.junit4.JMockit;
              import org.junit.runner.RunWith;

              import static org.junit.Assert.assertEquals;

              @RunWith(JMockit.class)
              class MyTest {
                  @Mocked
                  MyObject myObject;

                  void test() throws RuntimeException {
                      new NonStrictExpectations() {{
                          myObject.getSomeField();
                          returns("foo", "bar");
                      }};
                      assertEquals("foo", myObject.getSomeField());
                      assertEquals("bar", myObject.getSomeField());
                  }
              }
              """,
            """
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;

              import static org.junit.Assert.assertEquals;
              import static org.mockito.Mockito.lenient;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  MyObject myObject;

                  void test() throws RuntimeException {
                      lenient().when(myObject.getSomeField()).thenReturn("foo", "bar");
                      assertEquals("foo", myObject.getSomeField());
                      assertEquals("bar", myObject.getSomeField());
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
              }
              """
          ),
          java(
            """
              import java.util.ArrayList;
              import java.util.List;

              import mockit.NonStrictExpectations;
              import mockit.Mocked;
              import mockit.integration.junit4.JMockit;
              import org.junit.runner.RunWith;

              import static org.junit.Assert.assertNull;

              @RunWith(JMockit.class)
              class MyTest {
                  @Mocked
                  MyObject myObject;

                  void test() {
                      new NonStrictExpectations() {{
                          myObject.getSomeField((List<String>) any);
                          result = null;
                          myObject.getSomeOtherField((Object) any);
                          result = null;
                      }};
                      assertNull(myObject.getSomeField(new ArrayList<>()));
                      assertNull(myObject.getSomeOtherField(new Object()));
                  }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.List;

              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;

              import static org.junit.Assert.assertNull;
              import static org.mockito.Mockito.*;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  MyObject myObject;

                  void test() {
                      lenient().when(myObject.getSomeField(anyList())).thenReturn(null);
                      lenient().when(myObject.getSomeOtherField(any(Object.class))).thenReturn(null);
                      assertNull(myObject.getSomeField(new ArrayList<>()));
                      assertNull(myObject.getSomeOtherField(new Object()));
                  }
              }
              """
          )
        );
    }

    @Test
    void whenNoArguments() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;

              class MyObject {
                  public String getSomeField() {
                      return "X";
                  }
              }
              """
          ),
          java(
            """
              import java.util.ArrayList;
              import java.util.List;

              import mockit.NonStrictExpectations;
              import mockit.Mocked;
              import mockit.integration.junit4.JMockit;
              import org.junit.runner.RunWith;

              import static org.junit.Assert.assertNull;

              @RunWith(JMockit.class)
              class MyTest {
                  @Mocked
                  MyObject myObject;

                  void test() {
                      new NonStrictExpectations() {{
                          myObject.getSomeField();
                          result = null;
                      }};
                      assertNull(myObject.getSomeField());
                  }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.List;

              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;

              import static org.junit.Assert.assertNull;
              import static org.mockito.Mockito.lenient;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  MyObject myObject;

                  void test() {
                      lenient().when(myObject.getSomeField()).thenReturn(null);
                      assertNull(myObject.getSomeField());
                  }
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void whenMixedArgumentMatcher() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;

              class MyObject {
                  public String getSomeField(String s, String s2, String s3, long l1) {
                      return "X";
                  }
              }
              """
          ),
          java(
            """
              import java.util.ArrayList;
              import java.util.List;

              import mockit.NonStrictExpectations;
              import mockit.Mocked;
              import mockit.integration.junit4.JMockit;
              import org.junit.runner.RunWith;

              import static org.junit.Assert.assertNull;

              @RunWith(JMockit.class)
              class MyTest {
                  @Mocked
                  MyObject myObject;

                  void test() {
                      String bazz = "bazz";
                      new NonStrictExpectations() {{
                          myObject.getSomeField("foo", anyString, bazz, 10L);
                          result = null;
                      }};
                      assertNull(myObject.getSomeField("foo", "bar", bazz, 10L));
                  }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.List;

              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;

              import static org.junit.Assert.assertNull;
              import static org.mockito.Mockito.*;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  MyObject myObject;

                  void test() {
                      String bazz = "bazz";
                      lenient().when(myObject.getSomeField(eq("foo"), anyString(), eq(bazz), eq(10L))).thenReturn(null);
                      assertNull(myObject.getSomeField("foo", "bar", bazz, 10L));
                  }
              }
              """
          )
        );
    }

    @Test
    void whenSetupStatements() {
        //language=java
        rewriteRun(
          java(
            """
              class MyObject {

                  public String getSomeField(String s) {
                      return "X";
                  }
                  public String getString() {
                      return "Y";
                  }
              }
              """
          ),
          java(
            """
              import mockit.NonStrictExpectations;
              import mockit.Mocked;
              import mockit.integration.junit4.JMockit;
              import org.junit.runner.RunWith;

              import static org.junit.Assert.assertEquals;

              @RunWith(JMockit.class)
              class MyTest {
                  @Mocked
                  MyObject myObject;

                  void test() {
                      String a = "a";
                      String s = "s";

                      new NonStrictExpectations() {{
                          myObject.getSomeField(anyString);
                          result = s;

                          myObject.getString();
                          result = a;
                      }};

                      assertEquals("s", myObject.getSomeField("foo"));
                      assertEquals("a", myObject.getString());
                  }
              }
              """,
            """
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;

              import static org.junit.Assert.assertEquals;
              import static org.mockito.Mockito.anyString;
              import static org.mockito.Mockito.lenient;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  MyObject myObject;

                  void test() {
                      String a = "a";
                      String s = "s";
                      lenient().when(myObject.getSomeField(anyString())).thenReturn(s);
                      lenient().when(myObject.getString()).thenReturn(a);

                      assertEquals("s", myObject.getSomeField("foo"));
                      assertEquals("a", myObject.getString());
                  }
              }
              """
          )
        );
    }

    @Test
    void whenSetupStatements2() {
        //language=java
        rewriteRun(
          java(
            """
              class MyObject {
                  public String getSomeField(String s) {
                      return "X";
                  }
              }
              """
          ),
          java(
            """
              import mockit.NonStrictExpectations;
              import mockit.Mocked;
              import mockit.integration.junit4.JMockit;
              import org.junit.runner.RunWith;

              import static org.junit.Assert.assertEquals;

              @RunWith(JMockit.class)
              class MyTest {
                  @Mocked
                  MyObject myObject;

                  void test() {
                      String a = "a";
                      new NonStrictExpectations() {{
                          myObject.getSomeField(anyString);
                          String s = "s";
                          String b = "b";
                          result = s;
                      }};

                      assertEquals("s", myObject.getSomeField("foo"));
                  }
              }
              """,
            """
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;

              import static org.junit.Assert.assertEquals;
              import static org.mockito.Mockito.anyString;
              import static org.mockito.Mockito.lenient;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  MyObject myObject;

                  void test() {
                      String a = "a";
                      String s = "s";
                      String b = "b";
                      lenient().when(myObject.getSomeField(anyString())).thenReturn(s);

                      assertEquals("s", myObject.getSomeField("foo"));
                  }
              }
              """
          )
        );
    }

    @Test
    void whenSpy() {
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
              import mockit.NonStrictExpectations;
              import mockit.Tested;
              import mockit.integration.junit4.JMockit;
              import org.junit.runner.RunWith;

              import static org.junit.Assert.assertEquals;

              @RunWith(JMockit.class)
              class MyTest {
                  @Tested
                  MyObject myObject;

                  void test() {
                      new NonStrictExpectations(myObject) {{
                          myObject.getSomeField();
                          result = "foo";
                      }};
                      assertEquals("foo", myObject.getSomeField());
                  }
              }
              """,
            """
              import org.junit.runner.RunWith;
              import org.mockito.InjectMocks;
              import org.mockito.junit.MockitoJUnitRunner;

              import static org.junit.Assert.assertEquals;
              import static org.mockito.Mockito.lenient;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @InjectMocks
                  MyObject myObject;

                  void test() {
                      lenient().when(myObject.getSomeField()).thenReturn("foo");
                      assertEquals("foo", myObject.getSomeField());
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
              import mockit.NonStrictExpectations;
              import mockit.Mocked;
              import mockit.integration.junit4.JMockit;
              import org.junit.runner.RunWith;

              import static org.junit.Assert.assertEquals;
              import static org.junit.Assert.assertNull;

              @RunWith(JMockit.class)
              class MyTest {
                  @Mocked
                  Object myObject;

                  @Mocked
                  MyObject myOtherObject;

                  void test() {
                      new NonStrictExpectations() {{
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
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;

              import static org.junit.Assert.assertEquals;
              import static org.junit.Assert.assertNull;
              import static org.mockito.Mockito.*;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  Object myObject;

                  @Mock
                  MyObject myOtherObject;

                  void test() {
                      lenient().when(myObject.hashCode()).thenReturn(10);
                      lenient().when(myOtherObject.getSomeObjectField()).thenReturn(null);
                      lenient().when(myOtherObject.getSomeStringField(anyString(), anyLong())).thenReturn("foo");
                      assertEquals(10, myObject.hashCode());
                      assertNull(myOtherObject.getSomeObjectField());
                      myObject.wait(10L, 10);
                      assertEquals("foo", myOtherObject.getSomeStringField("bar", 10L));
                  }
              }
              """
          )
        );
    }

    @Test
    void whenMultipleExpectations() {
        //language=java
        rewriteRun(
          java(
            """
              class MyObject {
                  public String getSomeStringField() {
                      return "X";
                  }
              }
              """
          ),
          java(
            """
              import mockit.NonStrictExpectations;
              import mockit.Mocked;
              import mockit.integration.junit4.JMockit;
              import org.junit.runner.RunWith;

              import static org.junit.Assert.assertEquals;
              import static org.junit.Assert.assertNull;

              @RunWith(JMockit.class)
              class MyTest {
                  @Mocked
                  MyObject myObject;

                  void test() {
                      new NonStrictExpectations() {{
                          myObject.getSomeStringField();
                          result = "a";
                      }};
                      assertEquals("a", myObject.getSomeStringField());
                      new NonStrictExpectations() {{
                          myObject.getSomeStringField();
                          result = "b";
                      }};
                      assertEquals("b", myObject.getSomeStringField());
                  }
              }
              """,
            """
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;

              import static org.junit.Assert.assertEquals;
              import static org.junit.Assert.assertNull;
              import static org.mockito.Mockito.lenient;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  MyObject myObject;

                  void test() {
                      lenient().when(myObject.getSomeStringField()).thenReturn("a");
                      assertEquals("a", myObject.getSomeStringField());
                      lenient().when(myObject.getSomeStringField()).thenReturn("b");
                      assertEquals("b", myObject.getSomeStringField());
                  }
              }
              """
          )
        );
    }

    @Test
    void whenNoResultsNoTimes() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.NonStrictExpectations;
              import mockit.Mocked;
              import mockit.integration.junit4.JMockit;
              import org.junit.runner.RunWith;

              import static org.junit.Assert.assertEquals;
              import static org.junit.Assert.assertNull;

              @RunWith(JMockit.class)
              class MyTest {
                  @Mocked
                  Object myObject;

                  void test() {
                      new NonStrictExpectations() {{
                          myObject.wait(anyLong);
                      }};
                      myObject.wait(1L);
                  }
              }
              """,
            """
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;

              import static org.junit.Assert.assertEquals;
              import static org.junit.Assert.assertNull;

              @RunWith(MockitoJUnitRunner.class)
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
    void whenTimes() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.NonStrictExpectations;
              import mockit.Mocked;
              import mockit.integration.junit4.JMockit;
              import org.junit.runner.RunWith;

              @RunWith(JMockit.class)
              class MyTest {
                  @Mocked
                  Object myObject;

                  void test() {
                      new NonStrictExpectations() {{
                          myObject.wait(anyLong, anyInt);
                          times = 3;
                      }};
                      myObject.wait(10L, 10);
                      myObject.wait(10L, 10);
                      myObject.wait(10L, 10);
                  }
              }
              """,
            """
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;

              import static org.mockito.Mockito.*;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  Object myObject;

                  void test() {
                      myObject.wait(10L, 10);
                      myObject.wait(10L, 10);
                      myObject.wait(10L, 10);
                      verify(myObject, times(3)).wait(anyLong(), anyInt());
                  }
              }
              """
          )
        );
    }

    @Test
    void whenTimesAndResult() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.NonStrictExpectations;
              import mockit.Mocked;
              import mockit.integration.junit4.JMockit;
              import org.junit.runner.RunWith;

              @RunWith(JMockit.class)
              class MyTest {
                  @Mocked
                  Object myObject;

                  void test() {
                      new NonStrictExpectations() {{
                          myObject.toString();
                          result = "foo";
                          times = 2;
                      }};
                      myObject.toString();
                      myObject.toString();
                  }
              }
              """,
            """
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;

              import static org.mockito.Mockito.*;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  Object myObject;

                  void test() {
                      when(myObject.toString()).thenReturn("foo");
                      myObject.toString();
                      myObject.toString();
                      verify(myObject, times(2)).toString();
                  }
              }
              """
          )
        );
    }
}
