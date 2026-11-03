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
package org.openrewrite.java.testing.mockito;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.semver.LatestRelease;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.xml.tree.Xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.javaVersion;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcTestJava;
import static org.openrewrite.maven.Assertions.pomXml;

/**
 * Spring Boot 2 manages both Mockito and Byte Buddy. Raising the managed Mockito without raising Byte Buddy leaves
 * a Mockito that fails to load its mock maker, such as with a `ClassNotFoundException` for
 * `net.bytebuddy.utility.GraalImageCode`.
 */
class MockitoByteBuddyAlignmentTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "mockito-core-3"));
    }

    //language=xml
    private static final String SPRING_BOOT_2_1_POM = """
      <project>
        <modelVersion>4.0.0</modelVersion>
        <parent>
          <groupId>org.springframework.boot</groupId>
          <artifactId>spring-boot-starter-parent</artifactId>
          <version>2.1.6.RELEASE</version>
        </parent>
        <groupId>com.example</groupId>
        <artifactId>demo</artifactId>
        <version>1.0</version>
        <dependencies>
          <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
            <version>2.23.4</version>
            <scope>test</scope>
          </dependency>
        </dependencies>
      </project>
      """;

    //language=java
    private static final String TEST = """
      import org.mockito.Mockito;

      class MyTest {
          void test() {
              Runnable runnable = Mockito.mock(Runnable.class);
          }
      }
      """;

    @Test
    void byteBuddyRaisedAlongWithMockito4() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.java.testing.mockito.Mockito1to4Migration"),
          mavenProject("demo",
            pomXml(SPRING_BOOT_2_1_POM, spec -> spec.after(pom -> pom).afterRecipe(pom -> {
                assertThat(resolvedVersion(pom, "org.mockito", "mockito-core")).startsWith("4.");
                assertAtLeast(resolvedVersion(pom, "net.bytebuddy", "byte-buddy"), "1.12.19");
                assertAtLeast(resolvedVersion(pom, "net.bytebuddy", "byte-buddy-agent"), "1.12.19");
            })),
            srcTestJava(java(TEST, spec -> spec.markers(javaVersion(17))))
          )
        );
    }

    @Test
    void byteBuddyRaisedAlongWithMockito5() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.java.testing.mockito.Mockito1to5Migration"),
          mavenProject("demo",
            pomXml(SPRING_BOOT_2_1_POM, spec -> spec.after(pom -> pom).afterRecipe(pom -> {
                assertThat(resolvedVersion(pom, "org.mockito", "mockito-core")).startsWith("5.");
                assertAtLeast(resolvedVersion(pom, "net.bytebuddy", "byte-buddy"), "1.17.0");
                assertAtLeast(resolvedVersion(pom, "net.bytebuddy", "byte-buddy-agent"), "1.17.0");
            })),
            srcTestJava(java(TEST, spec -> spec.markers(javaVersion(17))))
          )
        );
    }

    @Test
    void mockitoManagedByTheBomIsLeftAlone() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.java.testing.mockito.Mockito1to5Migration"),
          mavenProject("demo",
            pomXml(SPRING_BOOT_2_1_POM.replaceAll("\\s*<version>2\\.23\\.4</version>", "")),
            srcTestJava(java(TEST, spec -> spec.markers(javaVersion(17))))
          )
        );
    }

    @Test
    void byteBuddyOfABomManagedMockitoIsLeftAlone() {
        rewriteRun(
          spec -> spec.recipeFromResources(
            "org.openrewrite.java.testing.mockito.UpgradeByteBuddyForMockito3",
            "org.openrewrite.java.testing.mockito.UpgradeByteBuddyForMockito4",
            "org.openrewrite.java.testing.mockito.UpgradeByteBuddyForMockito5"
          ),
          // Each BOM's Byte Buddy is below the recipe's minimum for that Mockito major, yet no older than its Mockito declares
          // Mockito 3.6.28 with Byte Buddy 1.10.22
          mavenProject("boot-2-4", pomXml(springBootStarterTestPom("2.4.13"))),
          // Mockito 4.0.0 with Byte Buddy 1.11.22
          mavenProject("boot-2-6", pomXml(springBootStarterTestPom("2.6.15"))),
          // Mockito 5.7.0 with Byte Buddy 1.14.19
          mavenProject("boot-3-2", pomXml(springBootStarterTestPom("3.2.12")))
        );
    }

    @Test
    void byteBuddyRaisedFromTheFirstMockitoDeclaringTheMinimum() {
        assertByteBuddyRaised("UpgradeByteBuddyForMockito3", "3.12.1", "1.11.13");

        assertByteBuddyRaised("UpgradeByteBuddyForMockito4", "4.10.0", "1.12.19");

        assertByteBuddyRaised("UpgradeByteBuddyForMockito5", "5.18.0", "1.17.0");
    }

    private void assertByteBuddyRaised(String recipe, String mockitoVersion, String minimum) {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.java.testing.mockito." + recipe),
          pomXml(SPRING_BOOT_2_1_POM.replace("2.23.4", mockitoVersion), spec -> spec.after(pom -> pom).afterRecipe(pom -> {
              assertAtLeast(resolvedVersion(pom, "net.bytebuddy", "byte-buddy"), minimum);
              assertAtLeast(resolvedVersion(pom, "net.bytebuddy", "byte-buddy-agent"), minimum);
          }))
        );
    }

    private static String springBootStarterTestPom(String bootVersion) {
        //language=xml
        return """
          <project>
            <modelVersion>4.0.0</modelVersion>
            <parent>
              <groupId>org.springframework.boot</groupId>
              <artifactId>spring-boot-starter-parent</artifactId>
              <version>%1$s</version>
            </parent>
            <groupId>com.example</groupId>
            <artifactId>boot-%1$s</artifactId>
            <version>1.0</version>
            <dependencies>
              <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-test</artifactId>
                <scope>test</scope>
              </dependency>
            </dependencies>
          </project>
          """.formatted(bootVersion);
    }

    private static String resolvedVersion(Xml.Document pom, String groupId, String artifactId) {
        return pom.getMarkers().findFirst(MavenResolutionResult.class).orElseThrow()
          .getDependencies().get(Scope.Test).stream()
          .filter(d -> groupId.equals(d.getGroupId()) && artifactId.equals(d.getArtifactId()))
          .map(ResolvedDependency::getVersion)
          .findFirst()
          .orElseThrow(() -> new AssertionError(groupId + ":" + artifactId + " is not resolved"));
    }

    private static void assertAtLeast(String actual, String minimum) {
        assertThat(new LatestRelease(null).compare(null, actual, minimum))
          .as("%s is at least %s", actual, minimum)
          .isGreaterThanOrEqualTo(0);
    }
}
