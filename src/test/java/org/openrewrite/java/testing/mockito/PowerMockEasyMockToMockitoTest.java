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
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class PowerMockEasyMockToMockitoTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
                "easymock-5",
                "mockito-core-5",
                "junit-4",
                "powermock-core-2",
                "powermock-api-support-2")
            .dependsOn("""
            package org.powermock.api.easymock;
            import java.lang.reflect.Method;
            public class PowerMock {
                public static <T> T createMock(Class<T> type, Method... methods) { return null; }
                public static void replay(Object... mocks) { }
                public static void verify(Object... mocks) { }
            }
            """))
          .recipeFromResources("org.openrewrite.java.testing.mockito.PowerMockEasyMockToMockito")
          // EasyMockVerifyToMockitoVerify generates `verify(mock).method()` without the method's type
          .typeValidationOptions(TypeValidation.builder().methodInvocations(false).build());
    }

    @DocumentExample
    @Test
    void createReplayAndVerifyMocks() {
        rewriteRun(java(
          """
          interface Cache { String get(String key); void put(String k, String v); }
          interface Engine { Cache cache(); }
          """), java(
          """
          import static org.junit.Assert.*;
          import org.easymock.EasyMock;
          import org.powermock.api.easymock.PowerMock;
          import org.junit.Before;
          import org.junit.Test;

          public class DirectiveTest {
              private Cache cacheMock;
              private Engine engineMock;

              @Before
              public void setUp() {
                  cacheMock = PowerMock.createMock(Cache.class);
                  engineMock = PowerMock.createMock(Engine.class);
              }

              @Test
              public void initialize() {
                  PowerMock.replay(cacheMock, engineMock);
                  assertNotNull(engineMock);
                  PowerMock.verify(cacheMock, engineMock);
              }

              @Test
              public void expects() {
                  EasyMock.expect(engineMock.cache()).andReturn(cacheMock);
                  EasyMock.expect(cacheMock.get("a")).andReturn("b");
                  PowerMock.replay(cacheMock, engineMock);
                  assertEquals("b", engineMock.cache().get("a"));
                  PowerMock.verify(cacheMock, engineMock);
              }
          }
          """,
          """
          import static org.junit.Assert.*;
          import static org.mockito.Mockito.verify;

          import org.junit.Before;
          import org.junit.Test;
          import org.mockito.Mockito;

          public class DirectiveTest {
              private Cache cacheMock;
              private Engine engineMock;

              @Before
              public void setUp() {
                  cacheMock = Mockito.mock(Cache.class);
                  engineMock = Mockito.mock(Engine.class);
              }

              @Test
              public void initialize() {
                  assertNotNull(engineMock);
                  Mockito.verify(cacheMock);
                  Mockito.verify(engineMock);
              }

              @Test
              public void expects() {
                  Mockito.when(engineMock.cache()).thenReturn(cacheMock);
                  Mockito.when(cacheMock.get("a")).thenReturn("b");
                  assertEquals("b", engineMock.cache().get("a"));
                  verify(cacheMock).get("a");
                  verify(engineMock).cache();
              }
          }
          """
          ));
    }
}
