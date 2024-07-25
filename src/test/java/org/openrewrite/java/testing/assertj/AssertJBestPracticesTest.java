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
package org.openrewrite.java.testing.assertj;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

import java.util.stream.Stream;

class AssertJBestPracticesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3.24"))
          .recipeFromResources("org.openrewrite.java.testing.assertj.Assertj");
    }

    @DocumentExample
    @Test
    void convertsIsEqualToEmptyString() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;
              class Test {
                  void test() {
                      assertThat("test").isEqualTo("");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;
              class Test {
                  void test() {
                      assertThat("test").isEmpty();
                  }
              }
              """
          )
        );
    }
    

    /**
     * Chained AssertJ assertions should be simplified to the corresponding dedicated assertion
     * 
     * https://next.sonarqube.com/sonarqube/coding_rules?open=java%3AS5838&rule_key=java%3AS5838
     */
    @Nested
    class SonarDedicatedAssertions {
        private static Stream<Arguments> replacements() {
            return Stream.of(
                    //Related to Object
                    Arguments.arguments("Object","assertThat(x).isEqualTo(null)","assertThat(x).isNull()"),
                    Arguments.arguments("Boolean","assertThat(x).isEqualTo(true)","assertThat(x).isTrue()"),
                    Arguments.arguments("Boolean","assertThat(x).isEqualTo(false)","assertThat(x).isFalse()"),
                    Arguments.arguments("Object","assertThat(x.equals(y)).isTrue()","assertThat(x).isEqualTo(y)"),
                    Arguments.arguments("Object","assertThat(x == y).isTrue()","assertThat(x).isSameAs(y)"),
                    Arguments.arguments("Object","assertThat(x == null).isTrue()","assertThat(x).isNull()"),
                    Arguments.arguments("Object","assertThat(x.toString()).isEqualTo(\"y\")","assertThat(x).hasToString(\"y\")"),
                    Arguments.arguments("Object","assertThat(x.hashCode()).isEqualTo(y.hashCode())","assertThat(x).hasSameHashCodeAs(y)"),
                    Arguments.arguments("Object","assertThat(x instanceof String).isTrue()","assertThat(x).isInstanceOf(String.class)"),
                    //Related to Comparable
                    Arguments.arguments("java.math.BigDecimal","assertThat(x.compareTo(y)).isZero()","assertThat(x).isEqualByComparingTo(y)"),
                    Arguments.arguments("int","assertThat(x >= y).isTrue()","assertThat(x).isGreaterThanOrEqualTo(y)"),
                    Arguments.arguments("long","assertThat(x > y).isTrue()","assertThat(x).isGreaterThan(y)"),
                    Arguments.arguments("double","assertThat(x <= y).isTrue()","assertThat(x).isLessThanOrEqualTo(y)"),
                    Arguments.arguments("float","assertThat(x < y).isTrue()","assertThat(x).isLessThan(y)"),
                    //Related to String
                    Arguments.arguments("String","assertThat(x.isEmpty()).isTrue()","assertThat(x).isEmpty()"),
                    Arguments.arguments("String","assertThat(x).hasSize(0)","assertThat(x).isEmpty()"),
                    Arguments.arguments("String","assertThat(x.equals(y)).isTrue()","assertThat(x).isEqualTo(y)"),
                    Arguments.arguments("String","assertThat(x.equalsIgnoreCase(y)).isTrue()","assertThat(x).isEqualToIgnoringCase(y)"),
                    Arguments.arguments("String","assertThat(x.contains(y)).isTrue()","assertThat(x).contains(y)"),
                    Arguments.arguments("String","assertThat(x.startsWith(y)).isTrue()","assertThat(x).startsWith(y)"),
                    Arguments.arguments("String","assertThat(x.endsWith(y)).isTrue()","assertThat(x).endsWith(y)"),
                    Arguments.arguments("String","assertThat(x.matches(y)).isTrue()","assertThat(x).matches(y)"),
                    Arguments.arguments("String","assertThat(x.trim()).isEmpty()","assertThat(x).isBlank()"),
                    Arguments.arguments("String","assertThat(x.length()).isEqualTo(5)","assertThat(x).hasSize(5)"),
                    Arguments.arguments("String","assertThat(x).hasSize(y.length())","assertThat(x).hasSameSizeAs(y)"),
                    //Related to File
                    Arguments.arguments("java.io.File","assertThat(x).hasSize(0)","assertThat(x).isEmpty()"),
                    Arguments.arguments("java.io.File","assertThat(x.length()).isZero()","assertThat(x).isEmpty()"),
                    Arguments.arguments("java.io.File","assertThat(x.length()).isEqualTo(3)","assertThat(x).hasSize(3)"),
                    Arguments.arguments("java.io.File","assertThat(x.canRead()).isTrue()","assertThat(x).canRead()"),
                    Arguments.arguments("java.io.File","assertThat(x.canWrite()).isTrue()","assertThat(x).canWrite()"),
                    Arguments.arguments("java.io.File","assertThat(x.exists()).isTrue()","assertThat(x).exists()"),
                    Arguments.arguments("java.io.File","assertThat(x.getName()).isEqualTo(\"a\")","assertThat(x).hasName(\"a\")"),
                    Arguments.arguments("java.io.File","assertThat(x.getParent()).isEqualTo(\"b\")","assertThat(x).hasParent(\"b\")"),
                    Arguments.arguments("java.io.File","assertThat(x.getParentFile()).isNull()","assertThat(x).hasNoParent()"),
                    Arguments.arguments("java.io.File","assertThat(x.isAbsolute()).isTrue()","assertThat(x).isAbsolute()"),
                    Arguments.arguments("java.io.File","assertThat(x.isAbsolute()).isFalse()","assertThat(x).isRelative()"),
                    Arguments.arguments("java.io.File","assertThat(x.isDirectory()).isTrue()","assertThat(x).isDirectory()"),
                    Arguments.arguments("java.io.File","assertThat(x.isFile()).isTrue()","assertThat(x).isFile()"),
                    Arguments.arguments("java.io.File","assertThat(x.list()).isEmpty()","assertThat(x).isEmptyDirectory()"),
                    //Related to Path
                    Arguments.arguments("java.nio.file.Path","assertThat(x.startsWith(\"x\")).isTrue()","assertThat(x).startsWithRaw(\"x\")"),
                    Arguments.arguments("java.nio.file.Path","assertThat(x.endsWith(\"y\")).isTrue()","assertThat(x).endsWithRaw(\"y\")"),
                    Arguments.arguments("java.nio.file.Path","assertThat(x.getParent()).isEqualTo(y)","assertThat(x).hasParentRaw(y)"),
                    Arguments.arguments("java.nio.file.Path","assertThat(x.getParent()).isNull()","assertThat(x).hasNoParentRaw()"),
                    Arguments.arguments("java.nio.file.Path","assertThat(x.isAbsolute()).isTrue()","assertThat(x).isAbsolute()"),
                    Arguments.arguments("java.nio.file.Path","assertThat(x.isAbsolute()).isFalse()","assertThat(x).isRelative()"),
                    ///Related to Array
                    Arguments.arguments("Object[]","assertThat(x.length).isZero()","assertThat(x).isEmpty()"),
                    Arguments.arguments("String[]","assertThat(x.length).isEqualTo(7)","assertThat(x).hasSize(7)"),
                    Arguments.arguments("int[]","assertThat(x.length).isEqualTo(y.length)","assertThat(x).hasSameSizeAs(y)"),
                    Arguments.arguments("boolean[]","assertThat(x.length).isLessThanOrEqualTo(2)","assertThat(x).hasSizeLessThanOrEqualTo(2)"),
                    Arguments.arguments("double[]","assertThat(x.length).isLessThan(5)","assertThat(x).hasSizeLessThan(5)"),
                    Arguments.arguments("long[]","assertThat(x.length).isGreaterThan(4)","assertThat(x).hasSizeGreaterThan(4)"),
                    Arguments.arguments("char[]","assertThat(x.length).isGreaterThanOrEqualTo(1)","assertThat(x).hasSizeGreaterThanOrEqualTo(1)"),
                    //Related to Collection
                    Arguments.arguments("java.util.Collection<String>","assertThat(x.isEmpty()).isTrue()","assertThat(x).isEmpty()"),
                    Arguments.arguments("java.util.Collection<String>","assertThat(x.size()).isZero()","assertThat(x).isEmpty()"),
                    Arguments.arguments("java.util.Collection<String>","assertThat(x.contains(\"f\")).isTrue()","assertThat(x).contains(\"f\")"),
                    Arguments.arguments("java.util.Collection<String>","assertThat(x.containsAll(y)).isTrue()","assertThat(x).containsAll(y)"),
                    //Related to Map
                    Arguments.arguments("java.util.Map<String, Object>","assertThat(x).hasSize(y.size())","assertThat(x).hasSameSizeAs(y)"),
                    Arguments.arguments("java.util.Map<String, Object>","assertThat(x.containsKey(\"b\")).isTrue()","assertThat(x).containsKey(\"b\")"),
                    Arguments.arguments("java.util.Map<String, Object>","assertThat(x.keySet()).contains(\"b\")","assertThat(x).containsKey(\"b\")"),
                    Arguments.arguments("java.util.Map<String, Object>","assertThat(x.keySet()).containsOnly(\"a\")","assertThat(x).containsOnlyKey(\"a\")"),
                    Arguments.arguments("java.util.Map<String, Object>","assertThat(x.containsValue(value)).isTrue()","assertThat(x).containsValue(value)"),
                    Arguments.arguments("java.util.Map<String, Object>","assertThat(x.values()).contains(value)","assertThat(x).containsValue(value)"),
                    Arguments.arguments("java.util.Map<String, Object>","assertThat(x.get(\"a\")).isEqualTo(value)","assertThat(x).containsEntry(\"a\", value)"),
                    //Related to Optional
                    Arguments.arguments("java.util.Optional<Object>","assertThat(x.isPresent()).isTrue()","assertThat(x).isPresent()"),
                    Arguments.arguments("java.util.Optional<Object>","assertThat(x.get()).isEqualTo(value)","assertThat(x).contains(value)"),
                    Arguments.arguments("java.util.Optional<Object>","assertThat(x.get()).isSameAs(value)","assertThat(x).containsSame(value)")

            );
        }

        @ParameterizedTest
        @MethodSource("replacements")
        void sonarReplacements(String argumentsType, String assertToReplace, String dedicatedAssertion) {
            //language=java
            String template = """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void test(%s x, %s y, Object value) {
                      %s;
                  }
              }
              """;

            String before = String.format(template, argumentsType, argumentsType, assertToReplace);
            String after = String.format(template, argumentsType, argumentsType, dedicatedAssertion);

            rewriteRun(
              java(before, after)
            );
        }
    }
}
