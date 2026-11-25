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
package org.openrewrite.java.testing.cleanup;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.groovy.GroovyParser;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.groovy.Assertions.groovy;
import static org.openrewrite.java.Assertions.java;

class RemoveEmptyTestsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "junit-4", "junit-jupiter-api-5", "spring-test-6", "spring-boot-test-3", "testng-7", "mockito-junit-jupiter-5"))
          .recipe(new RemoveEmptyTests());
    }

    @DocumentExample
    @Test
    void removeEmptyTest() {
        //language=java
        rewriteRun(

          java(
            """
              import org.junit.Test;
              class MyTest {
                  @Test
                  public void method() {
                  }
              }
              """,
            """
              class MyTest {
              }
              """
          )
        );
    }

    @Test
    void isNotTest() {
        //language=java
        rewriteRun(
          java(
            """
              class MyTest {
                  void method() {
                  }
              }
              """
          )
        );
    }

    @Test
    void emptyTestWithComments() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              class MyTest {
                  @Test
                  public void method() {
                      // comment
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsEmptyTestInClassWithMetaAnnotatedExtension() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.TestInstancePostProcessor;
              import org.junit.jupiter.api.extension.ExtendWith;

              import java.lang.annotation.Retention;
              import java.lang.annotation.RetentionPolicy;

              class ContextExtension implements TestInstancePostProcessor {
              }

              @Retention(RetentionPolicy.RUNTIME)
              @ExtendWith(ContextExtension.class)
              @interface ApplicationTest {
              }
              """
          ),
          java(
            """
              import org.junit.jupiter.api.Test;

              @ApplicationTest
              class ApplicationTests {
                  @Test
                  void contextLoads() {
                  }
              }
              """
          )
        );
    }

    @Test
    void removesEmptyTestOfJUnit4Runner() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.junit.runners.JUnit4;

              @RunWith(JUnit4.class)
              public class ApplicationTests {
                  @Test
                  public void contextLoads() {
                  }
              }
              """,
            """
              import org.junit.runner.RunWith;
              import org.junit.runners.JUnit4;

              @RunWith(JUnit4.class)
              public class ApplicationTests {
              }
              """
          )
        );
    }

    @Test
    void removesEmptyTestOfClassWithSetup() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;

              class ConfigTests {
                  String config;

                  @BeforeEach
                  void load() {
                      config = System.getProperty("config");
                  }

                  @Test
                  void configLoads() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;

              class ConfigTests {
                  String config;

                  @BeforeEach
                  void load() {
                      config = System.getProperty("config");
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsEmptyTestWhoseSuperclassRegistersExtension() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.TestInstancePostProcessor;
              import org.junit.jupiter.api.extension.ExtendWith;

              class ContextExtension implements TestInstancePostProcessor {
              }

              @ExtendWith(ContextExtension.class)
              abstract class AbstractIntegrationTest {
              }
              """
          ),
          java(
            """
              import org.junit.jupiter.api.Test;

              class ApplicationTests extends AbstractIntegrationTest {
                  @Test
                  void contextLoads() {
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsEmptyTestOfSpringBootTest() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.springframework.boot.test.context.SpringBootTest;

              @SpringBootTest
              class ApplicationTests {
                  @Test
                  void contextLoads() {
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsEmptyTestOfSpringJUnitConfig() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

              @SpringJUnitConfig
              class ApplicationTests {
                  @Test
                  void contextLoads() {
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsEmptyTestNestedInSpringBootTest() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
              import org.springframework.boot.test.context.SpringBootTest;

              @SpringBootTest
              class ApplicationTests {
                  @Nested
                  class Inner {
                      @Test
                      void contextLoads() {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsEmptyTestOfTestNgSpringContext() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.test.context.ContextConfiguration;
              import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
              import org.testng.annotations.Test;

              @ContextConfiguration
              public class ApplicationTests extends AbstractTestNGSpringContextTests {
                  @Test
                  public void contextLoads() {
                  }
              }
              """
          )
        );
    }

    @Test
    void removesEmptyTestOfMockitoExtension() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class ServiceTests {
                  @Test
                  void nothing() {
                  }

                  @Test
                  void something() {
                      System.out.println();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class ServiceTests {

                  @Test
                  void something() {
                      System.out.println();
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsEmptyTestWhenClassAnnotationDoesNotResolve() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

              @WebMvcTest
              class ApplicationTests {
                  @Test
                  void contextLoads() {
                  }
              }
              """));
    }

    @Test
    void removesEmptyTestOfMockitoSettings() {
        //language=java
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "junit-jupiter-api-5", "mockito-junit-jupiter-5", "mockito-core-5")),
          java(
          """
            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.mockito.junit.jupiter.MockitoExtension;
            import org.mockito.junit.jupiter.MockitoSettings;
            import org.mockito.quality.Strictness;

            @ExtendWith(MockitoExtension.class)
            @MockitoSettings(strictness = Strictness.LENIENT)
            class ServiceTests {
                @Test
                void nothing() {
                }
            }
            """,
          """
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.mockito.junit.jupiter.MockitoExtension;
            import org.mockito.junit.jupiter.MockitoSettings;
            import org.mockito.quality.Strictness;

            @ExtendWith(MockitoExtension.class)
            @MockitoSettings(strictness = Strictness.LENIENT)
            class ServiceTests {
            }
            """));
    }

    @Test
    void removesEmptyTestOfJUnitCondition() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.condition.EnabledOnOs;
              import org.junit.jupiter.api.condition.OS;

              @EnabledOnOs(OS.LINUX)
              class LinuxTests {
                  @Test
                  void nothing() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.condition.EnabledOnOs;
              import org.junit.jupiter.api.condition.OS;

              @EnabledOnOs(OS.LINUX)
              class LinuxTests {
              }
              """
          )
        );
    }

    @Test
    void removesEmptyTestWhoseSuperclassHasMockitoExtension() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              abstract class AbstractMockTest {
              }
              """),
          java(
            """
              import org.junit.jupiter.api.Test;

              class ServiceTests extends AbstractMockTest {
                  @Test
                  void nothing() {
                  }
              }
              """,
            """
              class ServiceTests extends AbstractMockTest {
              }
              """));
    }

    @Test
    void removesEmptyTestOfStaticClassNestedInSpringBootTest() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.springframework.boot.test.context.SpringBootTest;

              @SpringBootTest
              class ApplicationTests {
                  @Test
                  void contextLoads() {
                  }

                  static class PlainTests {
                      @Test
                      void nothing() {
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.springframework.boot.test.context.SpringBootTest;

              @SpringBootTest
              class ApplicationTests {
                  @Test
                  void contextLoads() {
                  }

                  static class PlainTests {
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsEmptyTestOfMockitoAndSpringExtension() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.springframework.test.context.junit.jupiter.SpringExtension;

              @ExtendWith({MockitoExtension.class, SpringExtension.class})
              class ApplicationTests {
                  @Test
                  void contextLoads() {
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsEmptyTestOfSpringRunner() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.springframework.test.context.junit4.SpringRunner;

              @RunWith(SpringRunner.class)
              public class ApplicationTests {
                  @Test
                  public void contextLoads() {
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsEmptyTestWhoseInterfaceHasSpringExtension() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.springframework.test.context.junit.jupiter.SpringExtension;

              @ExtendWith(SpringExtension.class)
              interface SpringTest {
              }
              """),
          java(
            """
              import org.junit.jupiter.api.Test;

              class ApplicationTests implements SpringTest {
                  @Test
                  void contextLoads() {
                  }
              }
              """));
    }

    @Test
    void removesEmptyTestOfMetaAnnotationWithInertExtension() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;
              import java.lang.annotation.Retention;
              import java.lang.annotation.RetentionPolicy;

              @Retention(RetentionPolicy.RUNTIME)
              @ExtendWith(MockitoExtension.class)
              @interface MockitoTest {
              }
              """
          ),
          java(
            """
              import org.junit.jupiter.api.Test;

              @MockitoTest
              class ATests {
                  @Test
                  void nothing() {
                  }
              }
              """,
            """
              @MockitoTest
              class ATests {
              }
              """
          )
        );
    }

    @Test
    void keepsEmptyTestOfMetaAnnotationWithExtensions() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.TestInstancePostProcessor;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.junit.jupiter.api.extension.Extensions;
              import org.mockito.junit.jupiter.MockitoExtension;
              import java.lang.annotation.Retention;
              import java.lang.annotation.RetentionPolicy;

              class ContextExtension implements TestInstancePostProcessor {
              }

              @Retention(RetentionPolicy.RUNTIME)
              @Extensions({@ExtendWith(MockitoExtension.class), @ExtendWith(ContextExtension.class)})
              @interface AppTest {
              }
              """
          ),
          java(
            """
              import org.junit.jupiter.api.Test;

              @AppTest
              class ATests {
                  @Test
                  void contextLoads() {
                  }
              }
              """
          )
        );
    }

    @Test
    void removesEmptyTestOfMetaAnnotationWithInertExtensions() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.junit.jupiter.api.extension.Extensions;
              import org.mockito.junit.jupiter.MockitoExtension;
              import java.lang.annotation.Retention;
              import java.lang.annotation.RetentionPolicy;

              @Retention(RetentionPolicy.RUNTIME)
              @Extensions({@ExtendWith(MockitoExtension.class)})
              @interface AppTest {
              }
              """
          ),
          java(
            """
              import org.junit.jupiter.api.Test;

              @AppTest
              class ATests {
                  @Test
                  void nothing() {
                  }
              }
              """,
            """
              @AppTest
              class ATests {
              }
              """
          )
        );
    }

    @Test
    void removesEmptyTestOfCyclicMetaAnnotations() {
        //language=java
        rewriteRun(
          java(
            """
              import java.lang.annotation.Retention;
              import java.lang.annotation.RetentionPolicy;

              @Retention(RetentionPolicy.RUNTIME)
              @B
              @interface A {
              }

              @Retention(RetentionPolicy.RUNTIME)
              @A
              @interface B {
              }
              """
          ),
          java(
            """
              import org.junit.jupiter.api.Test;

              @A
              class ATests {
                  @Test
                  void nothing() {
                  }
              }
              """,
            """
              @A
              class ATests {
              }
              """
          )
        );
    }

    @Test
    void keepsEmptyTestOfGenericSuperclassWithExtension() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.TestInstancePostProcessor;
              import org.junit.jupiter.api.extension.ExtendWith;

              class ContextExtension implements TestInstancePostProcessor {
              }

              @ExtendWith(ContextExtension.class)
              abstract class Base<T> {
              }
              """
          ),
          java(
            """
              import org.junit.jupiter.api.Test;

              class ATests extends Base<String> {
                  @Test
                  void contextLoads() {
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsEmptyTestOfGroovyExtension() {
        //language=groovy
        rewriteRun(
          spec -> spec.parser(GroovyParser.builder().classpathFromResource(new InMemoryExecutionContext(),
            "junit-jupiter-api-5")),
          groovy(
            """
              import org.junit.jupiter.api.Test
              import org.junit.jupiter.api.extension.TestInstancePostProcessor
              import org.junit.jupiter.api.extension.ExtendWith

              class Ctx implements TestInstancePostProcessor {
              }

              @ExtendWith(Ctx)
              class ATests {
                  @Test
                  void contextLoads() {
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsEmptyTestOfGroovyNestedExtension() {
        //language=groovy
        rewriteRun(
          spec -> spec.parser(GroovyParser.builder().classpathFromResource(new InMemoryExecutionContext(),
            "junit-jupiter-api-5")),
          groovy(
            """
              import org.junit.jupiter.api.Test
              import org.junit.jupiter.api.extension.TestInstancePostProcessor
              import org.junit.jupiter.api.extension.ExtendWith

              class Outer {
                  static class Ctx implements TestInstancePostProcessor {
                  }
              }

              @ExtendWith(Outer.Ctx)
              class ATests {
                  @Test
                  void contextLoads() {
                  }
              }
              """
          )
        );
    }

    @Test
    void removesEmptyTestOfClassNestedInInterface() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.Extension;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(ATests.Ctx.class)
              interface ATests {
                  class Ctx implements Extension {
                  }
                  class Inner {
                      @Test
                      void nothing() {
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.Extension;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(ATests.Ctx.class)
              interface ATests {
                  class Ctx implements Extension {
                  }
                  class Inner {
                  }
              }
              """
          )
        );
    }

    @Test
    void removesEmptyTestWhoseSuperclassHasCheckingExtension() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.AfterAllCallback;
              import org.junit.jupiter.api.extension.BeforeAllCallback;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.junit.jupiter.api.extension.ExtensionContext;

              @ExtendWith(LeakCheckExtension.class)
              abstract class TestBase {
              }

              class LeakCheckExtension implements BeforeAllCallback, AfterAllCallback {
                  public void beforeAll(ExtensionContext context) {
                  }

                  public void afterAll(ExtensionContext context) {
                  }
              }
              """
          ),
          java(
            """
              import org.junit.jupiter.api.Test;

              class PositionTest extends TestBase {
                  @Test
                  void nextSequence() {
                  }
              }
              """,
            """
              class PositionTest extends TestBase {
              }
              """
          )
        );
    }

    @Test
    void removesEmptyTestOfAnnotationRegisteringCheck() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.AfterEachCallback;
              import org.junit.jupiter.api.extension.BeforeEachCallback;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.junit.jupiter.api.extension.ExtensionContext;

              import java.lang.annotation.Retention;
              import java.lang.annotation.RetentionPolicy;

              @Retention(RetentionPolicy.RUNTIME)
              @ExtendWith(ChannelsClosedExtension.class)
              @interface AssertChannelsClosed {
              }

              class ChannelsClosedExtension implements BeforeEachCallback, AfterEachCallback {
                  public void beforeEach(ExtensionContext context) {
                  }

                  public void afterEach(ExtensionContext context) {
                  }
              }
              """
          ),
          java(
            """
              import org.junit.jupiter.api.Test;

              @AssertChannelsClosed
              class FactoryTests {
                  @Test
                  void createWhenHasRef() {
                  }
              }
              """,
            """
              @AssertChannelsClosed
              class FactoryTests {
              }
              """
          )
        );
    }

    @Test
    void removesEmptyTestOfCustomRunner() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.runners.BlockJUnit4ClassRunner;
              import org.junit.runners.model.InitializationError;

              public class RetryRunner extends BlockJUnit4ClassRunner {
                  public RetryRunner(Class<?> testClass) throws InitializationError {
                      super(testClass);
                  }
              }
              """
          ),
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;

              @RunWith(RetryRunner.class)
              public class RetryTests {
                  @Test
                  public void nothing() {
                  }
              }
              """,
            """
              import org.junit.runner.RunWith;

              @RunWith(RetryRunner.class)
              public class RetryTests {
              }
              """
          )
        );
    }

    @Test
    void keepsEmptyTestOfFrameworkThatStartsApplication() {
        //language=java
        rewriteRun(
          java(
            """
              package io.quarkus.test.junit;

              import org.junit.jupiter.api.extension.BeforeAllCallback;
              import org.junit.jupiter.api.extension.ExtensionContext;

              public class QuarkusIntegrationTestExtension implements BeforeAllCallback {
                  public void beforeAll(ExtensionContext context) {
                  }
              }
              """
          ),
          java(
            """
              import io.quarkus.test.junit.QuarkusIntegrationTestExtension;
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(QuarkusIntegrationTestExtension.class)
              class ApplicationIT {
                  @Test
                  void applicationStarts() {
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsEmptyTestOfSubclassOfSpringRunner() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
              import org.junit.runners.model.InitializationError;

              public class CamelSpringRunner extends SpringJUnit4ClassRunner {
                  public CamelSpringRunner(Class<?> clazz) throws InitializationError {
                      super(clazz);
                  }
              }
              """
          ),
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.springframework.test.context.ContextConfiguration;

              @RunWith(CamelSpringRunner.class)
              @ContextConfiguration("/context.xml")
              public class RouteTest {
                  @Test
                  public void contextLoads() {
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsEmptyTestOfDropwizardApp() {
        //language=java
        rewriteRun(
          java(
            """
              package io.dropwizard.testing.junit5;

              import org.junit.jupiter.api.extension.BeforeAllCallback;
              import org.junit.jupiter.api.extension.ExtensionContext;

              public class DropwizardExtensionsSupport implements BeforeAllCallback {
                  public void beforeAll(ExtensionContext context) {
                  }
              }
              """
          ),
          java(
            """
              import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(DropwizardExtensionsSupport.class)
              class AppTest {
                  @Test
                  void applicationStarts() {
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsEmptyTestOfWeld() {
        //language=java
        rewriteRun(
          java(
            """
              package org.jboss.weld.junit5.auto;

              import org.junit.jupiter.api.extension.BeforeAllCallback;
              import org.junit.jupiter.api.extension.ExtensionContext;

              public class WeldJunit5AutoExtension implements BeforeAllCallback {
                  public void beforeAll(ExtensionContext context) {
                  }
              }
              """
          ),
          java(
            """
              import org.jboss.weld.junit5.auto.WeldJunit5AutoExtension;
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(WeldJunit5AutoExtension.class)
              class BeansTest {
                  @Test
                  void deploymentIsValid() {
                  }
              }
              """
          )
        );
    }
}
