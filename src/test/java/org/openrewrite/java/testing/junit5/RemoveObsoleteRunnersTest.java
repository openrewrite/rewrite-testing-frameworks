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
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.openrewrite.java.Assertions.java;

class RemoveObsoleteRunnersTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4.13.+"))
          .recipe(new RemoveObsoleteRunners(
            List.of(
              "org.junit.runners.JUnit4",
              "org.junit.runners.BlockJUnit4ClassRunner"
            )
          ));
    }

    @Test
    void removesRunWithJunit4() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.runner.RunWith;
              import org.junit.runners.JUnit4;
              
              @RunWith(JUnit4.class)
              public class Foo {
              }
              """,
            """
              public class Foo {
              }
              """
          )
        );
    }

    @Test
    void removeRunWithBlockRunner() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.runner.RunWith;
              import org.junit.runners.BlockJUnit4ClassRunner;
              
              @RunWith(BlockJUnit4ClassRunner.class)
              public class Foo {
              }
              """,
            """
              public class Foo {
              }
              """
          )
        );
    }
}
