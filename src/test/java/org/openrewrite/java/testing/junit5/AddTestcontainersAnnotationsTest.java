/*
 * Copyright 2025 the original author or authors.
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

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class AddTestcontainersAnnotationsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddTestcontainersAnnotations())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "junit-4", "testcontainers-1", "junit-jupiter-1"));
    }

    @DocumentExample
    @Test
    void convertsSingleGenericContainerRule() {
        rewriteRun(
          // language=java
          java(
            // before
            """
              import org.junit.Rule;
              import org.junit.Test;
              import org.testcontainers.containers.GenericContainer;

              class MyTest {
                  @Rule
                  public GenericContainer<?> myContainer = new GenericContainer<>("redis:latest");
              }
              """,
            // after
            """
              import org.junit.Test;
              import org.testcontainers.containers.GenericContainer;
              import org.testcontainers.junit.jupiter.Container;
              import org.testcontainers.junit.jupiter.Testcontainers;

              @Testcontainers
              class MyTest {
                  @Container
                  public GenericContainer<?> myContainer = new GenericContainer<>("redis:latest");
              }
              """
          )
        );
    }

    @Test
    void convertsMultipleContainerRules() {
        rewriteRun(
          // language=java
          java(
            // before
            """
              import org.junit.Rule;
              import org.testcontainers.containers.GenericContainer;

              class MyTest {
                  @Rule
                  public GenericContainer<?> redis = new GenericContainer<>("redis:latest");

                  @Rule
                  public GenericContainer<?> postgres = new GenericContainer<>("postgres:latest");
              }
              """,
            // after
            """
              import org.testcontainers.containers.GenericContainer;
              import org.testcontainers.junit.jupiter.Container;
              import org.testcontainers.junit.jupiter.Testcontainers;

              @Testcontainers
              class MyTest {
                  @Container
                  public GenericContainer<?> redis = new GenericContainer<>("redis:latest");

                  @Container
                  public GenericContainer<?> postgres = new GenericContainer<>("postgres:latest");
              }
              """
          )
        );
    }

    @Test
    void convertsMixedRuleAndClassRule() {
        rewriteRun(
          // language=java
          java(
            // before
            """
              import org.junit.ClassRule;
              import org.junit.Rule;
              import org.testcontainers.containers.GenericContainer;

              class MyTest {
                  @ClassRule
                  public static GenericContainer<?> redis = new GenericContainer<>("redis:latest");

                  @Rule
                  public GenericContainer<?> postgres = new GenericContainer<>("postgres:latest");
              }
              """,
            // after
            """
              import org.testcontainers.containers.GenericContainer;
              import org.testcontainers.junit.jupiter.Container;
              import org.testcontainers.junit.jupiter.Testcontainers;

              @Testcontainers
              class MyTest {
                  @Container
                  public static GenericContainer<?> redis = new GenericContainer<>("redis:latest");

                  @Container
                  public GenericContainer<?> postgres = new GenericContainer<>("postgres:latest");
              }
              """
          )
        );
    }

    @Test
    void convertsSubclassedContainerRule() {
        rewriteRun(
          // language=java
          java(
            """
              package com.uber.fievel.testing.redis;

              import org.testcontainers.containers.GenericContainer;

              public class UberRedisContainer<SELF extends UberRedisContainer<SELF>>
                      extends GenericContainer<SELF> {
              }
              """
          ),
          // language=java
          java(
            // before
            """
              import com.uber.fievel.testing.redis.UberRedisContainer;
              import org.junit.ClassRule;
              import org.junit.rules.TestRule;

               class MyTest {
                   @ClassRule
                   public static UberRedisContainer redisContainer = new UberRedisContainer();
               }
              """,
            // after
            """
              import com.uber.fievel.testing.redis.UberRedisContainer;
              import org.junit.rules.TestRule;
              import org.testcontainers.junit.jupiter.Container;
              import org.testcontainers.junit.jupiter.Testcontainers;

              @Testcontainers
              class MyTest {
                   @Container
                   public static UberRedisContainer redisContainer = new UberRedisContainer();
               }
              """
          )
        );
    }

    @Test
    void ignoresNonGenericContainerRule() {
        rewriteRun(
          // language=java
          java(
            // before
            """
              import org.junit.Rule;
              import org.junit.rules.TemporaryFolder;

              class MyTest {
                  @Rule
                  public TemporaryFolder tempFolder = new TemporaryFolder();
              }
              """
          )
        );
    }

    @Test
    void ignoresNonRuleGenericContainer() {
        rewriteRun(
          java(
            // language=java
            """
              import org.testcontainers.containers.GenericContainer;

              class MyTest {
                  public static GenericContainer<?> c_stat = new GenericContainer<>("redis:latest");
                  public GenericContainer<?> c = new GenericContainer<>("redis:latest");
              }
              """
          )
        );
    }

    @Test
    void isIdempotentForAlreadyMigratedClasses() {
        rewriteRun(
          // language=java
          java(
            // before
            """
              import org.testcontainers.containers.GenericContainer;
              import org.testcontainers.junit.jupiter.Container;
              import org.testcontainers.junit.jupiter.Testcontainers;

              @Testcontainers
              class MyTest {
                  @Container
                  public GenericContainer<?> myContainer = new GenericContainer<>("redis:latest");
              }
              """
          )
        );
    }

    @Test
    void handlesPartiallyMigratedClass() {
        rewriteRun(
          // language=java
          java(
            // before
            """
              import org.junit.Rule;
              import org.testcontainers.containers.GenericContainer;
              import org.testcontainers.junit.jupiter.Testcontainers;

              @Testcontainers
              class MyTest {
                  @Rule
                  public GenericContainer<?> redis = new GenericContainer<>("redis:latest");
              }
              """,
            // after
            """
              import org.testcontainers.containers.GenericContainer;
              import org.testcontainers.junit.jupiter.Container;
              import org.testcontainers.junit.jupiter.Testcontainers;

              @Testcontainers
              class MyTest {
                  @Container
                  public GenericContainer<?> redis = new GenericContainer<>("redis:latest");
              }
              """
          )
        );
    }

    @Test
    void replacesOnlyRuleOrClassRuleAnnotation() {
        rewriteRun(
          // language=java
          java(
            // before
            """
              import org.junit.Rule;
              import org.testcontainers.containers.GenericContainer;

              class MyTest {
                  @Rule
                  @Deprecated
                  public GenericContainer<?> redis = new GenericContainer<>("redis:latest");
              }
              """,
            // after
            """
              import org.testcontainers.containers.GenericContainer;
              import org.testcontainers.junit.jupiter.Container;
              import org.testcontainers.junit.jupiter.Testcontainers;

              @Testcontainers
              class MyTest {
                  @Container
                  @Deprecated
                  public GenericContainer<?> redis = new GenericContainer<>("redis:latest");
              }
              """
          )
        );
    }

    @Test
    void modifiesOnlyTestClass() {
        rewriteRun(
          // language=java
          java(
            """
              import org.testcontainers.containers.GenericContainer;
              import org.junit.Rule;

              class MyTest {
                @Rule
                public GenericContainer<?> myContainer = new GenericContainer<>("redis:latest");

                class MyInnerClass {
                }
              }
              """,
            """
              import org.testcontainers.containers.GenericContainer;
              import org.testcontainers.junit.jupiter.Container;
              import org.testcontainers.junit.jupiter.Testcontainers;

              @Testcontainers
              class MyTest {
                  @Container
                  public GenericContainer<?> myContainer = new GenericContainer<>("redis:latest");

                class MyInnerClass {
                }
              }
              """
          )
        );
    }
}
