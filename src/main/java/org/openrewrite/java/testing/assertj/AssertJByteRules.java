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
import org.assertj.core.api.AbstractByteAssert;
import org.openrewrite.java.template.RecipeDescriptor;

import static org.assertj.core.data.Offset.offset;
import static org.assertj.core.data.Percentage.withPercentage;

@RecipeDescriptor(
      name = "Adopt AssertJ Byte Assertions",
      description = "Adopt AssertJ Byte Assertions. Favor semantically explicit methods (e.g. " +
            "`myByte.isZero()` over `myByte.isEqualTo(0)`)."
)
public class AssertJByteRules {

  @RecipeDescriptor(
        name = "Replace `isCloseTo` with `isEqualTo`",
        description = "Replace `isCloseTo` with `isEqualTo` when `offset` or `percentage` is zero."
  )
  static final class AbstractByteAssertIsEqualTo {
    @BeforeTemplate
    AbstractByteAssert<?> before(AbstractByteAssert<?> byteAssert, byte n) {
      return Refaster.anyOf(
          byteAssert.isCloseTo(n, offset((byte) 0)), byteAssert.isCloseTo(n, withPercentage(0)));
    }

    @AfterTemplate
    AbstractByteAssert<?> after(AbstractByteAssert<?> byteAssert, byte n) {
      return byteAssert.isEqualTo(n);
    }
  }

  @RecipeDescriptor(
        name = "Replace `isNotCloseTo` with `isNotEqualTo`",
        description = "Replace `isNotCloseTo` with `isNotEqualTo` when `offset` or `percentage` is zero."
  )
  static final class AbstractByteAssertIsNotEqualTo {
    @BeforeTemplate
    AbstractByteAssert<?> before(AbstractByteAssert<?> byteAssert, byte n) {
      return Refaster.anyOf(
          byteAssert.isNotCloseTo(n, offset((byte) 0)),
          byteAssert.isNotCloseTo(n, withPercentage(0)));
    }

    @AfterTemplate
    AbstractByteAssert<?> after(AbstractByteAssert<?> byteAssert, byte n) {
      return byteAssert.isNotEqualTo(n);
    }
  }

  @RecipeDescriptor(
        name = "Replace `isEqualTo(0)` with `isZero()`",
        description = "Replace `isEqualTo(0)` with `isZero()`."
  )
  static final class AbstractByteAssertIsZero {
    @BeforeTemplate
    AbstractByteAssert<?> before(AbstractByteAssert<?> byteAssert) {
      return byteAssert.isEqualTo((byte) 0);
    }

    @AfterTemplate
    AbstractByteAssert<?> after(AbstractByteAssert<?> byteAssert) {
      return byteAssert.isZero();
    }
  }

  @RecipeDescriptor(
        name = "Replace `isNotEqualTo(0)` with `isNotZero()`",
        description = "Replace `isNotEqualTo(0)` with `isNotZero()`."
  )
  static final class AbstractByteAssertIsNotZero {
    @BeforeTemplate
    AbstractByteAssert<?> before(AbstractByteAssert<?> byteAssert) {
      return byteAssert.isNotEqualTo((byte) 0);
    }

    @AfterTemplate
    AbstractByteAssert<?> after(AbstractByteAssert<?> byteAssert) {
      return byteAssert.isNotZero();
    }
  }

  @RecipeDescriptor(
        name = "Replace `isEqualTo(1)` with `isOne()`",
        description = "Replace `isEqualTo(1)` with `isOne()`."
  )
  static final class AbstractByteAssertIsOne {
    @BeforeTemplate
    AbstractByteAssert<?> before(AbstractByteAssert<?> byteAssert) {
      return byteAssert.isEqualTo((byte) 1);
    }

    @AfterTemplate
    AbstractByteAssert<?> after(AbstractByteAssert<?> byteAssert) {
      return byteAssert.isOne();
    }
  }
}
