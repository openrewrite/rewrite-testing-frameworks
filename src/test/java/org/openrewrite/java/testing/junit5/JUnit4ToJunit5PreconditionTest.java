/*
 * Copyright 2024 the original author or authors.
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

import java.util.Set;

import static org.openrewrite.java.Assertions.java;

class JUnit4ToJunit5PreconditionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new JUnit4ToJunit5Precondition(
          Set.of("MigratableBaseTestClass"),
            Set.of("org.junit.rules.TemporaryFolder"),
            Set.of("org.junit.rules.ExternalResource"),
            Set.of("org.junit.runners.Parameterized")))
          .parser(
            JavaParser.fromJavaVersion()
              .classpathFromResources(new InMemoryExecutionContext(),"junit-4", "junit-jupiter-api-5")
              // language=java
              .dependsOn(
                """
                  public class MigratableBaseTestClass {
                  }
                  """,
                """
                  public class NonMigratableBaseTestClass {
                  }
                  """,
                """
                  package io.grpc.testing;

                  import org.junit.rules.ExternalResource;

                  public class GrpcCleanupRule extends ExternalResource {
                  }
                  """,
                // Stubbing for junitparams.Parameters
                """
                  package junitparams;

                  import java.lang.annotation.*;

                  @Retention(RetentionPolicy.RUNTIME)
                  @Target({ElementType.TYPE, ElementType.METHOD})
                  public @interface Parameters {
                      Class<?> source();
                  }
                  """));
    }

    @DocumentExample
    @Test
    void extendsMigratableBaseTestClass() {
        rewriteRun(
          // language=java
          java(
            """
              import com.uber.fievel.testing.base.FievelTestBase;
              import org.junit.Test;

              public class Junit4Test extends MigratableBaseTestClass {
                @Test
                public void test() {
                  System.out.println("Hello, world!");
                }
              }
              """,
            """
              import com.uber.fievel.testing.base.FievelTestBase;
              import org.junit.Test;

              /*~~>*/public class Junit4Test extends MigratableBaseTestClass {
                @Test
                public void test() {
                  System.out.println("Hello, world!");
                }
              }
              """
          ));
    }

    @Test
    void hasSupportedRule() {
        rewriteRun(
          // language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.Test;
              import org.junit.rules.TemporaryFolder;

              public class Junit4Test {
                @Rule TemporaryFolder temporaryFolder = new TemporaryFolder();
                @Test
                public void test() {
                  System.out.println("Hello, world!");
                }
              }
              """,
            """
              import org.junit.Rule;
              import org.junit.Test;
              import org.junit.rules.TemporaryFolder;

              /*~~>*/public class Junit4Test {
                @Rule TemporaryFolder temporaryFolder = new TemporaryFolder();
                @Test
                public void test() {
                  System.out.println("Hello, world!");
                }
              }
              """
          ));
    }

    @Test
    void hasSupportedRunner() {
        rewriteRun(
          // language=java
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.junit.runners.Parameterized;

              @RunWith(Parameterized.class)
              public class Junit4Test {
                @Test
                public void test() {
                  System.out.println("Hello, world!");
                }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.junit.runners.Parameterized;

              /*~~>*/@RunWith(Parameterized.class)
              public class Junit4Test {
                @Test
                public void test() {
                  System.out.println("Hello, world!");
                }
              }
              """
          ));
    }

    @Test
    void extendsNonMigratableBaseTestClass() {
        rewriteRun(
          // language=java
          java(
            """
              import org.junit.Test;

              public class Junit4Test extends NonMigratableBaseTestClass {
                @Test
                public void test() {
                  System.out.println("Hello, world!");
                }
              }
              """
          ));
    }

    @Test
    void abstractTestBaseClass() {
        rewriteRun(
          // language=java
          java(
            """
              import org.junit.Before;

              public abstract class AbstractTest {
                @Before
                public void setup() {
                  System.out.println("Hello, world!");
                }
              }
              """,
            """
              import org.junit.Before;

              /*~~>*/public abstract class AbstractTest {
                @Before
                public void setup() {
                  System.out.println("Hello, world!");
                }
              }
              """
          ));
    }

    @Test
    void unsupportedRunner() {
        rewriteRun(
          // language=java
          java(
            """
              import org.junit.experimental.theories.Theories;
              import org.junit.runner.RunWith;
              import org.junit.Test;

              @RunWith(Theories.class)
              public class Junit4Test {
                @Test
                public void test() {
                  System.out.println("Hello, world!");
                }
              }
              """
          ));
    }

    @Test
    void unsupportedRule() {
        rewriteRun(
          // language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.rules.ErrorCollector;
              import org.junit.Test;

              public class Junit4Test {
                @Rule public ErrorCollector rule = new ErrorCollector();
                @Test
                public void test() {
                  System.out.println("Hello, world!");
                }
              }
              """
          ));
    }

    @Test
    void abstractTestAndSupportedRunnerTest() {
        // language=java
        rewriteRun(
          java(
            """
              import org.junit.Before;

              public abstract class AbstractTest {
                @Before
                public void setup() {
                  System.out.println("Setup method");
                }
              }
              """,
            """
              import org.junit.Before;

              /*~~>*/public abstract class AbstractTest {
                @Before
                public void setup() {
                  System.out.println("Setup method");
                }
              }
              """
          ),
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.junit.runners.Parameterized;

              @RunWith(Parameterized.class)
              public class SupportedRunnerTest extends AbstractTest {
                @Test
                public void test() {
                  System.out.println("Test method");
                }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.junit.runners.Parameterized;

              /*~~>*/@RunWith(Parameterized.class)
              public class SupportedRunnerTest extends AbstractTest {
                @Test
                public void test() {
                  System.out.println("Test method");
                }
              }
              """
          ));
    }

    @Test
    void abstractTestAndUnsupportedRuleTest() {
        // language=java
        rewriteRun(
          java(
            // no change since extended by an unmigratable class
            """
              import org.junit.Before;

              public abstract class AbstractTest {
                @Before
                public void setup() {
                  System.out.println("Setup method");
                }
              }
              """
          ),
          java(
            """
              import org.junit.Rule;
              import org.junit.rules.ErrorCollector;
              import org.junit.Test;

              public class UnsupportedRuleTest extends AbstractTest {
                @Rule public ErrorCollector rule = new ErrorCollector();
                @Test
                public void test() {
                  System.out.println("Test method");
                }
              }
              """
          ));
    }

    @Test
    void abstractTestSupportedRuleTestAndUnsupportedRunnerTest() {
        // language=java
        rewriteRun(
          java(
            // no change since one of the test class (UnSupportedRunnerTest) in group can't be migrated.
            """
              import org.junit.Before;

              public abstract class AbstractTest {
                @Before
                public void setup() {
                  System.out.println("Setup method");
                }
              }
              """
          ),
          java(
            """
              import org.junit.Rule;
              import org.junit.rules.TemporaryFolder;
              import org.junit.Test;

              public class SupportedRuleTest extends AbstractTest {
                @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
                @Test
                public void test() {
                  System.out.println("Test method");
                }
              }
              """
          ),
          java(
            """
              import org.junit.experimental.theories.Theories;
              import org.junit.runner.RunWith;
              import org.junit.Test;

              @RunWith(Theories.class)
              public class UnSupportedRunnerTest extends AbstractTest {
                @Test
                public void test() {
                  System.out.println("Test method");
                }
              }
              """
          ));
    }

    @Test
    void twoClassesWithSupportedAndUnsupportedRules() {
        // language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.Test;
              import org.junit.rules.TemporaryFolder;

              public class SupportedRulesClass {
                @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
                @Test
                public void test() {
                  System.out.println("Test with supported rule");
                }
              }
              """,
            """
              import org.junit.Rule;
              import org.junit.Test;
              import org.junit.rules.TemporaryFolder;

              /*~~>*/public class SupportedRulesClass {
                @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
                @Test
                public void test() {
                  System.out.println("Test with supported rule");
                }
              }
              """
          ),
          java(
            """
              import org.junit.Rule;
              import org.junit.Test;
              import org.junit.rules.ErrorCollector;

              public class UnsupportedRulesClass {
                @Rule public ErrorCollector errorCollector = new ErrorCollector();
                @Test
                public void test() {
                  System.out.println("Test with unsupported rule");
                }
              }
              """
          ));
    }

    @Test
    void supportedRunnerSubclassesTest() {
        rewriteRun(
          // language=java
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.junit.MockitoJUnitRunner;
              @RunWith(MockitoJUnitRunner.StrictStubs.class)
              public class SupportedRunnerSubclassTest {
                @Test
                public void test() {
                  System.out.println("Test with another supported runner");
                }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.junit.MockitoJUnitRunner;
              /*~~>*/@RunWith(MockitoJUnitRunner.StrictStubs.class)
              public class SupportedRunnerSubclassTest {
                @Test
                public void test() {
                  System.out.println("Test with another supported runner");
                }
              }
              """
          ));
    }

    @Test
    void unsupportedParametersAnnotationWithClassTypeSourceAttribute() {
        rewriteRun(
          // language=java
          java(
            // no change since the @Parameters annotation has a class-type for its source attribute
            """
              import org.junit.Rule;
              import org.junit.Test;
              import org.junit.rules.TemporaryFolder;
              import junitparams.Parameters;

              public class Junit4Test{
                @Rule TemporaryFolder temporaryFolder = new TemporaryFolder();
                @Test
                @Parameters(source = String.class)
                public void test() {
                  System.out.println("Hello, world!");
                }
              }
              """
          ));
    }

    @Test
    void supportedRuleTypes() {
        rewriteRun(
          // language=java
          java(
            """
            import org.junit.Rule;
            import io.grpc.testing.GrpcCleanupRule;

            public class ExternalResourceRule {
                @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();
            }
            """,
            """
            import org.junit.Rule;
            import io.grpc.testing.GrpcCleanupRule;

            /*~~>*/public class ExternalResourceRule {
                @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();
            }
            """
          ));
    }
}
