/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.assertj;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import org.openrewrite.java.template.RecipeDescriptor;

import static org.assertj.core.api.Assertions.assertThat;

@RecipeDescriptor(
        name = "Simplify the expectations of assertions",
        description = "Simplifies expectations on various assertions."
)
public class SimplifyAssertExpectations {

    @RecipeDescriptor(
            name = "Simplify `assertThat(int).isEqualTo(0)`",
            description = "Simplify `assertThat(int).isEqualTo(0)` to `assertThat(int).isZero()`."
    )
    public class SimplifyToIsZero {

        @BeforeTemplate
        void before(Integer i) {
            assertThat(i).isEqualTo(0);
        }

        @AfterTemplate
        void after(Integer i) {
            assertThat(i).isZero();
        }
    }

    @RecipeDescriptor(
            name = "Simplify `assertThat(o).isEqualTo(null)`",
            description = "Simplify `assertThat(o).isEqualTo(null)` to `assertThat(o).isNull()`."
    )
    public class SimplifyToIsNull {

        @BeforeTemplate
        void before(Object o) {
            assertThat(o).isEqualTo(null);
        }

        @AfterTemplate
        void after(Object o) {
            assertThat(o).isNull();
        }
    }

    @RecipeDescriptor(
            name = "Simplify `assertThat(i >= j).isTrue()`",
            description = "Simplify `assertThat(i >= j).isTrue()` to `assertThat(i).isGreaterThanOrEqualTo(j)`."
    )
    public class SimplifyGreaterEqualComparison {

        @BeforeTemplate
        void before(Integer i, Integer j) {
            assertThat(i >= j).isTrue();
        }

        @AfterTemplate
        void after(Integer i, Integer j) {
            assertThat(i).isGreaterThanOrEqualTo(j);
        }
    }
}
