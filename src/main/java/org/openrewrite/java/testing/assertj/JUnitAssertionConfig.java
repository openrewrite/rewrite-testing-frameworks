package org.openrewrite.java.testing.assertj;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.testing.assertj.AbstractJUnitAssertToAssertThatRecipe.JUnitAssertionVisitor;

@Getter
public abstract class JUnitAssertionConfig {
    public static final String JUNIT4_ASSERT = "org.junit.Assert";
    public static final String JUNIT5_ASSERT = "org.junit.jupiter.api.Assertions";

    private final String assertionClass;
    private final MethodMatcher methodMatcher;
    private final boolean messageIsFirstArg;

    private JUnitAssertionConfig(String assertionClass, boolean messageIsFirstArg, MethodMatcher methodMatcher) {
        this.assertionClass = assertionClass;
        this.messageIsFirstArg = messageIsFirstArg;
        this.methodMatcher = methodMatcher;
    }

    public static class JUnit4 extends JUnitAssertionConfig {
        public JUnit4(MethodMatcher methodMatcher) {
            super(JUNIT4_ASSERT, true, methodMatcher);
        }
    }

    public static class JUnit5 extends JUnitAssertionConfig {
        public JUnit5(MethodMatcher methodMatcher) {
            super(JUNIT5_ASSERT, true, methodMatcher);
        }
    }

}
