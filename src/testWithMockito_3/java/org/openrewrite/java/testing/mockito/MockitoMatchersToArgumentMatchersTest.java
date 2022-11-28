package org.openrewrite.java.testing.mockito;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class MockitoMatchersToArgumentMatchersTest implements RewriteTest {
    @Test
    void mockitoAnyListOfToListOf() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpath("mockito-core"))
            .recipe(Environment.builder()
              .scanRuntimeClasspath("org.openrewrite.java.testing.mockito")
              .build()
              .activateRecipes("org.openrewrite.java.testing.mockito.Mockito1to3Migration")),
          //language=java
          java("""
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
