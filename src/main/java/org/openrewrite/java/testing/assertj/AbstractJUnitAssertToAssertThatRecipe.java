package org.openrewrite.java.testing.assertj;

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesMethod;

import java.util.Arrays;

public abstract class AbstractJUnitAssertToAssertThatRecipe extends Recipe {
    protected static final String ASSERTJ = "org.assertj.core.api.Assertions";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext> () {
            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                for (JUnitAssertionConfig assertion : Arrays.asList(getJunit5Config(), getJunit4Config())) {
                    if (new UsesMethod<>(assertion.getMethodMatcher()).isAcceptable(sourceFile, ctx)) {
                        return true;
                    }
                }
                return false;
            }
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                for (JUnitAssertionConfig assertionConfig : Arrays.asList(getJunit5Config(), getJunit4Config())) {
                    tree = getJUnitAssertionVisitor(assertionConfig).visit(tree, ctx);
                }
                return tree;
            }

            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx, Cursor parent) {
                for (JUnitAssertionConfig assertionConfig : Arrays.asList(getJunit5Config(), getJunit4Config())) {
                    tree = getJUnitAssertionVisitor(assertionConfig).visit(tree, ctx, parent);
                }
                return tree;
            }
        };
    }

    public abstract static class JUnitAssertionVisitor extends JavaIsoVisitor<ExecutionContext> {
        protected final JUnitAssertionConfig config;

        protected JUnitAssertionVisitor(JUnitAssertionConfig config) {
            this.config = config;
        }
    }

    protected abstract JUnitAssertionConfig getJunit4Config();

    protected abstract JUnitAssertionConfig getJunit5Config();

    protected abstract JUnitAssertionVisitor getJUnitAssertionVisitor(JUnitAssertionConfig config);

}
