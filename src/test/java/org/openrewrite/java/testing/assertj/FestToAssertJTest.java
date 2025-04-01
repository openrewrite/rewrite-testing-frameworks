package org.openrewrite.java.testing.assertj;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

public class FestToAssertJTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "fest-assert-core"))
          .recipeFromResources("org.openrewrite.java.testing.assertj.FestToAssertj");
    }

    @DocumentExample
    @Test
    void documentExample() {
        //language=java
        rewriteRun(
          pomXml(
            // language=xml
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>sample-project</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.easytesting</groupId>
                          <artifactId>fest-assert-core</artifactId>
                          <version>2.0M10</version>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(str -> assertThat(str)
              .containsPattern("<groupId>org\\.assertj</groupId>\\s*<artifactId>assertj-core</artifactId>\\s*<version>3\\.\\d+\\.\\d+</version>")
              .doesNotContainPattern("<groupId>org\\.easytesting</groupId>\\s*<artifactId>fest-assert-core</artifactId>\\s*<version>2\\.0M10</version>")
              .actual())),
          // language=java
          java(
            """
              import org.fest.assertions.api.Assertions;

              import static org.fest.assertions.api.Assertions.assertThat;

              class MyTest {
                  void test(Object value) {
                      Assertions.assertThat(value).isEqualTo("foo");
                      assertThat(value).isEqualTo("bar");
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test(Object value) {
                      Assertions.assertThat(value).isEqualTo("foo");
                      assertThat(value).isEqualTo("bar");
                  }
              }
              """
          )
        );
    }
}
