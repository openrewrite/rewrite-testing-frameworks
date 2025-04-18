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
import org.assertj.core.api.AbstractIntegerAssert;
import org.openrewrite.java.template.RecipeDescriptor;

import static org.assertj.core.data.Offset.offset;
import static org.assertj.core.data.Percentage.withPercentage;

@RecipeDescriptor(
      name = "Adopt AssertJ Integer Assertions",
      description = "Adopt AssertJ Integer Assertions. Favor semantically explicit methods (e.g. " +
            "`myInteger.isZero()` over `myInteger.isEqualTo(0)`)."
)
public class AssertJIntegerRules {

  @RecipeDescriptor(
        name = "Replace `isCloseTo` with `isEqualTo`",
        description = "Replace `isCloseTo` with `isEqualTo` when `offset` or `percentage` is zero."
  )
  static final class AbstractIntegerAssertIsEqualTo {
    @BeforeTemplate
    AbstractIntegerAssert<?> before(AbstractIntegerAssert<?> intAssert, int n) {
      return Refaster.anyOf(
          intAssert.isCloseTo(n, offset(0)), intAssert.isCloseTo(n, withPercentage(0)));
    }

    @AfterTemplate
    AbstractIntegerAssert<?> after(AbstractIntegerAssert<?> intAssert, int n) {
      return intAssert.isEqualTo(n);
    }
  }

  @RecipeDescriptor(
        name = "Replace `isNotCloseTo` with `isNotEqualTo`",
        description = "Replace `isNotCloseTo` with `isNotEqualTo` when `offset` or `percentage` is zero."
  )
  static final class AbstractIntegerAssertIsNotEqualTo {
    @BeforeTemplate
    AbstractIntegerAssert<?> before(AbstractIntegerAssert<?> intAssert, int n) {
      return Refaster.anyOf(
          intAssert.isNotCloseTo(n, offset(0)), intAssert.isNotCloseTo(n, withPercentage(0)));
    }

    @AfterTemplate
    AbstractIntegerAssert<?> after(AbstractIntegerAssert<?> intAssert, int n) {
      return intAssert.isNotEqualTo(n);
    }
  }

  @RecipeDescriptor(
        name = "Replace `isEqualTo(0)` with `isZero()`",
        description = "Replace `isEqualTo(0)` with `isZero()`."
  )
  static final class AbstractIntegerAssertIsZero {
    @BeforeTemplate
    AbstractIntegerAssert<?> before(AbstractIntegerAssert<?> intAssert) {
      return intAssert.isEqualTo(0);
    }

    @AfterTemplate
    AbstractIntegerAssert<?> after(AbstractIntegerAssert<?> intAssert) {
      return intAssert.isZero();
    }
  }

  @RecipeDescriptor(
        name = "Replace `isNotEqualTo(0)` with `isNotZero()`",
        description = "Replace `isNotEqualTo(0)` with `isNotZero()`."
  )
  static final class AbstractIntegerAssertIsNotZero {
    @BeforeTemplate
    AbstractIntegerAssert<?> before(AbstractIntegerAssert<?> intAssert) {
      return intAssert.isNotEqualTo(0);
    }

    @AfterTemplate
    AbstractIntegerAssert<?> after(AbstractIntegerAssert<?> intAssert) {
      return intAssert.isNotZero();
    }
  }

  @RecipeDescriptor(
        name = "Replace `isEqualTo(1)` with `isOne()`",
        description = "Replace `isEqualTo(1)` with `isOne()`."
  )
  static final class AbstractIntegerAssertIsOne {
    @BeforeTemplate
    AbstractIntegerAssert<?> before(AbstractIntegerAssert<?> intAssert) {
      return intAssert.isEqualTo(1);
    }

    @AfterTemplate
    AbstractIntegerAssert<?> after(AbstractIntegerAssert<?> intAssert) {
      return intAssert.isOne();
    }
  }
}
