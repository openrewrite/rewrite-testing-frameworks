/*
 * Copyright 2022 the original author or authors.
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
package org.openrewrite.java.testing.cucumber;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.*;

@Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/264")
class CucumberAnnotationToSuiteTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CucumberAnnotationToSuite())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "cucumber-junit-platform-engine-7.11.+", "junit-platform-suite-api-1.9.+"));
    }

    @Test
    void shouldReplaceCucumberAnnotationWithSuiteWithSelectedClasspathResource() {
        //language=java
        rewriteRun(
          java(
            """
              package com.example.app;

              import io.cucumber.junit.platform.engine.Cucumber;

              @Cucumber
              public class CucumberJava8Definitions {
              }
              """,
            """
              package com.example.app;

              import org.junit.platform.suite.api.SelectClasspathResource;
              import org.junit.platform.suite.api.Suite;

              @Suite
              @SelectClasspathResource("com/example/app")
              public class CucumberJava8Definitions {
              }
              """
          )
        );
    }
}
