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
import org.assertj.core.api.AbstractLongAssert;
import org.openrewrite.java.template.RecipeDescriptor;

@RecipeDescriptor(
      name = "Adopt AssertJ Long Assertions",
      description = "Adopt AssertJ Long Assertions. Favor semantically explicit methods (e.g. " +
            "`myLong.isZero()` over `myLong.isEqualTo(0)`)."
)
public class AssertJLongRules {

  @RecipeDescriptor(
        name = "Replace `isCloseTo` with `isEqualTo`",
        description = "Replace `isCloseTo` with `isEqualTo` when `offset` or `percentage` is zero."
  )
  static final class AbstractLongAssertIsEqualTo {
    @BeforeTemplate
    AbstractLongAssert<?> before(AbstractLongAssert<?> longAssert, long n) {
      return Refaster.anyOf(
          longAssert.isCloseTo(n, offset(0L)), longAssert.isCloseTo(n, withPercentage(0)));
    }

    @AfterTemplate
    AbstractLongAssert<?> after(AbstractLongAssert<?> longAssert, long n) {
      return longAssert.isEqualTo(n);
    }
  }

  @RecipeDescriptor(
        name = "Replace `isNotCloseTo` with `isNotEqualTo`",
        description = "Replace `isNotCloseTo` with `isNotEqualTo` when `offset` or `percentage` is zero."
  )
  static final class AbstractLongAssertIsNotEqualTo {
    @BeforeTemplate
    AbstractLongAssert<?> before(AbstractLongAssert<?> longAssert, long n) {
      return Refaster.anyOf(
          longAssert.isNotCloseTo(n, offset(0L)), longAssert.isNotCloseTo(n, withPercentage(0)));
    }

    @AfterTemplate
    AbstractLongAssert<?> after(AbstractLongAssert<?> longAssert, long n) {
      return longAssert.isNotEqualTo(n);
    }
  }

  @RecipeDescriptor(
        name = "Replace `isEqualTo(0)` with `isZero()`",
        description = "Replace `isEqualTo(0)` with `isZero()`."
  )
  static final class AbstractLongAssertIsZero {
    @BeforeTemplate
    AbstractLongAssert<?> before(AbstractLongAssert<?> longAssert) {
      return longAssert.isEqualTo(0);
    }

    @AfterTemplate
    AbstractLongAssert<?> after(AbstractLongAssert<?> longAssert) {
      return longAssert.isZero();
    }
  }

  @RecipeDescriptor(
        name = "Replace `isNotEqualTo(0)` with `isNotZero()`",
        description = "Replace `isNotEqualTo(0)` with `isNotZero()`."
  )
  static final class AbstractLongAssertIsNotZero {
    @BeforeTemplate
    AbstractLongAssert<?> before(AbstractLongAssert<?> longAssert) {
      return longAssert.isNotEqualTo(0);
    }

    @AfterTemplate
    AbstractLongAssert<?> after(AbstractLongAssert<?> longAssert) {
      return longAssert.isNotZero();
    }
  }

  @RecipeDescriptor(
        name = "Replace `isEqualTo(1)` with `isOne()`",
        description = "Replace `isEqualTo(1)` with `isOne()`."
  )
  static final class AbstractLongAssertIsOne {
    @BeforeTemplate
    AbstractLongAssert<?> before(AbstractLongAssert<?> longAssert) {
      return longAssert.isEqualTo(1);
    }

    @AfterTemplate
    AbstractLongAssert<?> after(AbstractLongAssert<?> longAssert) {
      return longAssert.isOne();
    }
  }
}
