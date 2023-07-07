/*
 * Copyright 2023 the original author or authors.
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

import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.tree.*;

import java.util.*;
import java.util.stream.Collectors;

public class RemoveTryCatchBlocksFromUnitTests extends Recipe {
    private static final MethodMatcher ASSERT_FAIL_MATCHER = new MethodMatcher("org.junit.Assert fail(..)");
    private static final List<String> TEST_PATTERNS = Arrays.asList(
            "@org.junit.jupiter.api.Test",
            "@org.junit.jupiter.api.RepeatedTest",
            "@org.junit.jupiter.params.ParameterizedTest"
    );

    private static final List<AnnotationMatcher> MATCHERS = TEST_PATTERNS.stream().map(AnnotationMatcher::new).collect(Collectors.toList());

    @Override
    public String getDisplayName() {
        return "Unit test should throw exceptions instead of using `try-catch` blocks";
    }

    @Override
    public String getDescription() {
        return "When the code under test in a unit test throws an exception, the test itself fails. " +
                "Therefore, there is no need to surround the tested code with a try-catch.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-3658");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new RemoveTryCatchBlocksFromUnitsTestsVisitor();
    }

    private static class RemoveTryCatchBlocksFromUnitsTestsVisitor extends JavaVisitor<ExecutionContext> {
        private final String KEY = "CHANGES_NEEDED";
        @Override
        public J visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext ctx) {
            J.MethodDeclaration m = (J.MethodDeclaration) super.visitMethodDeclaration(md, ctx);
            if (md.getBody() == null) {
                return md;
            }

            List<Statement> statements = md.getBody().getStatements();
            // check if method is Unit test
            List<J.Annotation> annotations = md.getAllAnnotations();
            if (annotations.stream().noneMatch(ann -> MATCHERS.stream().anyMatch(matcher -> matcher.matches(ann)))) {
                return md;
            }

            // find try catch block
            List<J.Try> tryStatements = statements.stream()
                    .filter(s -> s instanceof J.Try)
                    .map(s -> (J.Try) s)
                    // not sure how to handle try-catch blocks with multiple catches
                    .filter(t -> t.getCatches().size() == 1)
                    .collect(Collectors.toList());
            if (tryStatements.size() == 0) {
                return md;
            }

            getCursor().putMessage(KEY, md);

            return (J.MethodDeclaration) super.visitMethodDeclaration(md, ctx);
        }

        @Override
        public J visitTry(J.Try try_, ExecutionContext ctx) {
            J.Try t = (J.Try) super.visitTry(try_, ctx);

            Cursor c = getCursor().dropParentWhile(is -> is instanceof J.Block ||
                                                         !(is instanceof Tree) ||
                                                         is instanceof J.Try);

            if (c.getMessage(KEY) != null) {
                if (try_.getCatches().get(0).getBody().getStatements().stream().noneMatch(
                        s -> s instanceof J.MethodInvocation && ASSERT_FAIL_MATCHER.matches((J.MethodInvocation) s)
                )) {
                    return try_;
                }

                // replace method body
                maybeRemoveImport("org.junit.Assert");
                maybeAddImport("org.junit.jupiter.api.Assertions");
                return JavaTemplate.builder("Assertions.assertDoesNotThrow(() -> { #{any()} })")
                        .imports("org.junit.jupiter.api.Assertions")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5.9"))
                        .build()
                        .apply(getCursor(), t.getCoordinates().replace(), t.getBody().getStatements().get(0));
            }

            return t;
        }
    }
}