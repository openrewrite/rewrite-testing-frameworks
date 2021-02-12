package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.NonNull;
import org.openrewrite.maven.MavenVisitor;
import org.openrewrite.maven.tree.Maven;

public class AddJUnitDependencies extends Recipe {

    @NonNull
    private String version = "5.x";

    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AddJUnitDependenciesVisitor();
    }

    private class AddJUnitDependenciesVisitor extends MavenVisitor {
        @Override
        public Maven visitMaven(Maven maven, ExecutionContext ctx) {
            if (Boolean.TRUE.equals(ctx.pollMessage(FindJUnit.JUNIT_REFS_EXIST_KEY))) {
                maybeAddDependency(
                        "org.junit.jupiter",
                        "junit-jupiter-api",
                        version,
                        null,
                        "test",
                        null
                );
                maybeAddDependency(
                        "org.junit.jupiter",
                        "junit-jupiter-engine",
                        version,
                        null,
                        "test",
                        null
                );
            }
            return super.visitMaven(maven, ctx);
        }
    }
}
