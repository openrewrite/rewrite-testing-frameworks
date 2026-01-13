/*
 * Copyright 2026 the original author or authors.
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

