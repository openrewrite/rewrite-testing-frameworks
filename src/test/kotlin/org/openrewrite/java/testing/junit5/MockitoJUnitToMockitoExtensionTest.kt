/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.testing.junit5

import org.junit.jupiter.api.Test
import org.openrewrite.Issue
import org.openrewrite.Parser
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class MockitoJUnitToMockitoExtensionTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .logCompilationWarningsAndErrors(false)
        .classpath("junit", "mockito-core")
        .dependsOn(
            listOf(
                Parser.Input.fromString("package org.junit.jupiter.api.extension;\n" +
                        "public @interface ExtendWith {\n" +
                        "Class[] value();\n" +
                        "}"),
                Parser.Input.fromString("package org.mockito.junit.jupiter;\n" +
                        "public class MockitoExtension {\n" +
                        "}")
            )
        )
        .build()

    override val recipe: Recipe
        get() = MockitoJUnitToMockitoExtension()

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/86")
    @Test
    fun leavesOtherRulesAlone() = assertChanged(
        before = """
            import org.junit.Rule;
            import org.junit.rules.TemporaryFolder;
            import org.mockito.Mock;
            import org.mockito.junit.MockitoRule;
            import org.mockito.junit.MockitoJUnit;

            class A {
            
                @Rule
                TemporaryFolder tempDir = new TemporaryFolder();

                @Rule
                MockitoRule mockitoRule = MockitoJUnit.rule();
            }
        """,
        after = """
            import org.junit.Rule;
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.junit.rules.TemporaryFolder;
            import org.mockito.Mock;
            import org.mockito.junit.jupiter.MockitoExtension;

            @ExtendWith(MockitoExtension.class)
            class A {

                @Rule
                TemporaryFolder tempDir = new TemporaryFolder();
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/86")
    @Test
    fun leavesOtherAnnotationsAlone() = assertChanged(
        before = """
            import org.junit.FixMethodOrder;
            import org.junit.Rule;
            import org.junit.runners.MethodSorters;
            import org.mockito.Mock;
            import org.mockito.junit.MockitoRule;
            import org.mockito.junit.MockitoJUnit;

            @FixMethodOrder(MethodSorters.NAME_ASCENDING)
            class A {
            
                @Rule
                MockitoRule mockitoRule = MockitoJUnit.rule();
            }
        """,
        after = """
            import org.junit.FixMethodOrder;
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.junit.runners.MethodSorters;
            import org.mockito.Mock;
            import org.mockito.junit.jupiter.MockitoExtension;

            @ExtendWith(MockitoExtension.class)
            @FixMethodOrder(MethodSorters.NAME_ASCENDING)
            class A {
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/86")
    @Test
    fun refactorMockitoRule() = assertChanged(
        before = """
            import java.util.List;

            import org.junit.Rule;
            import org.mockito.Mock;
            import org.mockito.junit.MockitoJUnit;
            import org.mockito.junit.MockitoRule;
            import org.mockito.quality.Strictness;

            class A {
            
                @Rule
                MockitoRule mockitoRule = MockitoJUnit.rule();

                @Mock
                private List<Integer> list;
    
                public void exampleTest() {
                    mockitoRule.strictness(Strictness.LENIENT);
                    list.add(100);
                }
            }
        """,
        after = """
            import java.util.List;

            import org.junit.jupiter.api.extension.ExtendWith;
            import org.mockito.Mock;
            import org.mockito.junit.jupiter.MockitoExtension;

            @ExtendWith(MockitoExtension.class)
            class A {

                @Mock
                private List<Integer> list;

                public void exampleTest() {
                    list.add(100);
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/86")
    @Test
    fun refactorMockitoTestRule() = assertChanged(
        before = """
            import java.util.List;

            import org.junit.Rule;
            import org.mockito.Mock;
            import org.mockito.junit.MockitoJUnit;
            import org.mockito.junit.MockitoTestRule;
            import org.mockito.quality.Strictness;

            class A {
            
                @Rule
                MockitoTestRule mockitoTestRule = MockitoJUnit.testRule();

                @Mock
                private List<Integer> list;
    
                public void exampleTest() {
                    mockitoTestRule.strictness(Strictness.LENIENT);
                    list.add(100);
                }
            }
        """,
        after = """
            import java.util.List;

            import org.junit.jupiter.api.extension.ExtendWith;
            import org.mockito.Mock;
            import org.mockito.junit.jupiter.MockitoExtension;

            @ExtendWith(MockitoExtension.class)
            class A {

                @Mock
                private List<Integer> list;

                public void exampleTest() {
                    list.add(100);
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/86")
    @Test
    fun onlyRefactorMockitoRule() = assertChanged(
        before = """
            import org.junit.Rule;
            import org.junit.Test;
            import org.mockito.Mock;
            import org.mockito.junit.MockitoJUnit;
            import org.mockito.junit.MockitoTestRule;
            import org.mockito.junit.VerificationCollector;
            import org.mockito.quality.Strictness;

            import java.util.List;

            import static org.mockito.Mockito.verify;

            class A {
            
                @Rule
                VerificationCollector verificationCollectorRule = MockitoJUnit.collector();

                @Rule
                MockitoTestRule mockitoTestRule = MockitoJUnit.testRule();

                @Mock
                private List<Integer> list;
    
                @Test
                public void exampleTest() {
                    verify(list).add(100);
                    verificationCollectorRule.collectAndReport();
                }
            }
        """,
        after = """
            import org.junit.Rule;
            import org.junit.Test;
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.mockito.Mock;
            import org.mockito.junit.MockitoJUnit;
            import org.mockito.junit.VerificationCollector;
            import org.mockito.junit.jupiter.MockitoExtension;

            import java.util.List;

            import static org.mockito.Mockito.verify;

            @ExtendWith(MockitoExtension.class)
            class A {
            
                @Rule
                VerificationCollector verificationCollectorRule = MockitoJUnit.collector();

                @Mock
                private List<Integer> list;
    
                @Test
                public void exampleTest() {
                    verify(list).add(100);
                    verificationCollectorRule.collectAndReport();
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/86")
    @Test
    fun unchangedMockitoCollectorRule() = assertUnchanged(
        before = """
            import java.util.List;

            import org.junit.Rule;
            import org.mockito.Mock;
            import org.mockito.junit.MockitoJUnit;
            import org.mockito.junit.VerificationCollector;

            class A {
            
                @Rule
                VerificationCollector verificationCollectorRule = MockitoJUnit.collector();

                @Mock
                private List<Integer> list;
    
                public void exampleTest() {
                    list.add(100);
                    verificationCollectorRule.collectAndReport();
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/86")
    @Test
    fun unchangedMockitoCollectorDeclaredInMethod() = assertUnchanged(
        before = """

            import java.util.List;

            import org.mockito.Mock;
            import org.mockito.exceptions.base.MockitoAssertionError;
            import org.mockito.junit.MockitoJUnit;
            import org.mockito.junit.VerificationCollector;

            import static org.junit.Assert.assertTrue;
            import static org.mockito.Mockito.mock;
            import static org.mockito.Mockito.verify;

            class A {
            
                public void unsupported() {
                    VerificationCollector collector = MockitoJUnit.collector().assertLazily();

                    List mockList = mock(List.class);
                    verify(mockList).add("one");
                    verify(mockList).clear();

                    try {
                        collector.collectAndReport();
                    } catch (MockitoAssertionError error) {
                        assertTrue(error.getMessage()
                            .contains("1. Wanted but not invoked:"));
                        assertTrue(error.getMessage()
                            .contains("2. Wanted but not invoked:"));
                    }
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/86")
    @Test
    fun leaveMockitoJUnitRunnerAlone() = assertChanged(
        before = """
            import org.junit.Rule;
            import org.junit.runner.RunWith;
            import org.junit.Test;
            import org.mockito.Mock;
            import org.mockito.junit.MockitoJUnit;
            import org.mockito.junit.MockitoTestRule;
            import org.mockito.runners.MockitoJUnitRunner;

            import java.util.List;

            import static org.mockito.Mockito.verify;

            @RunWith(MockitoJUnitRunner.class)
            class A {
            
                @Rule
                MockitoTestRule mockitoTestRule = MockitoJUnit.testRule();

                @Mock
                private List<Integer> list;
    
                @Test
                public void exampleTest() {
                    verify(list).add(100);
                }
            }
        """,
        after = """
            import org.junit.runner.RunWith;
            import org.junit.Test;
            import org.mockito.Mock;
            import org.mockito.runners.MockitoJUnitRunner;

            import java.util.List;

            import static org.mockito.Mockito.verify;

            @RunWith(MockitoJUnitRunner.class)
            class A {
            
                @Mock
                private List<Integer> list;
    
                @Test
                public void exampleTest() {
                    verify(list).add(100);
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/86")
    @Test
    fun leaveExtendWithAlone() = assertChanged(
        before = """
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.junit.Rule;
            import org.junit.Test;
            import org.mockito.Mock;
            import org.mockito.junit.jupiter.MockitoExtension;
            import org.mockito.junit.MockitoJUnit;
            import org.mockito.junit.MockitoTestRule;

            import java.util.List;

            import static org.mockito.Mockito.verify;

            @ExtendWith(MockitoExtension.class)
            class A {
            
                @Rule
                MockitoTestRule mockitoTestRule = MockitoJUnit.testRule();

                @Mock
                private List<Integer> list;
    
                @Test
                public void exampleTest() {
                    verify(list).add(100);
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.junit.Test;
            import org.mockito.Mock;
            import org.mockito.junit.jupiter.MockitoExtension;
            
            import java.util.List;

            import static org.mockito.Mockito.verify;

            @ExtendWith(MockitoExtension.class)
            class A {
            
                @Mock
                private List<Integer> list;
    
                @Test
                public void exampleTest() {
                    verify(list).add(100);
                }
            }
        """
    )
}
