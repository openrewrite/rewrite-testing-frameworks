package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.maven.MavenVisitor;
import org.openrewrite.maven.tree.Maven;

public class AddMockitoDependency extends Recipe {

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
