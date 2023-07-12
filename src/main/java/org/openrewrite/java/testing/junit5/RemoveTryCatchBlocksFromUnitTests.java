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
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

import java.util.*;

public class RemoveTryCatchBlocksFromUnitTests extends Recipe {
    private static final MethodMatcher ASSERT_FAIL_MATCHER = new MethodMatcher("org.junit.Assert fail(..)");
    private static final MethodMatcher GET_MESSAGE_MATCHER = new MethodMatcher("java.lang.Throwable getMessage()");

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
        return Preconditions.check(new UsesMethod<>("org.junit.Assert fail(..)", false), new RemoveTryCatchBlocksFromUnitsTestsVisitor());
    }

    private static class RemoveTryCatchBlocksFromUnitsTestsVisitor extends JavaVisitor<ExecutionContext> {
        @Override
        public J visitTry(J.Try try_, ExecutionContext ctx) {
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
            if (!ASSERT_FAIL_MATCHER.matches(failCall)) {
                return try_;
            }

            // should only pass if there is 1 or 0 arguments
            if (failCall.getArguments().size() == 1) {
                Expression arg = failCall.getArguments().get(0);
                if (failCall.getArguments().get(0) instanceof J.MethodInvocation) {
                    if (!GET_MESSAGE_MATCHER.matches((J.MethodInvocation) arg)) {
                        return try_;
                    }
                }
                if (!TypeUtils.isString(arg.getType())) {
                    return try_;
                }
            }else if (!failCall.getArguments().isEmpty()) {
                return try_;
            }

            maybeRemoveImport("org.junit.Assert");
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