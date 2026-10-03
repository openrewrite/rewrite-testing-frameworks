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
package org.openrewrite.java.testing.hamcrest;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcTestJava;
import static org.openrewrite.maven.Assertions.pomXml;

class AddHamcrestIfUsedTest implements RewriteTest {
    private static final String BEFORE = """
      <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>org.example</groupId>
          <artifactId>example</artifactId>
          <version>1.0</version>
      </project>
      """;

    private static final String AFTER = """
      <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>org.example</groupId>
          <artifactId>example</artifactId>
          <version>1.0</version>
          <dependencies>
              <dependency>
                  <groupId>org.hamcrest</groupId>
                  <artifactId>hamcrest</artifactId>
                  <version>2.2</version>
                  <scope>test</scope>
              </dependency>
          </dependencies>
      </project>
      """;

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddHamcrestIfUsed())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "hamcrest", "junit-4"));
    }

    @Test
    void addsDependencyForCoreMatchersAndMatcherAssert() {
        rewriteRun(
          mavenProject("example",
            pomXml(BEFORE, AFTER),
            srcTestJava(java(
              """
                import static org.hamcrest.CoreMatchers.containsString;
                import static org.hamcrest.MatcherAssert.assertThat;

                class T {
                    void test() {
                        assertThat("message", containsString("mess"));
                    }
                }
                """
            ))
          )
        );
    }

    @Test
    void migrationAddsDependencyForExpectedExceptionMessage() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.java.testing.junit5.JUnit4to5Migration")
            .cycles(1).expectedCyclesThatMakeChanges(1)
            .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-4", "hamcrest")),
          mavenProject("example",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>junit</groupId>
                            <artifactId>junit</artifactId>
                            <version>4.13.2</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> {
                  assertThat(pom).contains("<artifactId>hamcrest</artifactId>", "<artifactId>junit-jupiter</artifactId>")
                    .doesNotContain("<artifactId>junit</artifactId>");
                  return pom;
              })
            ),
            srcTestJava(java(
              """
                import org.junit.Rule;
                import org.junit.Test;
                import org.junit.rules.ExpectedException;

                class T {
                    @Rule public ExpectedException thrown = ExpectedException.none();
                    @Test public void test() {
                        thrown.expect(IllegalStateException.class);
                        thrown.expectMessage("bad");
                        failOperation();
                    }
                    void failOperation() {
                        throw new IllegalStateException("bad");
                    }
                }
                """,
              spec -> spec.after(source -> {
                  assertThat(source).contains("import org.junit.jupiter.api.Test;",
                      "import static org.hamcrest.CoreMatchers.containsString;",
                      "import static org.hamcrest.MatcherAssert.assertThat;",
                      "assertThat(exception.getMessage(), containsString(\"bad\"));")
                    .doesNotContain("ExpectedException");
                  return source;
              })
            ))
          )
        );
    }

    @Test
    void anticipatesExpectedMessageWithoutAddingToUnrelatedModules() {
        rewriteRun(
          spec -> spec.cycles(1).expectedCyclesThatMakeChanges(1),
          mavenProject("example",
            pomXml(BEFORE, AFTER),
            srcTestJava(java(
              """
                class T {
                    org.junit.rules.ExpectedException thrown = org.junit.rules.ExpectedException.none();
                    void test() {
                        thrown.expectMessage("bad");
                    }
                }
                """
            ))
          ),
          mavenProject("other",
            pomXml(BEFORE),
            srcTestJava(java("class Other {}"))
          )
        );
    }

    @Test
    void doesNotAddForClassOnlyExpectedException() {
        rewriteRun(
          mavenProject("example",
            pomXml(BEFORE),
            srcTestJava(java(
              """
                class T {
                    org.junit.rules.ExpectedException thrown = org.junit.rules.ExpectedException.none();
                    void test() {
                        thrown.expect(IllegalArgumentException.class);
                    }
                }
                """
            ))
          )
        );
    }

    @Test
    void doesNotAddDependencyWithoutHamcrestUsage() {
        rewriteRun(
          mavenProject("example",
            pomXml(BEFORE),
            srcTestJava(java("class T {}"))
          )
        );
    }
}
