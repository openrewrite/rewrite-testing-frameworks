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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.MethodDeclaration;
import org.openrewrite.java.tree.J.Modifier.Type;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LifecycleNonPrivate extends Recipe {

    private static final List<String> ANNOTATION_TYPES = Arrays.asList(
            "org.junit.jupiter.api.AfterAll",
            "org.junit.jupiter.api.AfterEach",
            "org.junit.jupiter.api.BeforeAll",
            "org.junit.jupiter.api.BeforeEach");

    @SuppressWarnings("unchecked")
    private static final TreeVisitor<?, ExecutionContext> PRECONDITION =
            Preconditions.or(ANNOTATION_TYPES.stream().map(r -> new UsesType<>(r, false)).toArray(UsesType[]::new));

    @Override
    public String getDisplayName() {
        return "Make lifecycle methods non private";
    }

    @Override
    public String getDescription() {
        return "Make JUnit 5's `@AfterAll`, `@AfterEach`, `@BeforeAll` and `@BeforeEach` non private.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(PRECONDITION, new LifecycleNonPrivateVisitor());
    }

    private static class LifecycleNonPrivateVisitor extends JavaIsoVisitor<ExecutionContext> {
        final List<AnnotationMatcher> lifeCycleAnnotationMatchers = ANNOTATION_TYPES.stream()
                .map(annoFqn -> "@" + annoFqn).map(AnnotationMatcher::new).collect(Collectors.toList());

        @Override
        public J.MethodDeclaration visitMethodDeclaration(MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);

            if (J.Modifier.hasModifier(md.getModifiers(), Type.Private) &&
                    md.getLeadingAnnotations().stream().anyMatch(ann -> lifeCycleAnnotationMatchers.stream()
                    .anyMatch(matcher -> matcher.matches(ann)))) {
                return maybeAutoFormat(md,
                        md.withModifiers(ListUtils.map(md.getModifiers(),
                                modifier -> modifier.getType() == Type.Private ? null : modifier)),
                        ctx, getCursor().getParentOrThrow());
            }
            return md;
        }
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

}
