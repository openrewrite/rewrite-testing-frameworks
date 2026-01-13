package org.openrewrite.java.testing.junit5;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import org.junit.jupiter.api.Assumptions;
import org.openrewrite.java.template.RecipeDescriptor;
import org.junit.Assume;

import java.util.Arrays;

@RecipeDescriptor(name = "Transform Assumes to Assumptions", description = "Transform Assumes to Assumptions")
public class AssumeToAssumptions {

    @RecipeDescriptor(name = "Transform Assume#assumeNotNull to an Assumption", description = "Transform Assume#assumeNotNull to an Assumption")
    static class AssumeNotNull {
        @BeforeTemplate
        void before(Object object) {
            Assume.assumeNotNull(object);
        }

        @AfterTemplate
        void after(Object object) {
            Assumptions.assumeTrue(null != object);
        }
    }

    @RecipeDescriptor(name = "Transform Assume#assumeNotNull to an Assumption", description = "Transform Assume#assumeNotNull to an Assumption")
    static class AssumeNotNullVariadic {
        @BeforeTemplate
        void before(Object... object) {
            Assume.assumeNotNull(object);
        }

        @AfterTemplate
        void after(Object... object) {
            Arrays.stream(object).forEach(o -> Assumptions.assumeTrue(null != o));
        }
    }
}

