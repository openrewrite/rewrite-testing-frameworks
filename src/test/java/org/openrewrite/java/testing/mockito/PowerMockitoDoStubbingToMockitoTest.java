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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class PowerMockitoDoStubbingToMockitoTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "mockito-core-3.12",
              "junit-jupiter-api-5",
              "powermock-core-1",
              "powermock-api-mockito-1",
              "powermock-api-support-1",
              "powermock-reflect-1"
            ))
          .recipe(new PowerMockitoDoStubbingToMockito());
    }

    @DocumentExample
    @Test
    void doNothingWithStringMethodName() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class MyTest {

                  private Calendar calendarSpy;

                  @BeforeEach
                  void setUp() throws Exception {
                      calendarSpy = PowerMockito.spy(Calendar.getInstance());
                      PowerMockito.doNothing().when(calendarSpy, "clear");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class MyTest {

                  private Calendar calendarSpy;

                  @BeforeEach
                  void setUp() throws Exception {
                      calendarSpy = PowerMockito.spy(Calendar.getInstance());
                      PowerMockito.doNothing().when(calendarSpy).clear();
                  }
              }
              """
          )
        );
    }

    @Test
    void doReturnWithStringMethodName() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class MyTest {

                  private Calendar calendarSpy;

                  @BeforeEach
                  void setUp() throws Exception {
                      calendarSpy = PowerMockito.spy(Calendar.getInstance());
                      PowerMockito.doReturn("gregorian").when(calendarSpy, "getCalendarType");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class MyTest {

                  private Calendar calendarSpy;

                  @BeforeEach
                  void setUp() throws Exception {
                      calendarSpy = PowerMockito.spy(Calendar.getInstance());
                      PowerMockito.doReturn("gregorian").when(calendarSpy).getCalendarType();
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForStaticMethodStubbing() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class MyTest {

                  @Test
                  void test() throws Exception {
                      PowerMockito.doReturn(Calendar.getInstance()).when(Calendar.class, "getInstance");
                  }
              }
              """
          )
        );
    }

    @Test
    void addsCommentForPrivateMethodStubbing() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class MyTest {

                  private Calendar calendarSpy;

                  @BeforeEach
                  void setUp() throws Exception {
                      calendarSpy = PowerMockito.spy(Calendar.getInstance());
                      PowerMockito.doNothing().when(calendarSpy, "updateTime");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class MyTest {

                  private Calendar calendarSpy;

                  @BeforeEach
                  void setUp() throws Exception {
                      calendarSpy = PowerMockito.spy(Calendar.getInstance());
                      // PowerMock private method stubbing is not supported by Mockito. Refactor the private method to package-private or extract to a collaborator.
                      PowerMockito.doNothing().when(calendarSpy, "updateTime");
                  }
              }
              """
          )
        );
    }

    @Test
    void addsCommentWhenPrivateOverloadsAreAmbiguous() {
        //language=java
        rewriteRun(
          java(
            """
              class Service {
                  private void helper(String s) {}
                  private void helper(Integer i) {}
              }
              """
          ),
          java(
            """
              import org.powermock.api.mockito.PowerMockito;

              class MyTest {
                  void setUp() throws Exception {
                      Service s = PowerMockito.spy(new Service());
                      PowerMockito.doNothing().when(s, "helper", "a");
                  }
              }
              """,
            """
              import org.powermock.api.mockito.PowerMockito;

              class MyTest {
                  void setUp() throws Exception {
                      Service s = PowerMockito.spy(new Service());
                      // PowerMock private method stubbing is not supported by Mockito. Refactor the private method to package-private or extract to a collaborator.
                      PowerMockito.doNothing().when(s, "helper", "a");
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenTheMethodCanNotBeResolved() {
        //language=java
        rewriteRun(
          java(
            """
              import org.powermock.api.mockito.PowerMockito;

              class MyTest {
                  void setUp() throws Exception {
                      Object o = PowerMockito.spy(new Object());
                      PowerMockito.doNothing().when(o, "doesNotExist", "a");
                  }
              }
              """
          )
        );
    }

    @Test
    void doStubbingWithStringMethodNameAndArgs() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class MyTest {

                  private Calendar calendarSpy;

                  @BeforeEach
                  void setUp() throws Exception {
                      calendarSpy = PowerMockito.spy(Calendar.getInstance());
                      PowerMockito.doNothing().when(calendarSpy, "set", 2026, 0, 1);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class MyTest {

                  private Calendar calendarSpy;

                  @BeforeEach
                  void setUp() throws Exception {
                      calendarSpy = PowerMockito.spy(Calendar.getInstance());
                      PowerMockito.doNothing().when(calendarSpy).set(2026, 0, 1);
                  }
              }
              """
          )
        );
    }

    @Test
    void leavesTheDoStubbingTargetAloneWhenAMemberIsStubbedByName() {
        //language=java
        rewriteRun(
          spec -> spec.recipe(new PowerMockitoDoStubbingTargetToMockito()),
          java(
            """
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class MyTest {
                  private static final String UPDATE_TIME = "updateTime";

                  void setUp() throws Exception {
                      Calendar spy = PowerMockito.spy(Calendar.getInstance());
                      PowerMockito.doNothing().when(spy, UPDATE_TIME);
                  }
              }
              """
          )
        );
    }

    @Test
    void retargetsTheDoStubbingWhenNoMemberIsStubbedByName() {
        //language=java
        rewriteRun(
          spec -> spec.recipe(new PowerMockitoDoStubbingTargetToMockito()),
          java(
            """
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class MyTest {
                  void setUp() {
                      Calendar spy = PowerMockito.spy(Calendar.getInstance());
                      PowerMockito.doNothing().when(spy).clear();
                  }
              }
              """,
            """
              import org.mockito.Mockito;
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class MyTest {
                  void setUp() {
                      Calendar spy = PowerMockito.spy(Calendar.getInstance());
                      Mockito.doNothing().when(spy).clear();
                  }
              }
              """
          )
        );
    }

    @Test
    void retargetsOnlyTheChainsThatStubNoMemberByName() {
        //language=java
        rewriteRun(
          spec -> spec.recipe(new PowerMockitoDoStubbingTargetToMockito()),
          java(
            """
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class MyTest {
                  void setUp() throws Exception {
                      Calendar spy = PowerMockito.spy(Calendar.getInstance());
                      PowerMockito.doNothing().when(spy, "updateTime");
                      PowerMockito.doThrow(new IllegalStateException()).doNothing().when(spy).clear();
                  }
              }
              """,
            """
              import org.mockito.Mockito;
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class MyTest {
                  void setUp() throws Exception {
                      Calendar spy = PowerMockito.spy(Calendar.getInstance());
                      PowerMockito.doNothing().when(spy, "updateTime");
                      Mockito.doThrow(new IllegalStateException()).doNothing().when(spy).clear();
                  }
              }
              """
          )
        );
    }

    // from apache/servicecomb-toolkit InvokeStaticMethodTest
    @Test
    void doThrowOnStaticVoidCallBecomesDynamicWhen() {
        //language=java
        rewriteRun(
          java(
            """
              class GenerateUtil {
                  static void generateContract(Object a, String b) {}
              }
              """
          ),
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.powermock.api.mockito.PowerMockito;

              import static org.mockito.ArgumentMatchers.any;
              import static org.mockito.ArgumentMatchers.anyString;

              class MyTest {
                  @Test
                  void test() {
                      PowerMockito.mockStatic(GenerateUtil.class);
                      PowerMockito.doThrow(new RuntimeException()).when(GenerateUtil.class);
                      // Powermockito limit: use argument matchers to specify method which would be mock
                      GenerateUtil.generateContract(any(), anyString());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.powermock.api.mockito.PowerMockito;

              import static org.mockito.ArgumentMatchers.any;
              import static org.mockito.ArgumentMatchers.anyString;

              class MyTest {
                  @Test
                  void test() {
                      PowerMockito.mockStatic(GenerateUtil.class);
                      PowerMockito.when(GenerateUtil.class, "generateContract", any(), anyString()).thenThrow(new RuntimeException());
                  }
              }
              """
          )
        );
    }

    // from jenkinsci/scm-sync-configuration-plugin V0_0_3CompatibilityTest: statements on one line
    @Test
    void doNothingOnStaticVoidCallWithStaticImports() {
        //language=java
        rewriteRun(
          java(
            """
              class Listener {
                  static void fireOnChange(Object saveable, Object file) {}
              }
              """
          ),
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.mockito.Matchers.notNull;
              import static org.powermock.api.mockito.PowerMockito.doNothing;
              import static org.powermock.api.mockito.PowerMockito.mockStatic;

              class MyTest {
                  @Test
                  void save() {
                      mockStatic(Listener.class);
                      doNothing().when(Listener.class); Listener.fireOnChange(notNull(), notNull());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.powermock.api.mockito.PowerMockito;

              import static org.mockito.Matchers.notNull;
              import static org.powermock.api.mockito.PowerMockito.mockStatic;

              class MyTest {
                  @Test
                  void save() {
                      mockStatic(Listener.class);
                      PowerMockito.when(Listener.class, "fireOnChange", notNull(), notNull()).thenAnswer(invocation -> null);
                  }
              }
              """
          )
        );
    }

    // from jenkinsci/scm-sync-configuration-plugin ScmSyncConfigurationBaseTest
    @Test
    void doReturnOnStaticCall() {
        //language=java
        rewriteRun(
          java(
            """
              class Hudson {
                  static Hudson getInstance() { return null; }
              }
              """
          ),
          java(
            """
              import org.powermock.api.mockito.PowerMockito;

              class MyTest {
                  void setUp(Hudson mocked) throws Exception {
                      PowerMockito.mockStatic(Hudson.class);
                      PowerMockito.doReturn(mocked).when(Hudson.class); Hudson.getInstance();
                  }
              }
              """,
            """
              import org.powermock.api.mockito.PowerMockito;

              class MyTest {
                  void setUp(Hudson mocked) throws Exception {
                      PowerMockito.mockStatic(Hudson.class);
                      PowerMockito.when(Hudson.class, "getInstance").thenReturn(mocked);
                  }
              }
              """
          )
        );
    }

    @Test
    void staticCallNotMockedInThisFileIsLeftAlone() {
        //language=java
        rewriteRun(
          java(
            """
              class Hudson {
                  static Hudson getInstance() { return null; }
              }
              """
          ),
          java(
            """
              import org.powermock.api.mockito.PowerMockito;

              class MyTest {
                  void setUp(Hudson mocked) throws Exception {
                      PowerMockito.doReturn(mocked).when(Hudson.class); Hudson.getInstance();
                  }
              }
              """
          )
        );
    }

    // from koraktor/steam-condenser-java WebApiTest
    @Test
    void doReturnByNameOnStaticMethod() {
        //language=java
        rewriteRun(
          java(
            """
              class WebApi {
                  static String load(String format, int version, Object params) { return null; }
              }
              """
          ),
          java(
            """
              import org.powermock.api.mockito.PowerMockito;

              class MyTest {
                  void test(Object params) throws Exception {
                      PowerMockito.spy(WebApi.class);
                      PowerMockito.doReturn("test").when(WebApi.class, "load", "json", 2, params);
                  }
              }
              """,
            """
              import org.powermock.api.mockito.PowerMockito;

              class MyTest {
                  void test(Object params) throws Exception {
                      PowerMockito.spy(WebApi.class);
                      PowerMockito.when(WebApi.class, "load", "json", 2, params).thenReturn("test");
                  }
              }
              """
          )
        );
    }

    // from digidotcom/xbee-java AccessPointsScanTest: the method name is a constant
    @Test
    void methodNameInConstant() {
        //language=java
        rewriteRun(
          java(
            """
              public class WifiDevice {
                  public java.util.List<String> scanAccessPoints() { return null; }
              }
              """
          ),
          java(
            """
              import org.powermock.api.mockito.PowerMockito;
              import java.util.List;

              class MyTest {
                  private static final String METHOD_SCAN_ACCESS_POINTS = "scanAccessPoints";

                  void test(WifiDevice wifiDevice, List<String> points) throws Exception {
                      PowerMockito.doReturn(points).when(wifiDevice, METHOD_SCAN_ACCESS_POINTS);
                  }
              }
              """,
            """
              import org.powermock.api.mockito.PowerMockito;
              import java.util.List;

              class MyTest {
                  private static final String METHOD_SCAN_ACCESS_POINTS = "scanAccessPoints";

                  void test(WifiDevice wifiDevice, List<String> points) throws Exception {
                      PowerMockito.doReturn(points).when(wifiDevice).scanAccessPoints();
                  }
              }
              """
          )
        );
    }

    @Test
    void methodOverriddenInSubclassIsOneTarget() {
        //language=java
        rewriteRun(
          java(
            """
              class Base {
                  public void sleep(int ms) {}
              }
              """
          ),
          java(
            """
              class Device extends Base {
                  @Override
                  public void sleep(int ms) {}
              }
              """
          ),
          java(
            """
              import org.powermock.api.mockito.PowerMockito;

              class MyTest {
                  void test(Device device) throws Exception {
                      PowerMockito.doNothing().when(device, "sleep", 10);
                  }
              }
              """,
            """
              import org.powermock.api.mockito.PowerMockito;

              class MyTest {
                  void test(Device device) throws Exception {
                      PowerMockito.doNothing().when(device).sleep(10);
                  }
              }
              """
          )
        );
    }

    @Test
    void packagePrivateMethodOfAnotherPackageIsLeftAlone() {
        //language=java
        rewriteRun(
          java(
            """
              package other;

              public class Device {
                  void sleep(int ms) {}
              }
              """
          ),
          java(
            """
              import org.powermock.api.mockito.PowerMockito;

              class MyTest {
                  void test(other.Device device) throws Exception {
                      PowerMockito.doNothing().when(device, "sleep", 10);
                  }
              }
              """
          )
        );
    }

    @Test
    void retargetsTheChainsThatDoNotStubByNameInTheSameFile() {
        //language=java
        rewriteRun(
          spec -> spec.recipe(new PowerMockitoDoStubbingTargetToMockito()),
          java(
            """
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class MyTest {
                  void setUp(Calendar spy, Calendar other) throws Exception {
                      PowerMockito.doNothing().when(spy, "updateTime");
                      PowerMockito.doNothing().when(other).clear();
                      PowerMockito.doNothing().when(Calendar.class);
                  }
              }
              """,
            """
              import org.mockito.Mockito;
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class MyTest {
                  void setUp(Calendar spy, Calendar other) throws Exception {
                      PowerMockito.doNothing().when(spy, "updateTime");
                      Mockito.doNothing().when(other).clear();
                      PowerMockito.doNothing().when(Calendar.class);
                  }
              }
              """
          )
        );
    }
}
