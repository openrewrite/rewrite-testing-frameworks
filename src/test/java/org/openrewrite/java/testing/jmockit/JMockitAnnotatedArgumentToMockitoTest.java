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
import static org.openrewrite.java.testing.jmockit.JMockitTestUtils.setDefaultParserSettings;

class JMockitAnnotatedArgumentToMockitoTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        setDefaultParserSettings(spec);
    }

    @DocumentExample
    @Test
    void mockedVariableTest() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.Mocked;
              
              import static org.junit.jupiter.api.Assertions.assertNotNull;
              
              class A {
                  @Mocked
                  Object mockedObject;
              
                  void test(@Mocked Object o, @Mocked Object o2) {
                      assertNotNull(o);
                      assertNotNull(o2);
                  }
              }
              """,
            """
              import org.mockito.Mock;
              import org.mockito.Mockito;
              
              import static org.junit.jupiter.api.Assertions.assertNotNull;
              
              class A {
                  @Mock
                  Object mockedObject;
              
                  void test() {
                      Object o = Mockito.mock(Object.class);
                      Object o2 = Mockito.mock(Object.class);
                      assertNotNull(o);
                      assertNotNull(o2);
                  }
              }
              """
          )
        );
    }

    @Test
    void mockedNoVariableTest() {
        rewriteRun(
          //language=java
          java(
            """
              import mockit.Mocked;
              
              import static org.junit.jupiter.api.Assertions.assertNotNull;
              
              class A {
                  @Mocked
                  Object mockedObject;
              
                  void test() {
                      assertNotNull(mockedObject);
                  }
              }
              """,
            """
              import org.mockito.Mock;
              
              import static org.junit.jupiter.api.Assertions.assertNotNull;
              
              class A {
                  @Mock
                  Object mockedObject;
              
                  void test() {
                      assertNotNull(mockedObject);
                  }
              }
              """
          )
        );
    }

    @Test
    void injectableVariableTest() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.Injectable;
              
              import static org.junit.jupiter.api.Assertions.assertNotNull;
              
              class A {
                  @Injectable
                  Object mockedObject;
              
                  void test(@Injectable Object o, @Injectable Object o2) {
                      assertNotNull(o);
                      assertNotNull(o2);
                  }
              }
              """,
            """
              import org.mockito.Mock;
              import org.mockito.Mockito;
              
              import static org.junit.jupiter.api.Assertions.assertNotNull;
              
              class A {
                  @Mock
                  Object mockedObject;
              
                  void test() {
                      Object o = Mockito.mock(Object.class);
                      Object o2 = Mockito.mock(Object.class);
                      assertNotNull(o);
                      assertNotNull(o2);
                  }
              }
              """
          )
        );
    }

    @Test
    void injectableNoVariableTest() {
        rewriteRun(
          //language=java
          java(
            """
              import mockit.Injectable;
              
              import static org.junit.jupiter.api.Assertions.assertNotNull;
              
              class A {
                  @Injectable
                  Object mockedObject;
              
                  void test() {
                      assertNotNull(mockedObject);
                  }
              }
              """,
            """
              import org.mockito.Mock;
              
              import static org.junit.jupiter.api.Assertions.assertNotNull;
              
              class A {
                  @Mock
                  Object mockedObject;
              
                  void test() {
                      assertNotNull(mockedObject);
                  }
              }
              """
          )
        );
    }
}
