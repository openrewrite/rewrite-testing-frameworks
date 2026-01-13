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
import org.openrewrite.java.template.Matcher;
import org.openrewrite.java.template.Matches;
import org.openrewrite.java.template.RecipeDescriptor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.TypeUtils;

import java.util.stream.Stream;

@RecipeDescriptor(
        name = "Transform `Assume` methods to `Assumptions`",
        description = "Transform `Assume` methods to without a direct counterpart to equivalent assumptions in `Assumptions`.")
public class AssumeNotNullToAssumptions {

    @RecipeDescriptor(
            name = "Transform singlar `assumeNotNull(object)` to `assumeFalse(object == null)`",
            description = "Transform singlar `Assume.assumeNotNull(object)` to `Assumptions.assumeFalse(object == null)`.")
    static class SingleArg {
        @BeforeTemplate
        void before(@Matches(SingleArgMatcher.class) Object object) {
            Assume.assumeNotNull(object);
        }

        @AfterTemplate
        void after(Object object) {
            Assumptions.assumeFalse(object == null);
        }
    }

    @RecipeDescriptor(
            name = "Transform two-argument `assumeNotNull` to a stream of `assumeFalse(object == null)`",
            description = "Transform `Assume.assumeNotNull(object1, object2)` to `Stream.of(object1, object2).forEach(o -> Assumptions.assumeFalse(o == null))`.")
    static class TwoArgs {
        @BeforeTemplate
        void before(Object object1, String object2) {
            Assume.assumeNotNull(object1, object2);
        }

        @AfterTemplate
        void after(Object object1, String object2) {
            Stream.of(object1, object2).forEach(o -> Assumptions.assumeFalse(o == null));
        }
    }

    @RecipeDescriptor(
            name = "Transform variadic `assumeNotNull(objects...)` to a stream of `assumeFalse(object == null)`",
            description = "Transform `Assume.assumeNotNull(objects...)` to `Stream.of(object1, object2).forEach(o -> Assumptions.assumeFalse(o == null))`.")
    static class VarArgs {
        @BeforeTemplate
        void before(Object objects) {
            Assume.assumeNotNull(objects);
        }

        @AfterTemplate
        void after(Object[] objects) {
            Stream.of(objects).forEach(o -> Assumptions.assumeFalse(o == null));
        }
    }

    public static class SingleArgMatcher implements Matcher<Expression> {
        @Override
        public boolean matches(Expression e) {
            return TypeUtils.asArray(e.getType()) == null;
        }
    }
}
