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
import org.openrewrite.maven.MavenVisitor;
import org.openrewrite.maven.tree.Maven;

public class AddMockitoDependency extends Recipe {

    @Override
    public String getDisplayName() {
        return "Add Mockito Dependency";
    }

    @Override
    public String getDescription() {
        return "Adds Mockito Dependency";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AddMockitoDependencyVisitor();
    }

    public static class AddMockitoDependencyVisitor extends MavenVisitor {

        @Override
        public Maven visitMaven(Maven maven, ExecutionContext executionContext) {
            if (Boolean.TRUE.equals(executionContext.pollMessage(MockitoRunnerToMockitoExtension.MOCKITO_ANNOTATION_REPLACED_KEY))) {
                maybeAddDependency("org.mockito", "mockito-junit-jupiter", "3.x", null, "test", null);
            }
            return maven;
        }
    }
}
