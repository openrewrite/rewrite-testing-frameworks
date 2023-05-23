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
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.shaded.jgit.transport.URIish;
import org.openrewrite.template.SourceTemplate;

import java.util.UUID;

public class MigrateFromHamcrest extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate from Hamcrest Matchers to JUnit5";
    }

    @Override
    public String getDescription() {
        return "This recipe will migrate all Hamcrest Matchers to JUnit5 assertions.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MigrationFromHamcrestVisitor();
    }

    private static class MigrationFromHamcrestVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, executionContext);
            //desired matcher for assertThat
            MethodMatcher matcherAssertMatcher = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(..)");
            //primitive matcher
            MethodMatcher everythingMatcher = new MethodMatcher("*..* *(..)");
            //desired matcher for org.hamcrest.Matchers methods, for example equalTo()
            MethodMatcher matcherMatcher = new MethodMatcher("org.hamcrest.Matchers *(..)");

            //this never gets matched
            if (matcherAssertMatcher.matches(mi)) {
                int i = 0; //placeholder
            }
            //but neither does this
            if (everythingMatcher.matches(mi)) {
                int j = 0; //placeholder
            }
            return mi;
        }
    }
}
