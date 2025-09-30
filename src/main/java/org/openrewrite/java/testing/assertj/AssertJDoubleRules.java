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
import org.assertj.core.api.AbstractDoubleAssert;
import org.assertj.core.data.Offset;
import org.openrewrite.java.template.RecipeDescriptor;

import static org.assertj.core.data.Offset.offset;
import static org.assertj.core.data.Percentage.withPercentage;

@RecipeDescriptor(
      name = "Adopt AssertJ Double Assertions",
      description = "Adopt AssertJ Double Assertions. Favor semantically explicit methods (e.g. " +
            "`myDouble.isZero()` over `myDouble.isEqualTo(0.0)`)."
)
public class AssertJDoubleRules {

  @RecipeDescriptor(
        name = "Replace `isEqualTo` with `isCloseTo`",
        description = "Replace `isEqualTo` with `isCloseTo` when `offset` or `percentage` is provided."
  )
  static final class AbstractDoubleAssertIsCloseToWithOffset {
    @BeforeTemplate
    AbstractDoubleAssert<?> before(
        AbstractDoubleAssert<?> doubleAssert, double n, Offset<Double> offset) {
      return doubleAssert.isEqualTo(n, offset);
    }

    @BeforeTemplate
    AbstractDoubleAssert<?> before(
        AbstractDoubleAssert<?> doubleAssert, Double n, Offset<Double> offset) {
      return doubleAssert.isEqualTo(n, offset);
    }

    @AfterTemplate
    AbstractDoubleAssert<?> after(
        AbstractDoubleAssert<?> doubleAssert, double n, Offset<Double> offset) {
      return doubleAssert.isCloseTo(n, offset);
    }
  }

  @RecipeDescriptor(
        name = "Replace `isCloseTo` with `isEqualTo`",
        description = "Replace `isCloseTo` with `isEqualTo` when `offset` or `percentage` is zero."
  )
  static final class AbstractDoubleAssertIsEqualTo {
    @BeforeTemplate
    AbstractDoubleAssert<?> before(AbstractDoubleAssert<?> doubleAssert, double n) {
      return Refaster.anyOf(
          doubleAssert.isCloseTo(n, offset(0.0)),
          doubleAssert.isCloseTo(n, offset(0d)),
          doubleAssert.isCloseTo(n, withPercentage(0)),
          doubleAssert.isCloseTo(n, withPercentage(0.0)),
          doubleAssert.isCloseTo(n, withPercentage(0d))
      );
    }

    @AfterTemplate
    AbstractDoubleAssert<?> after(AbstractDoubleAssert<?> doubleAssert, double n) {
      return doubleAssert.isEqualTo(n);
    }
  }

  @RecipeDescriptor(
        name = "Replace `isNotCloseTo` with `isNotEqualTo`",
        description = "Replace `isNotCloseTo` with `isNotEqualTo` when `offset` or `percentage` is zero."
  )
  static final class AbstractDoubleAssertIsNotEqualTo {
    @BeforeTemplate
    AbstractDoubleAssert<?> before(AbstractDoubleAssert<?> doubleAssert, double n) {
      return Refaster.anyOf(
          doubleAssert.isNotCloseTo(n, offset(0.0)),
          doubleAssert.isNotCloseTo(n, offset(0d)),
          doubleAssert.isNotCloseTo(n, withPercentage(0)),
          doubleAssert.isNotCloseTo(n, withPercentage(0.0)),
          doubleAssert.isNotCloseTo(n, withPercentage(0d))
      );
    }

    @AfterTemplate
    AbstractDoubleAssert<?> after(AbstractDoubleAssert<?> doubleAssert, double n) {
      return doubleAssert.isNotEqualTo(n);
    }
  }

  @RecipeDescriptor(
        name = "Replace `isEqualTo(0)` with `isZero()`",
        description = "Replace `isEqualTo(0)` with `isZero()`."
  )
  static final class AbstractDoubleAssertIsZero {
    @BeforeTemplate
    AbstractDoubleAssert<?> before(AbstractDoubleAssert<?> doubleAssert) {
      return Refaster.anyOf(
          doubleAssert.isEqualTo(0),
          doubleAssert.isEqualTo(0.0),
          doubleAssert.isEqualTo(0d)
      );
    }

    @AfterTemplate
    AbstractDoubleAssert<?> after(AbstractDoubleAssert<?> doubleAssert) {
      return doubleAssert.isZero();
    }
  }

  @RecipeDescriptor(
        name = "Replace `isNotEqualTo(0)` with `isNotZero()`",
        description = "Replace `isNotEqualTo(0)` with `isNotZero()`."
  )
  static final class AbstractDoubleAssertIsNotZero {
    @BeforeTemplate
    AbstractDoubleAssert<?> before(AbstractDoubleAssert<?> doubleAssert) {
      return Refaster.anyOf(
          doubleAssert.isNotEqualTo(0),
          doubleAssert.isNotEqualTo(0.0),
          doubleAssert.isNotEqualTo(0d)
      );
    }

    @AfterTemplate
    AbstractDoubleAssert<?> after(AbstractDoubleAssert<?> doubleAssert) {
      return doubleAssert.isNotZero();
    }
  }

  @RecipeDescriptor(
        name = "Replace `isEqualTo(1)` with `isOne()`",
        description = "Replace `isEqualTo(1)` with `isOne()`."
  )
  static final class AbstractDoubleAssertIsOne {
    @BeforeTemplate
    AbstractDoubleAssert<?> before(AbstractDoubleAssert<?> doubleAssert) {
      return Refaster.anyOf(
          doubleAssert.isEqualTo(1),
          doubleAssert.isEqualTo(1.0),
          doubleAssert.isEqualTo(1d)
      );
    }

    @AfterTemplate
    AbstractDoubleAssert<?> after(AbstractDoubleAssert<?> doubleAssert) {
      return doubleAssert.isOne();
    }
  }
}
