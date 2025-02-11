package org.openrewrite.java.testing.assertj;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;

import java.util.Arrays;

public abstract class AbstractJUnitAssertToAssertThatRecipe extends Recipe {
    protected static final String ASSERTJ = "org.assertj.core.api.Assertions";
    protected static final String ASSERT_THAT = "org.assertj.core.api.Assertions.assertThat";
    protected static final String ASSERT_WITHIN = "org.assertj.core.api.Assertions.within";
    protected static final String JUNIT4_ASSERT = "org.junit.Assert";
    protected static final String JUNIT5_ASSERT = "org.junit.jupiter.api.Assertions";
    protected static final String ASSERTJ_CORE = "assertj-core-3.24";
    private final String methodMatcherSuffix;

    protected AbstractJUnitAssertToAssertThatRecipe (String methodMatcherSuffix) {
        this.methodMatcherSuffix = methodMatcherSuffix;
    }

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

    @Getter()
    public static class JUnitAssertionConfig {

        private final String assertionClass;
        private final MethodMatcher methodMatcher;
        private final boolean messageIsFirstArg;

        private JUnitAssertionConfig(String assertionClass, boolean messageIsFirstArg, String methodMatcherSuffix) {
            this.assertionClass = assertionClass;
            this.messageIsFirstArg = messageIsFirstArg;
            this.methodMatcher = new MethodMatcher(String.format("%s %s", assertionClass, methodMatcherSuffix));
        }

    }

    public abstract static class JUnitAssertionVisitor extends JavaIsoVisitor<ExecutionContext> {
        protected final JUnitAssertionConfig config;

        protected JUnitAssertionVisitor(JUnitAssertionConfig config) {
            this.config = config;
        }
    }

    protected JUnitAssertionConfig getJunit4Config() {
        return new JUnitAssertionConfig(JUNIT4_ASSERT, true, methodMatcherSuffix);
    }

    protected JUnitAssertionConfig getJunit5Config() {
        return new JUnitAssertionConfig(JUNIT5_ASSERT, false, methodMatcherSuffix);
    }

    protected abstract JUnitAssertionVisitor getJUnitAssertionVisitor(JUnitAssertionConfig config);

}
