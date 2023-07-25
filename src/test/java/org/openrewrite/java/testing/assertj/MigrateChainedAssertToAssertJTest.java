package org.openrewrite.java.testing.assertj;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

public class MigrateChainedAssertToAssertJTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-jupiter-api-5.9",
              "hamcrest-2.2",
              "assertj-core-3.24"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing.assertj")
            .build()
            .activateRecipes("org.openrewrite.java.testing.assertj.SimplifyChainedAssertJAssertions"));
    }

    private static Stream<Arguments> stringReplacements() {
        return Stream.of(
          Arguments.arguments("getString().isEmpty()", "isTrue()", "isEmpty"),
          Arguments.arguments("getString()", "hasSize(0)", "isEmpty()"),
          Arguments.arguments("getString().equals(expected)", "isTrue()", "isEqualTo"),
          Arguments.arguments("getString().equalsIgnoreCase(expected)", "isTrue", "isEqualToIgnoringCase"),
          Arguments.arguments("getString().contains(expected)", "isTrue", "contains"),
          Arguments.arguments("getString().startsWith(expected)", "isTrue", "startsWith"),
          Arguments.arguments("getString().endsWith(expected)", "isTrue", "endsWith"),
          Arguments.arguments("getString().matches(expected)", "isTrue", "matches"),
          Arguments.arguments("getString().trim()", "isEmpty", "isBlank"),
          Arguments.arguments("getString().length()", "isEqualTo(length)", "hasSize"),
          Arguments.arguments("getString().isEmpty()", "isFalse(expected.length())", "isNotEmpty")
        );
    }

    @ParameterizedTest
    @MethodSource("stringReplacements")
    void stringReplacements(String chainedAssertion, String assertToReplace, String dedicatedAssertion, String argumentOne, String argumentTwo) {
        String template = """
          import org.junit.jupiter.api.Test;
          
          import static org.assertj.core.api.Assertions.assertThat;
          
          class MyTest {
              @Test
              void test() {
                  String expected = "hello world";
                  %s
              }
              
              String getString() {
                  return "hello world";
              }
          }
          """;
        String assertBefore = "assertThat()"
        String before = String.format(template, )
    }
}
