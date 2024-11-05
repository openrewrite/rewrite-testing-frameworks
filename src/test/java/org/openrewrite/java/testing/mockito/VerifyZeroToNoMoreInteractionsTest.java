package org.openrewrite.java.testing.mockito;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class VerifyZeroToNoMoreInteractionsTest implements RewriteTest {

    @Language("xml")
    private static final String POM_XML_WITH_MOCKITO_2 = """
      <project>
        <modelVersion>4.0.0</modelVersion>
        <groupId>bla.bla</groupId>
        <artifactId>bla-bla</artifactId>
        <version>1.0.0</version>
        <dependencies>
          <dependency>
              <groupId>org.mockito</groupId>
              <artifactId>mockito-core</artifactId>
              <version>2.17.0</version>
              <scope>test</scope>
          </dependency>
        </dependencies>
      </project>
      """;

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "mockito-core", "mockito-junit-jupiter", "junit-jupiter-api"))
          .recipe(new VerifyZeroToNoMoreInteractions());
    }

    @Test
    @DocumentExample
    void shouldReplaceToNoMoreInteractions() {
        //language=java
        rewriteRun(
          pomXml(POM_XML_WITH_MOCKITO_2),
          java("""
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.mockito.junit.jupiter.MockitoExtension;
            import org.junit.jupiter.api.Test;

            import static org.mockito.Mockito.verifyZeroInteractions;

            @ExtendWith(MockitoExtension.class)
            class MyTest {
                @Test
                void test() {
                    verifyZeroInteractions(System.out);
                }
            }
            """, """
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.mockito.junit.jupiter.MockitoExtension;
            import org.junit.jupiter.api.Test;

            import static org.mockito.Mockito.verifyNoMoreInteractions;

            @ExtendWith(MockitoExtension.class)
            class MyTest {
                @Test
                void test() {
                    verifyNoMoreInteractions(System.out);
                }
            }
            """));
    }

    @Test
    void shouldNotReplaceToNoMoreInteractionsForImportOnly() {
        //language=java
        rewriteRun(
          pomXml(POM_XML_WITH_MOCKITO_2),
          java("""
            import static org.mockito.Mockito.verifyZeroInteractions;

            class MyTest {}
            """));
    }

    @Test
    void doesNotConvertAnyOtherMethods() {
        rewriteRun(
          pomXml(POM_XML_WITH_MOCKITO_2),
          // language=java
          java("""
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.mockito.junit.jupiter.MockitoExtension;
            import org.mockito.Mock;
            import org.junit.jupiter.api.Test;
            import static org.mockito.Mockito.verifyZeroInteractions;
            import static org.mockito.Mockito.verify;

            @ExtendWith(MockitoExtension.class)
            class MyTest {
                @Mock
                Object myObject;

                @Test
                void test() {
                    verifyZeroInteractions(System.out);
                    verify(myObject);
                }
            }
            """, """
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.mockito.junit.jupiter.MockitoExtension;
            import org.mockito.Mock;
            import org.junit.jupiter.api.Test;
            import static org.mockito.Mockito.verifyNoMoreInteractions;
            import static org.mockito.Mockito.verify;

            @ExtendWith(MockitoExtension.class)
            class MyTest {
                @Mock
                Object myObject;

                @Test
                void test() {
                    verifyNoMoreInteractions(System.out);
                    verify(myObject);
                }
            }
            """));
    }

    @Test
    void doesConvertNestedMethodInvocations() {
        rewriteRun(
          pomXml(POM_XML_WITH_MOCKITO_2),
          // language=java
          java("""
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.mockito.junit.jupiter.MockitoExtension;
            import org.junit.jupiter.api.Test;

            import static org.junit.jupiter.api.Assertions.assertAll;
            import static org.mockito.Mockito.verifyZeroInteractions;

            @ExtendWith(MockitoExtension.class)
            class MyTest {
                @Test
                void test() {
                    assertAll(() -> verifyZeroInteractions(System.out));
                }
            }
            """, """
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.mockito.junit.jupiter.MockitoExtension;
            import org.junit.jupiter.api.Test;

            import static org.junit.jupiter.api.Assertions.assertAll;
            import static org.mockito.Mockito.verifyNoMoreInteractions;

            @ExtendWith(MockitoExtension.class)
            class MyTest {
                @Test
                void test() {
                    assertAll(() -> verifyNoMoreInteractions(System.out));
                }
            }
            """
          )
        );
    }

    @Test
    void shouldNotRunOnNewerMockito3OrHigher() {
        rewriteRun(
          //language=xml
          pomXml("""
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>bla.bla</groupId>
              <artifactId>bla-bla</artifactId>
              <version>1.0.0</version>
              <dependencies>
                <dependency>
                    <groupId>org.mockito</groupId>
                    <artifactId>mockito-core</artifactId>
                    <version>3.0.0</version>
                    <scope>test</scope>
                </dependency>
              </dependencies>
            </project>
            """),
          //language=java
          java("""
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.mockito.junit.jupiter.MockitoExtension;
            import org.junit.jupiter.api.Test;

            import static org.mockito.Mockito.verifyZeroInteractions;

            @ExtendWith(MockitoExtension.class)
            class MyTest {
                @Test
                void test() {
                    verifyZeroInteractions(System.out);
                }
            }
            """));
    }
}
