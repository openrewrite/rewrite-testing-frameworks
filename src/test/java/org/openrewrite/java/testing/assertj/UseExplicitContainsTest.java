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
package org.openrewrite.java.testing.assertj;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("ConstantConditions")
class UseExplicitContainsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9+", "assertj-core-3.24+"))
          .recipe(new UseExplicitContains());
    }

    @DocumentExample
    @Test
    void containsAndIsTrueBecomeContains() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Collection;
              import java.util.ArrayList;
              
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              public class MyTest {
              
                  @Test
                  public void test() {
                      Collection<String> collection = new ArrayList<>();
                      collection.add("3");
                      assertThat(collection.contains("3")).isTrue();
                  }
              }
              """,
            """
              import java.util.Collection;
              import java.util.ArrayList;
              
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              public class MyTest {
              
                  @Test
                  public void test() {
                      Collection<String> collection = new ArrayList<>();
                      collection.add("3");
                      assertThat(collection).contains("3");
                  }
              }
              """
          )
        );
    }

    @Test
    void containsAndIsFalseBecomeDoesNotContain() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Collection;
              import java.util.ArrayList;
              
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              public class MyTest {
              
                  @Test
                  public void test() {
                      Collection<String> collection = new ArrayList<>();
                      assertThat(collection.contains("3")).isFalse();
                  }
              }
              """,
            """
              import java.util.Collection;
              import java.util.ArrayList;
              
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              public class MyTest {
              
                  @Test
                  public void test() {
                      Collection<String> collection = new ArrayList<>();
                      assertThat(collection).doesNotContain("3");
                  }
              }
              """
          )
        );
    }

    @Test
    void IsFalseOrIsTrueWithoutContainsAreNotAffectected() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Collection;
              import java.util.ArrayList;
              
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              public class MyTest {
              
                  @Test
                  public void test() {
                      Collection<?> collection = new ArrayList<>();
                      assertThat(collection.isEmpty()).isTrue();
                      assertThat(!collection.isEmpty()).isFalse();
                  }
              }
              """)
        );
    }
}
