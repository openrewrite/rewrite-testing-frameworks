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

class PowerMockWhiteboxGetFieldToJavaReflectionTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),
              "powermock-core-1",
              "powermock-reflect-1"
            ))
          .recipe(new PowerMockWhiteboxGetFieldToJavaReflection());
    }

    @DocumentExample
    @Test
    void getFieldReturnsFieldVariable() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private String name;
              }
              """
          ),
          java(
            """
              import java.lang.reflect.Field;
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void test() {
                      Field f = Whitebox.getField(MyService.class, "name");
                  }
              }
              """,
            """
              import java.lang.reflect.Field;

              class MyServiceTest {
                  void test() throws Exception {
                      Field f = MyService.class.getDeclaredField("name");
                      f.setAccessible(true);
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForOtherWhiteboxApis() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private String name;
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void test() {
                      MyService service = new MyService();
                      Whitebox.setInternalState(service, "name", "expectedValue");
                  }
              }
              """
          )
        );
    }
}
