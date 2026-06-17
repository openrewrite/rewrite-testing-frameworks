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

class PowerMockWhiteboxGetInternalStateToJavaReflectionTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),
              "powermock-core-1",
              "powermock-reflect-1"
            ))
          .recipe(new PowerMockWhiteboxGetInternalStateToJavaReflection());
    }

    @DocumentExample
    @Test
    void getInternalStateWithAssignment() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private String name = "hello";
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void testGetField() {
                      MyService service = new MyService();
                      String result = Whitebox.getInternalState(service, "name");
                  }
              }
              """,
            """
              import java.lang.reflect.Field;

              class MyServiceTest {
                  void testGetField() throws Exception {
                      MyService service = new MyService();
                      Field nameField = service.getClass().getDeclaredField("name");
                      nameField.setAccessible(true);
                      String result = (String) nameField.get(service);
                  }
              }
              """
          )
        );
    }
}
