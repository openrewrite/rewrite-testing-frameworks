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
package org.openrewrite.java.testing.mockito;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.testing.junit5.MockitoJUnitToMockitoExtension;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MockitoJUnitToMockitoExtensionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "mockito-core-3.12", "mockito-junit-jupiter-3.12", "junit-4.13", "hamcrest-2.2", "junit-jupiter-api-5.9"))
          .recipe(new MockitoJUnitToMockitoExtension());
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/86")
    @Test
    void leavesOtherRulesAlone() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.rules.TemporaryFolder;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoRule;
              import org.mockito.junit.MockitoJUnit;
              
              class MyTest {
              
                  @Rule
                  TemporaryFolder tempDir = new TemporaryFolder();
              
                  @Rule
                  MockitoRule mockitoRule = MockitoJUnit.rule();
              }
              """,
            """
              import org.junit.Rule;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.junit.rules.TemporaryFolder;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
              
              @ExtendWith(MockitoExtension.class)
              class MyTest {
              
                  @Rule
                  TemporaryFolder tempDir = new TemporaryFolder();
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/86")
    @Test
    void leavesOtherAnnotationsAlone() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.FixMethodOrder;
              import org.junit.Rule;
              import org.junit.runners.MethodSorters;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoRule;
              import org.mockito.junit.MockitoJUnit;
              
              @FixMethodOrder(MethodSorters.NAME_ASCENDING)
              class MyTest {
              
                  @Rule
                  MockitoRule mockitoRule = MockitoJUnit.rule();
              }
              """,
            """
              import org.junit.FixMethodOrder;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.junit.runners.MethodSorters;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
              
              @ExtendWith(MockitoExtension.class)
              @FixMethodOrder(MethodSorters.NAME_ASCENDING)
              class MyTest {
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/86")
    @Test
    void refactorMockitoRule() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              
              import org.junit.Rule;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnit;
              import org.mockito.junit.MockitoRule;
              import org.mockito.quality.Strictness;
              
              class MyTest {
              
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
            """
              import java.util.List;
              
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.mockito.junit.jupiter.MockitoSettings;
              import org.mockito.quality.Strictness;
              
              @ExtendWith(MockitoExtension.class)
              @MockitoSettings(strictness = Strictness.LENIENT)
              class MyTest {
              
                  @Mock
                  private List<Integer> list;
              
                  public void exampleTest() {
                      list.add(100);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/86")
    @Test
    void refactorMockitoTestRule() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              
              import org.junit.Rule;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnit;
              import org.mockito.junit.MockitoTestRule;
              import org.mockito.quality.Strictness;
              
              class MyTest {
              
                  @Rule
                  MockitoTestRule mockitoTestRule = MockitoJUnit.rule();
              
                  @Mock
                  private List<Integer> list;
              
                  public void exampleTest() {
                      mockitoTestRule.strictness(Strictness.LENIENT);
                      list.add(100);
                  }
              }
              """,
            """
              import java.util.List;
              
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.mockito.junit.jupiter.MockitoSettings;
              import org.mockito.quality.Strictness;
              
              @ExtendWith(MockitoExtension.class)
              @MockitoSettings(strictness = Strictness.LENIENT)
              class MyTest {
              
                  @Mock
                  private List<Integer> list;
              
                  public void exampleTest() {
                      list.add(100);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/86")
    @Test
    void onlyRefactorMockitoRule() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.Test;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnit;
              import org.mockito.junit.MockitoTestRule;
              import org.mockito.junit.VerificationCollector;
              import org.mockito.quality.Strictness;
              
              import java.util.List;
              
              import static org.mockito.Mockito.verify;
              
              class MyTest {
              
                  @Rule
                  VerificationCollector verificationCollectorRule = MockitoJUnit.collector();
              
                  @Rule
                  MockitoTestRule mockitoTestRule = MockitoJUnit.rule();
              
                  @Mock
                  private List<Integer> list;
              
                  @Test
                  public void exampleTest() {
                      verify(list).add(100);
                      verificationCollectorRule.collectAndReport();
                  }
              }
              """,
            """
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
              class MyTest {
              
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
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/86")
    @Test
    void unchangedMockitoCollectorRule() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              
              import org.junit.Rule;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnit;
              import org.mockito.junit.VerificationCollector;
              
              class MyTest {
              
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
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/86")
    @Test
    void unchangedMockitoCollectorDeclaredInMethod() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              
              import org.mockito.Mock;
              import org.mockito.exceptions.base.MockitoAssertionError;
              import org.mockito.junit.MockitoJUnit;
              import org.mockito.junit.VerificationCollector;
              
              import static org.junit.Assert.assertTrue;
              import static org.mockito.Mockito.mock;
              import static org.mockito.Mockito.verify;
              
              class MyTest {
              
                  public void unsupported() {
                      VerificationCollector collector = MockitoJUnit.collector().assertLazily();
              
                      List<Object> mockList = mock(List.class);
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
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/86")
    @Test
    void leaveMockitoJUnitRunnerAlone() {
        //language=java
        rewriteRun(

          java(
            """
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
              class MyTest {
              
                  @Rule
                  MockitoTestRule mockitoTestRule = MockitoJUnit.rule();
              
                  @Mock
                  private List<Integer> list;
              
                  @Test
                  public void exampleTest() {
                      verify(list).add(100);
                  }
              }
              """,
            """
              import org.junit.runner.RunWith;
              import org.junit.Test;
              import org.mockito.Mock;
              import org.mockito.runners.MockitoJUnitRunner;
              
              import java.util.List;
              
              import static org.mockito.Mockito.verify;
              
              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
              
                  @Mock
                  private List<Integer> list;
              
                  @Test
                  public void exampleTest() {
                      verify(list).add(100);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/86")
    @Test
    void leaveExtendWithAlone() {
        //language=java
        rewriteRun(
          java(
            """
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
              class MyTest {
              
                  @Rule
                  MockitoTestRule mockitoTestRule = MockitoJUnit.rule();
              
                  @Mock
                  private List<Integer> list;
              
                  @Test
                  public void exampleTest() {
                      verify(list).add(100);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.junit.Test;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
              
              import java.util.List;
              
              import static org.mockito.Mockito.verify;
              
              @ExtendWith(MockitoExtension.class)
              class MyTest {
              
                  @Mock
                  private List<Integer> list;
              
                  @Test
                  public void exampleTest() {
                      verify(list).add(100);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/623")
    @Test
    void silentRuleAddMockitoSettings() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.Test;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnit;
              import org.mockito.junit.MockitoRule;
              
              import java.util.List;
              
              import static org.mockito.Mockito.when;
              
              public class MyTest {
              
                  @Rule
                  public MockitoRule rule = MockitoJUnit.rule().silent();
              
                  @Mock
                  private List<String> mockList;
              
                  @Test
                  public void testing() {
                      when(mockList.add("one")).thenReturn(true); // this won't get called
                      System.out.println("Hello world!");
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.mockito.junit.jupiter.MockitoSettings;
              import org.mockito.quality.Strictness;
              
              import java.util.List;
              
              import static org.mockito.Mockito.when;
              
              @ExtendWith(MockitoExtension.class)
              @MockitoSettings(strictness = Strictness.LENIENT)
              public class MyTest {
              
                  @Mock
                  private List<String> mockList;
              
                  @Test
                  public void testing() {
                      when(mockList.add("one")).thenReturn(true); // this won't get called
                      System.out.println("Hello world!");
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/623")
    @Test
    void warnStrictnessRuleAddMockitoSettings() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.Test;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnit;
              import org.mockito.junit.MockitoRule;
              import org.mockito.quality.Strictness;
              
              import java.util.List;
              
              import static org.mockito.Mockito.when;
              
              public class MyTest {
              
                  @Rule
                  public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.WARN);
              
                  @Mock
                  private List<String> mockList;
              
                  @Test
                  public void testing() {
                      when(mockList.add("one")).thenReturn(true); // this won't get called
                      System.out.println("Hello world!");
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.mockito.junit.jupiter.MockitoSettings;
              import org.mockito.quality.Strictness;
              
              import java.util.List;
              
              import static org.mockito.Mockito.when;
              
              @ExtendWith(MockitoExtension.class)
              @MockitoSettings(strictness = Strictness.WARN)
              public class MyTest {
              
                  @Mock
                  private List<String> mockList;
              
                  @Test
                  public void testing() {
                      when(mockList.add("one")).thenReturn(true); // this won't get called
                      System.out.println("Hello world!");
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/623")
    @Test
    void lenientStrictnessRuleAddMockitoSettings() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.Test;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnit;
              import org.mockito.junit.MockitoRule;
              import org.mockito.quality.Strictness;
              
              import java.util.List;
              
              import static org.mockito.Mockito.when;
              
              public class MyTest {
              
                  @Rule
                  public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.LENIENT);
              
                  @Mock
                  private List<String> mockList;
              
                  @Test
                  public void testing() {
                      when(mockList.add("one")).thenReturn(true); // this won't get called
                      System.out.println("Hello world!");
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.mockito.junit.jupiter.MockitoSettings;
              import org.mockito.quality.Strictness;
              
              import java.util.List;
              
              import static org.mockito.Mockito.when;
              
              @ExtendWith(MockitoExtension.class)
              @MockitoSettings(strictness = Strictness.LENIENT)
              public class MyTest {
              
                  @Mock
                  private List<String> mockList;
              
                  @Test
                  public void testing() {
                      when(mockList.add("one")).thenReturn(true); // this won't get called
                      System.out.println("Hello world!");
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/623")
    @Test
    void strictRuleDoNotAddMockitoSettings() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.Test;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnit;
              import org.mockito.junit.MockitoRule;
              import org.mockito.quality.Strictness;
              
              import java.util.List;
              
              import static org.mockito.Mockito.when;
              
              public class MyTest {
              
                  @Rule
                  public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);
              
                  @Mock
                  private List<String> mockList;
              
                  @Test
                  public void testing() {
                      when(mockList.add("one")).thenReturn(true); // this won't get called
                      System.out.println("Hello world!");
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
              
              import java.util.List;
              
              import static org.mockito.Mockito.when;
              
              @ExtendWith(MockitoExtension.class)
              public class MyTest {
              
                  @Mock
                  private List<String> mockList;
              
                  @Test
                  public void testing() {
                      when(mockList.add("one")).thenReturn(true); // this won't get called
                      System.out.println("Hello world!");
                  }
              }
              """
          )
        );
    }
}
