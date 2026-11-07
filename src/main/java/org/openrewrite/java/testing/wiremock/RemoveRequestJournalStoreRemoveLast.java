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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.search.DeclaresMethod;
import org.openrewrite.java.tree.J;

import java.util.concurrent.atomic.AtomicBoolean;

public class RemoveRequestJournalStoreRemoveLast extends Recipe {

    private static final MethodMatcher REMOVE_LAST =
            new MethodMatcher("com.github.tomakehurst.wiremock.store.RequestJournalStore removeLast()", true);

    private static final AnnotationMatcher OVERRIDE = new AnnotationMatcher("@java.lang.Override");

    @Getter
    final String displayName = "Remove `removeLast()` from custom WireMock request journal stores";

    @Getter
    final String description = "WireMock 4 dropped `removeLast()` from `RequestJournalStore`, as the request journal " +
            "no longer trims its store after every addition; a store wanting a maximum number of entries now evicts " +
            "the oldest ones itself. Remove the implementations, which no longer override anything, or where the " +
            "same file still calls `removeLast()` keep the method and only drop its `@Override`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new DeclaresMethod<>(REMOVE_LAST),
                new JavaIsoVisitor<ExecutionContext>() {

                    @Override
                    public J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                        if (!REMOVE_LAST.matches(m.getMethodType())) {
                            return m;
                        }
                        if (!calledInThisFile()) {
                            return null;
                        }
                        return (J.MethodDeclaration) new RemoveAnnotationVisitor(OVERRIDE)
                                .visitNonNull(m, ctx, getCursor().getParentOrThrow());
                    }

                    private boolean calledInThisFile() {
                        return new JavaIsoVisitor<AtomicBoolean>() {
                            // A `super.removeLast()` inside an implementation goes away along with it
                            @Override
                            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, AtomicBoolean found) {
                                return REMOVE_LAST.matches(method.getMethodType()) ? method :
                                        super.visitMethodDeclaration(method, found);
                            }

                            @Override
                            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean found) {
                                if (REMOVE_LAST.matches(method)) {
                                    found.set(true);
                                }
                                return super.visitMethodInvocation(method, found);
                            }
                        }.reduce(getCursor().firstEnclosingOrThrow(J.CompilationUnit.class), new AtomicBoolean()).get();
                    }
                });
    }
}
