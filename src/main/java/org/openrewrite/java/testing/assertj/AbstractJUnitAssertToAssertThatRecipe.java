/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import org.openrewrite.java.tree.J;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractJUnitAssertToAssertThatRecipe extends Recipe {
    protected static final String ASSERTJ = "org.assertj.core.api.Assertions";
    protected static final String ASSERT_THAT = "org.assertj.core.api.Assertions.assertThat";
    protected static final String ASSERT_WITHIN = "org.assertj.core.api.Assertions.within";
    protected static final String JUNIT4_ASSERT = "org.junit.Assert";
    protected static final String JUNIT5_ASSERT = "org.junit.jupiter.api.Assertions";
    protected static final String ASSERTJ_CORE = "assertj-core-3.24";
    private final String[] methodMatcherSuffix;

    protected AbstractJUnitAssertToAssertThatRecipe (String... methodMatcherSuffix) {
        this.methodMatcherSuffix = methodMatcherSuffix;
    }

    protected abstract JUnitAssertionVisitor getJUnitAssertionVisitor(JUnitAssertionConfig config);

    protected JUnitAssertionConfig getJunit4Config() {
        return new JUnitAssertionConfig(JUNIT4_ASSERT, true, methodMatcherSuffix);
    }

    protected JUnitAssertionConfig getJunit5Config() {
        return new JUnitAssertionConfig(JUNIT5_ASSERT, false, methodMatcherSuffix);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext> () {
            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                List<UsesMethod<Object>> usesMethods = Stream.of(getJunit5Config(), getJunit4Config())
                        .map(JUnitAssertionConfig::getMethodMatcher)
                        .flatMap(Arrays::stream)
                        .map(UsesMethod::new)
                        .collect(Collectors.toList());

                return usesMethods.stream().anyMatch(usesMethod -> usesMethod.isAcceptable(sourceFile, ctx));
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
        private final MethodMatcher[] methodMatcher;
        private final boolean messageIsFirstArg;

        private JUnitAssertionConfig(String assertionClass, boolean messageIsFirstArg, String... methodMatcherSuffix) {
            this.assertionClass = assertionClass;
            this.messageIsFirstArg = messageIsFirstArg;
            this.methodMatcher = Stream.of(methodMatcherSuffix)
                    .map(pattern -> new MethodMatcher(String.format("%s %s", assertionClass, pattern)))
                    .toArray(MethodMatcher[]::new);
        }

        public boolean matches (J.MethodInvocation method) {
            return Arrays.stream(methodMatcher).anyMatch(matcher -> matcher.matches(method));
        }

    }

    public abstract static class JUnitAssertionVisitor extends JavaIsoVisitor<ExecutionContext> {
        protected final JUnitAssertionConfig config;

        protected JUnitAssertionVisitor(JUnitAssertionConfig config) {
            this.config = config;
        }
    }

}
