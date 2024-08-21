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
package org.openrewrite.java.testing.junit5;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class AddJupiterDependenciesTest implements RewriteTest {

    @Language("java")
    private static final String SOME_TEST = """
      import org.junit.jupiter.api.Test;
      
      class FooTest {
          @Test
          void bar() {
          }
      }
      """;

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddJupiterDependencies())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api"));
    }

    @DocumentExample
    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/585")
    void addToTestScope() {
        rewriteRun(
          mavenProject("project",
            srcTestJava(java(SOME_TEST)),
            pomXml(
              //language=xml
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>project</artifactId>
                    <version>0.0.1</version>
                </project>
                """,
              spec -> spec.after(pom -> {
                  assertThat(pom)
                    .contains("junit-jupiter")
                    .contains("<scope>test</scope>");
                  return pom;
              })
            )
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/585")
    void addToCompileScope() {
        rewriteRun(
          mavenProject("project",
            srcMainJava(java(SOME_TEST)),
            pomXml(
              //language=xml
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>project</artifactId>
                    <version>0.0.1</version>
                </project>
                """,
              spec -> spec.after(pom -> {
                  assertThat(pom)
                    .contains("junit-jupiter")
                    .doesNotContain("<scope>test</scope>");
                  return pom;
              })
            )
          )
        );
    }
}
