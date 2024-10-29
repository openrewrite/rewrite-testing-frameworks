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

class RetainStrictnessWarnTest implements RewriteTest {

    @Language("xml")
    private static final String POM_XML_WITH_OLDER_MOCKITO = """
      <project>
        <modelVersion>4.0.0</modelVersion>
        <groupId>bla.bla</groupId>
        <artifactId>bla-bla</artifactId>
        <version>1.0.0</version>
        <dependencies>
          <dependency>
              <groupId>org.mockito</groupId>
              <artifactId>mockito-all</artifactId>
              <version>1.1</version>
              <scope>test</scope>
          </dependency>
        </dependencies>
      </project>
      """;

    @Language("java")
    private static final String JAVA_BEFORE = """
      import org.junit.jupiter.api.extension.ExtendWith;
      import org.mockito.junit.jupiter.MockitoExtension;
      
      @ExtendWith(MockitoExtension.class)
      class MyTest {
      }
      """;

    @Language("java")
    private static final String JAVA_AFTER = """
      import org.mockito.junit.jupiter.MockitoSettings;
      import org.mockito.quality.Strictness;
      
      @MockitoSettings(strictness = Strictness.WARN)
      class MyTest {
      }
      """;

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "mockito-core", "mockito-junit-jupiter", "junit-jupiter-api"))
          .recipe(new RetainStrictnessWarn());
    }

    @Test
    @DocumentExample
    void shouldAddMockitoSettingsWithLenientStubbing() {
        //language=java
        rewriteRun(
          pomXml(POM_XML_WITH_OLDER_MOCKITO),
          java(JAVA_BEFORE, JAVA_AFTER)
        );
    }

    @Test
    void shouldLeaveExisting() {
        //language=java
        rewriteRun(
          pomXml(POM_XML_WITH_OLDER_MOCKITO),
          java(
            """
              import org.mockito.junit.jupiter.MockitoSettings;
              import org.mockito.quality.Strictness;
              
              @MockitoSettings(strictness = Strictness.STRICT_STUBS)
              class MyTest {
              }
              """
          )
        );
    }

    @Test
    void shouldRunBeforeMockitoCore4() {
        rewriteRun(
          pomXml(POM_XML_WITH_OLDER_MOCKITO),
          java(JAVA_BEFORE, JAVA_AFTER)
        );
    }

    @Test
    void shouldNotRunOnNewerMockito() {
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
                      <version>4.0.0</version>
                      <scope>test</scope>
                  </dependency>
                </dependencies>
              </project>
              """
          ),
          java(JAVA_BEFORE)
        );
    }
}
