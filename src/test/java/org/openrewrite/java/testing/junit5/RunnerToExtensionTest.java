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

class RunnerToExtensionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4.13.2", "mockito-all-1.10.19"))
          .recipe(new RunnerToExtension(
              List.of("org.mockito.runners.MockitoJUnitRunner"),
              "org.mockito.junit.jupiter.MockitoExtension"
            )
          );
    }

    @Test
    void mockito() {
        rewriteRun(
          //language=java
          java(
            """
                  import org.junit.runner.RunWith;
                  import org.mockito.runners.MockitoJUnitRunner;
                  
                  @RunWith(MockitoJUnitRunner.class)
                  public class MyTest {
                  }
              """,
            """
                  import org.junit.jupiter.api.extension.ExtendWith;
                  import org.mockito.junit.jupiter.MockitoExtension;
                  
                  @ExtendWith(MockitoExtension.class)
                  public class MyTest {
                  }
              """
          )
        );
    }
}
