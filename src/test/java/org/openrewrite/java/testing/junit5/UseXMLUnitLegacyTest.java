package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

public class UseXMLUnitLegacyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "xmlunit-1.6"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath()
            .build()
            .activateRecipes("org.openrewrite.java.testing.junit5.UseXMLUnitLegacy"));
    }

    @Test
    void shouldMigrateMavenDependency() {
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <dependencies>
                    <dependency>
                      <groupId>xmlunit</groupId>
                      <artifactId>xmlunit</artifactId>
                      <version>1.6</version>
                    </dependency>
                  </dependencies>
                </project>
                """,
              """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.xmlunit</groupId>
                      <artifactId>xmlunit-legacy</artifactId>
                      <version>2.9.1</version>
                    </dependency>
                  </dependencies>
                </project>
                """
            )
          )
        );
    }
}
