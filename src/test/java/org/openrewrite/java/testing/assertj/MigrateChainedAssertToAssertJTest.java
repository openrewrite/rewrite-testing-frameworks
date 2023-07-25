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

import static org.openrewrite.java.Assertions.java;

public class MigrateChainedAssertToAssertJTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-jupiter-api-5.9",
              "assertj-core-3.24"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing.assertj")
            .build()
            .activateRecipes("org.openrewrite.java.testing.assertj.SimplifyChainedAssertJAssertions"));
    }

    private static Stream<Arguments> stringReplacements() {
        return Stream.of(
          Arguments.arguments("isEmpty", "isTrue", "isEmpty", "", ""),
          Arguments.arguments("getString", "hasSize", "isEmpty", "", "0"),
          Arguments.arguments("equals", "isTrue", "isEqualTo", "expected", ""),
          Arguments.arguments("equalsIgnoreCase", "isTrue", "isEqualToIgnoringCase", "expected", ""),
          Arguments.arguments("contains", "isTrue", "contains", "expected", ""),
          Arguments.arguments("startsWith", "isTrue", "startsWith", "expected", ""),
          Arguments.arguments("endsWith", "isTrue", "endsWith", "expected", ""),
          Arguments.arguments("matches", "isTrue", "matches", "expected", ""),
          Arguments.arguments("trim", "isEmpty", "isBlank", "", ""),
          Arguments.arguments("length", "isEqualTo", "hasSize", "", "length"),
          Arguments.arguments("isEmpty", "isFalse", "isNotEmpty", "", "expected.length()")
        );
    }

    @ParameterizedTest
    @MethodSource("stringReplacements")
    void stringReplacements(String chainedAssertion, String assertToReplace, String dedicatedAssertion, String firstArg, String secondArg) {
        //language=java
        String template = """
          import org.junit.jupiter.api.Test;
          
          import static org.junit.jupiter.api.Assertions.assertThat;
          
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
        String assertBefore = chainedAssertion.equals("getString") ? "assertThat(%s(%s)).%s(%s);" : "assertThat(getString().%s(%s)).%s(%s);";
        String assertAfter = "assertThat(getString()).%s(%s);";
        String before = String.format(template, assertBefore.formatted(chainedAssertion, firstArg, assertToReplace, secondArg));
        String after = String.format(template, assertAfter.formatted(dedicatedAssertion, firstArg.equals("") ? secondArg : firstArg));
        rewriteRun(java(before, after));
    }
}
