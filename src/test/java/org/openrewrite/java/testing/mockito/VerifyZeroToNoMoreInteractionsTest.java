/*
 * Copyright 2024 the original author or authors.
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
            .classpathFromResources(new InMemoryExecutionContext(), "mockito-core-3", "mockito-junit-jupiter-3"))
          .recipe(new VerifyZeroToNoMoreInteractions());
    }

    @Test
    @DocumentExample
    void shouldReplaceToNoMoreInteractions() {
        //language=java
        rewriteRun(
          pomXml(POM_XML_WITH_MOCKITO_2),
          java(
            """
              import static org.mockito.Mockito.verifyZeroInteractions;

              class MyTest {
                  void test() {
                      verifyZeroInteractions(System.out);
                  }
              }
              """,
            """
              import static org.mockito.Mockito.verifyNoMoreInteractions;

              class MyTest {
                  void test() {
                      verifyNoMoreInteractions(System.out);
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldNotReplaceToNoMoreInteractionsForImportOnly() {
        //language=java
        rewriteRun(
          pomXml(POM_XML_WITH_MOCKITO_2),
          java(
            """
              import static org.mockito.Mockito.verifyZeroInteractions;

              class MyTest {}
              """
          )
        );
    }

    @Test
    void doesNotConvertAnyOtherMethods() {
        rewriteRun(
          pomXml(POM_XML_WITH_MOCKITO_2),
          // language=java
          java(
            """
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.mockito.Mock;
              import static org.mockito.Mockito.verifyZeroInteractions;
              import static org.mockito.Mockito.verify;

              class MyTest {
                  @Mock
                  Object myObject;

                  void test() {
                      verifyZeroInteractions(System.out);
                      verify(myObject);
                  }
              }
              """,
            """
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.mockito.Mock;
              import static org.mockito.Mockito.verifyNoMoreInteractions;
              import static org.mockito.Mockito.verify;

              class MyTest {
                  @Mock
                  Object myObject;

                  void test() {
                      verifyNoMoreInteractions(System.out);
                      verify(myObject);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesConvertNestedMethodInvocations() {
        rewriteRun(
          pomXml(POM_XML_WITH_MOCKITO_2),
          // language=java
          java(
            """
              import java.util.function.Consumer;

              import static org.mockito.Mockito.verifyZeroInteractions;

              class MyTest {
                  void test() {
                      Runnable f = () -> verifyZeroInteractions(System.out);
                      f.run();
                  }
              }
              """,
            """
              import java.util.function.Consumer;

              import static org.mockito.Mockito.verifyNoMoreInteractions;

              class MyTest {
                  void test() {
                      Runnable f = () -> verifyNoMoreInteractions(System.out);
                      f.run();
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
          pomXml(
            """
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
          java(
            """
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.mockito.Mockito.verifyZeroInteractions;

              class MyTest {
                  void test() {
                      verifyZeroInteractions(System.out);
                  }
              }
              """
          )
        );
    }
}
