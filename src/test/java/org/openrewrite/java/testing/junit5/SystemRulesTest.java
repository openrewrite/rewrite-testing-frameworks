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
package org.openrewrite.java.testing.junit5;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SystemRulesTest {

    @Test
    void scanOnlyRecordsRuleTypedFields() {
        @Language("java")
        String base = """
          import org.junit.Rule;
          import org.junit.contrib.java.lang.system.RestoreSystemProperties;
          import org.junit.contrib.java.lang.system.SystemOutRule;
          import org.junit.rules.TestRule;

          public class BaseTest {
              @Rule
              public final SystemOutRule out = new SystemOutRule().enableLog();
              @Rule
              public final TestRule restore = new RestoreSystemProperties();
              protected String name = "base";
          }
          """;
        @Language("java")
        String sub = """
          import org.junit.Test;

          public class SubTest extends BaseTest {
              @Test
              public void logs() {
                  String greeting = "hi " + name;
                  System.out.println(greeting);
                  out.getLog();
              }
          }
          """;
        InMemoryExecutionContext ctx = new InMemoryExecutionContext();
        Map<String, Set<Path>> usages = new HashMap<>();
        JavaParser.fromJavaVersion()
          .classpathFromResources(ctx, "junit-4", "system-rules-1")
          .build()
          .parse(ctx, base, sub)
          .map(J.CompilationUnit.class::cast)
          .forEach(cu -> SystemRules.scanFieldUsages(cu, usages));

        assertThat(usages)
          .containsOnlyKeys("BaseTest#out", "BaseTest#restore")
          .containsEntry("BaseTest#out", Set.of(Path.of("BaseTest.java"), Path.of("SubTest.java")))
          .containsEntry("BaseTest#restore", Set.of(Path.of("BaseTest.java")));
    }
}
