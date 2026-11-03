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
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcTestJava;
import static org.openrewrite.maven.Assertions.pomXml;

class AnyToNullableTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "mockito-core-3.12")
            .logCompilationWarningsAndErrors(true))
          .recipeFromResources("org.openrewrite.java.testing.mockito.AnyToNullable");
    }

    @DocumentExample
    @Test
    void replaceAnyClassWithNullableClass() {
        //language=java
        rewriteRun(
          mavenProject("foo",
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
              """
            ),
            //language=java
            java(
                  """
              class Example {
                  String greet(Object obj) {
                      return "Hello " + obj;
                  }
              }
              """
            ),
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
          )
        );
    }

    @Test
    void shouldNotRemoveAnyImportWhenUntypedAnyIsStillUsed() {
        //language=java
        rewriteRun(
          mavenProject("foo",
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
              """
            ),
            //language=java
            java(
                  """
              class Example {
                  String greet(Object obj, Object obj2) {
                      return "Hello " + obj + obj2;
                  }
              }
              """
            ),
            //language=java
            java(
              """
                import static org.mockito.Mockito.mock;
                import static org.mockito.Mockito.when;
                import static org.mockito.Mockito.any;

                class MyTest {
                     void test() {
                        Example example = mock(Example.class);
                        when(example.greet(any(Object.class), any())).thenReturn("Hello world");
                     }
                }
                """,
              """
                import static org.mockito.ArgumentMatchers.any;
                import static org.mockito.ArgumentMatchers.nullable;
                import static org.mockito.Mockito.mock;
                import static org.mockito.Mockito.when;

                class MyTest {
                     void test() {
                        Example example = mock(Example.class);
                        when(example.greet(nullable(Object.class), any())).thenReturn("Hello world");
                     }
                }
                """
            )
          )
        );
    }

    @Test
    void doesNotTouchIfMockitoTwoPlus() {
        //language=java
        rewriteRun(
          mavenProject("foo",
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
              """
            ),
            //language=java
            java(
                  """
              class Example {
                  String greet(Object obj) {
                      return "Hello " + obj;
                  }
              }
              """
            ),
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
          )
        );
    }

    @Test
    void onlyChangesModulesDeclaringMockitoAll() {
        rewriteRun(
          mavenProject("legacy",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>legacy</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.mockito</groupId>
                            <artifactId>mockito-all</artifactId>
                            <version>1.10.19</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            srcTestJava(
              //language=java
              java(
                """
                  import static org.mockito.Mockito.any;
                  import static org.mockito.Mockito.mock;
                  import static org.mockito.Mockito.when;

                  class LegacyTest {
                      void test() {
                          Comparable<Object> comparable = mock(Comparable.class);
                          when(comparable.compareTo(any(Object.class))).thenReturn(0);
                      }
                  }
                  """,
                """
                  import static org.mockito.ArgumentMatchers.nullable;
                  import static org.mockito.Mockito.mock;
                  import static org.mockito.Mockito.when;

                  class LegacyTest {
                      void test() {
                          Comparable<Object> comparable = mock(Comparable.class);
                          when(comparable.compareTo(nullable(Object.class))).thenReturn(0);
                      }
                  }
                  """
              )
            )
          ),
          mavenProject("modern",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>modern</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.mockito</groupId>
                            <artifactId>mockito-core</artifactId>
                            <version>3.12.4</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            srcTestJava(
              //language=java
              java(
                """
                  import static org.mockito.Mockito.any;
                  import static org.mockito.Mockito.mock;
                  import static org.mockito.Mockito.when;

                  class ModernTest {
                      void test() {
                          Comparable<Object> comparable = mock(Comparable.class);
                          when(comparable.compareTo(any(Object.class))).thenReturn(0);
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void replaceMatchersCalls() {
        rewriteRun(
          mavenProject("foo",
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
                """
            ),
            //language=java
            java(
              """
                import java.util.Map;

                import static org.mockito.Matchers.any;
                import static org.mockito.Matchers.anyString;
                import static org.mockito.Mockito.mock;
                import static org.mockito.Mockito.when;

                class MyTest {
                    void test() {
                        Map<Object, String> map = mock(Map.class);
                        when(map.put(any(Object.class), anyString())).thenReturn("value");
                    }
                }
                """,
              """
                import java.util.Map;

                import static org.mockito.ArgumentMatchers.nullable;
                import static org.mockito.Mockito.mock;
                import static org.mockito.Mockito.when;

                class MyTest {
                    void test() {
                        Map<Object, String> map = mock(Map.class);
                        when(map.put(nullable(Object.class), nullable(String.class))).thenReturn("value");
                    }
                }
                """
            )
          )
        );
    }
}
