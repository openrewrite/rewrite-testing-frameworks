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

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("JUnitMalformedDeclaration")
class JUnitParamsRunnerToParameterizedTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "JUnitParams-1.1", "hamcrest-3"))
          .recipe(new JUnitParamsRunnerToParameterized());
    }

    @DocumentExample
    @Test
    void hasAssociatedMethodSource() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import junitparams.JUnitParamsRunner;
              import junitparams.Parameters;

              @RunWith(JUnitParamsRunner.class)
              public class PersonTests {

                  @Test
                  @Parameters
                  public void personIsAdult(int age, boolean valid) {
                  }

                  private Object[] parametersForPersonIsAdult() {
                      return new Object[]{new Object[]{13, false}, new Object[]{17, false}};
                  }

                  @Test
                  @Parameters
                  public void personIsChild(int age, boolean valid) {
                  }

                  private Object[] parametersForPersonIsChild() {
                      return new Object[]{new Object[]{3, false}, new Object[]{7, false}};
                  }

                  @Test
                  public void regularTest() {}
              }
              """,
            """
              import org.junit.Test;
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.MethodSource;

              public class PersonTests {

                  @ParameterizedTest
                  @MethodSource("parametersForPersonIsAdult")
                  public void personIsAdult(int age, boolean valid) {
                  }

                  private static Object[] parametersForPersonIsAdult() {
                      return new Object[]{new Object[]{13, false}, new Object[]{17, false}};
                  }

                  @ParameterizedTest
                  @MethodSource("parametersForPersonIsChild")
                  public void personIsChild(int age, boolean valid) {
                  }

                  private static Object[] parametersForPersonIsChild() {
                      return new Object[]{new Object[]{3, false}, new Object[]{7, false}};
                  }

                  @Test
                  public void regularTest() {}
              }
              """
          )
        );
    }

    @Test
    void hasSpecifiedMethodSource() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import junitparams.JUnitParamsRunner;
              import junitparams.Parameters;
              import junitparams.NamedParameters;
              import junitparams.naming.TestCaseName;

              @RunWith(JUnitParamsRunner.class)
              public class PersonTests {

                  @Test
                  @Parameters(method = "youngAdultPersonParams")
                  @TestCaseName("persons-age: {0} is-young-adult: {1}")
                  public void personIsAdult(int age, boolean valid) {
                  }

                  private Object[] youngAdultPersonParams() {
                      return new Object[]{new Object[]{13, false}, new Object[]{17, false}};
                  }

                  @Test
                  @Parameters(named = "named-params")
                  @TestCaseName("persons-age: {0} is-young-adult: {1}")
                  public void personIsNamedAdult(int age, boolean valid) {
                  }

                  @NamedParameters("named-params")
                  private Object[] namedPeopleParams() {
                      return new Object[]{new Object[]{13, false}, new Object[]{17, false}};
                  }

                  @Test
                  @Parameters(method = "named2,named3")
                  public void paramsInMultipleMethods(String p1, Integer p2) { }

                  private Object named2() {
                      return new Object[]{"AAA", 1};
                  }

                  private Object named3() {
                      return new Object[]{"BBB", 2};
                  }
              }
              """,
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.MethodSource;

              public class PersonTests {

                  @ParameterizedTest(name = "persons-age: {0} is-young-adult: {1}")
                  @MethodSource("youngAdultPersonParams")
                  public void personIsAdult(int age, boolean valid) {
                  }

                  private static Object[] youngAdultPersonParams() {
                      return new Object[]{new Object[]{13, false}, new Object[]{17, false}};
                  }

                  @ParameterizedTest(name = "persons-age: {0} is-young-adult: {1}")
                  @MethodSource("namedPeopleParams")
                  public void personIsNamedAdult(int age, boolean valid) {
                  }


                  private static Object[] namedPeopleParams() {
                      return new Object[]{new Object[]{13, false}, new Object[]{17, false}};
                  }

                  @ParameterizedTest
                  @MethodSource({"named2", "named3"})
                  public void paramsInMultipleMethods(String p1, Integer p2) { }

                  private static Object named2() {
                      return new Object[]{"AAA", 1};
                  }

                  private static Object named3() {
                      return new Object[]{"BBB", 2};
                  }
              }
              """
          )
        );
    }

    @Test
    void methodSourceWithInstanceAccess() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import java.time.LocalDateTime;
              import junitparams.JUnitParamsRunner;
              import junitparams.Parameters;
              import junitparams.NamedParameters;
              import junitparams.naming.TestCaseName;

              @RunWith(JUnitParamsRunner.class)
              public class PersonTests {

                  @Test
                  @Parameters(method = "youngAdultPersonParams")
                  public void personIsAdult(int age, boolean valid) {
                  }

                  private Object[] youngAdultPersonParams() {
                      return new Object[]{new Object[]{getAge(2000), false}, new Object[]{getAge(2005), false}};
                  }

                  private int getAge(int birthYear) {
                      return getCurrentYear() - birthYear;
                  }

                  private int getCurrentYear() {
                     return LocalDateTime.now().getYear();
                  }
              }
              """,
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.MethodSource;

              import java.time.LocalDateTime;

              public class PersonTests {

                  @ParameterizedTest
                  @MethodSource("youngAdultPersonParams")
                  public void personIsAdult(int age, boolean valid) {
                  }

                  private static Object[] youngAdultPersonParams() {
                      return new Object[]{new Object[]{getAge(2000), false}, new Object[]{getAge(2005), false}};
                  }

                  private static int getAge(int birthYear) {
                      return getCurrentYear() - birthYear;
                  }

                  private static int getCurrentYear() {
                     return LocalDateTime.now().getYear();
                  }
              }
              """
          )
        );
    }

    @Test
    void csvSource() {
        //language=java
        rewriteRun(
          java(
            """
             import org.junit.Test;
             import org.junit.runner.RunWith;
             import junitparams.JUnitParamsRunner;
             import junitparams.Parameters;

             @RunWith(JUnitParamsRunner.class)
             class CsvSourceTests {
                 @Test
                 @Parameters({"Lav,20", "Katy,25"})
                 public void csvSource(String name, int age) { }
             }
             """,
            """
             import org.junit.jupiter.params.ParameterizedTest;
             import org.junit.jupiter.params.provider.CsvSource;

             class CsvSourceTests {
                 @ParameterizedTest
                 @CsvSource({"Lav,20", "Katy,25"})
                 public void csvSource(String name, int age) { }
             }
             """
          )
        );
    }

    @Test
    void csvSourceWithExplicitValue() {
        //language=java
        rewriteRun(
          java(
            """
             import org.junit.Test;
             import org.junit.runner.RunWith;
             import junitparams.JUnitParamsRunner;
             import junitparams.Parameters;

             @RunWith(JUnitParamsRunner.class)
             class CsvSourceTests {
                 @Test
                 @Parameters(value = {"Lav,20", "Katy,25"})
                 public void csvSource(String name, int age) { }
             }
             """,
            """
             import org.junit.jupiter.params.ParameterizedTest;
             import org.junit.jupiter.params.provider.CsvSource;

             class CsvSourceTests {
                 @ParameterizedTest
                 @CsvSource(value = {"Lav,20", "Katy,25"})
                 public void csvSource(String name, int age) { }
             }
             """
          )
        );
    }

    @Test
    void csvSourceWithCustomConverterNotConverted() {
        //language=java
        rewriteRun(
          java(
            """
             import org.junit.Test;
             import org.junit.runner.RunWith;
             import java.util.Date;
             import junitparams.converters.Param;
             import junitparams.JUnitParamsRunner;
             import junitparams.Parameters;
             import junitparams.converters.NullableConverter;

             @RunWith(JUnitParamsRunner.class)
             class CsvSourceTests {
                 @Test
                 @Parameters({"01.12.2012"})
                 public void csvSource(@Param(converter = NullableConverter.class) Date date) { }
             }
             """,
            """
             import org.junit.Test;
             import org.junit.runner.RunWith;
             import java.util.Date;
             import junitparams.converters.Param;
             import junitparams.JUnitParamsRunner;
             import junitparams.Parameters;
             import junitparams.converters.NullableConverter;

             @RunWith(JUnitParamsRunner.class)
             class CsvSourceTests {
                 @Test
                 // JunitParamsRunnerToParameterized conversion not supported
                 @Parameters({"01.12.2012"})
                 public void csvSource(@Param(converter = NullableConverter.class) Date date) { }
             }
             """
          )
        );
    }

    @Test
    void enumSourceNotConverted() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import junitparams.JUnitParamsRunner;
              import junitparams.Parameters;

              enum PersonType {MALE, FEMALE}

              @RunWith(JUnitParamsRunner.class)
              class EnumSourceTests {
                  @Test
                  @Parameters(source = PersonType.class)
                  public void enumsAsParamsInMethod(PersonType person) { }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import junitparams.JUnitParamsRunner;
              import junitparams.Parameters;

              enum PersonType {MALE, FEMALE}

              @RunWith(JUnitParamsRunner.class)
              class EnumSourceTests {
                  @Test
                  // JunitParamsRunnerToParameterized conversion not supported
                  @Parameters(source = PersonType.class)
                  public void enumsAsParamsInMethod(PersonType person) { }
              }
              """
          )
        );
    }
}
