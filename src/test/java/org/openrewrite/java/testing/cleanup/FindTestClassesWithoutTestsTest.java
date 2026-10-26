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
package org.openrewrite.java.testing.cleanup;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.groovy.GroovyParser;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.groovy.Assertions.groovy;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.srcMainJava;
import static org.openrewrite.java.Assertions.srcTestJava;
import static org.openrewrite.kotlin.Assertions.kotlin;

class FindTestClassesWithoutTestsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-4",
              "junit-jupiter-api-5",
              "junit-jupiter-params-5",
              "testng-7")
            //language=java
            .dependsOn(
              """
                package com.tngtech.archunit.junit;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                @Retention(RetentionPolicy.RUNTIME)
                public @interface ArchTest {
                }
                """,
              """
                package io.cucumber.junit;
                public class Cucumber extends org.junit.runners.ParentRunner<Object> {
                    protected Cucumber(Class<?> testClass) throws Exception {
                        super(testClass);
                    }
                }
                """,
              """
                package cucumber.api.junit;
                public class Cucumber extends org.junit.runners.ParentRunner<Object> {
                    protected Cucumber(Class<?> testClass) throws Exception {
                        super(testClass);
                    }
                }
                """,
              """
                package org.junit.platform.suite.api;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                @Retention(RetentionPolicy.RUNTIME)
                public @interface Suite {
                }
                """,
              """
                package org.junit.platform.suite.api;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                @Retention(RetentionPolicy.RUNTIME)
                public @interface SelectPackages {
                    String[] value();
                }
                """,
              """
                package org.junit.platform.suite.api;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                @Retention(RetentionPolicy.RUNTIME)
                public @interface SelectClasses {
                    Class<?>[] value();
                }
                """,
              """
                package com.googlecode.zohhak.api;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                @Retention(RetentionPolicy.RUNTIME)
                public @interface TestWith {
                    String[] value();
                }
                """,
              """
                package com.googlecode.zohhak.api.runners;
                public class ZohhakRunner extends org.junit.runners.BlockJUnit4ClassRunner {
                    public ZohhakRunner(Class<?> testClass) throws Exception {
                        super(testClass);
                    }
                }
                """,
              """
                package au.com.dius.pact.provider.junit;
                public class PactRunner extends org.junit.runners.ParentRunner<Object> {
                    public PactRunner(Class<?> testClass) throws Exception {
                        super(testClass);
                    }
                }
                """,
              """
                package au.com.dius.pact.provider.junit;
                public class RestPactRunner extends PactRunner {
                    public RestPactRunner(Class<?> testClass) throws Exception {
                        super(testClass);
                    }
                }
                """,
              """
                package com.intuit.karate.junit4;
                public class Karate extends org.junit.runners.ParentRunner<Object> {
                    public Karate(Class<?> testClass) throws Exception {
                        super(testClass);
                    }
                }
                """,
              """
                package io.cucumber.junit.platform.engine;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                @Retention(RetentionPolicy.RUNTIME)
                public @interface Cucumber {
                }
                """
            ))
          .parser(KotlinParser.builder()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "junit-jupiter-api-5")
            //language=kotlin
            .dependsOn(
              """
                package kotlin.test

                typealias Test = org.junit.jupiter.api.Test
                """,
              """
                package io.kotest.core.spec

                abstract class Spec
                """,
              """
                package io.kotest.core.spec.style

                import io.kotest.core.spec.Spec

                abstract class StringSpec(body: StringSpec.() -> Unit = {}) : Spec() {
                    operator fun String.invoke(test: () -> Unit) {}
                }
                """,
              """
                package org.spekframework.spek2

                abstract class Spek(val root: () -> Unit)
                """
            ))
          .parser(GroovyParser.builder()
            .classpathFromResource(new InMemoryExecutionContext(), "junit-jupiter-api-5"))
          .recipe(new FindTestClassesWithoutTests());
    }

    @DocumentExample
    @Test
    void classWithoutTests() {
        rewriteRun(
          srcTestJava(
            //language=java
            java(
              """
                import org.junit.jupiter.api.BeforeEach;

                class CalculatorTest {
                    @BeforeEach
                    void setUp() {
                    }

                    void add() {
                    }
                }
                """,
              """
                import org.junit.jupiter.api.BeforeEach;

                class /*~~>*/CalculatorTest {
                    @BeforeEach
                    void setUp() {
                    }

                    void add() {
                    }
                }
                """
            )
          )
        );
    }

    @Nested
    class Flagged {

        @Test
        void classNamesAndJunit3TestCase() {
            rewriteRun(
              srcTestJava(
                //language=java
                java("class CalculatorTests {}", "class /*~~>*/CalculatorTests {}"),
                //language=java
                java("class CalculatorTestCase {}", "class /*~~>*/CalculatorTestCase {}"),
                //language=java
                java(
                  """
                    import junit.framework.TestCase;

                    public class CalculatorChecks extends TestCase {
                        public void checkAdd() {
                        }

                        void testPackagePrivate() {
                        }

                        public void testWithArgument(int i) {
                        }

                        public static void testStatic() {
                        }

                        public int testNonVoid() {
                            return 0;
                        }

                        public static Object suite() {
                            return null;
                        }
                    }
                    """,
                  """
                    import junit.framework.TestCase;

                    public class /*~~>*/CalculatorChecks extends TestCase {
                        public void checkAdd() {
                        }

                        void testPackagePrivate() {
                        }

                        public void testWithArgument(int i) {
                        }

                        public static void testStatic() {
                        }

                        public int testNonVoid() {
                            return 0;
                        }

                        public static Object suite() {
                            return null;
                        }
                    }
                    """
                )
              )
            );
        }

        @Test
        void testsOnlyInNonNestedOrCyclicInnerClasses() {
            rewriteRun(
              srcTestJava(
                //language=java
                java(
                  """
                    import org.junit.jupiter.api.Test;

                    class CalculatorTest {
                        class Inner {
                            @Test
                            void add() {
                            }
                        }
                    }
                    """,
                  """
                    import org.junit.jupiter.api.Test;

                    class /*~~>*/CalculatorTest {
                        class Inner {
                            @Test
                            void add() {
                            }
                        }
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import org.junit.jupiter.api.Nested;

                    class AddTest {
                        @Nested
                        class Positive extends AddTest {
                        }
                    }
                    """,
                  """
                    import org.junit.jupiter.api.Nested;

                    class /*~~>*/AddTest {
                        @Nested
                        class Positive extends AddTest {
                        }
                    }
                    """
                )
              )
            );
        }

        @Test
        void withoutTestNgTests() {
            rewriteRun(
              srcTestJava(
                //language=java
                java(
                  """
                    import org.testng.annotations.Test;

                    @Test
                    class PackagePrivateTest {
                        void add() {
                        }
                    }
                    """,
                  """
                    import org.testng.annotations.Test;

                    @Test
                    class /*~~>*/PackagePrivateTest {
                        void add() {
                        }
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import org.testng.annotations.AfterMethod;
                    import org.testng.annotations.BeforeMethod;
                    import org.testng.annotations.DataProvider;
                    import org.testng.annotations.Test;

                    @Test
                    public class ConfigurationTest {
                        @BeforeMethod
                        public void setUp() {
                        }

                        @AfterMethod
                        public void tearDown() {
                        }

                        @DataProvider
                        public Object[][] values() {
                            return new Object[0][];
                        }

                        public static void helper() {
                        }
                    }
                    """,
                  """
                    import org.testng.annotations.AfterMethod;
                    import org.testng.annotations.BeforeMethod;
                    import org.testng.annotations.DataProvider;
                    import org.testng.annotations.Test;

                    @Test
                    public class /*~~>*/ConfigurationTest {
                        @BeforeMethod
                        public void setUp() {
                        }

                        @AfterMethod
                        public void tearDown() {
                        }

                        @DataProvider
                        public Object[][] values() {
                            return new Object[0][];
                        }

                        public static void helper() {
                        }
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import org.testng.annotations.Factory;

                    public class FactoryConstructorTest {
                        @Factory
                        public FactoryConstructorTest() {
                        }
                    }
                    """,
                  """
                    import org.testng.annotations.Factory;

                    public class /*~~>*/FactoryConstructorTest {
                        @Factory
                        public FactoryConstructorTest() {
                        }
                    }
                    """
                )
              )
            );
        }

        @Test
        void junit4RunnersThatNeedTests() {
            rewriteRun(
              srcTestJava(
                //language=java
                java(
                  """
                    import org.junit.runner.RunWith;
                    import org.junit.runners.Parameterized;

                    @RunWith(Parameterized.class)
                    public class ParameterizedCalculatorTest {
                    }
                    """,
                  """
                    import org.junit.runner.RunWith;
                    import org.junit.runners.Parameterized;

                    @RunWith(Parameterized.class)
                    public class /*~~>*/ParameterizedCalculatorTest {
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import org.junit.runner.RunWith;
                    import org.junit.runners.JUnit4;

                    @RunWith(JUnit4.class)
                    public class CalculatorTest {
                    }
                    """,
                  """
                    import org.junit.runner.RunWith;
                    import org.junit.runners.JUnit4;

                    @RunWith(JUnit4.class)
                    public class /*~~>*/CalculatorTest {
                    }
                    """
                )
              )
            );
        }

        @Test
        void kotlinAndGroovy() {
            rewriteRun(
              srcTestJava(
                //language=kotlin
                kotlin("class KotlinCalculatorTest", "class /*~~>*/KotlinCalculatorTest"),
                //language=kotlin
                kotlin(
                  """
                    import org.junit.jupiter.api.BeforeEach

                    class SetUpOnlyTest {
                        @BeforeEach
                        fun setUp() {
                        }
                    }
                    """,
                  """
                    import org.junit.jupiter.api.BeforeEach

                    class /*~~>*/SetUpOnlyTest {
                        @BeforeEach
                        fun setUp() {
                        }
                    }
                    """
                ),
                //language=kotlin
                kotlin(
                  "class SerializableTest : java.io.Serializable",
                  "class /*~~>*/SerializableTest : java.io.Serializable"
                ),
                //language=groovy
                groovy("class GroovyCalculatorTests {}", "class /*~~>*/GroovyCalculatorTests {}")
              )
            );
        }

        @Test
        void unresolvedAnnotationsThatAreNotTestAnnotations() {
            rewriteRun(
              spec -> spec.typeValidationOptions(TypeValidation.none()),
              srcTestJava(
                //language=java
                java(
                  """
                    import com.example.Disabled;
                    import com.example.Inject;
                    import com.example.Mock;

                    class MockedTest {
                        @Mock
                        Object service;

                        @Inject
                        void setUp() {
                        }

                        @Disabled
                        void add() {
                        }
                    }
                    """,
                  """
                    import com.example.Disabled;
                    import com.example.Inject;
                    import com.example.Mock;

                    class /*~~>*/MockedTest {
                        @Mock
                        Object service;

                        @Inject
                        void setUp() {
                        }

                        @Disabled
                        void add() {
                        }
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import com.example.Tag;
                    import org.junit.jupiter.api.Test;

                    class TaggedTest {
                        @Tag
                        class Add {
                            @Test
                            void positive() {
                            }
                        }
                    }
                    """,
                  """
                    import com.example.Tag;
                    import org.junit.jupiter.api.Test;

                    class /*~~>*/TaggedTest {
                        @Tag
                        class Add {
                            @Test
                            void positive() {
                            }
                        }
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import com.example.CustomSuite;

                    @CustomSuite
                    class ClassAnnotationTests {
                    }
                    """,
                  """
                    import com.example.CustomSuite;

                    @CustomSuite
                    class /*~~>*/ClassAnnotationTests {
                    }
                    """
                )
              )
            );
        }

        @Test
        void unresolvedAnnotationOutsideClassType() {
            rewriteRun(
              spec -> spec.typeValidationOptions(TypeValidation.none()),
              srcTestJava(
                //language=java
                java(
                  """
                    import com.example.Fixture;

                    class CalculatorTest {
                        void helper() {
                            @Fixture int x = 1;
                        }

                        void check(@Fixture String s) {
                        }

                        static {
                            @Fixture int y = 2;
                        }

                        Runnable task = new Runnable() {
                            @Fixture
                            public void run() {
                            }
                        };

                        static class Data {
                            @Fixture String name;
                        }
                    }
                    """,
                  """
                    import com.example.Fixture;

                    class /*~~>*/CalculatorTest {
                        void helper() {
                            @Fixture int x = 1;
                        }

                        void check(@Fixture String s) {
                        }

                        static {
                            @Fixture int y = 2;
                        }

                        Runnable task = new Runnable() {
                            @Fixture
                            public void run() {
                            }
                        };

                        static class Data {
                            @Fixture String name;
                        }
                    }
                    """
                )
              )
            );
        }
    }

    @Nested
    class ContainsTests {

        @Test
        void junit3And4() {
            rewriteRun(
              srcTestJava(
                //language=java
                java(
                  """
                    import junit.framework.TestCase;

                    public class Junit3Test extends TestCase {
                        public void testAdd() {
                        }
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import org.junit.Test;

                    public class Junit4Test {
                        @Test
                        public void add() {
                        }
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import org.junit.experimental.theories.Theories;
                    import org.junit.experimental.theories.Theory;
                    import org.junit.runner.RunWith;

                    @RunWith(Theories.class)
                    public class TheoryTest {
                        @Theory
                        public void add(int i) {
                        }
                    }
                    """
                )
              )
            );
        }

        @Test
        void junit5() {
            rewriteRun(
              srcTestJava(
                //language=java
                java(
                  """
                    import org.junit.jupiter.api.Test;

                    class ATest {
                        @Test
                        void add() {
                        }
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import org.junit.jupiter.api.RepeatedTest;

                    class BTest {
                        @RepeatedTest(2)
                        void add() {
                        }
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import org.junit.jupiter.params.ParameterizedTest;
                    import org.junit.jupiter.params.provider.ValueSource;

                    class CTest {
                        @ParameterizedTest
                        @ValueSource(ints = 1)
                        void add(int i) {
                        }
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import org.junit.jupiter.api.DynamicTest;
                    import org.junit.jupiter.api.TestFactory;

                    import java.util.stream.Stream;

                    class DTest {
                        @TestFactory
                        Stream<DynamicTest> add() {
                            return Stream.empty();
                        }
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import org.junit.jupiter.api.TestTemplate;

                    class ETest {
                        @TestTemplate
                        void add() {
                        }
                    }
                    """
                )
              )
            );
        }

        @Test
        void composedTestAnnotation() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import java.lang.annotation.Retention;
                  import java.lang.annotation.RetentionPolicy;

                  @Test
                  @Retention(RetentionPolicy.RUNTIME)
                  @interface UnitTest {
                  }
                  """
              ),
              //language=java
              java(
                """
                  import java.lang.annotation.Retention;
                  import java.lang.annotation.RetentionPolicy;

                  @UnitTest
                  @Retention(RetentionPolicy.RUNTIME)
                  @interface FastTest {
                  }
                  """
              ),
              srcTestJava(
                //language=java
                java(
                  """
                    class CalculatorTest {
                        @UnitTest
                        void add() {
                        }
                    }
                    """
                ),
                //language=java
                java(
                  """
                    class FastCalculatorTest {
                        @FastTest
                        void add() {
                        }
                    }
                    """
                )
              )
            );
        }

        @Test
        void junitPlatformTestable() {
            rewriteRun(
              //language=java
              java(
                """
                  package org.junit.platform.commons.annotation;
                  import java.lang.annotation.Retention;
                  import java.lang.annotation.RetentionPolicy;
                  @Retention(RetentionPolicy.RUNTIME)
                  public @interface Testable {
                  }
                  """
              ),
              //language=java
              java(
                """
                  package net.jqwik.api;
                  import java.lang.annotation.Retention;
                  import java.lang.annotation.RetentionPolicy;
                  import org.junit.platform.commons.annotation.Testable;
                  @Testable
                  @Retention(RetentionPolicy.RUNTIME)
                  public @interface Property {
                  }
                  """
              ),
              srcTestJava(
                //language=java
                java(
                  """
                    import net.jqwik.api.Property;

                    class CalculatorTest {
                        @Property
                        boolean add() {
                            return true;
                        }
                    }
                    """
                )
              )
            );
        }

        @Test
        void composedAnnotationWithUnresolvedMetaAnnotation() {
            rewriteRun(
              spec -> spec.typeValidationOptions(TypeValidation.none()),
              //language=java
              java(
                """
                  import com.example.EngineTest;
                  import java.lang.annotation.Retention;
                  import java.lang.annotation.RetentionPolicy;

                  @EngineTest
                  @Retention(RetentionPolicy.RUNTIME)
                  @interface CustomTest {
                  }
                  """
              ),
              srcTestJava(
                //language=java
                java(
                  """
                    class CalculatorTest {
                        @CustomTest
                        void add() {
                        }
                    }
                    """
                )
              )
            );
        }

        @Test
        void withTestNgTests() {
            rewriteRun(
              srcTestJava(
                //language=java
                java(
                  """
                    import org.testng.annotations.Test;

                    public class MethodTest {
                        @Test
                        public void add() {
                        }
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import org.testng.annotations.Test;

                    @Test
                    public class ClassLevelTest {
                        public void add() {
                        }
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import org.testng.annotations.Test;

                    @Test
                    public abstract class AbstractClassLevelTest {
                    }
                    """
                ),
                //language=java
                java(
                  """
                    public class InheritedClassLevelTest extends AbstractClassLevelTest {
                        public void add() {
                        }
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import org.testng.annotations.Parameters;
                    import org.testng.annotations.Test;

                    @Test
                    public class ParametersTest {
                        @Parameters("x")
                        public void add(String x) {
                        }
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import org.testng.annotations.Factory;

                    public class FactoryTest {
                        @Factory
                        public Object[] create() {
                            return new Object[0];
                        }
                    }
                    """
                )
              )
            );
        }

        @Test
        void archUnit() {
            rewriteRun(
              srcTestJava(
                //language=java
                java(
                  """
                    import com.tngtech.archunit.junit.ArchTest;

                    class FieldRuleTest {
                        @ArchTest
                        static final Object rule = null;
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import com.tngtech.archunit.junit.ArchTest;

                    class MethodRuleTest {
                        @ArchTest
                        static void rule(Object classes) {
                        }
                    }
                    """
                )
              )
            );
        }

        @Test
        void zohhak() {
            rewriteRun(
              srcTestJava(
                //language=java
                java(
                  """
                    import com.googlecode.zohhak.api.TestWith;
                    import com.googlecode.zohhak.api.runners.ZohhakRunner;
                    import org.junit.runner.RunWith;

                    @RunWith(ZohhakRunner.class)
                    public class CalculatorTest {
                        @TestWith("1, 2")
                        public void add(int a, int b) {
                        }
                    }
                    """
                )
              )
            );
        }

        @Test
        void kotlinTests() {
            rewriteRun(
              srcTestJava(
                //language=kotlin
                kotlin(
                  """
                    import org.junit.jupiter.api.Test

                    class JupiterTest {
                        @Test
                        fun add() {
                        }
                    }
                    """
                ),
                //language=kotlin
                kotlin(
                  """
                    import kotlin.test.Test

                    class KotlinTestTest {
                        @Test
                        fun add() {
                        }
                    }
                    """
                ),
                //language=kotlin
                kotlin(
                  """
                    import org.junit.jupiter.api.Nested
                    import org.junit.jupiter.api.Test

                    class NestedTest {
                        @Nested
                        inner class Add {
                            @Test
                            fun positive() {
                            }
                        }
                    }
                    """
                )
              )
            );
        }

        @Test
        void nestedClasses() {
            rewriteRun(
              srcTestJava(
                //language=java
                java(
                  """
                    import org.junit.jupiter.api.Nested;
                    import org.junit.jupiter.api.Test;

                    class CalculatorTest {
                        @Nested
                        class Add {
                            @Nested
                            class Negative {
                                @Test
                                void add() {
                                }
                            }
                        }
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import org.junit.jupiter.api.Nested;
                    import org.junit.jupiter.api.Test;

                    class ModifierTest {
                        public @Nested class Add {
                            @Test
                            void positive() {
                            }
                        }
                    }
                    """
                )
              )
            );
        }

        @Test
        void inherited() {
            rewriteRun(
              srcTestJava(
                //language=java
                java(
                  """
                    import org.junit.jupiter.api.Test;

                    abstract class AbstractCalculatorTest {
                        @Test
                        void add() {
                        }
                    }
                    """
                ),
                //language=java
                java("class CalculatorTest extends AbstractCalculatorTest {}"),
                //language=java
                java(
                  """
                    import junit.framework.TestCase;

                    public class BaseChecks extends TestCase {
                        public void testAdd() {
                        }
                    }
                    """
                ),
                //language=java
                java("public class ChecksTest extends BaseChecks {}"),
                //language=java
                java(
                  """
                    import org.junit.jupiter.api.Test;

                    interface CalculatorContract {
                        @Test
                        default void add() {
                        }
                    }
                    """
                ),
                //language=java
                java("class ContractTest implements CalculatorContract {}"),
                //language=java
                java(
                  """
                    import org.junit.jupiter.api.Nested;
                    import org.junit.jupiter.api.Test;

                    abstract class AbstractListTest {
                        @Nested
                        class WhenEmpty {
                            @Test
                            void add() {
                            }
                        }
                    }
                    """
                ),
                //language=java
                java("class ArrayListTest extends AbstractListTest {}")
              )
            );
        }

        @Test
        void nestedClassExtendingLaterScannedSuperclass() {
            rewriteRun(
              srcTestJava(
                //language=java
                java(
                  """
                    import org.junit.jupiter.api.Nested;

                    class CalculatorTest {
                        @Nested
                        class Add extends AbstractAddTest {
                        }
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import org.junit.jupiter.api.Nested;
                    import org.junit.jupiter.api.Test;

                    abstract class AbstractAddTest {
                        @Nested
                        class Positive {
                            @Test
                            void add() {
                            }
                        }
                    }
                    """
                )
              )
            );
        }
    }

    @Nested
    class NotFlagged {

        @Test
        void notTestClasses() {
            rewriteRun(
              srcTestJava(
                //language=java
                java("abstract class AbstractCalculatorTest {}"),
                //language=java
                java("class TestData {}"),
                //language=java
                java("interface CalculatorTest {}"),
                //language=java
                java("enum CalculatorTests { ADD }"),
                //language=java
                java("record CalculatorTestCase(int value) {}")
              )
            );
        }

        @Test
        void mainSources() {
            rewriteRun(
              srcMainJava(
                //language=java
                java("public class CalculatorTest {}")
              )
            );
        }

        @Test
        void kotlinSealedClass() {
            rewriteRun(
              srcTestJava(
                //language=kotlin
                kotlin("sealed class CalculatorTest")
              )
            );
        }

        @Test
        void specifications() {
            rewriteRun(
              //language=groovy
              groovy(
                """
                  package spock.lang

                  abstract class Specification {
                  }
                  """
              ),
              srcTestJava(
                //language=kotlin
                kotlin(
                  """
                    import io.kotest.core.spec.style.StringSpec

                    class KotestTest : StringSpec({
                        "adds" { }
                    })
                    """
                ),
                //language=kotlin
                kotlin(
                  """
                    import org.spekframework.spek2.Spek

                    class SpekTest : Spek({
                    })
                    """
                ),
                //language=groovy
                groovy(
                  """
                    import spock.lang.Specification

                    class SpockTest extends Specification {
                        def "adds"() {
                            expect:
                            1 + 1 == 2
                        }
                    }
                    """
                )
              )
            );
        }

        @Test
        void junit3And4Suites() {
            rewriteRun(
              srcTestJava(
                //language=java
                java(
                  """
                    import org.junit.runner.RunWith;
                    import org.junit.runners.Suite;

                    @RunWith(Suite.class)
                    @Suite.SuiteClasses({})
                    public class AllTests {
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import org.junit.experimental.categories.Categories;
                    import org.junit.runner.RunWith;

                    @RunWith(Categories.class)
                    public class CategoryTests {
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import org.junit.experimental.runners.Enclosed;
                    import org.junit.runner.RunWith;

                    @RunWith(Enclosed.class)
                    public class EnclosedTest {
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import io.cucumber.junit.Cucumber;
                    import org.junit.runner.RunWith;

                    @RunWith(value = Cucumber.class)
                    public class RunCucumberTest {
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import org.junit.runner.RunWith;

                    @RunWith(cucumber.api.junit.Cucumber.class)
                    public class RunLegacyCucumberTest {
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import io.cucumber.junit.Cucumber;
                    import org.junit.runner.RunWith;

                    @RunWith(Cucumber.class)
                    public abstract class AbstractCucumberTest {
                    }
                    """
                ),
                //language=java
                java("public class InheritedCucumberTest extends AbstractCucumberTest {}"),
                //language=java
                java(
                  """
                    import junit.framework.Test;
                    import junit.framework.TestSuite;

                    public class SuiteMethodTests {
                        public static Test suite() {
                            return new TestSuite();
                        }
                    }
                    """
                )
              )
            );
        }

        @Test
        void junit4RunnersThatBuildTestsFromFiles() {
            rewriteRun(
              srcTestJava(
                //language=java
                java(
                  """
                    import au.com.dius.pact.provider.junit.PactRunner;
                    import org.junit.runner.RunWith;

                    @RunWith(PactRunner.class)
                    public class ProviderTest {
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import au.com.dius.pact.provider.junit.RestPactRunner;
                    import org.junit.runner.RunWith;

                    @RunWith(RestPactRunner.class)
                    public class RestProviderTest {
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import com.intuit.karate.junit4.Karate;
                    import org.junit.runner.RunWith;

                    @RunWith(Karate.class)
                    public class KarateTest {
                    }
                    """
                )
              )
            );
        }

        @Test
        void junitPlatformSuites() {
            rewriteRun(
              srcTestJava(
                //language=java
                java(
                  """
                    import org.junit.platform.suite.api.Suite;

                    @Suite
                    class AllTests {
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import org.junit.platform.suite.api.SelectPackages;

                    @SelectPackages("com.example")
                    class PackageTests {
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import org.junit.platform.suite.api.SelectClasses;

                    @SelectClasses(String.class)
                    class ClassTests {
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import org.junit.platform.suite.api.SelectPackages;
                    import org.junit.runner.RunWith;
                    import org.junit.runners.JUnit4;

                    @RunWith(JUnit4.class)
                    @SelectPackages("com.example")
                    class RunWithAndSelectTests {
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import io.cucumber.junit.platform.engine.Cucumber;

                    @Cucumber
                    public class RunCucumberPlatformTest {
                    }
                    """
                )
              )
            );
        }

        @Test
        void unresolvedTypesOnClass() {
            rewriteRun(
              spec -> spec.typeValidationOptions(TypeValidation.none()),
              srcTestJava(
                //language=java
                java(
                  """
                    import com.example.CustomTest;

                    class MethodAnnotationTest {
                        @CustomTest
                        void add() {
                        }
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import com.example.Property;
                    import com.example.TestWith;
                    import com.example.Theory;

                    class TestLikeNamesTest {
                        @Theory
                        void theory() {
                        }

                        @Property
                        void property() {
                        }

                        @TestWith("1")
                        void testWith() {
                        }
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import com.example.AbstractCalculatorTest;

                    class SuperclassTest extends AbstractCalculatorTest {
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import com.example.CalculatorContract;

                    class InterfaceTest implements CalculatorContract {
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import com.example.CustomRunner;
                    import org.junit.runner.RunWith;

                    @RunWith(CustomRunner.class)
                    public class RunnerTest {
                    }
                    """
                )
              )
            );
        }

        @Test
        void unresolvedTypesInSuperclass() {
            rewriteRun(
              spec -> spec.typeValidationOptions(TypeValidation.none()),
              srcTestJava(
                //language=java
                java(
                  """
                    import com.example.CustomTest;

                    public abstract class AbstractAnnotatedTest {
                        @CustomTest
                        void add() {
                        }
                    }
                    """
                ),
                //language=java
                java("public class AnnotatedTest extends AbstractAnnotatedTest {}"),
                //language=java
                java(
                  """
                    import com.example.CalculatorContract;

                    public abstract class AbstractContractTest implements CalculatorContract {
                    }
                    """
                ),
                //language=java
                java("public class ContractTest extends AbstractContractTest {}"),
                //language=java
                java(
                  """
                    import com.example.LibraryTestSupport;

                    public abstract class AbstractLibraryTest extends LibraryTestSupport {
                    }
                    """
                ),
                //language=java
                java("public class LibraryTest extends AbstractLibraryTest {}")
              )
            );
        }

        @Test
        void unresolvedNestedAnnotation() {
            rewriteRun(
              spec -> spec.typeValidationOptions(TypeValidation.none()),
              srcTestJava(
                //language=java
                java(
                  """
                    import com.example.Nested;
                    import org.junit.jupiter.api.Test;

                    class CalculatorTest {
                        @Nested
                        class Add {
                            @Test
                            void positive() {
                            }
                        }
                    }
                    """
                ),
                //language=java
                java(
                  """
                    import org.junit.jupiter.api.Test;

                    class ModifierTest {
                        public @com.example.Nested class Add {
                            @Test
                            void positive() {
                            }
                        }
                    }
                    """
                )
              )
            );
        }

        @Test
        void nestedTestWithLaterScannedComposedAnnotation() {
            rewriteRun(
              spec -> spec.typeValidationOptions(TypeValidation.none()),
              srcTestJava(
                //language=java
                java(
                  """
                    import org.junit.jupiter.api.Nested;

                    class CalculatorTest {
                        @Nested
                        class Add {
                            @IntegrationTest
                            void positive() {
                            }
                        }
                    }
                    """
                )
              ),
              //language=java
              java(
                """
                  import com.example.EngineTest;
                  import java.lang.annotation.Retention;
                  import java.lang.annotation.RetentionPolicy;

                  @EngineTest
                  @Retention(RetentionPolicy.RUNTIME)
                  @interface IntegrationTest {
                  }
                  """
              )
            );
        }
    }
}
