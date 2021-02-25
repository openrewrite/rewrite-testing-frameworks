/*
 * Copyright 2020 the original author or authors.
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
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.FindTypes;
import org.openrewrite.java.tree.J;

public class FindJUnit5 extends Recipe {

    public static final String JUNIT_REFS_EXIST_KEY = "junitReferencesExist";

    @Override
    public String getDisplayName() {
        return "Find JUnit 5";
    }

    @Override
    public String getDescription() {
        return "Find JUnit 5 References";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new FindJUnitVisitor();
    }

    private static class FindJUnitVisitor extends JavaVisitor<ExecutionContext> {
        @Override
        public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            boolean junitReferencesExist =
                            !FindTypes.find(cu, "org.junit.jupiter.api.Test").isEmpty();
            ctx.putMessage(JUNIT_REFS_EXIST_KEY, junitReferencesExist);
            return cu;
        }
    }
}
