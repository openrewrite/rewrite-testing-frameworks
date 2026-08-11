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
package org.openrewrite.java.testing.mockito;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MockUtilsToStaticTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "mockito-all-1.10"))
          .recipe(new MockUtilsToStatic());
    }

    @DocumentExample
    @Test
    void basicInstanceToStaticSwap() {
        //language=java
        rewriteRun(
          java(
            """
              package mockito.example;

              import org.mockito.internal.util.MockUtil;
              
              public class MockitoMockUtils {
                  public void isMockExample() {
                      new MockUtil().isMock("I am a real String");
                  }
              }
              """,
            """
              package mockito.example;

              import org.mockito.internal.util.MockUtil;
              
              public class MockitoMockUtils {
                  public void isMockExample() {
                      MockUtil.isMock("I am a real String");
                  }
              }
              """
          )
        );
    }

    @Test
    void mockUtilsVariableToStatic() {
        //language=java
        rewriteRun(
          java(
            """
              package mockito.example;

              import org.mockito.internal.util.MockUtil;
              
              public class MockitoMockUtils {
                  public void isMockExample() {
                      MockUtil util = new MockUtil();
                      util.isMock("I am a real String");
                  }
              }
              """,
            """
              package mockito.example;

              import org.mockito.internal.util.MockUtil;

              public class MockitoMockUtils {
                  public void isMockExample() {
                      MockUtil.isMock("I am a real String");
                  }
              }
              """
          )
        );
    }

    @Test
    void mockUtilsFieldToStatic() {
        //language=java
        rewriteRun(
          java(
            """
              package mockito.example;

              import org.mockito.internal.util.MockUtil;
              
              public class MockitoMockUtils {
                  MockUtil util = new MockUtil();
                  public void isMockExample() {
                      util.isMock("I am a real String");
                  }
              }
              """,
            """
              package mockito.example;

              import org.mockito.internal.util.MockUtil;

              public class MockitoMockUtils {
                  public void isMockExample() {
                      MockUtil.isMock("I am a real String");
                  }
              }
              """
          )
        );
    }

    @Test
    void retainLocalVariableUsedAsMethodArgument() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  boolean test(Object value) {
                      MockUtil util = new MockUtil();
                      observe(util);
                      return util.isMock(value);
                  }

                  void observe(Object value) {
                  }
              }
              """,
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  boolean test(Object value) {
                      MockUtil util = new MockUtil();
                      observe(util);
                      return MockUtil.isMock(value);
                  }

                  void observe(Object value) {
                  }
              }
              """
          )
        );
    }

    @Test
    void retainFieldUsedAsReturnValue() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  private MockUtil util = new MockUtil();

                  MockUtil expose() {
                      return util;
                  }

                  boolean test(Object value) {
                      return util.isMock(value);
                  }
              }
              """,
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  private MockUtil util = new MockUtil();

                  MockUtil expose() {
                      return util;
                  }

                  boolean test(Object value) {
                      return MockUtil.isMock(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void retainStaticFieldUsedInComparison() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  private static MockUtil util = new MockUtil();

                  static boolean isShared(MockUtil other) {
                      return other == util;
                  }

                  static boolean test(Object value) {
                      return util.isMock(value);
                  }
              }
              """,
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  private static MockUtil util = new MockUtil();

                  static boolean isShared(MockUtil other) {
                      return other == util;
                  }

                  static boolean test(Object value) {
                      return MockUtil.isMock(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void retainVariableUsedToInitializeAnAlias() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  boolean test(Object value) {
                      MockUtil util = new MockUtil();
                      MockUtil alias = util;
                      return alias.isMock(value);
                  }
              }
              """,
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  boolean test(Object value) {
                      MockUtil util = new MockUtil();
                      MockUtil alias = util;
                      return MockUtil.isMock(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void removeFirstDeclaratorOnlyPreservingSiblingEvaluation() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  boolean test(Object value) {
                      MockUtil util = new MockUtil(), observed = createObserved();
                      observe(observed);
                      return util.isMock(value);
                  }

                  MockUtil createObserved() {
                      return new MockUtil();
                  }

                  void observe(Object value) {
                  }
              }
              """,
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  boolean test(Object value) {
                      MockUtil observed = createObserved();
                      observe(observed);
                      return MockUtil.isMock(value);
                  }

                  MockUtil createObserved() {
                      return new MockUtil();
                  }

                  void observe(Object value) {
                  }
              }
              """
          )
        );
    }

    @Test
    void removeLastDeclaratorOnlyPreservingSiblingEvaluation() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  boolean test(Object value) {
                      MockUtil observed = createObserved(), util = new MockUtil();
                      observe(observed);
                      return util.isMock(value);
                  }

                  MockUtil createObserved() {
                      return new MockUtil();
                  }

                  void observe(Object value) {
                  }
              }
              """,
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  boolean test(Object value) {
                      MockUtil observed = createObserved();
                      observe(observed);
                      return MockUtil.isMock(value);
                  }

                  MockUtil createObserved() {
                      return new MockUtil();
                  }

                  void observe(Object value) {
                  }
              }
              """
          )
        );
    }

    @Test
    void removeFirstDeclaratorSpanningMultipleLines() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  boolean test(Object value) {
                      MockUtil util = new MockUtil(),
                              observed = createObserved();
                      observe(observed);
                      return util.isMock(value);
                  }

                  MockUtil createObserved() {
                      return new MockUtil();
                  }

                  void observe(Object value) {
                  }
              }
              """,
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  boolean test(Object value) {
                      MockUtil observed = createObserved();
                      observe(observed);
                      return MockUtil.isMock(value);
                  }

                  MockUtil createObserved() {
                      return new MockUtil();
                  }

                  void observe(Object value) {
                  }
              }
              """
          )
        );
    }

    @Test
    void removeLastDeclaratorWithSpaceBeforeComma() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  boolean test(Object value) {
                      MockUtil observed = createObserved() , util = new MockUtil();
                      observe(observed);
                      return util.isMock(value);
                  }

                  MockUtil createObserved() {
                      return new MockUtil();
                  }

                  void observe(Object value) {
                  }
              }
              """,
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  boolean test(Object value) {
                      MockUtil observed = createObserved();
                      observe(observed);
                      return MockUtil.isMock(value);
                  }

                  MockUtil createObserved() {
                      return new MockUtil();
                  }

                  void observe(Object value) {
                  }
              }
              """
          )
        );
    }

    @Test
    void removeFirstDeclaratorWithoutSpaceAfterComma() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  boolean test(Object value) {
                      MockUtil util = new MockUtil(),observed = createObserved();
                      observe(observed);
                      return util.isMock(value);
                  }

                  MockUtil createObserved() {
                      return new MockUtil();
                  }

                  void observe(Object value) {
                  }
              }
              """,
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  boolean test(Object value) {
                      MockUtil observed = createObserved();
                      observe(observed);
                      return MockUtil.isMock(value);
                  }

                  MockUtil createObserved() {
                      return new MockUtil();
                  }

                  void observe(Object value) {
                  }
              }
              """
          )
        );
    }

    @Test
    void removeFieldWhoseUsesAreMigratedThroughThisReceiver() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  private MockUtil util = new MockUtil();
                  private MockUtil kept = new MockUtil();

                  MockUtil expose() {
                      return kept;
                  }

                  boolean test(Object value) {
                      return this.util.isMock(value);
                  }

                  boolean keptTest(Object value) {
                      return kept.isMock(value);
                  }
              }
              """,
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  private MockUtil kept = new MockUtil();

                  MockUtil expose() {
                      return kept;
                  }

                  boolean test(Object value) {
                      return MockUtil.isMock(value);
                  }

                  boolean keptTest(Object value) {
                      return MockUtil.isMock(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void removeVariableWhoseNameCollidesWithMethodDeclarationTypeDeclarationAndLabelNames() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  static class util {
                  }

                  boolean test(Object value) {
                      MockUtil util = new MockUtil();
                      MockUtil kept = new MockUtil();
                      observe(kept);
                      boolean result = util.isMock(value) && kept.isMock(value);
                      util();
                      util:
                      while (result) {
                          break util;
                      }
                      return result;
                  }

                  void util() {
                  }

                  void observe(Object value) {
                  }
              }
              """,
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  static class util {
                  }

                  boolean test(Object value) {
                      MockUtil kept = new MockUtil();
                      observe(kept);
                      boolean result = MockUtil.isMock(value) && MockUtil.isMock(value);
                      util();
                      util:
                      while (result) {
                          break util;
                      }
                      return result;
                  }

                  void util() {
                  }

                  void observe(Object value) {
                  }
              }
              """
          )
        );
    }

    @Test
    void removeVariableWhoseUseIsAMigratedMethodReference() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.internal.util.MockUtil;

              import java.util.function.Predicate;

              class Test {
                  boolean test(Object value) {
                      MockUtil util = new MockUtil();
                      MockUtil kept = new MockUtil();
                      observe(kept);
                      Predicate<Object> isMock = util::isMock;
                      return isMock.test(value) && kept.isMock(value);
                  }

                  void observe(Object value) {
                  }
              }
              """,
            """
              import org.mockito.internal.util.MockUtil;

              import java.util.function.Predicate;

              class Test {
                  boolean test(Object value) {
                      MockUtil kept = new MockUtil();
                      observe(kept);
                      Predicate<Object> isMock = MockUtil::isMock;
                      return isMock.test(value) && MockUtil.isMock(value);
                  }

                  void observe(Object value) {
                  }
              }
              """
          )
        );
    }

    @Test
    void removeFieldWhoseUseIsAMigratedMethodReferenceThroughThisReceiver() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.internal.util.MockUtil;

              import java.util.function.Predicate;

              class Test {
                  private MockUtil util = new MockUtil();
                  private MockUtil kept = new MockUtil();

                  MockUtil expose() {
                      return kept;
                  }

                  Predicate<Object> checker() {
                      return this.util::isMock;
                  }
              }
              """,
            """
              import org.mockito.internal.util.MockUtil;

              import java.util.function.Predicate;

              class Test {
                  private MockUtil kept = new MockUtil();

                  MockUtil expose() {
                      return kept;
                  }

                  Predicate<Object> checker() {
                      return MockUtil::isMock;
                  }
              }
              """
          )
        );
    }

    @Test
    void removeDeclarationDespiteSameNamedPackageSegment() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  boolean test(Object value) {
                      MockUtil util = new MockUtil();
                      java.util.Date date = new java.util.Date();
                      return util.isMock(value) && date.getTime() >= 0L;
                  }
              }
              """,
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  boolean test(Object value) {
                      java.util.Date date = new java.util.Date();
                      return MockUtil.isMock(value) && date.getTime() >= 0L;
                  }
              }
              """
          )
        );
    }

    @Test
    void removeFullyQualifiedDeclarationDespiteSameNamedPackageSegment() {
        //language=java
        rewriteRun(
          java(
            """
              class Test {
                  boolean test(Object value) {
                      org.mockito.internal.util.MockUtil util = new org.mockito.internal.util.MockUtil();
                      return util.isMock(value);
                  }
              }
              """,
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  boolean test(Object value) {
                      return MockUtil.isMock(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void removeDeclarationDespiteSameNamedEnumConstant() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  enum Kind { util, other }

                  boolean test(Object value) {
                      MockUtil util = new MockUtil();
                      return util.isMock(value) && Kind.util != Kind.other;
                  }
              }
              """,
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  enum Kind { util, other }

                  boolean test(Object value) {
                      return MockUtil.isMock(value) && Kind.util != Kind.other;
                  }
              }
              """
          )
        );
    }

    @Test
    void removeDeclarationDespiteSameNamedAnnotationAttribute() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  @interface Marker {
                      String util();
                  }

                  @Marker(util = "x")
                  boolean test(Object value) {
                      MockUtil util = new MockUtil();
                      return util.isMock(value);
                  }
              }
              """,
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  @interface Marker {
                      String util();
                  }

                  @Marker(util = "x")
                  boolean test(Object value) {
                      return MockUtil.isMock(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void removeDeclarationDespiteSameNamedNestedTypeUsedAsStaticReceiver() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  static class util {
                      static boolean flag() {
                          return true;
                      }
                  }

                  boolean other() {
                      return util.flag();
                  }

                  boolean test(Object value) {
                      MockUtil util = new MockUtil();
                      return util.isMock(value);
                  }
              }
              """,
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  static class util {
                      static boolean flag() {
                          return true;
                      }
                  }

                  boolean other() {
                      return util.flag();
                  }

                  boolean test(Object value) {
                      return MockUtil.isMock(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void removeDeclarationDespiteSameNamedTypeParameter() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.internal.util.MockUtil;

              import java.util.List;

              class Test {
                  <util> util pick(List<util> items) {
                      return items.get(0);
                  }

                  boolean test(Object value) {
                      MockUtil util = new MockUtil();
                      return util.isMock(value);
                  }
              }
              """,
            """
              import org.mockito.internal.util.MockUtil;

              import java.util.List;

              class Test {
                  <util> util pick(List<util> items) {
                      return items.get(0);
                  }

                  boolean test(Object value) {
                      return MockUtil.isMock(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void removeImportWithLastDeclaration() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  void test() {
                      MockUtil util = new MockUtil();
                  }
              }
              """,
            """
              class Test {
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveDeclarationsOfSimilarlyNamedTypes() {
        //language=java
        rewriteRun(
          java(
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  static class FakeUtil {
                      boolean isMock(Object value) {
                          return true;
                      }
                  }

                  boolean test(Object value) {
                      FakeUtil fake = new FakeUtil();
                      MockUtil util = new MockUtil();
                      return fake.isMock(value) && util.isMock(value);
                  }
              }
              """,
            """
              import org.mockito.internal.util.MockUtil;

              class Test {
                  static class FakeUtil {
                      boolean isMock(Object value) {
                          return true;
                      }
                  }

                  boolean test(Object value) {
                      FakeUtil fake = new FakeUtil();
                      return fake.isMock(value) && MockUtil.isMock(value);
                  }
              }
              """
          )
        );
    }
}
