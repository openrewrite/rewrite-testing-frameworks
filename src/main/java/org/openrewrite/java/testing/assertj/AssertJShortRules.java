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
import org.assertj.core.api.AbstractShortAssert;
import org.openrewrite.java.template.RecipeDescriptor;

@RecipeDescriptor(
      name = "Adopt AssertJ Short Assertions",
      description = "Adopt AssertJ Short Assertions. Favor semantically explicit methods (e.g. " +
            "`myShort.isZero()` over `myShort.isEqualTo(0)`)."
)
public class AssertJShortRules {

  @RecipeDescriptor(
        name = "Replace `isCloseTo` with `isEqualTo`",
        description = "Replace `isCloseTo` with `isEqualTo` when `offset` or `percentage` is zero."
  )
  static final class AbstractShortAssertIsEqualTo {
    @BeforeTemplate
    AbstractShortAssert<?> before(AbstractShortAssert<?> shortAssert, short n) {
      return Refaster.anyOf(
          shortAssert.isCloseTo(n, offset((short) 0)), shortAssert.isCloseTo(n, withPercentage(0)));
    }

    @AfterTemplate
    AbstractShortAssert<?> after(AbstractShortAssert<?> shortAssert, short n) {
      return shortAssert.isEqualTo(n);
    }
  }

  @RecipeDescriptor(
        name = "Replace `isNotCloseTo` with `isNotEqualTo`",
        description = "Replace `isNotCloseTo` with `isNotEqualTo` when `offset` or `percentage` is zero."
  )
  static final class AbstractShortAssertIsNotEqualTo {
    @BeforeTemplate
    AbstractShortAssert<?> before(AbstractShortAssert<?> shortAssert, short n) {
      return Refaster.anyOf(
          shortAssert.isNotCloseTo(n, offset((short) 0)),
          shortAssert.isNotCloseTo(n, withPercentage(0)));
    }

    @AfterTemplate
    AbstractShortAssert<?> after(AbstractShortAssert<?> shortAssert, short n) {
      return shortAssert.isNotEqualTo(n);
    }
  }

  @RecipeDescriptor(
        name = "Replace `isEqualTo(0)` with `isZero()`",
        description = "Replace `isEqualTo(0)` with `isZero()`."
  )
  static final class AbstractShortAssertIsZero {
    @BeforeTemplate
    AbstractShortAssert<?> before(AbstractShortAssert<?> shortAssert) {
      return shortAssert.isEqualTo((short) 0);
    }

    @AfterTemplate
    AbstractShortAssert<?> after(AbstractShortAssert<?> shortAssert) {
      return shortAssert.isZero();
    }
  }

  @RecipeDescriptor(
        name = "Replace `isNotEqualTo(0)` with `isNotZero()`",
        description = "Replace `isNotEqualTo(0)` with `isNotZero()`."
  )
  static final class AbstractShortAssertIsNotZero {
    @BeforeTemplate
    AbstractShortAssert<?> before(AbstractShortAssert<?> shortAssert) {
      return shortAssert.isNotEqualTo((short) 0);
    }

    @AfterTemplate
    AbstractShortAssert<?> after(AbstractShortAssert<?> shortAssert) {
      return shortAssert.isNotZero();
    }
  }

  @RecipeDescriptor(
        name = "Replace `isEqualTo(1)` with `isOne()`",
        description = "Replace `isEqualTo(1)` with `isOne()`."
  )
  static final class AbstractShortAssertIsOne {
    @BeforeTemplate
    AbstractShortAssert<?> before(AbstractShortAssert<?> shortAssert) {
      return shortAssert.isEqualTo((short) 1);
    }

    @AfterTemplate
    AbstractShortAssert<?> after(AbstractShortAssert<?> shortAssert) {
      return shortAssert.isOne();
    }
  }
}
