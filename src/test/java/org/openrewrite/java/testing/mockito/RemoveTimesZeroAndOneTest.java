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
package org.openrewrite.java.testing.mockito;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveTimesZeroAndOneTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "mockito-core"))
          .recipe(new RemoveTimesZeroAndOne());
    }


    @DocumentExample
    @Test
    void replaceTimesZero() {
        rewriteRun(
          //language=Java
          java(
            """
              import static org.mockito.Mockito.times;
              import static org.mockito.Mockito.verify;

              class MyTest {
                  void test(Object myObject) {
                      myObject.wait();
                      verify(myObject, times(0)).wait();
                  }
              }
              """,
            """
              import static org.mockito.Mockito.never;
              import static org.mockito.Mockito.verify;

              class MyTest {
                  void test(Object myObject) {
                      myObject.wait();
                      verify(myObject, never()).wait();
                  }
              }
              """
          )
        );
    }

    @Test
    void removeTimesOne() {
        rewriteRun(
          //language=Java
          java(
            """
              import static org.mockito.Mockito.times;
              import static org.mockito.Mockito.verify;

              class MyTest {
                  void test(Object myObject) {
                      myObject.wait();
                      verify(myObject, times(1)).wait();
                  }
              }
              """,
            """
              import static org.mockito.Mockito.verify;

              class MyTest {
                  void test(Object myObject) {
                      myObject.wait();
                      verify(myObject).wait();
                  }
              }
              """
          )
        );
    }

    @Test
    void retainTimesTwo() {
        rewriteRun(
          //language=Java
          java(
            """
              import static org.mockito.Mockito.times;
              import static org.mockito.Mockito.verify;

              class MyTest {
                  void test(Object myObject) {
                      myObject.wait();
                      verify(myObject, times(2)).wait();
                  }
              }
              """
          )
        );
    }
}
