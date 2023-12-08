/*
 * Copyright 2021 the original author or authors.
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
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MockitoMatchersToArgumentMatchersTest implements RewriteTest {
    @DocumentExample
    @Test
    void mockitoAnyListOfToListOf() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
              .classpathFromResources(new InMemoryExecutionContext(), "mockito-core-3.12"))
            .recipe(Environment.builder()
              .scanRuntimeClasspath("org.openrewrite.java.testing.mockito")
              .build()
              .activateRecipes("org.openrewrite.java.testing.mockito.Mockito1to3Migration")),
          //language=java
          java(
                """
              package mockito.example;

              import java.util.List;
              import java.util.Map;
              import java.util.Set;
                            
              import static org.mockito.ArgumentMatchers.anyListOf;
              import static org.mockito.ArgumentMatchers.anySetOf;
              import static org.mockito.ArgumentMatchers.anyMapOf;
              import static org.mockito.Mockito.mock;
              import static org.mockito.Mockito.when;
                            
              public class MockitoVarargMatcherTest {
                  public static class Foo {
                      public boolean addList(List<String> strings) { return true; }
                      public boolean addSet(Set<String> strings) { return true; }
                      public boolean addMap(Map<String, String> stringStringMap) { return true; }
                  }
                  public void usesVarargMatcher() {
                      Foo mockFoo = mock(Foo.class);
                      when(mockFoo.addList(anyListOf(String.class))).thenReturn(true);
                      when(mockFoo.addSet(anySetOf(String.class))).thenReturn(true);
                      when(mockFoo.addMap(anyMapOf(String.class, String.class))).thenReturn(true);
                  }
              }
              """,
            """
              package mockito.example;

              import java.util.List;
              import java.util.Map;
              import java.util.Set;
                            
              import static org.mockito.ArgumentMatchers.anyList;
              import static org.mockito.ArgumentMatchers.anySet;
              import static org.mockito.ArgumentMatchers.anyMap;
              import static org.mockito.Mockito.mock;
              import static org.mockito.Mockito.when;
                            
              public class MockitoVarargMatcherTest {
                  public static class Foo {
                      public boolean addList(List<String> strings) { return true; }
                      public boolean addSet(Set<String> strings) { return true; }
                      public boolean addMap(Map<String, String> stringStringMap) { return true; }
                  }
                  public void usesVarargMatcher() {
                      Foo mockFoo = mock(Foo.class);
                      when(mockFoo.addList(anyList())).thenReturn(true);
                      when(mockFoo.addSet(anySet())).thenReturn(true);
                      when(mockFoo.addMap(anyMap())).thenReturn(true);
                  }
              }
              """)
        );
    }
}
