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
package org.openrewrite.java.testing.mockito;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class AnyToNullableTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "mockito-core-3.12")
            .logCompilationWarningsAndErrors(true))
          .recipe(new AnyToNullable());
    }

    @Test
    void replaceAnyClassWithNullableClass() {
        //language=java
        rewriteRun(
          //language=xml
          pomXml(
                """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>foo</artifactId>
                <version>1.0.0</version>
                <dependencies>
                    <dependency>
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-all</artifactId>
                        <version>1.10.19</version>
                    </dependency>
                </dependencies>
            </project>
            """),
          //language=java
          java(
                """
            class Example {
                String greet(Object obj) {
                    return "Hello " + obj;
                }
            }
            """),
          //language=java
          java(
            """
              import static org.mockito.Mockito.mock;
              import static org.mockito.Mockito.when;
              import static org.mockito.Mockito.any;
              
              class MyTest {
                   void test() {
                      Example example = mock(Example.class);
                      when(example.greet(any(Object.class))).thenReturn("Hello world");
                   }
              }
              """,
            """
              import static org.mockito.ArgumentMatchers.nullable;
              import static org.mockito.Mockito.mock;
              import static org.mockito.Mockito.when;
                            
              class MyTest {
                   void test() {
                      Example example = mock(Example.class);
                      when(example.greet(nullable(Object.class))).thenReturn("Hello world");
                   }
              }
              """
          )
        );
    }

    @Test
    void doesNotTouchIfMockitoTwoPlus() {
        //language=java
        rewriteRun(
          //language=xml
          pomXml(
                """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>foo</artifactId>
                <version>1.0.0</version>
                <dependencies>
                    <dependency>
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-core</artifactId>
                        <version>3.12.0</version>
                    </dependency>
                </dependencies>
            </project>
            """),
          //language=java
          java(
                """
            class Example {
                String greet(Object obj) {
                    return "Hello " + obj;
                }
            }
            """),
          //language=java
          java(
            """
              import static org.mockito.Mockito.mock;
              import static org.mockito.Mockito.when;
              import static org.mockito.Mockito.any;
              
              class MyTest {
                   void test() {
                      Example example = mock(Example.class);
                      when(example.greet(any(Object.class))).thenReturn("Hello world");
                   }
              }
              """
          )
        );
    }

}
