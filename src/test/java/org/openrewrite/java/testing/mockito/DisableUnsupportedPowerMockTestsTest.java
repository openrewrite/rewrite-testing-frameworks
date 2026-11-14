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
package org.openrewrite.java.testing.mockito;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.testing.mockito.table.PowerMockTestsDisabled;
import org.openrewrite.test.RecipeQualityValidation;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static java.util.Collections.emptyMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.Assertions.java;

class DisableUnsupportedPowerMockTestsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          // The imports stay for the code the recipe comments out, so whoever reworks the test can restore it
          .recipeQualityValidation(RecipeQualityValidation.builder().removeUnusedImports(false).build())
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-4",
              "junit-jupiter-api-5",
              "mockito-core-3.12",
              "powermock-core-1",
              "powermock-api-mockito-1",
              "powermock-api-support-1",
              "powermock-module-junit4",
              "powermock-reflect-1"
            ))
          .recipe(new DisableUnsupportedPowerMockTests());
    }

    @DocumentExample
    @Test
    void disablesAJunit4TestThatVerifiesAPrivateMethod() {
        rewriteRun(
          spec -> spec.dataTable(PowerMockTestsDisabled.Row.class, rows -> {
              assertThat(rows).hasSize(1);
              PowerMockTestsDisabled.Row row = rows.getFirst();
              assertThat(row.getTestClass()).isEqualTo("MyTest");
              assertThat(row.getDisabledElement()).isEqualTo("verifiesAPrivateMethod");
              assertThat(row.getScope()).isEqualTo("METHOD");
              assertThat(row.getReason()).contains("verifyPrivate");
          }),
          //language=java
          java(
            """
              import org.junit.Test;
              import org.mockito.Mockito;
              import org.powermock.api.mockito.PowerMockito;

              public class MyTest {

                  @Test
                  public void verifiesAPrivateMethod() throws Exception {
                      Object target = Mockito.mock(Object.class);
                      PowerMockito.verifyPrivate(target, Mockito.times(1)).invoke("hidden");
                  }
              }
              """,
            """
              import org.junit.Ignore;
              import org.junit.Test;
              import org.mockito.Mockito;
              import org.powermock.api.mockito.PowerMockito;

              public class MyTest {

                  @Test
                  @Ignore("PowerMock test disabled by migration: rework it not to rely on private members")
                  public void verifiesAPrivateMethod() throws Exception {
                      // The body of this test is kept for reference while it is migrated by hand:
                      // Object target = Mockito.mock(Object.class);
                      // PowerMockito.verifyPrivate(target, Mockito.times(1)).invoke("hidden");
                  }
              }
              """
          )
        );
    }

    @Test
    void disablesAJunit5Test() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.mockito.Mockito;
              import org.powermock.api.mockito.PowerMockito;

              class MyTest {

                  @Test
                  void verifiesAPrivateMethod() throws Exception {
                      Object target = Mockito.mock(Object.class);
                      PowerMockito.verifyPrivate(target, Mockito.times(1)).invoke("hidden");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Disabled;
              import org.junit.jupiter.api.Test;
              import org.mockito.Mockito;
              import org.powermock.api.mockito.PowerMockito;

              class MyTest {

                  @Test
                  @Disabled("PowerMock test disabled by migration: rework it not to rely on private members")
                  void verifiesAPrivateMethod() throws Exception {
                      // The body of this test is kept for reference while it is migrated by hand:
                      // Object target = Mockito.mock(Object.class);
                      // PowerMockito.verifyPrivate(target, Mockito.times(1)).invoke("hidden");
                  }
              }
              """
          )
        );
    }

    @Test
    void leavesTestsWhosePowerMockUsageMigrates() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.powermock.api.mockito.PowerMockito;

              import java.util.Calendar;

              class MyTest {

                  @Test
                  void mocksAStatic() {
                      PowerMockito.mockStatic(Calendar.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotDisableATestThatIsAlreadyDisabled() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Disabled;
              import org.junit.jupiter.api.Test;
              import org.mockito.Mockito;
              import org.powermock.api.mockito.PowerMockito;

              class MyTest {

                  @Disabled("flaky")
                  @Test
                  void verifiesAPrivateMethod() throws Exception {
                      Object target = Mockito.mock(Object.class);
                      PowerMockito.verifyPrivate(target, Mockito.times(1)).invoke("hidden");
                  }
              }
              """
          )
        );
    }

    @Test
    void disablesTheWholeClassWhenTheUsageIsOutsideATestMethod() {
        rewriteRun(
          spec -> spec.dataTable(PowerMockTestsDisabled.Row.class, rows -> {
              List<String> scopes = rows.stream().map(PowerMockTestsDisabled.Row::getScope).toList();
              assertThat(scopes).containsExactly("CLASS");
          }),
          //language=java
          java(
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;
              import org.mockito.Mockito;
              import org.powermock.api.mockito.PowerMockito;

              class MyTest {

                  @BeforeEach
                  void setUp() throws Exception {
                      Object target = Mockito.mock(Object.class);
                      PowerMockito.verifyPrivate(target, Mockito.times(1)).invoke("hidden");
                  }

                  @Test
                  void aTest() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Disabled;
              import org.junit.jupiter.api.Test;
              import org.mockito.Mockito;
              import org.powermock.api.mockito.PowerMockito;

              @Disabled("PowerMock test disabled by migration: rework it not to rely on private members")
              class MyTest {

                  @BeforeEach
                  void setUp() throws Exception {
                      // The body of this test is kept for reference while it is migrated by hand:
                      // Object target = Mockito.mock(Object.class);
                      // PowerMockito.verifyPrivate(target, Mockito.times(1)).invoke("hidden");
                  }

                  @Test
                  void aTest() {
                  }
              }
              """
          )
        );
    }

    @Test
    void helperReturningAValueStillCompilesOnceItsBodyIsCommentedOut() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.mockito.Mockito;
              import org.powermock.api.mockito.PowerMockito;

              class MyTest {

                  @Test
                  void aTest() throws Exception {
                      verified();
                  }

                  static Object verified() throws Exception {
                      Object target = Mockito.mock(Object.class);
                      PowerMockito.verifyPrivate(target, Mockito.times(1)).invoke("hidden");
                      return target;
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Disabled;
              import org.junit.jupiter.api.Test;
              import org.mockito.Mockito;
              import org.powermock.api.mockito.PowerMockito;

              @Disabled("PowerMock test disabled by migration: rework it not to rely on private members")
              class MyTest {

                  @Test
                  void aTest() throws Exception {
                      verified();
                  }

                  static Object verified() throws Exception {
                      // The body of this test is kept for reference while it is migrated by hand:
                      // Object target = Mockito.mock(Object.class);
                      // PowerMockito.verifyPrivate(target, Mockito.times(1)).invoke("hidden");
                      // return target;
                      throw new UnsupportedOperationException("Disabled by the PowerMock migration");
                  }
              }
              """
          )
        );
    }

    private static void testNg(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion()
          .logCompilationWarningsAndErrors(true)
          .classpathFromResources(new InMemoryExecutionContext(),
            "testng-7",
            "mockito-core-3.12",
            "powermock-core-1",
            "powermock-api-mockito-1",
            "powermock-api-support-1",
            "powermock-reflect-1"
          ));
    }

    /// The test classpath of a project on TestNG before 6.13, which has no `@Ignore`
    private static JavaSourceSet testNgWithoutIgnore() {
        return new JavaSourceSet(randomId(), "test",
          List.of(JavaType.ShallowClass.build("org.testng.annotations.Test")), emptyMap());
    }

    @Test
    void disablesATestNgTestWithEnabledFalseBeforeTestNg613() {
        rewriteRun(
          DisableUnsupportedPowerMockTestsTest::testNg,
          //language=java
          java(
            """
              public class Service {
                  private void init() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import org.powermock.api.mockito.PowerMockito;
              import org.testng.annotations.Test;

              public class ServiceTest {
                  @Test(groups = "unit")
                  public void initializes() throws Exception {
                      Service service = PowerMockito.spy(new Service());
                      PowerMockito.verifyPrivate(service).invoke("init");
                  }

                  @Test
                  public void unaffected() {
                  }
              }
              """,
            """
              import org.powermock.api.mockito.PowerMockito;
              import org.testng.annotations.Test;

              public class ServiceTest {
                  @Test(groups = "unit", enabled = false)
                  public void initializes() throws Exception {
                      // The body of this test is kept for reference while it is migrated by hand:
                      // Service service = PowerMockito.spy(new Service());
                      // PowerMockito.verifyPrivate(service).invoke("init");
                  }

                  @Test
                  public void unaffected() {
                  }
              }
              """,
            spec -> spec.markers(testNgWithoutIgnore())
          )
        );
    }

    @Test
    void disablesEveryTestNgTestOfAClassWithEnabledFalseBeforeTestNg613() {
        rewriteRun(
          DisableUnsupportedPowerMockTestsTest::testNg,
          //language=java
          java(
            """
              public class Service {
                  private void init() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import org.powermock.api.mockito.PowerMockito;
              import org.testng.annotations.BeforeMethod;
              import org.testng.annotations.Test;

              public class ServiceTest {
                  @BeforeMethod
                  public void setUp() throws Exception {
                      Service service = PowerMockito.spy(new Service());
                      PowerMockito.verifyPrivate(service).invoke("init");
                  }

                  @Test
                  public void first() {
                  }

                  @Test(enabled = true)
                  public void second() {
                  }
              }
              """,
            """
              import org.powermock.api.mockito.PowerMockito;
              import org.testng.annotations.BeforeMethod;
              import org.testng.annotations.Test;

              public class ServiceTest {
                  @BeforeMethod
                  public void setUp() throws Exception {
                      // The body of this test is kept for reference while it is migrated by hand:
                      // Service service = PowerMockito.spy(new Service());
                      // PowerMockito.verifyPrivate(service).invoke("init");
                  }

                  @Test(enabled = false)
                  public void first() {
                  }

                  @Test(enabled = false)
                  public void second() {
                  }
              }
              """,
            spec -> spec.markers(testNgWithoutIgnore())
          )
        );
    }

    @Test
    void disablesATestNgTestWithIgnoreWhereTestNgHasIt() {
        rewriteRun(
          DisableUnsupportedPowerMockTestsTest::testNg,
          //language=java
          java(
            """
              public class Service {
                  private void init() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import org.powermock.api.mockito.PowerMockito;
              import org.testng.annotations.Test;

              public class ServiceTest {
                  @Test
                  public void initializes() throws Exception {
                      Service service = PowerMockito.spy(new Service());
                      PowerMockito.verifyPrivate(service).invoke("init");
                  }
              }
              """,
            """
              import org.powermock.api.mockito.PowerMockito;
              import org.testng.annotations.Ignore;
              import org.testng.annotations.Test;

              public class ServiceTest {
                  @Test
                  @Ignore("PowerMock test disabled by migration: rework it not to rely on private members")
                  public void initializes() throws Exception {
                      // The body of this test is kept for reference while it is migrated by hand:
                      // Service service = PowerMockito.spy(new Service());
                      // PowerMockito.verifyPrivate(service).invoke("init");
                  }
              }
              """
          )
        );
    }
}
