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

import com.google.errorprone.refaster.Refaster;
import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import org.assertj.core.api.AbstractFloatAssert;
import org.assertj.core.data.Offset;
import org.openrewrite.java.template.RecipeDescriptor;

import static org.assertj.core.data.Offset.offset;
import static org.assertj.core.data.Percentage.withPercentage;

@RecipeDescriptor(
      name = "Adopt AssertJ Float Assertions",
      description = "Adopt AssertJ Float Assertions. Favor semantically explicit methods (e.g. " +
            "`myFloat.isZero()` over `myFloat.isEqualTo(0.0f)`)."
)
public class AssertJFloatRules {

  @RecipeDescriptor(
        name = "Replace `isEqualTo` with `isCloseTo`",
        description = "Replace `isEqualTo` with `isCloseTo` when `offset` or `percentage` is provided."
  )
  static final class AbstractFloatAssertIsCloseToWithOffset {
    @BeforeTemplate
    AbstractFloatAssert<?> before(
        AbstractFloatAssert<?> floatAssert, float n, Offset<Float> offset) {
      return floatAssert.isEqualTo(n, offset);
    }

    @BeforeTemplate
    AbstractFloatAssert<?> before(
        AbstractFloatAssert<?> floatAssert, Float n, Offset<Float> offset) {
      return floatAssert.isEqualTo(n, offset);
    }

    @AfterTemplate
    AbstractFloatAssert<?> after(
        AbstractFloatAssert<?> floatAssert, float n, Offset<Float> offset) {
      return floatAssert.isCloseTo(n, offset);
    }
  }

  @RecipeDescriptor(
        name = "Replace `isCloseTo` with `isEqualTo`",
        description = "Replace `isCloseTo` with `isEqualTo` when `offset` or `percentage` is zero."
  )
  static final class AbstractFloatAssertIsEqualTo {
    @BeforeTemplate
    AbstractFloatAssert<?> before(AbstractFloatAssert<?> floatAssert, float n) {
      return Refaster.anyOf(
          floatAssert.isCloseTo(n, offset(0f)), floatAssert.isCloseTo(n, withPercentage(0)));
    }

    @AfterTemplate
    AbstractFloatAssert<?> after(AbstractFloatAssert<?> floatAssert, float n) {
      return floatAssert.isEqualTo(n);
    }
  }

  @RecipeDescriptor(
        name = "Replace `isNotCloseTo` with `isNotEqualTo`",
        description = "Replace `isNotCloseTo` with `isNotEqualTo` when `offset` or `percentage` is zero."
  )
  static final class AbstractFloatAssertIsNotEqualTo {
    @BeforeTemplate
    AbstractFloatAssert<?> before(AbstractFloatAssert<?> floatAssert, float n) {
      return Refaster.anyOf(
          floatAssert.isNotCloseTo(n, offset(0f)), floatAssert.isNotCloseTo(n, withPercentage(0)));
    }

    @AfterTemplate
    AbstractFloatAssert<?> after(AbstractFloatAssert<?> floatAssert, float n) {
      return floatAssert.isNotEqualTo(n);
    }
  }

  @RecipeDescriptor(
        name = "Replace `isEqualTo(0)` with `isZero()`",
        description = "Replace `isEqualTo(0)` with `isZero()`."
  )
  static final class AbstractFloatAssertIsZero {
    @BeforeTemplate
    AbstractFloatAssert<?> before(AbstractFloatAssert<?> floatAssert) {
      return floatAssert.isEqualTo(0);
    }

    @AfterTemplate
    AbstractFloatAssert<?> after(AbstractFloatAssert<?> floatAssert) {
      return floatAssert.isZero();
    }
  }

  @RecipeDescriptor(
        name = "Replace `isNotEqualTo(0)` with `isNotZero()`",
        description = "Replace `isNotEqualTo(0)` with `isNotZero()`."
  )
  static final class AbstractFloatAssertIsNotZero {
    @BeforeTemplate
    AbstractFloatAssert<?> before(AbstractFloatAssert<?> floatAssert) {
      return floatAssert.isNotEqualTo(0);
    }

    @AfterTemplate
    AbstractFloatAssert<?> after(AbstractFloatAssert<?> floatAssert) {
      return floatAssert.isNotZero();
    }
  }

  @RecipeDescriptor(
        name = "Replace `isEqualTo(1)` with `isOne()`",
        description = "Replace `isEqualTo(1)` with `isOne()`."
  )
  static final class AbstractFloatAssertIsOne {
    @BeforeTemplate
    AbstractFloatAssert<?> before(AbstractFloatAssert<?> floatAssert) {
      return floatAssert.isEqualTo(1);
    }

    @AfterTemplate
    AbstractFloatAssert<?> after(AbstractFloatAssert<?> floatAssert) {
      return floatAssert.isOne();
    }
  }
}
