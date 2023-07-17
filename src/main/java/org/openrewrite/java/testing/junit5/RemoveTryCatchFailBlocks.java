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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.Collections;
import java.util.Set;

public class RemoveTryCatchFailBlocks extends Recipe {
    private static final MethodMatcher ASSERT_FAIL_NO_ARG = new MethodMatcher("org.junit.jupiter.api.Assertions fail(..)");
    private static final MethodMatcher ASSERT_FAIL_STRING_ARG = new MethodMatcher("org.junit.jupiter.api.Assertions fail(String)");
    private static final MethodMatcher GET_MESSAGE_MATCHER = new MethodMatcher("java.lang.Throwable getMessage()");

    @Override
    public String getDisplayName() {
        return "Replace `fail()` in `try-catch` blocks with `Assertions.assertDoesNotThrow(() -> { ... })`";
    }

    @Override
    public String getDescription() {
        return "Replace `try-catch` blocks where `catch` merely contains a `fail(..)` statement with " +
               "`Assertions.assertDoesNotThrow(() -> { ... })`.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-3658");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>("org.junit.jupiter.api.Assertions fail(..)", false), new RemoveTryCatchBlocksFromUnitsTestsVisitor());
    }

    private static class RemoveTryCatchBlocksFromUnitsTestsVisitor extends JavaVisitor<ExecutionContext> {
        @Override
        public J visitTry(J.Try jtry, ExecutionContext ctx) {
            J.Try try_ = (J.Try) super.visitTry(jtry, ctx);
            // only one catch block, such that we know it's safe to apply this recipe, and doesn't have resources
            if (try_.getResources() != null || try_.getCatches().size() != 1) {
                return try_;
            }

            /*
            Only one statement in the catch block, which is a fail(), with no or a simple String argument.
            We would not want to convert for instance fail(cleanUpAndReturnMessage()) might still have side
            effects that we don't want to remove.
             */
            J.Try.Catch catchBlock = try_.getCatches().get(0);
            if (catchBlock.getBody().getStatements().size() != 1) {
                return try_;
            }
            Statement statement = catchBlock.getBody().getStatements().get(0);
            if (!(statement instanceof J.MethodInvocation)) {
                return try_;
            }
            J.MethodInvocation failCall = (J.MethodInvocation) statement;
            if (failCall.getArguments().get(0) instanceof J.Binary) {
                J.Binary binaryArg = (J.Binary)failCall.getArguments().get(0);
                // check if either side is a method
                if (binaryArg.getLeft() instanceof J.MethodInvocation || binaryArg.getRight() instanceof J.MethodInvocation) {
                    if (!GET_MESSAGE_MATCHER.matches(binaryArg.getLeft()) && binaryArg.getRight() instanceof J.Literal) {
                        return try_;
                    }else if (!GET_MESSAGE_MATCHER.matches(binaryArg.getRight()) && binaryArg.getLeft() instanceof J.Literal) {
                        return try_;
                    }
                }else if (!(binaryArg.getRight() instanceof J.Literal) || !(binaryArg.getLeft() instanceof J.Literal)) {
                    return try_;
                }
            }

            if (!ASSERT_FAIL_NO_ARG.matches(failCall) || !ASSERT_FAIL_STRING_ARG.matches(failCall)) {
                return try_;
            }

            // only valid method that returns string should be getMessage()
            if (ASSERT_FAIL_STRING_ARG.matches(failCall)) {
                Expression failCallString = failCall.getArguments().get(0);
                if (failCallString instanceof J.MethodInvocation && !GET_MESSAGE_MATCHER.matches((J.MethodInvocation) failCallString)) {
                    return try_;
                }
                if (failCallString instanceof J.Literal) {
                    // Retain the fail(String) call argument
                    maybeAddImport("org.junit.jupiter.api.Assertions");
                    return JavaTemplate.builder("Assertions.assertDoesNotThrow(() -> #{any()}, #{any(String)})")
                            .contextSensitive()
                            .imports("org.junit.jupiter.api.Assertions")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5.9"))
                            .build()
                            .apply(getCursor(), try_.getCoordinates().replace(), try_.getBody(), failCallString);
                }
            }

            maybeAddImport("org.junit.jupiter.api.Assertions");
            return JavaTemplate.builder("Assertions.assertDoesNotThrow(() -> #{any()})")
                    .contextSensitive()
                    .imports("org.junit.jupiter.api.Assertions")
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5.9"))
                    .build()
                    .apply(getCursor(), try_.getCoordinates().replace(), try_.getBody());
        }
    }
}