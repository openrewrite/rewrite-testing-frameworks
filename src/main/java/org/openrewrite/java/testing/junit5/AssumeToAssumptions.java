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
import org.junit.Assume;
import org.junit.jupiter.api.Assumptions;
import org.openrewrite.java.template.RecipeDescriptor;

import java.util.Arrays;

@RecipeDescriptor(
        name = "Transform `Assume` methods to `Assumptions`",
        description = "Transform `Assume` methods to without a direct counterpart to equivalent assumptions in `Assumptions`.")
public class AssumeToAssumptions {

    @RecipeDescriptor(
            name = "Transform singlar `assumeNotNull(object)` to `assumeFalse(object == null)`",
            description = "Transform singlar `Assume.assumeNotNull(object)` to `Assumptions.assumeFalse(object == null)`.")
    static class AssumeNotNull {
        @BeforeTemplate
        void before(Object object) {
            Assume.assumeNotNull(object);
        }

        @AfterTemplate
        void after(Object object) {
            Assumptions.assumeFalse(object == null);
        }
    }

    @RecipeDescriptor(
            name = "Transform variadic `assumeNotNull(objects...)` to a stream of `assumeFalse(object == null)`",
            description = "Transform `Assume#assumeNotNull` to an Assumption")
    static class AssumeNotNullVariadic {
        @BeforeTemplate
        void before(Object[] object) {
            Assume.assumeNotNull(object);
        }

        @AfterTemplate
        void after(Object[] object) {
            Arrays.stream(object).forEach(o -> Assumptions.assumeFalse(o == null));
        }
    }
}
