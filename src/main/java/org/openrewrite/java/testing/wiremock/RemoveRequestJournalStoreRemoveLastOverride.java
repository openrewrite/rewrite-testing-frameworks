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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.search.DeclaresMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.trait.Comments;

public class RemoveRequestJournalStoreRemoveLastOverride extends Recipe {

    private static final MethodMatcher REMOVE_LAST = new MethodMatcher("com.github.tomakehurst.wiremock.store.RequestJournalStore removeLast()", true);
    private static final MethodMatcher WIREMOCK_REMOVE_LAST = new MethodMatcher("com.github.tomakehurst.wiremock..* removeLast()");
    private static final AnnotationMatcher OVERRIDE = new AnnotationMatcher("@java.lang.Override");
    private static final String TODO = " TODO WireMock 4 no longer calls `removeLast()`, so evict surplus entries from `add(..)` instead";

    @Getter
    final String displayName = "Drop `@Override` from `removeLast()` in custom WireMock request journal stores";

    @Getter
    final String description = "WireMock 4 dropped `removeLast()` from `RequestJournalStore`, as the request journal no longer trims its store after every addition; a store wanting a maximum number of entries now evicts the oldest ones itself. Keep the implementations, which code may still call directly, but drop their `@Override` and any `super.removeLast()` delegation, and add a `TODO` to move the eviction into `add(..)`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new DeclaresMethod<>(REMOVE_LAST), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                if (!REMOVE_LAST.matches(m.getMethodType())) {
                    return m;
                }
                m = (J.MethodDeclaration) new RemoveAnnotationVisitor(OVERRIDE).visitNonNull(m, ctx, getCursor().getParentOrThrow());
                if (m.getBody() != null) {
                    m = m.withBody(m.getBody().withStatements(ListUtils.filter(m.getBody().getStatements(), s -> !isSuperRemoveLast(s))));
                }
                Comments comments = Comments.of(new Cursor(getCursor().getParentOrThrow(), m));
                return comments.hasComment(TODO) ? m : comments.comment(TODO, Comments.Placement.BEFORE, lastLineOf(m.getPrefix()));
            }

            private String lastLineOf(Space prefix) {
                String whitespace = prefix.getComments().isEmpty() ? prefix.getWhitespace() : prefix.getComments().get(prefix.getComments().size() - 1).getSuffix();
                int lineStart = whitespace.lastIndexOf('\n');
                return lineStart < 0 ? " " : whitespace.substring(lineStart);
            }

            private boolean isSuperRemoveLast(Statement statement) {
                if (!(statement instanceof J.MethodInvocation)) {
                    return false;
                }
                J.MethodInvocation invocation = (J.MethodInvocation) statement;
                return WIREMOCK_REMOVE_LAST.matches(invocation) && invocation.getSelect() instanceof J.Identifier && "super".equals(((J.Identifier) invocation.getSelect()).getSimpleName());
            }
        });
    }
}
