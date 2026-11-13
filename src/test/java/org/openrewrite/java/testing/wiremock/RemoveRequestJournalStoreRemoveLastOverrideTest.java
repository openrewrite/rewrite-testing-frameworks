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
package org.openrewrite.java.testing.wiremock;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveRequestJournalStoreRemoveLastOverrideTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveRequestJournalStoreRemoveLastOverride())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "wiremock-3.13"));
    }

    @DocumentExample
    @Test
    void overrideAndSuperCallAreDropped() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.store.InMemoryRequestJournalStore;

              class AuditingStore extends InMemoryRequestJournalStore {
                  int evictions;

                  @Override
                  public void removeLast() {
                      evictions++;
                      super.removeLast();
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.store.InMemoryRequestJournalStore;

              class AuditingStore extends InMemoryRequestJournalStore {
                  int evictions;

                  // TODO WireMock 4 no longer calls `removeLast()`, so evict surplus entries from `add(..)` instead
                  public void removeLast() {
                      evictions++;
                  }
              }
              """
          )
        );
    }

    @Test
    void implementationOfTheInterface() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.store.RequestJournalStore;

              abstract class CustomStore implements RequestJournalStore {
                  @Override
                  public void removeLast() {
                  }

                  public void trim() {
                      removeLast();
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.store.RequestJournalStore;

              abstract class CustomStore implements RequestJournalStore {
                  // TODO WireMock 4 no longer calls `removeLast()`, so evict surplus entries from `add(..)` instead
                  public void removeLast() {
                  }

                  public void trim() {
                      removeLast();
                  }
              }
              """
          )
        );
    }

    @Test
    void implementationWithoutOverride() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.store.RequestJournalStore;

              abstract class CustomStore implements RequestJournalStore {
                  public void removeLast() {
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.store.RequestJournalStore;

              abstract class CustomStore implements RequestJournalStore {
                  // TODO WireMock 4 no longer calls `removeLast()`, so evict surplus entries from `add(..)` instead
                  public void removeLast() {
                  }
              }
              """
          )
        );
    }

    @Test
    void todoGoesBetweenJavadocAndMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.store.RequestJournalStore;

              abstract class CustomStore implements RequestJournalStore {
                  /**
                   * Drops the oldest entry.
                   */
                  @Override
                  public void removeLast() {
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.store.RequestJournalStore;

              abstract class CustomStore implements RequestJournalStore {
                  /**
                   * Drops the oldest entry.
                   */
                  // TODO WireMock 4 no longer calls `removeLast()`, so evict surplus entries from `add(..)` instead
                  public void removeLast() {
                  }
              }
              """
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void unrelatedRemoveLast() {
            rewriteRun(
              //language=java
              java(
                """
                  class Queue {
                      public void removeLast() {
                      }
                  }
                  """
              )
            );
        }
    }
}
