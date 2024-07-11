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
import org.openrewrite.java.template.Primitive;
import org.openrewrite.java.template.RecipeDescriptor;

import static org.assertj.core.api.Assertions.assertThat;

public class IdiomaticAssertions {
    @RecipeDescriptor(
            name = "Replace redundant `String` method calls with self",
            description = "Replace redundant `substring(..)` and `toString()` method calls with the `String` self."
    )
    public static class AssertThatIsEqualToZero {
        @BeforeTemplate
        void before(@Primitive Integer actual) {
            assertThat(actual).isEqualTo(0);
        }

        @AfterTemplate
        void after(@Primitive Integer actual) {
            assertThat(actual).isZero();
        }
    }
}
