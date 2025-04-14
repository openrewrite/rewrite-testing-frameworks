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

import static org.assertj.core.data.Offset.offset;
import static org.assertj.core.data.Percentage.withPercentage;

import com.google.errorprone.refaster.Refaster;
import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import java.math.BigInteger;
import org.assertj.core.api.AbstractBigIntegerAssert;
import org.openrewrite.java.template.RecipeDescriptor;

@RecipeDescriptor(
      name = "Adopt AssertJ BigInteger Assertions",
      description = "Adopt AssertJ BigInteger Assertions. Favor semantically explicit methods (e.g. " +
            "`myBigInteger.isZero()` over `myBigInteger.isEqualTo(0)`)."
)
public class AssertJBigIntegerRules {

  @RecipeDescriptor(
        name = "Replace `isCloseTo` with `isEqualTo`",
        description = "Replace `isCloseTo` with `isEqualTo` when `offset` or `percentage` is zero."
  )
  static final class AbstractBigIntegerAssertIsEqualTo {
    @BeforeTemplate
    AbstractBigIntegerAssert<?> before(AbstractBigIntegerAssert<?> bigIntegerAssert, BigInteger n) {
      return Refaster.anyOf(
          bigIntegerAssert.isCloseTo(n, offset(BigInteger.ZERO)),
          bigIntegerAssert.isCloseTo(n, withPercentage(0)));
    }

    @AfterTemplate
    AbstractBigIntegerAssert<?> after(AbstractBigIntegerAssert<?> bigIntegerAssert, BigInteger n) {
      return bigIntegerAssert.isEqualTo(n);
    }
  }

  @RecipeDescriptor(
        name = "Replace `isNotCloseTo` with `isNotEqualTo`",
        description = "Replace `isNotCloseTo` with `isNotEqualTo` when `offset` or `percentage` is zero."
  )
  static final class AbstractBigIntegerAssertIsNotEqualTo {
    @BeforeTemplate
    AbstractBigIntegerAssert<?> before(AbstractBigIntegerAssert<?> bigIntegerAssert, BigInteger n) {
      return Refaster.anyOf(
          bigIntegerAssert.isNotCloseTo(n, offset(BigInteger.ZERO)),
          bigIntegerAssert.isNotCloseTo(n, withPercentage(0)));
    }

    @AfterTemplate
    AbstractBigIntegerAssert<?> after(AbstractBigIntegerAssert<?> bigIntegerAssert, BigInteger n) {
      return bigIntegerAssert.isNotEqualTo(n);
    }
  }

  @RecipeDescriptor(
        name = "Replace `isEqualTo(0)` with `isZero()`",
        description = "Replace `isEqualTo(0)` with `isZero()`."
  )
  static final class AbstractBigIntegerAssertIsZero {
    @BeforeTemplate
    AbstractBigIntegerAssert<?> before(AbstractBigIntegerAssert<?> bigIntegerAssert) {
      return Refaster.anyOf(
          bigIntegerAssert.isEqualTo(0),
          bigIntegerAssert.isEqualTo(BigInteger.ZERO));
    }

    @AfterTemplate
    AbstractBigIntegerAssert<?> after(AbstractBigIntegerAssert<?> bigIntegerAssert) {
      return bigIntegerAssert.isZero();
    }
  }

  @RecipeDescriptor(
        name = "Replace `isNotEqualTo(0)` with `isNotZero()`",
        description = "Replace `isNotEqualTo(0)` with `isNotZero()`."
  )
  static final class AbstractBigIntegerAssertIsNotZero {
    @BeforeTemplate
    AbstractBigIntegerAssert<?> before(AbstractBigIntegerAssert<?> bigIntegerAssert) {
      return Refaster.anyOf(
          bigIntegerAssert.isNotEqualTo(0),
          bigIntegerAssert.isNotEqualTo(BigInteger.ZERO));
    }

    @AfterTemplate
    AbstractBigIntegerAssert<?> after(AbstractBigIntegerAssert<?> bigIntegerAssert) {
      return bigIntegerAssert.isNotZero();
    }
  }

  @RecipeDescriptor(
        name = "Replace `isEqualTo(1)` with `isOne()`",
        description = "Replace `isEqualTo(1)` with `isOne()`."
  )
  static final class AbstractBigIntegerAssertIsOne {
    @BeforeTemplate
    AbstractBigIntegerAssert<?> before(AbstractBigIntegerAssert<?> bigIntegerAssert) {
      return Refaster.anyOf(
          bigIntegerAssert.isEqualTo(1),
          bigIntegerAssert.isEqualTo(1L),
          bigIntegerAssert.isEqualTo(BigInteger.ONE));
    }

    @AfterTemplate
    AbstractBigIntegerAssert<?> after(AbstractBigIntegerAssert<?> bigIntegerAssert) {
      return bigIntegerAssert.isOne();
    }
  }
}
