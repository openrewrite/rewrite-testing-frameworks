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
package org.openrewrite.java.testing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.testing.cleanup.AssertionsArgumentOrder;
import org.openrewrite.java.testing.junit5.AddMissingNested;
import org.openrewrite.java.testing.junit5.LifecycleNonPrivate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThatCode;

class PreconditionConcurrencyTest {

    private static final int THREADS = 8;
    private static final int ITERATIONS = 20_000;

    static Stream<Recipe> recipes() {
        return Stream.of(new AssertionsArgumentOrder(), new AddMissingNested(), new LifecycleNonPrivate());
    }

    @ParameterizedTest
    @MethodSource("recipes")
    void visitorsCanRunConcurrently(Recipe recipe) throws Exception {
        // given
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);
        List<SourceFile> sources = JavaParser.fromJavaVersion().build()
          .parse(ctx, "class A {}", "class B {}", "class C {}", "class D {}")
          .collect(toList());
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);

        // when
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < THREADS; t++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < ITERATIONS; i++) {
                        recipe.getVisitor().visit(sources.get(i % sources.size()), new InMemoryExecutionContext());
                    }
                    return null;
                }));
            }
            start.countDown();

            // then
            for (Future<?> future : futures) {
                assertThatCode(future::get).doesNotThrowAnyException();
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
