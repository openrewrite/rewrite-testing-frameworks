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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class JUnitSoftAssertionsToSoftAssertionsExtensionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new JUnitSoftAssertionsToSoftAssertionsExtension())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-4", "junit-jupiter-api-5", "assertj-core-3"));
    }

    @DocumentExample
    @Test
    void softAssertionsRule() {
        rewriteRun(
          // language=java
          java(
            """
              import org.assertj.core.api.JUnitSoftAssertions;
              import org.junit.Rule;
              import org.junit.Test;

              public class SoftlyTest {
                  @Rule
                  public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

                  @Test
                  public void multipleAssertions() {
                      softly.assertThat("foo").isEqualTo("bar");
                  }
              }
              """,
            """
              import org.assertj.core.api.SoftAssertions;
              import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
              import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
              import org.junit.Test;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(SoftAssertionsExtension.class)
              public class SoftlyTest {
                  @InjectSoftAssertions
                  public SoftAssertions softly;

                  @Test
                  public void multipleAssertions() {
                      softly.assertThat("foo").isEqualTo("bar");
                  }
              }
              """
          )
        );
    }

    @Test
    void bddSoftAssertionsRule() {
        rewriteRun(
          // language=java
          java(
            """
              import org.assertj.core.api.JUnitBDDSoftAssertions;
              import org.junit.Rule;

              public class SoftlyTest {
                  @Rule
                  public final JUnitBDDSoftAssertions softly = new JUnitBDDSoftAssertions();
              }
              """,
            """
              import org.assertj.core.api.BDDSoftAssertions;
              import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
              import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(SoftAssertionsExtension.class)
              public class SoftlyTest {
                  @InjectSoftAssertions
                  public BDDSoftAssertions softly;
              }
              """
          )
        );
    }

    @Test
    void ruleOnSameLineWithoutAccessModifier() {
        rewriteRun(
          // language=java
          java(
            """
              import org.assertj.core.api.JUnitSoftAssertions;
              import org.junit.Rule;

              class SoftlyTest {
                  @Rule
                  final JUnitSoftAssertions softly = new JUnitSoftAssertions();
              }
              """,
            """
              import org.assertj.core.api.SoftAssertions;
              import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
              import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(SoftAssertionsExtension.class)
              class SoftlyTest {
                  @InjectSoftAssertions
                  SoftAssertions softly;
              }
              """
          )
        );
    }

    @Test
    void retainExistingExtendWith() {
        rewriteRun(
          // language=java
          java(
            """
              import org.assertj.core.api.JUnitSoftAssertions;
              import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
              import org.junit.Rule;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(SoftAssertionsExtension.class)
              class SoftlyTest {
                  @Rule
                  public JUnitSoftAssertions softly = new JUnitSoftAssertions();
              }
              """,
            """
              import org.assertj.core.api.SoftAssertions;
              import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
              import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(SoftAssertionsExtension.class)
              class SoftlyTest {
                  @InjectSoftAssertions
                  public SoftAssertions softly;
              }
              """
          )
        );
    }

    @Test
    void nestedClass() {
        rewriteRun(
          // language=java
          java(
            """
              import org.assertj.core.api.JUnitSoftAssertions;
              import org.junit.Rule;
              import org.junit.jupiter.api.Nested;

              class SoftlyTest {
                  @Nested
                  class Inner {
                      @Rule
                      public final JUnitSoftAssertions softly = new JUnitSoftAssertions();
                  }
              }
              """,
            """
              import org.assertj.core.api.SoftAssertions;
              import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
              import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.extension.ExtendWith;

              class SoftlyTest {
                  @ExtendWith(SoftAssertionsExtension.class)
                  @Nested
                  class Inner {
                      @InjectSoftAssertions
                      public SoftAssertions softly;
                  }
              }
              """
          )
        );
    }

    @Test
    void retainOtherRules() {
        rewriteRun(
          // language=java
          java(
            """
              import org.assertj.core.api.JUnitSoftAssertions;
              import org.junit.Rule;
              import org.junit.rules.TemporaryFolder;

              class SoftlyTest {
                  @Rule
                  public final TemporaryFolder folder = new TemporaryFolder();

                  @Rule
                  public final JUnitSoftAssertions softly = new JUnitSoftAssertions();
              }
              """,
            """
              import org.assertj.core.api.SoftAssertions;
              import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
              import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
              import org.junit.Rule;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.junit.rules.TemporaryFolder;

              @ExtendWith(SoftAssertionsExtension.class)
              class SoftlyTest {
                  @Rule
                  public final TemporaryFolder folder = new TemporaryFolder();

                  @InjectSoftAssertions
                  public SoftAssertions softly;
              }
              """
          )
        );
    }

    @Test
    void noChangeForLocalSoftAssertions() {
        rewriteRun(
          // language=java
          java(
            """
              import org.assertj.core.api.JUnitSoftAssertions;
              import org.junit.Rule;
              import org.junit.rules.TemporaryFolder;

              class SoftlyTest {
                  @Rule
                  public final TemporaryFolder folder = new TemporaryFolder();

                  void notATest() {
                      JUnitSoftAssertions softly = new JUnitSoftAssertions();
                      softly.assertThat("foo").isEqualTo("bar");
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForClassRule() {
        rewriteRun(
          // language=java
          java(
            """
              import org.assertj.core.api.JUnitSoftAssertions;
              import org.junit.ClassRule;

              class SoftlyTest {
                  @ClassRule
                  public static final JUnitSoftAssertions softly = new JUnitSoftAssertions();
              }
              """
          )
        );
    }

    @Test
    void noChangeForClassRuleAlongsideRule() {
        rewriteRun(
          // language=java
          java(
            """
              import org.assertj.core.api.JUnitSoftAssertions;
              import org.junit.ClassRule;
              import org.junit.Rule;

              class SoftlyTest {
                  @ClassRule
                  public static final JUnitSoftAssertions classSoftly = new JUnitSoftAssertions();

                  @Rule
                  public final JUnitSoftAssertions softly = new JUnitSoftAssertions();
              }
              """
          )
        );
    }

    @Test
    void noChangeForRuleChainMember() {
        rewriteRun(
          // language=java
          java(
            """
              import org.assertj.core.api.JUnitSoftAssertions;
              import org.junit.Rule;
              import org.junit.rules.RuleChain;

              class SoftlyTest {
                  private final JUnitSoftAssertions chained = new JUnitSoftAssertions();

                  @Rule
                  public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

                  @Rule
                  public final RuleChain chain = RuleChain.outerRule(chained);
              }
              """
          )
        );
    }

    @Test
    void convertOnlyTheRuleTypeWithoutRemainingRuleFields() {
        rewriteRun(
          // language=java
          java(
            """
              import org.assertj.core.api.JUnitBDDSoftAssertions;
              import org.assertj.core.api.JUnitSoftAssertions;
              import org.junit.ClassRule;
              import org.junit.Rule;

              class SoftlyTest {
                  @ClassRule
                  public static final JUnitSoftAssertions classSoftly = new JUnitSoftAssertions();

                  @Rule
                  public final JUnitBDDSoftAssertions softly = new JUnitBDDSoftAssertions();
              }
              """,
            """
              import org.assertj.core.api.BDDSoftAssertions;
              import org.assertj.core.api.JUnitSoftAssertions;
              import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
              import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
              import org.junit.ClassRule;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(SoftAssertionsExtension.class)
              class SoftlyTest {
                  @ClassRule
                  public static final JUnitSoftAssertions classSoftly = new JUnitSoftAssertions();

                  @InjectSoftAssertions
                  public BDDSoftAssertions softly;
              }
              """
          )
        );
    }
}
