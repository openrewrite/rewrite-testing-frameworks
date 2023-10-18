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
package org.openrewrite.java.testing.mockito;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class JMockitToMockitoTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-jupiter-api-5.9",
              "jmockit-1.49",
              "mockito-core-3.12"
            ))
          .recipeFromResource(
            "/META-INF/rewrite/mockito.yml",
            "org.openrewrite.java.testing.mockito.JMockitToMockito"
          );
    }

    @Test
    void rewrite() {
        //language=java
        rewriteRun(
          java(
            """                  
              import static org.junit.jupiter.api.Assertions.assertNull;
                            
              import mockit.Expectations;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;
                            
              @ExtendWith(JMockitExtension.class)
              class MyTest {
                @Mocked
                MyObject myObject;
                            
                void test() {
                  new Expectations() {{
                    myObject.getSomeField();
                    result = null;
                  }};
                  assertNull(myObject.getSomeField());
                }
              }
              """,
            """                  
              import static org.junit.jupiter.api.Assertions.assertNull;
              import static org.mockito.Mockito.when;
                            
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
                            
              @ExtendWith(MockitoExtension.class)
              class MyTest {
                @Mock
                MyObject myObject;
                            
                void test() {
                  when(myObject.getSomeField()).thenReturn(null);
                  assertNull(myObject.getSomeField());
                }
              }
              """
          )
        );
    }
}
