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
import org.openrewrite.java.testing.mockito.table.PowerMockTestsDisabled;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

class DisableUnsupportedPowerMockTestsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
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
              PowerMockTestsDisabled.Row row = rows.get(0);
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
}
