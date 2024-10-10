package org.openrewrite.java.testing.mockito;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class SonarS6068FixerTest implements RewriteTest {
    @Test
    void verify_Simple_ShouldUpdate() {
        rewriteRun(
          spec -> spec.recipe(new SonarS6068Fixer())
            .typeValidationOptions(TypeValidation.builder().build()),
          //language=Java
          java(
            """
            import static org.mockito.Mockito.verify;
            import static org.mockito.Mockito.mock;
            import static org.mockito.ArgumentMatchers.eq;
            class Test {
                public void test() {
                    final var mockString = mock(String.class);
                    verify(mockString).replace(eq("foo"), eq("bar"));
                }
            }
            """,
            """
            import static org.mockito.Mockito.verify;
            import static org.mockito.Mockito.mock;
            import static org.mockito.ArgumentMatchers.eq;
            class Test {
                public void test() {
                    final var mockString = mock(String.class);
                    verify(mockString).replace("foo","bar");
                }
            }
            """
          )
        );
    }

    @Test
    void when_Simple_ShouldUpdate() {
        rewriteRun(
          spec -> spec.recipe(new SonarS6068Fixer())
            .typeValidationOptions(TypeValidation.builder().build()),
          //language=Java
          java(
            """
            import static org.mockito.Mockito.mock;
            import static org.mockito.Mockito.when;
            import static org.mockito.ArgumentMatchers.eq;
            class Test {
                public void test() {
                    final var mockString = mock(String.class);
                    when(mockString.replace(eq("foo"), eq("bar"))).thenReturn("bar");
                }
            }
            """,
            """
            import static org.mockito.Mockito.mock;
            import static org.mockito.Mockito.when;
            import static org.mockito.ArgumentMatchers.eq;
            class Test {
                public void test() {
                    final var mockString = mock(String.class);
                    when(mockString.replace("foo", "bar")).thenReturn("bar");
                }
            }
            """
          )
        );
    }

    @Test
    void when_MoreComplexMatchers_ShouldNotUpdate() {
        rewriteRun(
          spec -> spec.recipe(new SonarS6068Fixer())
            .typeValidationOptions(TypeValidation.builder().build()),
          //language=Java
          java(
            """
            import static org.mockito.Mockito.mock;
            import static org.mockito.Mockito.when;
            import static org.mockito.ArgumentMatchers.eq;
            import static org.mockito.ArgumentMatchers.anyString;
            class Test {
                public void test() {
                    final var mockString = mock(String.class);
                    when(mockString.replace(eq("foo"), anyString())).thenReturn("bar");
                }
            }
            """
          )
        );
    }
}
