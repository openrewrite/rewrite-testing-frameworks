/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.testing.assertj;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SimplifyRedundantAssertJChainsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3"))
          .recipe(new SimplifyRedundantAssertJChains());
    }

    @DocumentExample
    @Test
    void simplifyStringAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(String str) {
                      assertThat(str).isNotNull().isNotEmpty();
                      assertThat(str).isNotNull().isEmpty();
                      assertThat(str).isNotNull().isBlank();
                      assertThat(str).isNotNull().isNotBlank();
                      assertThat(str).isNotNull().hasSize(5);
                      assertThat(str).isNotNull().contains("test");
                      assertThat(str).isNotNull().startsWith("pre");
                      assertThat(str).isNotNull().endsWith("fix");
                      assertThat(str).isNotNull().matches(".*pattern.*");
                      assertThat(str).isNotNull().isEqualToIgnoringCase("TEST");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(String str) {
                      assertThat(str).isNotEmpty();
                      assertThat(str).isEmpty();
                      assertThat(str).isBlank();
                      assertThat(str).isNotBlank();
                      assertThat(str).hasSize(5);
                      assertThat(str).contains("test");
                      assertThat(str).startsWith("pre");
                      assertThat(str).endsWith("fix");
                      assertThat(str).matches(".*pattern.*");
                      assertThat(str).isEqualToIgnoringCase("TEST");
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyCollectionAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.util.*;

              class Test {
                  void test(Collection<String> collection) {
                      assertThat(collection).isNotNull().isNotEmpty();
                      assertThat(collection).isNotNull().isEmpty();
                      assertThat(collection).isNotNull().hasSize(3);
                      assertThat(collection).isNotNull().contains("item");
                      assertThat(collection).isNotNull().containsOnly("a", "b");
                      assertThat(collection).isNotNull().containsExactly("a", "b", "c");
                      assertThat(collection).isNotNull().containsAll(Arrays.asList("a", "b"));
                      assertThat(collection).isNotEmpty().contains("item");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.util.*;

              class Test {
                  void test(Collection<String> collection) {
                      assertThat(collection).isNotEmpty();
                      assertThat(collection).isEmpty();
                      assertThat(collection).hasSize(3);
                      assertThat(collection).contains("item");
                      assertThat(collection).containsOnly("a", "b");
                      assertThat(collection).containsExactly("a", "b", "c");
                      assertThat(collection).containsAll(Arrays.asList("a", "b"));
                      assertThat(collection).contains("item");
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyListAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.util.List;

              class Test {
                  void test(List<String> list) {
                      assertThat(list).isNotNull().isNotEmpty();
                      assertThat(list).isNotNull().isEmpty();
                      assertThat(list).isNotNull().hasSize(5);
                      assertThat(list).isNotNull().contains("item");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.util.List;

              class Test {
                  void test(List<String> list) {
                      assertThat(list).isNotEmpty();
                      assertThat(list).isEmpty();
                      assertThat(list).hasSize(5);
                      assertThat(list).contains("item");
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyMapAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.util.Map;

              class Test {
                  void test(Map<String, String> map) {
                      assertThat(map).isNotNull().isNotEmpty();
                      assertThat(map).isNotNull().isEmpty();
                      assertThat(map).isNotNull().hasSize(2);
                      assertThat(map).isNotNull().containsKey("key");
                      assertThat(map).isNotNull().containsKeys("key1", "key2");
                      assertThat(map).isNotNull().containsValue("value");
                      assertThat(map).isNotNull().containsEntry("key", "value");
                      assertThat(map).isNotEmpty().containsKey("key");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.util.Map;

              class Test {
                  void test(Map<String, String> map) {
                      assertThat(map).isNotEmpty();
                      assertThat(map).isEmpty();
                      assertThat(map).hasSize(2);
                      assertThat(map).containsKey("key");
                      assertThat(map).containsKeys("key1", "key2");
                      assertThat(map).containsValue("value");
                      assertThat(map).containsEntry("key", "value");
                      assertThat(map).containsKey("key");
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyOptionalAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.util.Optional;

              class Test {
                  void test(Optional<String> optional) {
                      assertThat(optional).isNotNull().isPresent();
                      assertThat(optional).isNotNull().isNotPresent();
                      assertThat(optional).isNotNull().isEmpty();
                      assertThat(optional).isNotNull().isNotEmpty();
                      assertThat(optional).isNotNull().contains("value");
                      assertThat(optional).isPresent().contains("value");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.util.Optional;

              class Test {
                  void test(Optional<String> optional) {
                      assertThat(optional).isPresent();
                      assertThat(optional).isNotPresent();
                      assertThat(optional).isEmpty();
                      assertThat(optional).isNotEmpty();
                      assertThat(optional).contains("value");
                      assertThat(optional).contains("value");
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyBooleanAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(Boolean bool) {
                      assertThat(bool).isNotNull().isTrue();
                      assertThat(bool).isNotNull().isFalse();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(Boolean bool) {
                      assertThat(bool).isTrue();
                      assertThat(bool).isFalse();
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyObjectAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(Object obj, Object other) {
                      assertThat(obj).isNotNull().isEqualTo(other);
                      assertThat(obj).isNotNull().isSameAs(other);

                      assertThat(obj).isNotNull().isNotEqualTo(other);
                      assertThat(obj).isNotNull().isNotSameAs(other);
                      assertThat(obj).isNotNull().isInstanceOf(String.class);
                      assertThat(obj).isNotNull().hasSameClassAs(other);
                      assertThat(obj).isNotNull().hasToString("text");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(Object obj, Object other) {
                      assertThat(obj).isNotNull().isEqualTo(other);
                      assertThat(obj).isNotNull().isSameAs(other);

                      assertThat(obj).isNotEqualTo(other);
                      assertThat(obj).isNotSameAs(other);
                      assertThat(obj).isInstanceOf(String.class);
                      assertThat(obj).hasSameClassAs(other);
                      assertThat(obj).hasToString("text");
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyArrayAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(String[] array) {
                      assertThat(array).isNotNull().isNotEmpty();
                      assertThat(array).isNotNull().isEmpty();
                      assertThat(array).isNotNull().hasSize(3);
                      assertThat(array).isNotNull().contains("item");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(String[] array) {
                      assertThat(array).isNotEmpty();
                      assertThat(array).isEmpty();
                      assertThat(array).hasSize(3);
                      assertThat(array).contains("item");
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyNumberAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.math.BigDecimal;

              class Test {
                  void testInteger(Integer num) {
                      assertThat(num).isNotNull().isZero();
                      assertThat(num).isNotNull().isNotZero();
                      assertThat(num).isNotNull().isPositive();
                      assertThat(num).isNotNull().isNegative();
                  }

                  void testLong(Long num) {
                      assertThat(num).isNotNull().isZero();
                      assertThat(num).isNotNull().isNotZero();
                  }

                  void testDouble(Double num) {
                      assertThat(num).isNotNull().isZero();
                  }

                  void testBigDecimal(BigDecimal num) {
                      assertThat(num).isNotNull().isZero();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.math.BigDecimal;

              class Test {
                  void testInteger(Integer num) {
                      assertThat(num).isZero();
                      assertThat(num).isNotZero();
                      assertThat(num).isPositive();
                      assertThat(num).isNegative();
                  }

                  void testLong(Long num) {
                      assertThat(num).isZero();
                      assertThat(num).isNotZero();
                  }

                  void testDouble(Double num) {
                      assertThat(num).isZero();
                  }

                  void testBigDecimal(BigDecimal num) {
                      assertThat(num).isZero();
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyFileAndPathAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.io.File;
              import java.nio.file.Path;

              class Test {
                  void testFile(File file) {
                      assertThat(file).isNotNull().exists();
                      assertThat(file).isNotNull().isFile();
                      assertThat(file).isNotNull().isDirectory();
                      assertThat(file).isNotNull().canRead();
                      assertThat(file).isNotNull().canWrite();
                  }

                  void testPath(Path path) {
                      assertThat(path).isNotNull().exists();
                      assertThat(path).isNotNull().isRegularFile();
                      assertThat(path).isNotNull().isDirectory();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.io.File;
              import java.nio.file.Path;

              class Test {
                  void testFile(File file) {
                      assertThat(file).exists();
                      assertThat(file).isFile();
                      assertThat(file).isDirectory();
                      assertThat(file).canRead();
                      assertThat(file).canWrite();
                  }

                  void testPath(Path path) {
                      assertThat(path).exists();
                      assertThat(path).isRegularFile();
                      assertThat(path).isDirectory();
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyHasSizeBeforeContainsExactly() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.util.*;

              class Test {
                  void test(List<String> list) {
                      assertThat(list).hasSize(3).containsExactly("a", "b", "c");
                      assertThat(list).hasSize(2).containsExactlyInAnyOrder("b", "a");
                      assertThat(list).hasSize(1).containsExactlyElementsOf(List.of("a"));
                      assertThat(list).hasSize(2).containsExactlyInAnyOrderElementsOf(Set.of("a", "b"));
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.util.*;

              class Test {
                  void test(List<String> list) {
                      assertThat(list).containsExactly("a", "b", "c");
                      assertThat(list).containsExactlyInAnyOrder("b", "a");
                      assertThat(list).containsExactlyElementsOf(List.of("a"));
                      assertThat(list).containsExactlyInAnyOrderElementsOf(Set.of("a", "b"));
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotSimplifyHasSizeBeforeContains() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.util.List;

              class Test {
                  void test(List<String> list) {
                      // These should NOT be simplified - contains doesn't verify exact size
                      assertThat(list).hasSize(3).contains("a", "b");
                      assertThat(list).hasSize(3).containsOnly("a", "b", "c");
                      assertThat(list).hasSize(3).containsAll(List.of("a", "b"));
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotSimplifyWhenNotRedundant() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(String str) {
                      // These should not be simplified
                      assertThat(str).isNotNull();
                      assertThat(str).isNotEmpty();
                      assertThat(str).isNotNull().isNotNull(); // Double isNotNull - keep as is
                  }
              }
              """
          )
        );
    }

    @Test
    void preservesMethodArguments() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.util.List;

              class Test {
                  void test(List<String> list, String item1, String item2) {
                      assertThat(list).isNotNull().contains(item1, item2);
                      assertThat(list).isNotNull().hasSize(10);
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.util.List;

              class Test {
                  void test(List<String> list, String item1, String item2) {
                      assertThat(list).contains(item1, item2);
                      assertThat(list).hasSize(10);
                  }
              }
              """
          )
        );
    }
}
