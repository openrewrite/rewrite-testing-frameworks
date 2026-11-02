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
package org.openrewrite.java.testing.easymock;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.javaVersion;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcTestJava;
import static org.openrewrite.maven.Assertions.pomXml;

class EasyMockToMockitoDependenciesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-4", "easymock-5"))
          .recipeFromResources("org.openrewrite.java.testing.easymock.EasyMockToMockito");
    }

    @ParameterizedTest
    @CsvSource({"8, 4", "11, 5", "17, 5"})
    void mockitoVersionThatRunsOnTheJavaVersion(int javaVersion, int mockitoMajorVersion) {
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>junit</groupId>
                            <artifactId>junit</artifactId>
                            <version>4.13.2</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.easymock</groupId>
                            <artifactId>easymock</artifactId>
                            <version>4.3</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> {
                  Matcher version = Pattern.compile("<artifactId>mockito-core</artifactId>\\s*<version>(\\d+)\\.").matcher(pom);
                  assertThat(version.find()).as(pom).isTrue();
                  assertThat(version.group(1)).isEqualTo(String.valueOf(mockitoMajorVersion));
                  assertThat(pom).doesNotContain("easymock");
                  return pom;
              })
            ),
            srcTestJava(
              //language=java
              java(
                """
                  import org.junit.Test;

                  import static org.easymock.EasyMock.*;

                  public class ExampleTest {
                      @Test
                      public void test() {
                          Runnable runnable = createMock(Runnable.class);
                          replay(runnable);
                          runnable.run();
                      }
                  }
                  """,
                """
                  import org.junit.Test;

                  import static org.mockito.Mockito.mock;

                  public class ExampleTest {
                      @Test
                      public void test() {
                          Runnable runnable = mock(Runnable.class);
                          runnable.run();
                      }
                  }
                  """,
                spec -> spec.markers(javaVersion(javaVersion))
              )
            )
          )
        );
    }
}
