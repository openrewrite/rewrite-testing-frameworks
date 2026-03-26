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

class PowerMockRunnerDelegateToRunWithTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-4",
              "powermock-module-junit4"))
          .recipe(new PowerMockRunnerDelegateToRunWith());
    }

    @DocumentExample
    @Test
    void replacesDelegateWithRunWith() {
        //language=java
        rewriteRun(
          // Stub for PowerMockRunnerDelegate
          java(
            """
              package org.powermock.modules.junit4;

              import java.lang.annotation.*;

              @Retention(RetentionPolicy.RUNTIME)
              @Target(ElementType.TYPE)
              public @interface PowerMockRunnerDelegate {
                  Class<?> value();
              }
              """
          ),
          // Stub for a delegate runner
          java(
            """
              package org.springframework.test.context.junit4;

              public class SpringJUnit4ClassRunner extends org.junit.runner.Runner {
                  public SpringJUnit4ClassRunner(Class<?> clazz) {}
                  @Override public org.junit.runner.Description getDescription() { return null; }
                  @Override public void run(org.junit.runner.notification.RunNotifier notifier) {}
              }
              """
          ),
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.powermock.modules.junit4.PowerMockRunner;
              import org.powermock.modules.junit4.PowerMockRunnerDelegate;
              import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

              @RunWith(PowerMockRunner.class)
              @PowerMockRunnerDelegate(SpringJUnit4ClassRunner.class)
              public class MyTest {

                  @Test
                  public void testSomething() {
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

              @RunWith(SpringJUnit4ClassRunner.class)
              public class MyTest {

                  @Test
                  public void testSomething() {
                  }
              }
              """
          )
        );
    }

    @Test
    void removesRunWithPowerMockRunnerWithoutDelegate() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.powermock.modules.junit4.PowerMockRunner;

              @RunWith(PowerMockRunner.class)
              public class MyTest {

                  @Test
                  public void testSomething() {
                  }
              }
              """,
            """
              import org.junit.Test;


              public class MyTest {

                  @Test
                  public void testSomething() {
                  }
              }
              """
          )
        );
    }
}
