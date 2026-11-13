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

class RemoveRequestJournalStoreRemoveLastTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveRequestJournalStoreRemoveLast())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "wiremock-3.13"));
    }

    @DocumentExample
    @Test
    void implementationIsRemoved() {
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
              }
              """
          )
        );
    }

    @Test
    void implementationOfTheInterfaceIsRemoved() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.tomakehurst.wiremock.store.RequestJournalStore;

              abstract class CustomStore implements RequestJournalStore {
                  @Override
                  public void removeLast() {
                  }

                  public void flush() {
                  }
              }
              """,
            """
              import com.github.tomakehurst.wiremock.store.RequestJournalStore;

              abstract class CustomStore implements RequestJournalStore {
                  public void flush() {
                  }
              }
              """
          )
        );
    }

    @Test
    void stillCalledSoOnlyOverrideIsDropped() {
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
