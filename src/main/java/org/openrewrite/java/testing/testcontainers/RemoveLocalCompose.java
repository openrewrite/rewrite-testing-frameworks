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
package org.openrewrite.java.testing.testcontainers;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.RemoveMethodInvocationsVisitor;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.openrewrite.java.RemoveMethodInvocationsVisitor.isTrueArgument;

public class RemoveLocalCompose extends Recipe {

    private static final String DOCKER_COMPOSE_CONTAINER = "org.testcontainers.containers.DockerComposeContainer withLocalCompose(boolean)";
    private static final String COMPOSE_CONTAINER = "org.testcontainers.containers.ComposeContainer withLocalCompose(boolean)";

    @Getter
    final String displayName = "Remove `withLocalCompose(true)`";

    @Getter
    final String description = "Testcontainers 2 removed `withLocalCompose(boolean)` from `ComposeContainer`, which uses the local " +
            "Docker Compose binary when it is created without a `DockerImageName`. This removes `withLocalCompose(true)` " +
            "from `DockerComposeContainer` and `ComposeContainer`. Other arguments are left for a manual migration.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        Map<MethodMatcher, Predicate<List<Expression>>> matchers = new HashMap<>();
        matchers.put(new MethodMatcher(DOCKER_COMPOSE_CONTAINER, true), isTrueArgument());
        matchers.put(new MethodMatcher(COMPOSE_CONTAINER, true), isTrueArgument());
        return Preconditions.check(
                Preconditions.or(new UsesMethod<>(DOCKER_COMPOSE_CONTAINER, true), new UsesMethod<>(COMPOSE_CONTAINER, true)),
                new RemoveMethodInvocationsVisitor(matchers));
    }
}
