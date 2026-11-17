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
package org.openrewrite.java.testing.junit4;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcTestJava;
import static org.openrewrite.maven.Assertions.pomXml;

class UseCoreMatchersContainsStringTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-4", "hamcrest-core-1.3"))
          .recipeFromResources("org.openrewrite.java.testing.junit4.UseCoreMatchersContainsString");
    }

    private static final String TEST_BEFORE = """
      import static org.junit.Assert.assertThat;
      import static org.junit.matchers.JUnitMatchers.containsString;

      public class FooTest {
          public void testIt() {
              assertThat("abc", containsString("b"));
          }
      }
      """;

    private static String pom(String junitVersion) {
        return """
          <project>
            <groupId>com.example</groupId>
            <artifactId>app</artifactId>
            <version>1.0</version>
            <dependencies>
              <dependency>
                <groupId>junit</groupId>
                <artifactId>junit</artifactId>
                <version>%s</version>
                <scope>test</scope>
              </dependency>
            </dependencies>
          </project>
          """.formatted(junitVersion);
    }

    @Test
    void movesToCoreMatchersOnJUnit4_11OrLater() {
        rewriteRun(
          mavenProject("app",
            //language=java
            srcTestJava(java(
              TEST_BEFORE,
              """
                import static org.hamcrest.CoreMatchers.containsString;
                import static org.junit.Assert.assertThat;

                public class FooTest {
                    public void testIt() {
                        assertThat("abc", containsString("b"));
                    }
                }
                """
            )),
            //language=xml
            pomXml(pom("4.12"))
          )
        );
    }

    @Test
    void leavesBeforeJUnit4_11AsItsHamcrestLacksIt() {
        rewriteRun(
          mavenProject("app",
            //language=java
            srcTestJava(java(TEST_BEFORE)),
            //language=xml
            pomXml(pom("4.10"))
          )
        );
    }
}
