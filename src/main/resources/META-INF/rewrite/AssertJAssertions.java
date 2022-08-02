/*
 * Copyright 2021 the original author or authors.
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
package org.assertj.core.api;
import java.io.*;

public abstract class Abstract2DArrayAssert extends AbstractAssert implements Array2DAssert {
  public abstract Abstract2DArrayAssert isDeepEqualTo(Object p0);
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractArrayAssert extends AbstractEnumerableAssert implements ArraySortedAssert {
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractAssert implements Assert {
  public static boolean throwUnsupportedExceptionOnEquals;
  public WritableAssertionInfo info;
  public WritableAssertionInfo getWritableAssertionInfo() { return (WritableAssertionInfo) (Object) null; }
  public AbstractAssert describedAs(org.assertj.core.description.Description p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert isEqualTo(Object p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert isNotEqualTo(Object p0) { return (AbstractAssert) (Object) null; }
  public void isNull() {}
  public AbstractAssert isNotNull() { return (AbstractAssert) (Object) null; }
  public AbstractAssert isSameAs(Object p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert isNotSameAs(Object p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert isIn(Object[] p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert isNotIn(Object[] p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert isIn(Iterable p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert isNotIn(Iterable p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert is(Condition p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert isNot(Condition p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert has(Condition p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert doesNotHave(Condition p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert satisfies(Condition p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert asInstanceOf(InstanceOfAssertFactory p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert isInstanceOf(Class p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert isInstanceOfSatisfying(Class p0, java.util.function.Consumer p1) { return (AbstractAssert) (Object) null; }
  public AbstractAssert isInstanceOfAny(Class[] p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert isNotInstanceOf(Class p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert isNotInstanceOfAny(Class[] p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert hasSameClassAs(Object p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert hasToString(String p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert doesNotHaveToString(String p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert doesNotHaveSameClassAs(Object p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert isExactlyInstanceOf(Class p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert isNotExactlyInstanceOf(Class p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert isOfAnyClassIn(Class[] p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert isNotOfAnyClassIn(Class[] p0) { return (AbstractAssert) (Object) null; }
  public AbstractListAssert asList() { return (AbstractListAssert) (Object) null; }
  public AbstractStringAssert asString() { return (AbstractStringAssert) (Object) null; }
  public String descriptionText() { return (String) (Object) null; }
  public AbstractAssert overridingErrorMessage(String p0, Object[] p1) { return (AbstractAssert) (Object) null; }
  public AbstractAssert overridingErrorMessage(java.util.function.Supplier p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert withFailMessage(String p0, Object[] p1) { return (AbstractAssert) (Object) null; }
  public AbstractAssert withFailMessage(java.util.function.Supplier p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert as(java.util.function.Supplier p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert usingComparator(java.util.Comparator p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert usingComparator(java.util.Comparator p0, String p1) { return (AbstractAssert) (Object) null; }
  public AbstractAssert usingDefaultComparator() { return (AbstractAssert) (Object) null; }
  public AbstractAssert withThreadDumpOnError() { return (AbstractAssert) (Object) null; }
  public AbstractAssert withRepresentation(org.assertj.core.presentation.Representation p0) { return (AbstractAssert) (Object) null; }
  public boolean equals(Object p0) { return (boolean) (Object) null; }
  public int hashCode() { return (int) (Object) null; }
  public AbstractAssert matches(java.util.function.Predicate p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert matches(java.util.function.Predicate p0, String p1) { return (AbstractAssert) (Object) null; }
  public AbstractAssert satisfies(java.util.function.Consumer p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert satisfiesAnyOf(java.util.function.Consumer p0, java.util.function.Consumer p1) { return (AbstractAssert) (Object) null; }
  public AbstractAssert satisfiesAnyOf(java.util.function.Consumer p0, java.util.function.Consumer p1, java.util.function.Consumer p2) { return (AbstractAssert) (Object) null; }
  public AbstractAssert satisfiesAnyOf(java.util.function.Consumer p0, java.util.function.Consumer p1, java.util.function.Consumer p2, java.util.function.Consumer p3) { return (AbstractAssert) (Object) null; }
  public static void setCustomRepresentation(org.assertj.core.presentation.Representation p0) {}
  public static void setPrintAssertionsDescription(boolean p0) {}
  public static void setDescriptionConsumer(java.util.function.Consumer p0) {}
  public AbstractAssert hasSameHashCodeAs(Object p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert doesNotHaveSameHashCodeAs(Object p0) { return (AbstractAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractAtomicFieldUpdaterAssert extends AbstractObjectAssert {
  public AbstractAtomicFieldUpdaterAssert hasValue(Object p0, Object p1) { return (AbstractAtomicFieldUpdaterAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractAtomicReferenceAssert extends AbstractObjectAssert {
  public AbstractAtomicReferenceAssert hasReference(Object p0) { return (AbstractAtomicReferenceAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractBigDecimalAssert extends AbstractComparableAssert implements NumberAssert {
  public AbstractBigDecimalAssert isZero() { return (AbstractBigDecimalAssert) (Object) null; }
  public AbstractBigDecimalAssert isNotZero() { return (AbstractBigDecimalAssert) (Object) null; }
  public AbstractBigDecimalAssert isOne() { return (AbstractBigDecimalAssert) (Object) null; }
  public AbstractBigDecimalAssert isPositive() { return (AbstractBigDecimalAssert) (Object) null; }
  public AbstractBigDecimalAssert isNegative() { return (AbstractBigDecimalAssert) (Object) null; }
  public AbstractBigDecimalAssert isNotPositive() { return (AbstractBigDecimalAssert) (Object) null; }
  public AbstractBigDecimalAssert isNotNegative() { return (AbstractBigDecimalAssert) (Object) null; }
  public AbstractBigDecimalAssert isBetween(java.math.BigDecimal p0, java.math.BigDecimal p1) { return (AbstractBigDecimalAssert) (Object) null; }
  public AbstractBigDecimalAssert isStrictlyBetween(java.math.BigDecimal p0, java.math.BigDecimal p1) { return (AbstractBigDecimalAssert) (Object) null; }
  public AbstractBigDecimalAssert isEqualTo(String p0) { return (AbstractBigDecimalAssert) (Object) null; }
  public AbstractBigDecimalAssert isEqualByComparingTo(String p0) { return (AbstractBigDecimalAssert) (Object) null; }
  public AbstractBigDecimalAssert isNotEqualByComparingTo(String p0) { return (AbstractBigDecimalAssert) (Object) null; }
  public AbstractBigDecimalAssert usingComparator(java.util.Comparator p0) { return (AbstractBigDecimalAssert) (Object) null; }
  public AbstractBigDecimalAssert usingComparator(java.util.Comparator p0, String p1) { return (AbstractBigDecimalAssert) (Object) null; }
  public AbstractBigDecimalAssert usingDefaultComparator() { return (AbstractBigDecimalAssert) (Object) null; }
  public AbstractBigDecimalAssert isCloseTo(java.math.BigDecimal p0, org.assertj.core.data.Offset p1) { return (AbstractBigDecimalAssert) (Object) null; }
  public AbstractBigDecimalAssert isNotCloseTo(java.math.BigDecimal p0, org.assertj.core.data.Offset p1) { return (AbstractBigDecimalAssert) (Object) null; }
  public AbstractBigDecimalAssert isCloseTo(java.math.BigDecimal p0, org.assertj.core.data.Percentage p1) { return (AbstractBigDecimalAssert) (Object) null; }
  public AbstractBigDecimalAssert isNotCloseTo(java.math.BigDecimal p0, org.assertj.core.data.Percentage p1) { return (AbstractBigDecimalAssert) (Object) null; }
  public AbstractBigDecimalAssert isLessThanOrEqualTo(java.math.BigDecimal p0) { return (AbstractBigDecimalAssert) (Object) null; }
  public AbstractBigDecimalAssert isGreaterThanOrEqualTo(java.math.BigDecimal p0) { return (AbstractBigDecimalAssert) (Object) null; }
  public AbstractComparableAssert isStrictlyBetween(Comparable p0, Comparable p1) { return (AbstractComparableAssert) (Object) null; }
  public AbstractComparableAssert isBetween(Comparable p0, Comparable p1) { return (AbstractComparableAssert) (Object) null; }
  public AbstractComparableAssert isGreaterThanOrEqualTo(Comparable p0) { return (AbstractComparableAssert) (Object) null; }
  public AbstractComparableAssert isLessThanOrEqualTo(Comparable p0) { return (AbstractComparableAssert) (Object) null; }
  public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Offset p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Offset p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isStrictlyBetween(Number p0, Number p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isBetween(Number p0, Number p1) { return (NumberAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class AbstractBigIntegerAssert extends AbstractComparableAssert implements NumberAssert {
  public AbstractBigIntegerAssert isZero() { return (AbstractBigIntegerAssert) (Object) null; }
  public AbstractBigIntegerAssert isNotZero() { return (AbstractBigIntegerAssert) (Object) null; }
  public AbstractBigIntegerAssert isOne() { return (AbstractBigIntegerAssert) (Object) null; }
  public AbstractBigIntegerAssert isPositive() { return (AbstractBigIntegerAssert) (Object) null; }
  public AbstractBigIntegerAssert isNegative() { return (AbstractBigIntegerAssert) (Object) null; }
  public AbstractBigIntegerAssert isNotNegative() { return (AbstractBigIntegerAssert) (Object) null; }
  public AbstractBigIntegerAssert isNotPositive() { return (AbstractBigIntegerAssert) (Object) null; }
  public AbstractBigIntegerAssert isCloseTo(java.math.BigInteger p0, org.assertj.core.data.Offset p1) { return (AbstractBigIntegerAssert) (Object) null; }
  public AbstractBigIntegerAssert isNotCloseTo(java.math.BigInteger p0, org.assertj.core.data.Offset p1) { return (AbstractBigIntegerAssert) (Object) null; }
  public AbstractBigIntegerAssert isCloseTo(java.math.BigInteger p0, org.assertj.core.data.Percentage p1) { return (AbstractBigIntegerAssert) (Object) null; }
  public AbstractBigIntegerAssert isNotCloseTo(java.math.BigInteger p0, org.assertj.core.data.Percentage p1) { return (AbstractBigIntegerAssert) (Object) null; }
  public AbstractBigIntegerAssert isBetween(java.math.BigInteger p0, java.math.BigInteger p1) { return (AbstractBigIntegerAssert) (Object) null; }
  public AbstractBigIntegerAssert isStrictlyBetween(java.math.BigInteger p0, java.math.BigInteger p1) { return (AbstractBigIntegerAssert) (Object) null; }
  public AbstractBigIntegerAssert isEqualTo(String p0) { return (AbstractBigIntegerAssert) (Object) null; }
  public AbstractBigIntegerAssert isEqualTo(int p0) { return (AbstractBigIntegerAssert) (Object) null; }
  public AbstractBigIntegerAssert isEqualTo(long p0) { return (AbstractBigIntegerAssert) (Object) null; }
  public AbstractBigIntegerAssert usingComparator(java.util.Comparator p0) { return (AbstractBigIntegerAssert) (Object) null; }
  public AbstractBigIntegerAssert usingComparator(java.util.Comparator p0, String p1) { return (AbstractBigIntegerAssert) (Object) null; }
  public AbstractBigIntegerAssert usingDefaultComparator() { return (AbstractBigIntegerAssert) (Object) null; }
  public AbstractComparableAssert isStrictlyBetween(Comparable p0, Comparable p1) { return (AbstractComparableAssert) (Object) null; }
  public AbstractComparableAssert isBetween(Comparable p0, Comparable p1) { return (AbstractComparableAssert) (Object) null; }
  public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Offset p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Offset p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isStrictlyBetween(Number p0, Number p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isBetween(Number p0, Number p1) { return (NumberAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractBooleanArrayAssert extends AbstractArrayAssert {
  public void isNullOrEmpty() {}
  public void isEmpty() {}
  public AbstractBooleanArrayAssert isNotEmpty() { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert hasSize(int p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert hasSizeGreaterThan(int p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert hasSizeGreaterThanOrEqualTo(int p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert hasSizeLessThan(int p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert hasSizeLessThanOrEqualTo(int p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert hasSizeBetween(int p0, int p1) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert hasSameSizeAs(Iterable p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert contains(boolean[] p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert contains(Boolean[] p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert containsOnly(boolean[] p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert containsOnly(Boolean[] p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert containsOnlyOnce(boolean[] p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert containsOnlyOnce(Boolean[] p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert containsSequence(boolean[] p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert containsSequence(Boolean[] p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert containsSubsequence(boolean[] p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert containsSubsequence(Boolean[] p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert contains(boolean p0, org.assertj.core.data.Index p1) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert doesNotContain(boolean[] p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert doesNotContain(Boolean[] p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert doesNotContain(boolean p0, org.assertj.core.data.Index p1) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert doesNotHaveDuplicates() { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert startsWith(boolean[] p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert startsWith(Boolean[] p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert endsWith(boolean[] p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert endsWith(Boolean[] p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert isSorted() { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert isSortedAccordingTo(java.util.Comparator p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert usingElementComparator(java.util.Comparator p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert usingDefaultElementComparator() { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert containsExactly(boolean[] p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert containsExactly(Boolean[] p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert containsExactlyInAnyOrder(boolean[] p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert containsExactlyInAnyOrder(Boolean[] p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert containsAnyOf(boolean[] p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public AbstractBooleanArrayAssert containsAnyOf(Boolean[] p0) { return (AbstractBooleanArrayAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractBooleanAssert extends AbstractAssert {
  public AbstractBooleanAssert isTrue() { return (AbstractBooleanAssert) (Object) null; }
  public AbstractBooleanAssert isFalse() { return (AbstractBooleanAssert) (Object) null; }
  public AbstractBooleanAssert isEqualTo(boolean p0) { return (AbstractBooleanAssert) (Object) null; }
  public AbstractBooleanAssert isNotEqualTo(boolean p0) { return (AbstractBooleanAssert) (Object) null; }
  public AbstractBooleanAssert usingComparator(java.util.Comparator p0) { return (AbstractBooleanAssert) (Object) null; }
  public AbstractBooleanAssert usingComparator(java.util.Comparator p0, String p1) { return (AbstractBooleanAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractByteArrayAssert extends AbstractArrayAssert {
  public void isNullOrEmpty() {}
  public void isEmpty() {}
  public AbstractByteArrayAssert isNotEmpty() { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert hasSize(int p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert hasSizeGreaterThan(int p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert hasSizeGreaterThanOrEqualTo(int p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert hasSizeLessThan(int p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert hasSizeLessThanOrEqualTo(int p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert hasSizeBetween(int p0, int p1) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert hasSameSizeAs(Iterable p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert contains(byte[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert contains(Byte[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert contains(int[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert containsOnly(byte[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert containsOnly(Byte[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert containsOnly(int[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert containsOnlyOnce(byte[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert containsOnlyOnce(Byte[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert containsOnlyOnce(int[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert containsSequence(byte[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert containsSequence(Byte[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert containsSequence(int[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert containsSubsequence(byte[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert containsSubsequence(Byte[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert containsSubsequence(int[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert contains(byte p0, org.assertj.core.data.Index p1) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert contains(int p0, org.assertj.core.data.Index p1) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert doesNotContain(byte[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert doesNotContain(Byte[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert doesNotContain(int[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert doesNotContain(byte p0, org.assertj.core.data.Index p1) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert doesNotContain(int p0, org.assertj.core.data.Index p1) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert doesNotHaveDuplicates() { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert startsWith(byte[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert startsWith(Byte[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert startsWith(int[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert endsWith(byte[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert endsWith(Byte[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert endsWith(int[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert isSorted() { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert isSortedAccordingTo(java.util.Comparator p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert usingElementComparator(java.util.Comparator p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert usingDefaultElementComparator() { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert containsExactly(byte[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert containsExactly(Byte[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert containsExactly(int[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert containsExactlyInAnyOrder(byte[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert containsExactlyInAnyOrder(Byte[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert containsExactlyInAnyOrder(int[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert containsAnyOf(byte[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert containsAnyOf(Byte[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractByteArrayAssert containsAnyOf(int[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractStringAssert asHexString() { return (AbstractStringAssert) (Object) null; }
  public AbstractStringAssert asString() { return (AbstractStringAssert) (Object) null; }
  public AbstractStringAssert asString(java.nio.charset.Charset p0) { return (AbstractStringAssert) (Object) null; }
  public AbstractStringAssert encodedAsBase64() { return (AbstractStringAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractByteAssert extends AbstractComparableAssert implements NumberAssert {
  public AbstractByteAssert isEqualTo(byte p0) { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert isNotEqualTo(byte p0) { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert isZero() { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert isNotZero() { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert isOne() { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert isPositive() { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert isNegative() { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert isNotNegative() { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert isNotPositive() { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert isEven() { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert isOdd() { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert isLessThan(byte p0) { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert isLessThanOrEqualTo(byte p0) { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert isGreaterThan(byte p0) { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert isGreaterThanOrEqualTo(byte p0) { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert isBetween(Byte p0, Byte p1) { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert isStrictlyBetween(Byte p0, Byte p1) { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert isCloseTo(byte p0, org.assertj.core.data.Offset p1) { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert isNotCloseTo(byte p0, org.assertj.core.data.Offset p1) { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert isCloseTo(Byte p0, org.assertj.core.data.Offset p1) { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert isNotCloseTo(Byte p0, org.assertj.core.data.Offset p1) { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert isCloseTo(Byte p0, org.assertj.core.data.Percentage p1) { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert isNotCloseTo(Byte p0, org.assertj.core.data.Percentage p1) { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert isCloseTo(byte p0, org.assertj.core.data.Percentage p1) { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert isNotCloseTo(byte p0, org.assertj.core.data.Percentage p1) { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert usingComparator(java.util.Comparator p0) { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert usingComparator(java.util.Comparator p0, String p1) { return (AbstractByteAssert) (Object) null; }
  public AbstractByteAssert usingDefaultComparator() { return (AbstractByteAssert) (Object) null; }
  public AbstractComparableAssert isStrictlyBetween(Comparable p0, Comparable p1) { return (AbstractComparableAssert) (Object) null; }
  public AbstractComparableAssert isBetween(Comparable p0, Comparable p1) { return (AbstractComparableAssert) (Object) null; }
  public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Offset p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Offset p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isStrictlyBetween(Number p0, Number p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isBetween(Number p0, Number p1) { return (NumberAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractCharArrayAssert extends AbstractArrayAssert {
  public void isNullOrEmpty() {}
  public void isEmpty() {}
  public AbstractCharArrayAssert isNotEmpty() { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert hasSize(int p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert hasSizeGreaterThan(int p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert hasSizeGreaterThanOrEqualTo(int p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert hasSizeLessThan(int p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert hasSizeLessThanOrEqualTo(int p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert hasSizeBetween(int p0, int p1) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert hasSameSizeAs(Iterable p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert contains(char[] p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert contains(Character[] p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert containsOnly(char[] p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert containsOnly(Character[] p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert containsOnlyOnce(char[] p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert containsOnlyOnce(Character[] p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert containsSequence(char[] p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert containsSequence(Character[] p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert containsSubsequence(char[] p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert containsSubsequence(Character[] p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert contains(char p0, org.assertj.core.data.Index p1) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert doesNotContain(char[] p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert doesNotContain(Character[] p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert doesNotContain(char p0, org.assertj.core.data.Index p1) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert doesNotHaveDuplicates() { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert startsWith(char[] p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert startsWith(Character[] p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert endsWith(char[] p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert endsWith(Character[] p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert isSorted() { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert isSortedAccordingTo(java.util.Comparator p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert usingElementComparator(java.util.Comparator p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert usingDefaultElementComparator() { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert containsExactly(char[] p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert containsExactly(Character[] p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert containsExactlyInAnyOrder(char[] p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert containsExactlyInAnyOrder(Character[] p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert inUnicode() { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert containsAnyOf(char[] p0) { return (AbstractCharArrayAssert) (Object) null; }
  public AbstractCharArrayAssert containsAnyOf(Character[] p0) { return (AbstractCharArrayAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractCharSequenceAssert extends AbstractAssert implements EnumerableAssert {
  public void isNullOrEmpty() {}
  public void isEmpty() {}
  public AbstractCharSequenceAssert isNotEmpty() { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert isBlank() { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert isNotBlank() { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert containsWhitespaces() { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert containsOnlyWhitespaces() { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert doesNotContainAnyWhitespaces() { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert doesNotContainOnlyWhitespaces() { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert isJavaBlank() { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert isNotJavaBlank() { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert hasSize(int p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert hasSizeLessThan(int p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert hasSizeLessThanOrEqualTo(int p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert hasSizeGreaterThan(int p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert hasSizeGreaterThanOrEqualTo(int p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert hasSizeBetween(int p0, int p1) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert hasLineCount(int p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert hasSameSizeAs(CharSequence p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert hasSameSizeAs(Object p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert hasSameSizeAs(Iterable p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert isEqualToIgnoringCase(CharSequence p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert isNotEqualToIgnoringCase(CharSequence p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert containsOnlyDigits() { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert containsOnlyOnce(CharSequence p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert contains(CharSequence[] p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert contains(Iterable p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert containsSequence(CharSequence[] p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert containsSequence(Iterable p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert containsSubsequence(CharSequence[] p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert containsSubsequence(Iterable p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert containsIgnoringCase(CharSequence p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert doesNotContain(CharSequence[] p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert doesNotContain(Iterable p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert doesNotContainIgnoringCase(CharSequence[] p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert doesNotContainPattern(CharSequence p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert doesNotContainPattern(java.util.regex.Pattern p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert startsWith(CharSequence p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert doesNotStartWith(CharSequence p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert endsWith(CharSequence p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert doesNotEndWith(CharSequence p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert matches(CharSequence p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert doesNotMatch(CharSequence p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert matches(java.util.regex.Pattern p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert doesNotMatch(java.util.regex.Pattern p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert isXmlEqualTo(CharSequence p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert isXmlEqualToContentOf(File p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert usingElementComparator(java.util.Comparator p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert usingDefaultElementComparator() { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert usingComparator(java.util.Comparator p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert usingComparator(java.util.Comparator p0, String p1) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert usingDefaultComparator() { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert inHexadecimal() { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert inUnicode() { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert isEqualToIgnoringWhitespace(CharSequence p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert isNotEqualToIgnoringWhitespace(CharSequence p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert isEqualToNormalizingWhitespace(CharSequence p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert isNotEqualToNormalizingWhitespace(CharSequence p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert isEqualToNormalizingPunctuationAndWhitespace(CharSequence p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert isSubstringOf(CharSequence p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert containsPattern(CharSequence p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert containsPattern(java.util.regex.Pattern p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert isEqualToNormalizingNewlines(CharSequence p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert isEqualToIgnoringNewLines(CharSequence p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert isLowerCase() { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert isUpperCase() { return (AbstractCharSequenceAssert) (Object) null; }
  public AbstractCharSequenceAssert isEqualToNormalizingUnicode(CharSequence p0) { return (AbstractCharSequenceAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractCharacterAssert extends AbstractComparableAssert {
  public AbstractCharacterAssert isEqualTo(char p0) { return (AbstractCharacterAssert) (Object) null; }
  public AbstractCharacterAssert isNotEqualTo(char p0) { return (AbstractCharacterAssert) (Object) null; }
  public AbstractCharacterAssert isLessThan(char p0) { return (AbstractCharacterAssert) (Object) null; }
  public AbstractCharacterAssert isLessThanOrEqualTo(char p0) { return (AbstractCharacterAssert) (Object) null; }
  public AbstractCharacterAssert isGreaterThan(char p0) { return (AbstractCharacterAssert) (Object) null; }
  public AbstractCharacterAssert inUnicode() { return (AbstractCharacterAssert) (Object) null; }
  public AbstractCharacterAssert isGreaterThanOrEqualTo(char p0) { return (AbstractCharacterAssert) (Object) null; }
  public AbstractCharacterAssert isLowerCase() { return (AbstractCharacterAssert) (Object) null; }
  public AbstractCharacterAssert isUpperCase() { return (AbstractCharacterAssert) (Object) null; }
  public AbstractCharacterAssert usingComparator(java.util.Comparator p0) { return (AbstractCharacterAssert) (Object) null; }
  public AbstractCharacterAssert usingComparator(java.util.Comparator p0, String p1) { return (AbstractCharacterAssert) (Object) null; }
  public AbstractCharacterAssert usingDefaultComparator() { return (AbstractCharacterAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractClassAssert extends AbstractAssert {
  public AbstractClassAssert isAssignableFrom(Class[] p0) { return (AbstractClassAssert) (Object) null; }
  public AbstractClassAssert isNotInterface() { return (AbstractClassAssert) (Object) null; }
  public AbstractClassAssert isInterface() { return (AbstractClassAssert) (Object) null; }
  public AbstractClassAssert isAbstract() { return (AbstractClassAssert) (Object) null; }
  public AbstractClassAssert isAnnotation() { return (AbstractClassAssert) (Object) null; }
  public AbstractClassAssert isNotAnnotation() { return (AbstractClassAssert) (Object) null; }
  public AbstractClassAssert isFinal() { return (AbstractClassAssert) (Object) null; }
  public AbstractClassAssert isNotFinal() { return (AbstractClassAssert) (Object) null; }
  public AbstractClassAssert isPublic() { return (AbstractClassAssert) (Object) null; }
  public AbstractClassAssert isProtected() { return (AbstractClassAssert) (Object) null; }
  public AbstractClassAssert isPackagePrivate() { return (AbstractClassAssert) (Object) null; }
  public AbstractClassAssert hasAnnotations(Class[] p0) { return (AbstractClassAssert) (Object) null; }
  public AbstractClassAssert hasAnnotation(Class p0) { return (AbstractClassAssert) (Object) null; }
  public AbstractClassAssert hasSuperclass(Class p0) { return (AbstractClassAssert) (Object) null; }
  public AbstractClassAssert hasNoSuperclass() { return (AbstractClassAssert) (Object) null; }
  public AbstractClassAssert hasFields(String[] p0) { return (AbstractClassAssert) (Object) null; }
  public AbstractClassAssert hasPublicFields(String[] p0) { return (AbstractClassAssert) (Object) null; }
  public AbstractClassAssert hasOnlyPublicFields(String[] p0) { return (AbstractClassAssert) (Object) null; }
  public AbstractClassAssert hasDeclaredFields(String[] p0) { return (AbstractClassAssert) (Object) null; }
  public AbstractClassAssert hasOnlyDeclaredFields(String[] p0) { return (AbstractClassAssert) (Object) null; }
  public AbstractClassAssert hasMethods(String[] p0) { return (AbstractClassAssert) (Object) null; }
  public AbstractClassAssert hasDeclaredMethods(String[] p0) { return (AbstractClassAssert) (Object) null; }
  public AbstractClassAssert hasPublicMethods(String[] p0) { return (AbstractClassAssert) (Object) null; }
  public AbstractClassAssert hasPackage(String p0) { return (AbstractClassAssert) (Object) null; }
  public AbstractClassAssert hasPackage(Package p0) { return (AbstractClassAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractComparableAssert extends AbstractObjectAssert implements ComparableAssert {
  public AbstractComparableAssert isEqualByComparingTo(Comparable p0) { return (AbstractComparableAssert) (Object) null; }
  public AbstractComparableAssert isNotEqualByComparingTo(Comparable p0) { return (AbstractComparableAssert) (Object) null; }
  public AbstractComparableAssert isLessThan(Comparable p0) { return (AbstractComparableAssert) (Object) null; }
  public AbstractComparableAssert isLessThanOrEqualTo(Comparable p0) { return (AbstractComparableAssert) (Object) null; }
  public AbstractComparableAssert isGreaterThan(Comparable p0) { return (AbstractComparableAssert) (Object) null; }
  public AbstractComparableAssert isGreaterThanOrEqualTo(Comparable p0) { return (AbstractComparableAssert) (Object) null; }
  public AbstractComparableAssert isBetween(Comparable p0, Comparable p1) { return (AbstractComparableAssert) (Object) null; }
  public AbstractComparableAssert isStrictlyBetween(Comparable p0, Comparable p1) { return (AbstractComparableAssert) (Object) null; }
  public AbstractComparableAssert usingComparator(java.util.Comparator p0) { return (AbstractComparableAssert) (Object) null; }
  public AbstractComparableAssert usingComparator(java.util.Comparator p0, String p1) { return (AbstractComparableAssert) (Object) null; }
  public AbstractComparableAssert usingDefaultComparator() { return (AbstractComparableAssert) (Object) null; }
  public AbstractComparableAssert inHexadecimal() { return (AbstractComparableAssert) (Object) null; }
  public AbstractComparableAssert inBinary() { return (AbstractComparableAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractCompletableFutureAssert extends AbstractAssert {
  public AbstractCompletableFutureAssert isDone() { return (AbstractCompletableFutureAssert) (Object) null; }
  public AbstractCompletableFutureAssert isNotDone() { return (AbstractCompletableFutureAssert) (Object) null; }
  public AbstractCompletableFutureAssert isCompletedExceptionally() { return (AbstractCompletableFutureAssert) (Object) null; }
  public AbstractCompletableFutureAssert isNotCompletedExceptionally() { return (AbstractCompletableFutureAssert) (Object) null; }
  public AbstractCompletableFutureAssert isCancelled() { return (AbstractCompletableFutureAssert) (Object) null; }
  public AbstractCompletableFutureAssert isNotCancelled() { return (AbstractCompletableFutureAssert) (Object) null; }
  public AbstractCompletableFutureAssert isCompleted() { return (AbstractCompletableFutureAssert) (Object) null; }
  public AbstractCompletableFutureAssert isNotCompleted() { return (AbstractCompletableFutureAssert) (Object) null; }
  public AbstractCompletableFutureAssert isCompletedWithValue(Object p0) { return (AbstractCompletableFutureAssert) (Object) null; }
  public AbstractCompletableFutureAssert isCompletedWithValueMatching(java.util.function.Predicate p0) { return (AbstractCompletableFutureAssert) (Object) null; }
  public AbstractCompletableFutureAssert isCompletedWithValueMatching(java.util.function.Predicate p0, String p1) { return (AbstractCompletableFutureAssert) (Object) null; }
  public AbstractCompletableFutureAssert hasFailed() { return (AbstractCompletableFutureAssert) (Object) null; }
  public AbstractCompletableFutureAssert hasNotFailed() { return (AbstractCompletableFutureAssert) (Object) null; }
  public ObjectAssert succeedsWithin(java.time.Duration p0) { return (ObjectAssert) (Object) null; }
  public ObjectAssert succeedsWithin(long p0, java.util.concurrent.TimeUnit p1) { return (ObjectAssert) (Object) null; }
  public AbstractAssert succeedsWithin(java.time.Duration p0, InstanceOfAssertFactory p1) { return (AbstractAssert) (Object) null; }
  public AbstractAssert succeedsWithin(long p0, java.util.concurrent.TimeUnit p1, InstanceOfAssertFactory p2) { return (AbstractAssert) (Object) null; }
  public AbstractThrowableAssert hasFailedWithThrowableThat() { return (AbstractThrowableAssert) (Object) null; }
  public WithThrowable failsWithin(java.time.Duration p0) { return (WithThrowable) (Object) null; }
  public WithThrowable failsWithin(long p0, java.util.concurrent.TimeUnit p1) { return (WithThrowable) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractDateAssert extends AbstractAssert {
  public AbstractDateAssert isEqualTo(String p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isEqualTo(java.time.Instant p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isEqualToIgnoringHours(String p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isEqualToIgnoringHours(java.time.Instant p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isEqualToIgnoringHours(java.util.Date p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isEqualToIgnoringMinutes(String p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isEqualToIgnoringMinutes(java.time.Instant p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isEqualToIgnoringMinutes(java.util.Date p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isEqualToIgnoringSeconds(String p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isEqualToIgnoringSeconds(java.time.Instant p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isEqualToIgnoringSeconds(java.util.Date p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isEqualToIgnoringMillis(String p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isEqualToIgnoringMillis(java.time.Instant p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isEqualToIgnoringMillis(java.util.Date p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isNotEqualTo(String p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isNotEqualTo(java.time.Instant p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isIn(String[] p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isIn(java.time.Instant[] p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInWithStringDateCollection(java.util.Collection p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isNotIn(String[] p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isNotIn(java.time.Instant[] p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isNotInWithStringDateCollection(java.util.Collection p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isBefore(java.util.Date p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isBefore(java.time.Instant p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isBefore(String p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isBeforeOrEqualsTo(java.util.Date p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isBeforeOrEqualTo(java.util.Date p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isBeforeOrEqualTo(java.time.Instant p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isBeforeOrEqualsTo(String p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isBeforeOrEqualTo(String p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isAfter(java.util.Date p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isAfter(java.time.Instant p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isAfter(String p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isAfterOrEqualsTo(java.util.Date p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isAfterOrEqualTo(java.util.Date p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isAfterOrEqualTo(java.time.Instant p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isAfterOrEqualsTo(String p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isAfterOrEqualTo(String p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isBetween(java.util.Date p0, java.util.Date p1) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isBetween(String p0, String p1) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isBetween(java.time.Instant p0, java.time.Instant p1) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isBetween(java.util.Date p0, java.util.Date p1, boolean p2, boolean p3) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isBetween(String p0, String p1, boolean p2, boolean p3) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isBetween(java.time.Instant p0, java.time.Instant p1, boolean p2, boolean p3) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isNotBetween(java.util.Date p0, java.util.Date p1, boolean p2, boolean p3) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isNotBetween(java.time.Instant p0, java.time.Instant p1, boolean p2, boolean p3) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isNotBetween(String p0, String p1, boolean p2, boolean p3) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isNotBetween(java.util.Date p0, java.util.Date p1) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isNotBetween(java.time.Instant p0, java.time.Instant p1) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isNotBetween(String p0, String p1) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInThePast() { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isToday() { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInTheFuture() { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isBeforeYear(int p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isAfterYear(int p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert hasYear(int p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isWithinYear(int p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert hasMonth(int p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isWithinMonth(int p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert hasDayOfMonth(int p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isWithinDayOfMonth(int p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert hasDayOfWeek(int p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isWithinDayOfWeek(int p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert hasHourOfDay(int p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isWithinHourOfDay(int p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert hasMinute(int p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isWithinMinute(int p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert hasSecond(int p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isWithinSecond(int p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert hasMillisecond(int p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isWithinMillisecond(int p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInSameYearAs(java.util.Date p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInSameYearAs(java.time.Instant p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInSameYearAs(String p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInSameMonthAs(java.util.Date p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInSameMonthAs(java.time.Instant p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInSameMonthAs(String p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInSameDayAs(java.util.Date p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInSameDayAs(java.time.Instant p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInSameDayAs(String p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInSameHourWindowAs(java.util.Date p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInSameHourWindowAs(java.time.Instant p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInSameHourWindowAs(String p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInSameHourAs(java.util.Date p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInSameHourAs(String p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInSameMinuteWindowAs(java.util.Date p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInSameMinuteWindowAs(java.time.Instant p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInSameMinuteWindowAs(String p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInSameMinuteAs(java.util.Date p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInSameMinuteAs(String p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInSameSecondWindowAs(java.util.Date p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInSameSecondWindowAs(java.time.Instant p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInSameSecondWindowAs(String p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInSameSecondAs(java.util.Date p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isInSameSecondAs(String p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isCloseTo(java.util.Date p0, long p1) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isCloseTo(java.time.Instant p0, long p1) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert isCloseTo(String p0, long p1) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert hasTime(long p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert hasSameTimeAs(java.util.Date p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert hasSameTimeAs(String p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert withDateFormat(java.text.DateFormat p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert withDateFormat(String p0) { return (AbstractDateAssert) (Object) null; }
  public static void setLenientDateParsing(boolean p0) {}
  public static void registerCustomDateFormat(java.text.DateFormat p0) {}
  public static void registerCustomDateFormat(String p0) {}
  public static void useDefaultDateFormatsOnly() {}
  public AbstractDateAssert withDefaultDateFormatsOnly() { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert usingComparator(java.util.Comparator p0) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert usingComparator(java.util.Comparator p0, String p1) { return (AbstractDateAssert) (Object) null; }
  public AbstractDateAssert usingDefaultComparator() { return (AbstractDateAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractDoubleArrayAssert extends AbstractArrayAssert {
  public void isNullOrEmpty() {}
  public void isEmpty() {}
  public AbstractDoubleArrayAssert isNotEmpty() { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert hasSize(int p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert hasSizeGreaterThan(int p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert hasSizeGreaterThanOrEqualTo(int p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert hasSizeLessThan(int p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert hasSizeLessThanOrEqualTo(int p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert hasSizeBetween(int p0, int p1) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert hasSameSizeAs(Iterable p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert contains(double[] p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert contains(Double[] p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert contains(double[] p0, org.assertj.core.data.Offset p1) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert contains(Double[] p0, org.assertj.core.data.Offset p1) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert containsOnly(double[] p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert containsOnly(Double[] p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert containsOnly(double[] p0, org.assertj.core.data.Offset p1) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert containsOnly(Double[] p0, org.assertj.core.data.Offset p1) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert containsOnlyOnce(double[] p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert containsOnlyOnce(Double[] p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert containsOnlyOnce(double[] p0, org.assertj.core.data.Offset p1) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert containsOnlyOnce(Double[] p0, org.assertj.core.data.Offset p1) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert containsSequence(double[] p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert containsSequence(Double[] p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert containsSequence(double[] p0, org.assertj.core.data.Offset p1) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert containsSequence(Double[] p0, org.assertj.core.data.Offset p1) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert containsSubsequence(double[] p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert containsSubsequence(Double[] p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert containsSubsequence(double[] p0, org.assertj.core.data.Offset p1) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert containsSubsequence(Double[] p0, org.assertj.core.data.Offset p1) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert contains(double p0, org.assertj.core.data.Index p1) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert contains(double p0, org.assertj.core.data.Index p1, org.assertj.core.data.Offset p2) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert doesNotContain(double[] p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert doesNotContain(Double[] p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert doesNotContain(double[] p0, org.assertj.core.data.Offset p1) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert doesNotContain(Double[] p0, org.assertj.core.data.Offset p1) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert doesNotContain(double p0, org.assertj.core.data.Index p1) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert doesNotContain(double p0, org.assertj.core.data.Index p1, org.assertj.core.data.Offset p2) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert doesNotHaveDuplicates() { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert doesNotHaveDuplicates(org.assertj.core.data.Offset p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert startsWith(double[] p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert startsWith(Double[] p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert startsWith(double[] p0, org.assertj.core.data.Offset p1) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert startsWith(Double[] p0, org.assertj.core.data.Offset p1) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert endsWith(double[] p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert endsWith(Double[] p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert endsWith(double[] p0, org.assertj.core.data.Offset p1) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert endsWith(Double[] p0, org.assertj.core.data.Offset p1) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert isSorted() { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert isSortedAccordingTo(java.util.Comparator p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert usingElementComparator(java.util.Comparator p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert usingDefaultElementComparator() { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert containsExactly(double[] p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert containsExactly(Double[] p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert containsExactlyInAnyOrder(double[] p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert containsExactlyInAnyOrder(Double[] p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert containsExactly(double[] p0, org.assertj.core.data.Offset p1) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert containsExactly(Double[] p0, org.assertj.core.data.Offset p1) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert usingComparatorWithPrecision(Double p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert containsAnyOf(double[] p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public AbstractDoubleArrayAssert containsAnyOf(Double[] p0) { return (AbstractDoubleArrayAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractDoubleAssert extends AbstractComparableAssert implements FloatingPointNumberAssert {
  public void AbstractDoubleAssert(double p0, Class p1) {}
  public AbstractDoubleAssert isNaN() { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isNotNaN() { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isZero() { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isNotZero() { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isOne() { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isPositive() { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isNegative() { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isNotNegative() { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isNotPositive() { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isCloseTo(double p0, org.assertj.core.data.Offset p1) { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isNotCloseTo(double p0, org.assertj.core.data.Offset p1) { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isCloseTo(Double p0, org.assertj.core.data.Offset p1) { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isNotCloseTo(Double p0, org.assertj.core.data.Offset p1) { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isCloseTo(Double p0, org.assertj.core.data.Percentage p1) { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isNotCloseTo(Double p0, org.assertj.core.data.Percentage p1) { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isCloseTo(double p0, org.assertj.core.data.Percentage p1) { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isNotCloseTo(double p0, org.assertj.core.data.Percentage p1) { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isEqualTo(double p0) { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isEqualTo(Double p0) { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isEqualTo(Double p0, org.assertj.core.data.Offset p1) { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isEqualTo(double p0, org.assertj.core.data.Offset p1) { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isNotEqualTo(double p0) { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isNotEqualTo(Double p0) { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isLessThan(double p0) { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isLessThanOrEqualTo(double p0) { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isLessThanOrEqualTo(Double p0) { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isGreaterThan(double p0) { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isGreaterThanOrEqualTo(double p0) { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isGreaterThanOrEqualTo(Double p0) { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isBetween(Double p0, Double p1) { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isStrictlyBetween(Double p0, Double p1) { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert usingComparator(java.util.Comparator p0) { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert usingComparator(java.util.Comparator p0, String p1) { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert usingDefaultComparator() { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isFinite() { return (AbstractDoubleAssert) (Object) null; }
  public AbstractDoubleAssert isInfinite() { return (AbstractDoubleAssert) (Object) null; }
  public AbstractComparableAssert isStrictlyBetween(Comparable p0, Comparable p1) { return (AbstractComparableAssert) (Object) null; }
  public AbstractComparableAssert isBetween(Comparable p0, Comparable p1) { return (AbstractComparableAssert) (Object) null; }
  public AbstractComparableAssert isGreaterThanOrEqualTo(Comparable p0) { return (AbstractComparableAssert) (Object) null; }
  public AbstractComparableAssert isLessThanOrEqualTo(Comparable p0) { return (AbstractComparableAssert) (Object) null; }
  public FloatingPointNumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Offset p1) { return (FloatingPointNumberAssert) (Object) null; }
  public FloatingPointNumberAssert isCloseTo(Number p0, org.assertj.core.data.Offset p1) { return (FloatingPointNumberAssert) (Object) null; }
  public FloatingPointNumberAssert isEqualTo(Number p0, org.assertj.core.data.Offset p1) { return (FloatingPointNumberAssert) (Object) null; }
  public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isStrictlyBetween(Number p0, Number p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isBetween(Number p0, Number p1) { return (NumberAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractDurationAssert extends AbstractComparableAssert {
  public AbstractDurationAssert isZero() { return (AbstractDurationAssert) (Object) null; }
  public AbstractDurationAssert isNegative() { return (AbstractDurationAssert) (Object) null; }
  public AbstractDurationAssert isPositive() { return (AbstractDurationAssert) (Object) null; }
  public AbstractDurationAssert hasNanos(long p0) { return (AbstractDurationAssert) (Object) null; }
  public AbstractDurationAssert hasMillis(long p0) { return (AbstractDurationAssert) (Object) null; }
  public AbstractDurationAssert hasSeconds(long p0) { return (AbstractDurationAssert) (Object) null; }
  public AbstractDurationAssert hasMinutes(long p0) { return (AbstractDurationAssert) (Object) null; }
  public AbstractDurationAssert hasHours(long p0) { return (AbstractDurationAssert) (Object) null; }
  public AbstractDurationAssert hasDays(long p0) { return (AbstractDurationAssert) (Object) null; }
  public AbstractDurationAssert isCloseTo(java.time.Duration p0, java.time.Duration p1) { return (AbstractDurationAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractEnumerableAssert extends AbstractAssert implements EnumerableAssert {
  public AbstractEnumerableAssert hasSameSizeAs(Object p0) { return (AbstractEnumerableAssert) (Object) null; }
  public AbstractEnumerableAssert inHexadecimal() { return (AbstractEnumerableAssert) (Object) null; }
  public AbstractEnumerableAssert inBinary() { return (AbstractEnumerableAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractFileAssert extends AbstractAssert {
  public AbstractFileAssert exists() { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert doesNotExist() { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert isFile() { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert isDirectory() { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert isAbsolute() { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert isRelative() { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert hasContentEqualTo(File p0) { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert hasSameContentAs(File p0) { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert hasSameTextualContentAs(File p0) { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert hasSameBinaryContentAs(File p0) { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert hasSameContentAs(File p0, java.nio.charset.Charset p1) { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert hasSameTextualContentAs(File p0, java.nio.charset.Charset p1) { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert hasBinaryContent(byte[] p0) { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert hasSize(long p0) { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert usingCharset(String p0) { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert usingCharset(java.nio.charset.Charset p0) { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert hasContent(String p0) { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert canWrite() { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert canRead() { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert hasParent(File p0) { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert hasParent(String p0) { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert hasExtension(String p0) { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert hasName(String p0) { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert hasNoParent() { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert hasDigest(java.security.MessageDigest p0, byte[] p1) { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert hasDigest(java.security.MessageDigest p0, String p1) { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert hasDigest(String p0, byte[] p1) { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert hasDigest(String p0, String p1) { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert isDirectoryContaining(java.util.function.Predicate p0) { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert isDirectoryContaining(String p0) { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert isDirectoryRecursivelyContaining(String p0) { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert isDirectoryRecursivelyContaining(java.util.function.Predicate p0) { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert isDirectoryNotContaining(java.util.function.Predicate p0) { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert isDirectoryNotContaining(String p0) { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert isEmptyDirectory() { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert isNotEmptyDirectory() { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert isEmpty() { return (AbstractFileAssert) (Object) null; }
  public AbstractFileAssert isNotEmpty() { return (AbstractFileAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractFloatArrayAssert extends AbstractArrayAssert {
  public void isNullOrEmpty() {}
  public void isEmpty() {}
  public AbstractFloatArrayAssert isNotEmpty() { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert hasSize(int p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert hasSizeGreaterThan(int p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert hasSizeGreaterThanOrEqualTo(int p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert hasSizeLessThan(int p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert hasSizeLessThanOrEqualTo(int p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert hasSizeBetween(int p0, int p1) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert hasSameSizeAs(Iterable p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert contains(float[] p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert contains(Float[] p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert contains(float[] p0, org.assertj.core.data.Offset p1) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert contains(Float[] p0, org.assertj.core.data.Offset p1) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert containsOnly(float[] p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert containsOnly(Float[] p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert containsOnly(float[] p0, org.assertj.core.data.Offset p1) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert containsOnly(Float[] p0, org.assertj.core.data.Offset p1) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert containsOnlyOnce(float[] p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert containsOnlyOnce(Float[] p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert containsOnlyOnce(float[] p0, org.assertj.core.data.Offset p1) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert containsOnlyOnce(Float[] p0, org.assertj.core.data.Offset p1) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert containsSequence(float[] p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert containsSequence(Float[] p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert containsSequence(float[] p0, org.assertj.core.data.Offset p1) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert containsSequence(Float[] p0, org.assertj.core.data.Offset p1) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert containsSubsequence(float[] p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert containsSubsequence(Float[] p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert containsSubsequence(float[] p0, org.assertj.core.data.Offset p1) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert containsSubsequence(Float[] p0, org.assertj.core.data.Offset p1) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert contains(float p0, org.assertj.core.data.Index p1) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert contains(float p0, org.assertj.core.data.Index p1, org.assertj.core.data.Offset p2) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert doesNotContain(float[] p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert doesNotContain(Float[] p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert doesNotContain(float[] p0, org.assertj.core.data.Offset p1) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert doesNotContain(Float[] p0, org.assertj.core.data.Offset p1) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert doesNotContain(float p0, org.assertj.core.data.Index p1) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert doesNotContain(float p0, org.assertj.core.data.Index p1, org.assertj.core.data.Offset p2) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert doesNotHaveDuplicates() { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert doesNotHaveDuplicates(org.assertj.core.data.Offset p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert startsWith(float[] p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert startsWith(Float[] p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert startsWith(float[] p0, org.assertj.core.data.Offset p1) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert startsWith(Float[] p0, org.assertj.core.data.Offset p1) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert endsWith(float[] p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert endsWith(Float[] p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert endsWith(float[] p0, org.assertj.core.data.Offset p1) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert endsWith(Float[] p0, org.assertj.core.data.Offset p1) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert isSorted() { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert isSortedAccordingTo(java.util.Comparator p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert usingElementComparator(java.util.Comparator p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert usingDefaultElementComparator() { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert containsExactly(float[] p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert containsExactly(Float[] p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert containsExactly(float[] p0, org.assertj.core.data.Offset p1) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert containsExactly(Float[] p0, org.assertj.core.data.Offset p1) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert containsExactlyInAnyOrder(float[] p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert containsExactlyInAnyOrder(Float[] p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert usingComparatorWithPrecision(Float p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert containsAnyOf(float[] p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public AbstractFloatArrayAssert containsAnyOf(Float[] p0) { return (AbstractFloatArrayAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractFloatAssert extends AbstractComparableAssert implements FloatingPointNumberAssert {
  public void AbstractFloatAssert(float p0, Class p1) {}
  public AbstractFloatAssert isNaN() { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isNotNaN() { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isZero() { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isNotZero() { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isOne() { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isPositive() { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isNegative() { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isNotNegative() { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isNotPositive() { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isEqualTo(float p0) { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isEqualTo(Float p0) { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isCloseTo(float p0, org.assertj.core.data.Offset p1) { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isNotCloseTo(float p0, org.assertj.core.data.Offset p1) { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isCloseTo(Float p0, org.assertj.core.data.Offset p1) { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isNotCloseTo(Float p0, org.assertj.core.data.Offset p1) { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isCloseTo(Float p0, org.assertj.core.data.Percentage p1) { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isNotCloseTo(Float p0, org.assertj.core.data.Percentage p1) { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isCloseTo(float p0, org.assertj.core.data.Percentage p1) { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isNotCloseTo(float p0, org.assertj.core.data.Percentage p1) { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isEqualTo(Float p0, org.assertj.core.data.Offset p1) { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isEqualTo(float p0, org.assertj.core.data.Offset p1) { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isNotEqualTo(float p0) { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isNotEqualTo(Float p0) { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isLessThan(float p0) { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isLessThanOrEqualTo(float p0) { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isLessThanOrEqualTo(Float p0) { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isGreaterThan(float p0) { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isGreaterThanOrEqualTo(float p0) { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isGreaterThanOrEqualTo(Float p0) { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isBetween(Float p0, Float p1) { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isStrictlyBetween(Float p0, Float p1) { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert usingComparator(java.util.Comparator p0) { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert usingComparator(java.util.Comparator p0, String p1) { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert usingDefaultComparator() { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isFinite() { return (AbstractFloatAssert) (Object) null; }
  public AbstractFloatAssert isInfinite() { return (AbstractFloatAssert) (Object) null; }
  public AbstractComparableAssert isStrictlyBetween(Comparable p0, Comparable p1) { return (AbstractComparableAssert) (Object) null; }
  public AbstractComparableAssert isBetween(Comparable p0, Comparable p1) { return (AbstractComparableAssert) (Object) null; }
  public AbstractComparableAssert isGreaterThanOrEqualTo(Comparable p0) { return (AbstractComparableAssert) (Object) null; }
  public AbstractComparableAssert isLessThanOrEqualTo(Comparable p0) { return (AbstractComparableAssert) (Object) null; }
  public FloatingPointNumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Offset p1) { return (FloatingPointNumberAssert) (Object) null; }
  public FloatingPointNumberAssert isCloseTo(Number p0, org.assertj.core.data.Offset p1) { return (FloatingPointNumberAssert) (Object) null; }
  public FloatingPointNumberAssert isEqualTo(Number p0, org.assertj.core.data.Offset p1) { return (FloatingPointNumberAssert) (Object) null; }
  public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isStrictlyBetween(Number p0, Number p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isBetween(Number p0, Number p1) { return (NumberAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractFutureAssert extends AbstractAssert {
  public AbstractFutureAssert isCancelled() { return (AbstractFutureAssert) (Object) null; }
  public AbstractFutureAssert isNotCancelled() { return (AbstractFutureAssert) (Object) null; }
  public AbstractFutureAssert isDone() { return (AbstractFutureAssert) (Object) null; }
  public AbstractFutureAssert isNotDone() { return (AbstractFutureAssert) (Object) null; }
  public ObjectAssert succeedsWithin(java.time.Duration p0) { return (ObjectAssert) (Object) null; }
  public ObjectAssert succeedsWithin(long p0, java.util.concurrent.TimeUnit p1) { return (ObjectAssert) (Object) null; }
  public AbstractAssert succeedsWithin(java.time.Duration p0, InstanceOfAssertFactory p1) { return (AbstractAssert) (Object) null; }
  public AbstractAssert succeedsWithin(long p0, java.util.concurrent.TimeUnit p1, InstanceOfAssertFactory p2) { return (AbstractAssert) (Object) null; }
  public WithThrowable failsWithin(java.time.Duration p0) { return (WithThrowable) (Object) null; }
  public WithThrowable failsWithin(long p0, java.util.concurrent.TimeUnit p1) { return (WithThrowable) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractInputStreamAssert extends AbstractAssert {
  public AbstractInputStreamAssert hasContentEqualTo(InputStream p0) { return (AbstractInputStreamAssert) (Object) null; }
  public AbstractInputStreamAssert hasSameContentAs(InputStream p0) { return (AbstractInputStreamAssert) (Object) null; }
  public AbstractInputStreamAssert isEmpty() { return (AbstractInputStreamAssert) (Object) null; }
  public AbstractInputStreamAssert isNotEmpty() { return (AbstractInputStreamAssert) (Object) null; }
  public AbstractInputStreamAssert hasContent(String p0) { return (AbstractInputStreamAssert) (Object) null; }
  public AbstractInputStreamAssert hasBinaryContent(byte[] p0) { return (AbstractInputStreamAssert) (Object) null; }
  public AbstractInputStreamAssert hasDigest(java.security.MessageDigest p0, byte[] p1) { return (AbstractInputStreamAssert) (Object) null; }
  public AbstractInputStreamAssert hasDigest(java.security.MessageDigest p0, String p1) { return (AbstractInputStreamAssert) (Object) null; }
  public AbstractInputStreamAssert hasDigest(String p0, byte[] p1) { return (AbstractInputStreamAssert) (Object) null; }
  public AbstractInputStreamAssert hasDigest(String p0, String p1) { return (AbstractInputStreamAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class AbstractInstantAssert extends AbstractTemporalAssert {
  public AbstractInstantAssert isBefore(java.time.Instant p0) { return (AbstractInstantAssert) (Object) null; }
  public AbstractInstantAssert isBefore(String p0) { return (AbstractInstantAssert) (Object) null; }
  public AbstractInstantAssert isBeforeOrEqualTo(java.time.Instant p0) { return (AbstractInstantAssert) (Object) null; }
  public AbstractInstantAssert isBeforeOrEqualTo(String p0) { return (AbstractInstantAssert) (Object) null; }
  public AbstractInstantAssert isAfterOrEqualTo(java.time.Instant p0) { return (AbstractInstantAssert) (Object) null; }
  public AbstractInstantAssert isAfterOrEqualTo(String p0) { return (AbstractInstantAssert) (Object) null; }
  public AbstractInstantAssert isAfter(java.time.Instant p0) { return (AbstractInstantAssert) (Object) null; }
  public AbstractInstantAssert isAfter(String p0) { return (AbstractInstantAssert) (Object) null; }
  public AbstractInstantAssert isEqualTo(String p0) { return (AbstractInstantAssert) (Object) null; }
  public AbstractInstantAssert isNotEqualTo(String p0) { return (AbstractInstantAssert) (Object) null; }
  public AbstractInstantAssert isIn(String[] p0) { return (AbstractInstantAssert) (Object) null; }
  public AbstractInstantAssert isNotIn(String[] p0) { return (AbstractInstantAssert) (Object) null; }
  public AbstractInstantAssert isBetween(java.time.Instant p0, java.time.Instant p1) { return (AbstractInstantAssert) (Object) null; }
  public AbstractInstantAssert isBetween(String p0, String p1) { return (AbstractInstantAssert) (Object) null; }
  public AbstractInstantAssert isStrictlyBetween(java.time.Instant p0, java.time.Instant p1) { return (AbstractInstantAssert) (Object) null; }
  public AbstractInstantAssert isStrictlyBetween(String p0, String p1) { return (AbstractInstantAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractIntArrayAssert extends AbstractArrayAssert {
  public void isNullOrEmpty() {}
  public void isEmpty() {}
  public AbstractIntArrayAssert isNotEmpty() { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert hasSize(int p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert hasSizeGreaterThan(int p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert hasSizeGreaterThanOrEqualTo(int p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert hasSizeLessThan(int p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert hasSizeLessThanOrEqualTo(int p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert hasSizeBetween(int p0, int p1) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert hasSameSizeAs(Iterable p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert contains(int[] p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert contains(Integer[] p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert containsOnly(int[] p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert containsOnly(Integer[] p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert containsOnlyOnce(int[] p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert containsOnlyOnce(Integer[] p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert containsSequence(int[] p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert containsSequence(Integer[] p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert containsSubsequence(int[] p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert containsSubsequence(Integer[] p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert contains(int p0, org.assertj.core.data.Index p1) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert doesNotContain(int[] p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert doesNotContain(Integer[] p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert doesNotContain(int p0, org.assertj.core.data.Index p1) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert doesNotHaveDuplicates() { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert startsWith(int[] p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert startsWith(Integer[] p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert endsWith(int[] p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert endsWith(Integer[] p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert isSorted() { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert isSortedAccordingTo(java.util.Comparator p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert usingElementComparator(java.util.Comparator p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert usingDefaultElementComparator() { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert containsExactly(int[] p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert containsExactly(Integer[] p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert containsExactlyInAnyOrder(int[] p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert containsExactlyInAnyOrder(Integer[] p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert containsAnyOf(int[] p0) { return (AbstractIntArrayAssert) (Object) null; }
  public AbstractIntArrayAssert containsAnyOf(Integer[] p0) { return (AbstractIntArrayAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractIntegerAssert extends AbstractComparableAssert implements NumberAssert {
  public AbstractIntegerAssert isEqualTo(int p0) { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert isEqualTo(long p0) { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert isNotEqualTo(int p0) { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert isZero() { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert isNotZero() { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert isOne() { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert isPositive() { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert isNegative() { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert isNotNegative() { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert isNotPositive() { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert isEven() { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert isOdd() { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert isLessThan(int p0) { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert isLessThanOrEqualTo(int p0) { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert isGreaterThan(int p0) { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert isGreaterThanOrEqualTo(int p0) { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert isBetween(Integer p0, Integer p1) { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert isStrictlyBetween(Integer p0, Integer p1) { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert isCloseTo(int p0, org.assertj.core.data.Offset p1) { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert isNotCloseTo(int p0, org.assertj.core.data.Offset p1) { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert isCloseTo(Integer p0, org.assertj.core.data.Offset p1) { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert isNotCloseTo(Integer p0, org.assertj.core.data.Offset p1) { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert isCloseTo(Integer p0, org.assertj.core.data.Percentage p1) { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert isNotCloseTo(Integer p0, org.assertj.core.data.Percentage p1) { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert isCloseTo(int p0, org.assertj.core.data.Percentage p1) { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert isNotCloseTo(int p0, org.assertj.core.data.Percentage p1) { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert usingComparator(java.util.Comparator p0) { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert usingComparator(java.util.Comparator p0, String p1) { return (AbstractIntegerAssert) (Object) null; }
  public AbstractIntegerAssert usingDefaultComparator() { return (AbstractIntegerAssert) (Object) null; }
  public AbstractComparableAssert isStrictlyBetween(Comparable p0, Comparable p1) { return (AbstractComparableAssert) (Object) null; }
  public AbstractComparableAssert isBetween(Comparable p0, Comparable p1) { return (AbstractComparableAssert) (Object) null; }
  public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Offset p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Offset p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isStrictlyBetween(Number p0, Number p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isBetween(Number p0, Number p1) { return (NumberAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractIterableAssert extends AbstractAssert implements ObjectEnumerableAssert {
  public void isNullOrEmpty() {}
  public void isEmpty() {}
  public AbstractIterableAssert isNotEmpty() { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert hasSize(int p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert hasSizeGreaterThan(int p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert hasSizeGreaterThanOrEqualTo(int p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert hasSizeLessThan(int p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert hasSizeLessThanOrEqualTo(int p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert hasSizeBetween(int p0, int p1) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert hasOnlyOneElementSatisfying(java.util.function.Consumer p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert hasSameSizeAs(Object p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert hasSameSizeAs(Iterable p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert contains(Object[] p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert containsOnly(Object[] p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert containsOnlyOnce(Object[] p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert containsOnlyNulls() { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert containsExactly(Object[] p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert containsExactlyInAnyOrder(Object[] p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert containsExactlyInAnyOrderElementsOf(Iterable p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert isSubsetOf(Iterable p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert isSubsetOf(Object[] p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert containsSequence(Object[] p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert containsSequence(Iterable p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert doesNotContainSequence(Object[] p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert doesNotContainSequence(Iterable p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert containsSubsequence(Object[] p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert containsSubsequence(Iterable p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert doesNotContainSubsequence(Object[] p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert doesNotContainSubsequence(Iterable p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert doesNotContain(Object[] p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert doesNotContainAnyElementsOf(Iterable p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert doesNotHaveDuplicates() { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert startsWith(Object[] p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert endsWith(Object p0, Object[] p1) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert endsWith(Object[] p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert containsNull() { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert doesNotContainNull() { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert are(Condition p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert areNot(Condition p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert have(Condition p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert doNotHave(Condition p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert areAtLeastOne(Condition p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert areAtLeast(int p0, Condition p1) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert areAtMost(int p0, Condition p1) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert areExactly(int p0, Condition p1) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert haveAtLeastOne(Condition p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert haveAtLeast(int p0, Condition p1) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert haveAtMost(int p0, Condition p1) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert haveExactly(int p0, Condition p1) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert hasAtLeastOneElementOfType(Class p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert hasOnlyElementsOfType(Class p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert doesNotHaveAnyElementsOfTypes(Class[] p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert hasOnlyElementsOfTypes(Class[] p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert containsAll(Iterable p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert usingElementComparator(java.util.Comparator p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert usingDefaultElementComparator() { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert containsAnyOf(Object[] p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert containsAnyElementsOf(Iterable p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractListAssert extracting(String p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert extractingResultOf(String p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert extractingResultOf(String p0, Class p1) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert extracting(String p0, Class p1) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert extracting(String[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert extracting(java.util.function.Function p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert map(java.util.function.Function p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert extracting(org.assertj.core.api.iterable.ThrowingExtractor p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert map(org.assertj.core.api.iterable.ThrowingExtractor p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert flatExtracting(java.util.function.Function p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert flatMap(java.util.function.Function p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert flatExtracting(org.assertj.core.api.iterable.ThrowingExtractor p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert flatMap(org.assertj.core.api.iterable.ThrowingExtractor p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert flatExtracting(java.util.function.Function[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert flatMap(java.util.function.Function[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert flatExtracting(org.assertj.core.api.iterable.ThrowingExtractor[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert flatMap(org.assertj.core.api.iterable.ThrowingExtractor[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert flatExtracting(String p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert extracting(java.util.function.Function[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert map(java.util.function.Function[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert flatExtracting(String[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractIterableAssert containsExactlyElementsOf(Iterable p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert containsOnlyElementsOf(Iterable p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert containsOnlyOnceElementsOf(Iterable p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert hasSameElementsAs(Iterable p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert usingComparatorForElementFieldsWithNames(java.util.Comparator p0, String[] p1) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert usingComparatorForElementFieldsWithType(java.util.Comparator p0, Class p1) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert usingComparatorForType(java.util.Comparator p0, Class p1) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert usingFieldByFieldElementComparator() { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert usingRecursiveFieldByFieldElementComparator() { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert usingRecursiveFieldByFieldElementComparator(org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration p0) { return (AbstractIterableAssert) (Object) null; }
  public RecursiveComparisonAssert usingRecursiveComparison() { return (RecursiveComparisonAssert) (Object) null; }
  public RecursiveComparisonAssert usingRecursiveComparison(org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration p0) { return (RecursiveComparisonAssert) (Object) null; }
  public AbstractIterableAssert usingElementComparatorOnFields(String[] p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert usingElementComparatorIgnoringFields(String[] p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert inHexadecimal() { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert inBinary() { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert filteredOn(String p0, Object p1) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert filteredOnNull(String p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert filteredOn(String p0, org.assertj.core.api.filter.FilterOperator p1) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert filteredOn(Condition p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert filteredOn(java.util.function.Function p0, Object p1) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert filteredOnAssertions(java.util.function.Consumer p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractAssert first() { return (AbstractAssert) (Object) null; }
  public AbstractAssert first(InstanceOfAssertFactory p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert last() { return (AbstractAssert) (Object) null; }
  public AbstractAssert last(InstanceOfAssertFactory p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert element(int p0) { return (AbstractAssert) (Object) null; }
  public AbstractAssert element(int p0, InstanceOfAssertFactory p1) { return (AbstractAssert) (Object) null; }
  public AbstractAssert singleElement() { return (AbstractAssert) (Object) null; }
  public AbstractAssert singleElement(InstanceOfAssertFactory p0) { return (AbstractAssert) (Object) null; }
  public AbstractIterableAssert filteredOn(java.util.function.Predicate p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert allMatch(java.util.function.Predicate p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert allMatch(java.util.function.Predicate p0, String p1) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert allSatisfy(java.util.function.Consumer p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert anyMatch(java.util.function.Predicate p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert zipSatisfy(Iterable p0, java.util.function.BiConsumer p1) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert anySatisfy(java.util.function.Consumer p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert noneSatisfy(java.util.function.Consumer p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert satisfiesExactly(java.util.function.Consumer[] p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert satisfiesExactlyInAnyOrder(java.util.function.Consumer[] p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert as(String p0, Object[] p1) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert as(org.assertj.core.description.Description p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert describedAs(org.assertj.core.description.Description p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert describedAs(String p0, Object[] p1) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert doesNotHave(Condition p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert doesNotHaveSameClassAs(Object p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert has(Condition p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert hasSameClassAs(Object p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert hasToString(String p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert is(Condition p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert isEqualTo(Object p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert isExactlyInstanceOf(Class p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert isIn(Iterable p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert isIn(Object[] p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert isInstanceOf(Class p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert isInstanceOfAny(Class[] p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert isNot(Condition p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert isNotEqualTo(Object p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert isNotExactlyInstanceOf(Class p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert isNotIn(Iterable p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert isNotIn(Object[] p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert isNotInstanceOf(Class p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert isNotInstanceOfAny(Class[] p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert isNotOfAnyClassIn(Class[] p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert isNotNull() { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert isNotSameAs(Object p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert isOfAnyClassIn(Class[] p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert isSameAs(Object p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert noneMatch(java.util.function.Predicate p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert overridingErrorMessage(String p0, Object[] p1) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert usingDefaultComparator() { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert usingComparator(java.util.Comparator p0) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert usingComparator(java.util.Comparator p0, String p1) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert withFailMessage(String p0, Object[] p1) { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableAssert withThreadDumpOnError() { return (AbstractIterableAssert) (Object) null; }
  public AbstractIterableSizeAssert size() { return (AbstractIterableSizeAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractIterableSizeAssert extends AbstractIntegerAssert {
  public abstract AbstractIterableAssert returnToIterable();
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractIteratorAssert extends AbstractAssert {
  public AbstractIteratorAssert hasNext() { return (AbstractIteratorAssert) (Object) null; }
  public AbstractIteratorAssert isExhausted() { return (AbstractIteratorAssert) (Object) null; }
  public IterableAssert toIterable() { return (IterableAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractListAssert extends AbstractIterableAssert implements IndexedObjectEnumerableAssert {
  public AbstractListAssert contains(Object p0, org.assertj.core.data.Index p1) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert doesNotContain(Object p0, org.assertj.core.data.Index p1) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert has(Condition p0, org.assertj.core.data.Index p1) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert is(Condition p0, org.assertj.core.data.Index p1) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert isSorted() { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert isSortedAccordingTo(java.util.Comparator p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert satisfies(java.util.function.Consumer p0, org.assertj.core.data.Index p1) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert usingElementComparator(java.util.Comparator p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert usingDefaultElementComparator() { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert as(String p0, Object[] p1) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert as(org.assertj.core.description.Description p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert describedAs(org.assertj.core.description.Description p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert describedAs(String p0, Object[] p1) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert doesNotHave(Condition p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert doesNotHaveSameClassAs(Object p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert has(Condition p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert hasSameClassAs(Object p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert hasToString(String p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert is(Condition p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert isEqualTo(Object p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert isExactlyInstanceOf(Class p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert isIn(Iterable p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert isIn(Object[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert isInstanceOf(Class p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert isInstanceOfAny(Class[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert isNot(Condition p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert isNotEqualTo(Object p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert isNotExactlyInstanceOf(Class p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert isNotIn(Iterable p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert isNotIn(Object[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert isNotInstanceOf(Class p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert isNotInstanceOfAny(Class[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert isNotOfAnyClassIn(Class[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert isNotNull() { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert isNotSameAs(Object p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert isOfAnyClassIn(Class[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert isSameAs(Object p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert overridingErrorMessage(String p0, Object[] p1) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert usingDefaultComparator() { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert usingComparator(java.util.Comparator p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert usingComparator(java.util.Comparator p0, String p1) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert withFailMessage(String p0, Object[] p1) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert withThreadDumpOnError() { return (AbstractListAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractLocalDateAssert extends AbstractTemporalAssert {
  public static String NULL_LOCAL_DATE_TIME_PARAMETER_MESSAGE;
  public AbstractLocalDateAssert isBefore(java.time.LocalDate p0) { return (AbstractLocalDateAssert) (Object) null; }
  public AbstractLocalDateAssert isBefore(String p0) { return (AbstractLocalDateAssert) (Object) null; }
  public AbstractLocalDateAssert isBeforeOrEqualTo(java.time.LocalDate p0) { return (AbstractLocalDateAssert) (Object) null; }
  public AbstractLocalDateAssert isBeforeOrEqualTo(String p0) { return (AbstractLocalDateAssert) (Object) null; }
  public AbstractLocalDateAssert isAfterOrEqualTo(java.time.LocalDate p0) { return (AbstractLocalDateAssert) (Object) null; }
  public AbstractLocalDateAssert isAfterOrEqualTo(String p0) { return (AbstractLocalDateAssert) (Object) null; }
  public AbstractLocalDateAssert isAfter(java.time.LocalDate p0) { return (AbstractLocalDateAssert) (Object) null; }
  public AbstractLocalDateAssert isAfter(String p0) { return (AbstractLocalDateAssert) (Object) null; }
  public AbstractLocalDateAssert isEqualTo(String p0) { return (AbstractLocalDateAssert) (Object) null; }
  public AbstractLocalDateAssert isNotEqualTo(String p0) { return (AbstractLocalDateAssert) (Object) null; }
  public AbstractLocalDateAssert isIn(String[] p0) { return (AbstractLocalDateAssert) (Object) null; }
  public AbstractLocalDateAssert isNotIn(String[] p0) { return (AbstractLocalDateAssert) (Object) null; }
  public AbstractLocalDateAssert isToday() { return (AbstractLocalDateAssert) (Object) null; }
  public AbstractLocalDateAssert isBetween(java.time.LocalDate p0, java.time.LocalDate p1) { return (AbstractLocalDateAssert) (Object) null; }
  public AbstractLocalDateAssert isBetween(String p0, String p1) { return (AbstractLocalDateAssert) (Object) null; }
  public AbstractLocalDateAssert isStrictlyBetween(java.time.LocalDate p0, java.time.LocalDate p1) { return (AbstractLocalDateAssert) (Object) null; }
  public AbstractLocalDateAssert isStrictlyBetween(String p0, String p1) { return (AbstractLocalDateAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractLocalDateTimeAssert extends AbstractTemporalAssert {
  public static String NULL_LOCAL_DATE_TIME_PARAMETER_MESSAGE;
  public AbstractLocalDateTimeAssert isBefore(java.time.LocalDateTime p0) { return (AbstractLocalDateTimeAssert) (Object) null; }
  public AbstractLocalDateTimeAssert isBefore(String p0) { return (AbstractLocalDateTimeAssert) (Object) null; }
  public AbstractLocalDateTimeAssert isBeforeOrEqualTo(java.time.LocalDateTime p0) { return (AbstractLocalDateTimeAssert) (Object) null; }
  public AbstractLocalDateTimeAssert isBeforeOrEqualTo(String p0) { return (AbstractLocalDateTimeAssert) (Object) null; }
  public AbstractLocalDateTimeAssert isAfterOrEqualTo(java.time.LocalDateTime p0) { return (AbstractLocalDateTimeAssert) (Object) null; }
  public AbstractLocalDateTimeAssert isAfterOrEqualTo(String p0) { return (AbstractLocalDateTimeAssert) (Object) null; }
  public AbstractLocalDateTimeAssert isAfter(java.time.LocalDateTime p0) { return (AbstractLocalDateTimeAssert) (Object) null; }
  public AbstractLocalDateTimeAssert isAfter(String p0) { return (AbstractLocalDateTimeAssert) (Object) null; }
  public AbstractLocalDateTimeAssert isEqualTo(Object p0) { return (AbstractLocalDateTimeAssert) (Object) null; }
  public AbstractLocalDateTimeAssert isEqualTo(String p0) { return (AbstractLocalDateTimeAssert) (Object) null; }
  public AbstractLocalDateTimeAssert isNotEqualTo(Object p0) { return (AbstractLocalDateTimeAssert) (Object) null; }
  public AbstractLocalDateTimeAssert isNotEqualTo(String p0) { return (AbstractLocalDateTimeAssert) (Object) null; }
  public AbstractLocalDateTimeAssert isIn(String[] p0) { return (AbstractLocalDateTimeAssert) (Object) null; }
  public AbstractLocalDateTimeAssert isNotIn(String[] p0) { return (AbstractLocalDateTimeAssert) (Object) null; }
  public AbstractLocalDateTimeAssert isCloseToUtcNow(org.assertj.core.data.TemporalUnitOffset p0) { return (AbstractLocalDateTimeAssert) (Object) null; }
  public AbstractLocalDateTimeAssert usingDefaultComparator() { return (AbstractLocalDateTimeAssert) (Object) null; }
  public AbstractLocalDateTimeAssert isEqualToIgnoringNanos(java.time.LocalDateTime p0) { return (AbstractLocalDateTimeAssert) (Object) null; }
  public AbstractLocalDateTimeAssert isEqualToIgnoringSeconds(java.time.LocalDateTime p0) { return (AbstractLocalDateTimeAssert) (Object) null; }
  public AbstractLocalDateTimeAssert isEqualToIgnoringMinutes(java.time.LocalDateTime p0) { return (AbstractLocalDateTimeAssert) (Object) null; }
  public AbstractLocalDateTimeAssert isEqualToIgnoringHours(java.time.LocalDateTime p0) { return (AbstractLocalDateTimeAssert) (Object) null; }
  public AbstractLocalDateTimeAssert isBetween(java.time.LocalDateTime p0, java.time.LocalDateTime p1) { return (AbstractLocalDateTimeAssert) (Object) null; }
  public AbstractLocalDateTimeAssert isBetween(String p0, String p1) { return (AbstractLocalDateTimeAssert) (Object) null; }
  public AbstractLocalDateTimeAssert isStrictlyBetween(java.time.LocalDateTime p0, java.time.LocalDateTime p1) { return (AbstractLocalDateTimeAssert) (Object) null; }
  public AbstractLocalDateTimeAssert isStrictlyBetween(String p0, String p1) { return (AbstractLocalDateTimeAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractLocalTimeAssert extends AbstractTemporalAssert {
  public static String NULL_LOCAL_TIME_PARAMETER_MESSAGE;
  public AbstractLocalTimeAssert isBefore(java.time.LocalTime p0) { return (AbstractLocalTimeAssert) (Object) null; }
  public AbstractLocalTimeAssert isBefore(String p0) { return (AbstractLocalTimeAssert) (Object) null; }
  public AbstractLocalTimeAssert isBeforeOrEqualTo(java.time.LocalTime p0) { return (AbstractLocalTimeAssert) (Object) null; }
  public AbstractLocalTimeAssert isBeforeOrEqualTo(String p0) { return (AbstractLocalTimeAssert) (Object) null; }
  public AbstractLocalTimeAssert isAfterOrEqualTo(java.time.LocalTime p0) { return (AbstractLocalTimeAssert) (Object) null; }
  public AbstractLocalTimeAssert isAfterOrEqualTo(String p0) { return (AbstractLocalTimeAssert) (Object) null; }
  public AbstractLocalTimeAssert isAfter(java.time.LocalTime p0) { return (AbstractLocalTimeAssert) (Object) null; }
  public AbstractLocalTimeAssert isAfter(String p0) { return (AbstractLocalTimeAssert) (Object) null; }
  public AbstractLocalTimeAssert isEqualTo(String p0) { return (AbstractLocalTimeAssert) (Object) null; }
  public AbstractLocalTimeAssert isNotEqualTo(String p0) { return (AbstractLocalTimeAssert) (Object) null; }
  public AbstractLocalTimeAssert isIn(String[] p0) { return (AbstractLocalTimeAssert) (Object) null; }
  public AbstractLocalTimeAssert isNotIn(String[] p0) { return (AbstractLocalTimeAssert) (Object) null; }
  public AbstractLocalTimeAssert isEqualToIgnoringNanos(java.time.LocalTime p0) { return (AbstractLocalTimeAssert) (Object) null; }
  public AbstractLocalTimeAssert isEqualToIgnoringSeconds(java.time.LocalTime p0) { return (AbstractLocalTimeAssert) (Object) null; }
  public AbstractLocalTimeAssert hasSameHourAs(java.time.LocalTime p0) { return (AbstractLocalTimeAssert) (Object) null; }
  public AbstractLocalTimeAssert isBetween(java.time.LocalTime p0, java.time.LocalTime p1) { return (AbstractLocalTimeAssert) (Object) null; }
  public AbstractLocalTimeAssert isBetween(String p0, String p1) { return (AbstractLocalTimeAssert) (Object) null; }
  public AbstractLocalTimeAssert isStrictlyBetween(java.time.LocalTime p0, java.time.LocalTime p1) { return (AbstractLocalTimeAssert) (Object) null; }
  public AbstractLocalTimeAssert isStrictlyBetween(String p0, String p1) { return (AbstractLocalTimeAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class AbstractLongAdderAssert extends AbstractAssert implements NumberAssert, ComparableAssert {
  public AbstractLongAdderAssert hasValue(long p0) { return (AbstractLongAdderAssert) (Object) null; }
  public AbstractLongAdderAssert doesNotHaveValue(long p0) { return (AbstractLongAdderAssert) (Object) null; }
  public AbstractLongAdderAssert usingComparator(java.util.Comparator p0) { return (AbstractLongAdderAssert) (Object) null; }
  public AbstractLongAdderAssert usingComparator(java.util.Comparator p0, String p1) { return (AbstractLongAdderAssert) (Object) null; }
  public AbstractLongAdderAssert usingDefaultComparator() { return (AbstractLongAdderAssert) (Object) null; }
  public AbstractLongAdderAssert isZero() { return (AbstractLongAdderAssert) (Object) null; }
  public AbstractLongAdderAssert isNotZero() { return (AbstractLongAdderAssert) (Object) null; }
  public AbstractLongAdderAssert isOne() { return (AbstractLongAdderAssert) (Object) null; }
  public AbstractLongAdderAssert isPositive() { return (AbstractLongAdderAssert) (Object) null; }
  public AbstractLongAdderAssert isNegative() { return (AbstractLongAdderAssert) (Object) null; }
  public AbstractLongAdderAssert isNotNegative() { return (AbstractLongAdderAssert) (Object) null; }
  public AbstractLongAdderAssert isNotPositive() { return (AbstractLongAdderAssert) (Object) null; }
  public AbstractLongAdderAssert isEqualByComparingTo(Long p0) { return (AbstractLongAdderAssert) (Object) null; }
  public AbstractLongAdderAssert isNotEqualByComparingTo(Long p0) { return (AbstractLongAdderAssert) (Object) null; }
  public AbstractLongAdderAssert isLessThan(Long p0) { return (AbstractLongAdderAssert) (Object) null; }
  public AbstractLongAdderAssert isLessThanOrEqualTo(Long p0) { return (AbstractLongAdderAssert) (Object) null; }
  public AbstractLongAdderAssert isGreaterThan(Long p0) { return (AbstractLongAdderAssert) (Object) null; }
  public AbstractLongAdderAssert isGreaterThanOrEqualTo(Long p0) { return (AbstractLongAdderAssert) (Object) null; }
  public AbstractLongAdderAssert isBetween(Long p0, Long p1) { return (AbstractLongAdderAssert) (Object) null; }
  public AbstractLongAdderAssert isStrictlyBetween(Long p0, Long p1) { return (AbstractLongAdderAssert) (Object) null; }
  public AbstractLongAdderAssert isCloseTo(Long p0, org.assertj.core.data.Offset p1) { return (AbstractLongAdderAssert) (Object) null; }
  public AbstractLongAdderAssert isNotCloseTo(Long p0, org.assertj.core.data.Offset p1) { return (AbstractLongAdderAssert) (Object) null; }
  public AbstractLongAdderAssert isCloseTo(Long p0, org.assertj.core.data.Percentage p1) { return (AbstractLongAdderAssert) (Object) null; }
  public AbstractLongAdderAssert isNotCloseTo(Long p0, org.assertj.core.data.Percentage p1) { return (AbstractLongAdderAssert) (Object) null; }
  public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Offset p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Offset p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isStrictlyBetween(Number p0, Number p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isBetween(Number p0, Number p1) { return (NumberAssert) (Object) null; }
  public ComparableAssert isStrictlyBetween(Comparable p0, Comparable p1) { return (ComparableAssert) (Object) null; }
  public ComparableAssert isBetween(Comparable p0, Comparable p1) { return (ComparableAssert) (Object) null; }
  public ComparableAssert isGreaterThanOrEqualTo(Comparable p0) { return (ComparableAssert) (Object) null; }
  public ComparableAssert isGreaterThan(Comparable p0) { return (ComparableAssert) (Object) null; }
  public ComparableAssert isLessThanOrEqualTo(Comparable p0) { return (ComparableAssert) (Object) null; }
  public ComparableAssert isLessThan(Comparable p0) { return (ComparableAssert) (Object) null; }
  public ComparableAssert isNotEqualByComparingTo(Comparable p0) { return (ComparableAssert) (Object) null; }
  public ComparableAssert isEqualByComparingTo(Comparable p0) { return (ComparableAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractLongArrayAssert extends AbstractArrayAssert {
  public void isNullOrEmpty() {}
  public void isEmpty() {}
  public AbstractLongArrayAssert isNotEmpty() { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert hasSize(int p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert hasSizeGreaterThan(int p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert hasSizeGreaterThanOrEqualTo(int p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert hasSizeLessThan(int p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert hasSizeLessThanOrEqualTo(int p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert hasSizeBetween(int p0, int p1) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert hasSameSizeAs(Iterable p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert contains(long[] p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert contains(Long[] p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert containsOnly(long[] p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert containsOnly(Long[] p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert containsOnlyOnce(long[] p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert containsOnlyOnce(Long[] p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert containsSequence(long[] p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert containsSequence(Long[] p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert containsSubsequence(long[] p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert containsSubsequence(Long[] p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert contains(long p0, org.assertj.core.data.Index p1) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert doesNotContain(long[] p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert doesNotContain(Long[] p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert doesNotContain(long p0, org.assertj.core.data.Index p1) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert doesNotHaveDuplicates() { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert startsWith(long[] p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert startsWith(Long[] p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert endsWith(long[] p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert endsWith(Long[] p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert isSorted() { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert isSortedAccordingTo(java.util.Comparator p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert usingElementComparator(java.util.Comparator p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert usingDefaultElementComparator() { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert containsExactly(long[] p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert containsExactly(Long[] p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert containsExactlyInAnyOrder(long[] p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert containsExactlyInAnyOrder(Long[] p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert containsAnyOf(long[] p0) { return (AbstractLongArrayAssert) (Object) null; }
  public AbstractLongArrayAssert containsAnyOf(Long[] p0) { return (AbstractLongArrayAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractLongAssert extends AbstractComparableAssert implements NumberAssert {
  public AbstractLongAssert isEqualTo(long p0) { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert isNotEqualTo(long p0) { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert isZero() { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert isNotZero() { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert isOne() { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert isPositive() { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert isNegative() { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert isNotNegative() { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert isNotPositive() { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert isEven() { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert isOdd() { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert isLessThan(long p0) { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert isLessThanOrEqualTo(long p0) { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert isGreaterThan(long p0) { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert isGreaterThanOrEqualTo(long p0) { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert isBetween(Long p0, Long p1) { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert isStrictlyBetween(Long p0, Long p1) { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert isCloseTo(long p0, org.assertj.core.data.Offset p1) { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert isNotCloseTo(long p0, org.assertj.core.data.Offset p1) { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert isCloseTo(Long p0, org.assertj.core.data.Offset p1) { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert isNotCloseTo(Long p0, org.assertj.core.data.Offset p1) { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert isCloseTo(Long p0, org.assertj.core.data.Percentage p1) { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert isNotCloseTo(Long p0, org.assertj.core.data.Percentage p1) { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert isCloseTo(long p0, org.assertj.core.data.Percentage p1) { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert isNotCloseTo(long p0, org.assertj.core.data.Percentage p1) { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert usingComparator(java.util.Comparator p0) { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert usingComparator(java.util.Comparator p0, String p1) { return (AbstractLongAssert) (Object) null; }
  public AbstractLongAssert usingDefaultComparator() { return (AbstractLongAssert) (Object) null; }
  public AbstractComparableAssert isStrictlyBetween(Comparable p0, Comparable p1) { return (AbstractComparableAssert) (Object) null; }
  public AbstractComparableAssert isBetween(Comparable p0, Comparable p1) { return (AbstractComparableAssert) (Object) null; }
  public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Offset p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Offset p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isStrictlyBetween(Number p0, Number p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isBetween(Number p0, Number p1) { return (NumberAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractMapAssert extends AbstractObjectAssert implements EnumerableAssert {
  public AbstractMapAssert allSatisfy(java.util.function.BiConsumer p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert anySatisfy(java.util.function.BiConsumer p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert noneSatisfy(java.util.function.BiConsumer p0) { return (AbstractMapAssert) (Object) null; }
  public void isNullOrEmpty() {}
  public void isEmpty() {}
  public AbstractMapAssert isNotEmpty() { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert hasSize(int p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert hasSizeGreaterThan(int p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert hasSizeGreaterThanOrEqualTo(int p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert hasSizeLessThan(int p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert hasSizeLessThanOrEqualTo(int p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert hasSizeBetween(int p0, int p1) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert hasSameSizeAs(Object p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert hasSameSizeAs(Iterable p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert hasSameSizeAs(java.util.Map p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert contains(java.util.Map.Entry[] p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert containsAnyOf(java.util.Map.Entry[] p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert containsAllEntriesOf(java.util.Map p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert containsExactlyEntriesOf(java.util.Map p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert containsExactlyInAnyOrderEntriesOf(java.util.Map p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert containsEntry(Object p0, Object p1) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert hasEntrySatisfying(Object p0, Condition p1) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert hasEntrySatisfying(Object p0, java.util.function.Consumer p1) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert hasEntrySatisfying(Condition p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert hasEntrySatisfying(Condition p0, Condition p1) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert hasKeySatisfying(Condition p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert hasValueSatisfying(Condition p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert doesNotContain(java.util.Map.Entry[] p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert doesNotContainEntry(Object p0, Object p1) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert containsKey(Object p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert containsKeys(Object[] p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert doesNotContainKey(Object p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert doesNotContainKeys(Object[] p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert containsOnlyKeys(Object[] p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert containsOnlyKeys(Iterable p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert containsValue(Object p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert containsValues(Object[] p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert doesNotContainValue(Object p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert containsOnly(java.util.Map.Entry[] p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert containsExactly(java.util.Map.Entry[] p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert usingElementComparator(java.util.Comparator p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert usingDefaultElementComparator() { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert as(String p0, Object[] p1) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert as(org.assertj.core.description.Description p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert describedAs(org.assertj.core.description.Description p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert describedAs(String p0, Object[] p1) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert doesNotHave(Condition p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert doesNotHaveSameClassAs(Object p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert has(Condition p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert hasSameClassAs(Object p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert hasToString(String p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert is(Condition p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert isEqualTo(Object p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert isExactlyInstanceOf(Class p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert isIn(Iterable p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert isIn(Object[] p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert isInstanceOf(Class p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert isInstanceOfAny(Class[] p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert isNot(Condition p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert isNotEqualTo(Object p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert isNotExactlyInstanceOf(Class p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert isNotIn(Iterable p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert isNotIn(Object[] p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert isNotInstanceOf(Class p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert isNotInstanceOfAny(Class[] p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert isNotOfAnyClassIn(Class[] p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert isNotNull() { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert isNotSameAs(Object p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert isOfAnyClassIn(Class[] p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert isSameAs(Object p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert overridingErrorMessage(String p0, Object[] p1) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert usingDefaultComparator() { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert usingComparator(java.util.Comparator p0) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert usingComparator(java.util.Comparator p0, String p1) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert withFailMessage(String p0, Object[] p1) { return (AbstractMapAssert) (Object) null; }
  public AbstractMapAssert withThreadDumpOnError() { return (AbstractMapAssert) (Object) null; }
  public AbstractMapSizeAssert size() { return (AbstractMapSizeAssert) (Object) null; }
  public AbstractListAssert extracting(Object[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert extractingByKeys(Object[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractObjectAssert extracting(Object p0) { return (AbstractObjectAssert) (Object) null; }
  public AbstractObjectAssert extractingByKey(Object p0) { return (AbstractObjectAssert) (Object) null; }
  public AbstractAssert extractingByKey(Object p0, InstanceOfAssertFactory p1) { return (AbstractAssert) (Object) null; }
  public AbstractListAssert extractingFromEntries(java.util.function.Function p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert extractingFromEntries(java.util.function.Function[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert flatExtracting(String[] p0) { return (AbstractListAssert) (Object) null; }
  public RecursiveComparisonAssert usingRecursiveComparison() { return (RecursiveComparisonAssert) (Object) null; }
  public RecursiveComparisonAssert usingRecursiveComparison(org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration p0) { return (RecursiveComparisonAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractMapSizeAssert extends AbstractIntegerAssert {
  public abstract AbstractMapAssert returnToMap();
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractObjectArrayAssert extends AbstractAssert implements IndexedObjectEnumerableAssert, ArraySortedAssert {
  public AbstractObjectArrayAssert as(org.assertj.core.description.Description p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert as(String p0, Object[] p1) { return (AbstractObjectArrayAssert) (Object) null; }
  public void isNullOrEmpty() {}
  public void isEmpty() {}
  public AbstractObjectArrayAssert isNotEmpty() { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert hasSize(int p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert hasSizeGreaterThan(int p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert hasSizeGreaterThanOrEqualTo(int p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert hasSizeLessThan(int p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert hasSizeLessThanOrEqualTo(int p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert hasSizeBetween(int p0, int p1) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert hasSameSizeAs(Object p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert hasSameSizeAs(Iterable p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert contains(Object[] p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert containsOnly(Object[] p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert containsOnlyElementsOf(Iterable p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert containsOnlyNulls() { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert hasOnlyOneElementSatisfying(java.util.function.Consumer p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert hasSameElementsAs(Iterable p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert containsOnlyOnce(Object[] p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert containsOnlyOnceElementsOf(Iterable p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert containsExactly(Object[] p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert containsExactlyInAnyOrder(Object[] p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert containsExactlyInAnyOrderElementsOf(Iterable p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert containsExactlyElementsOf(Iterable p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert containsSequence(Object[] p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert containsSequence(Iterable p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert doesNotContainSequence(Object[] p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert doesNotContainSequence(Iterable p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert containsSubsequence(Object[] p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert containsSubsequence(Iterable p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert doesNotContainSubsequence(Object[] p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert doesNotContainSubsequence(Iterable p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert contains(Object p0, org.assertj.core.data.Index p1) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert hasOnlyElementsOfTypes(Class[] p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert doesNotContain(Object p0, org.assertj.core.data.Index p1) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert doesNotContain(Object[] p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert doesNotContainAnyElementsOf(Iterable p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert doesNotHaveDuplicates() { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert startsWith(Object[] p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert endsWith(Object[] p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert endsWith(Object p0, Object[] p1) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert isSubsetOf(Iterable p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert isSubsetOf(Object[] p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert containsNull() { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert doesNotContainNull() { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert are(Condition p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert areNot(Condition p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert have(Condition p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert doNotHave(Condition p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert areAtLeast(int p0, Condition p1) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert areAtLeastOne(Condition p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert areAtMost(int p0, Condition p1) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert areExactly(int p0, Condition p1) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert haveAtLeastOne(Condition p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert haveAtLeast(int p0, Condition p1) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert haveAtMost(int p0, Condition p1) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert haveExactly(int p0, Condition p1) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert hasAtLeastOneElementOfType(Class p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert hasOnlyElementsOfType(Class p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert doesNotHaveAnyElementsOfTypes(Class[] p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert isSorted() { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert isSortedAccordingTo(java.util.Comparator p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert containsAll(Iterable p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert usingElementComparator(java.util.Comparator p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert usingDefaultElementComparator() { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert usingComparatorForElementFieldsWithNames(java.util.Comparator p0, String[] p1) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert usingComparatorForElementFieldsWithType(java.util.Comparator p0, Class p1) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert usingComparatorForType(java.util.Comparator p0, Class p1) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert usingFieldByFieldElementComparator() { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert usingRecursiveFieldByFieldElementComparator() { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert usingElementComparatorOnFields(String[] p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert usingElementComparatorIgnoringFields(String[] p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractListAssert extracting(String p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert extracting(String p0, Class p1) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert extracting(String[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert extracting(java.util.function.Function p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert extracting(org.assertj.core.api.iterable.ThrowingExtractor p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert extracting(java.util.function.Function[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert flatExtracting(java.util.function.Function p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert flatExtracting(org.assertj.core.api.iterable.ThrowingExtractor p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert flatExtracting(String p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert extractingResultOf(String p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert extractingResultOf(String p0, Class p1) { return (AbstractListAssert) (Object) null; }
  public AbstractObjectArrayAssert inHexadecimal() { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert inBinary() { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert filteredOn(String p0, Object p1) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert filteredOnNull(String p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert filteredOn(String p0, org.assertj.core.api.filter.FilterOperator p1) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert filteredOn(Condition p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert filteredOn(java.util.function.Predicate p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert filteredOn(java.util.function.Function p0, Object p1) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert filteredOnAssertions(java.util.function.Consumer p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert allMatch(java.util.function.Predicate p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert allMatch(java.util.function.Predicate p0, String p1) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert allSatisfy(java.util.function.Consumer p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert anyMatch(java.util.function.Predicate p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert zipSatisfy(Object[] p0, java.util.function.BiConsumer p1) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert anySatisfy(java.util.function.Consumer p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert noneSatisfy(java.util.function.Consumer p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert satisfiesExactly(java.util.function.Consumer[] p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert satisfiesExactlyInAnyOrder(java.util.function.Consumer[] p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert containsAnyOf(Object[] p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert containsAnyElementsOf(Iterable p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public AbstractObjectArrayAssert noneMatch(java.util.function.Predicate p0) { return (AbstractObjectArrayAssert) (Object) null; }
  public RecursiveComparisonAssert usingRecursiveComparison() { return (RecursiveComparisonAssert) (Object) null; }
  public RecursiveComparisonAssert usingRecursiveComparison(org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration p0) { return (RecursiveComparisonAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractObjectAssert extends AbstractAssert {
  public void AbstractObjectAssert(Object p0, Class p1) {}
  public AbstractObjectAssert as(org.assertj.core.description.Description p0) { return (AbstractObjectAssert) (Object) null; }
  public AbstractObjectAssert as(String p0, Object[] p1) { return (AbstractObjectAssert) (Object) null; }
  public AbstractObjectAssert isEqualToIgnoringNullFields(Object p0) { return (AbstractObjectAssert) (Object) null; }
  public AbstractObjectAssert isEqualToComparingOnlyGivenFields(Object p0, String[] p1) { return (AbstractObjectAssert) (Object) null; }
  public AbstractObjectAssert isEqualToIgnoringGivenFields(Object p0, String[] p1) { return (AbstractObjectAssert) (Object) null; }
  public AbstractObjectAssert hasNoNullFieldsOrProperties() { return (AbstractObjectAssert) (Object) null; }
  public AbstractObjectAssert hasAllNullFieldsOrProperties() { return (AbstractObjectAssert) (Object) null; }
  public AbstractObjectAssert hasNoNullFieldsOrPropertiesExcept(String[] p0) { return (AbstractObjectAssert) (Object) null; }
  public AbstractObjectAssert hasAllNullFieldsOrPropertiesExcept(String[] p0) { return (AbstractObjectAssert) (Object) null; }
  public AbstractObjectAssert isEqualToComparingFieldByField(Object p0) { return (AbstractObjectAssert) (Object) null; }
  public AbstractObjectAssert usingComparatorForFields(java.util.Comparator p0, String[] p1) { return (AbstractObjectAssert) (Object) null; }
  public AbstractObjectAssert usingComparatorForType(java.util.Comparator p0, Class p1) { return (AbstractObjectAssert) (Object) null; }
  public AbstractObjectAssert hasFieldOrProperty(String p0) { return (AbstractObjectAssert) (Object) null; }
  public AbstractObjectAssert hasFieldOrPropertyWithValue(String p0, Object p1) { return (AbstractObjectAssert) (Object) null; }
  public AbstractObjectAssert hasOnlyFields(String[] p0) { return (AbstractObjectAssert) (Object) null; }
  public AbstractListAssert extracting(String[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractObjectAssert extracting(String p0) { return (AbstractObjectAssert) (Object) null; }
  public AbstractAssert extracting(String p0, InstanceOfAssertFactory p1) { return (AbstractAssert) (Object) null; }
  public AbstractListAssert extracting(java.util.function.Function[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractObjectAssert extracting(java.util.function.Function p0) { return (AbstractObjectAssert) (Object) null; }
  public AbstractAssert extracting(java.util.function.Function p0, InstanceOfAssertFactory p1) { return (AbstractAssert) (Object) null; }
  public AbstractObjectAssert isEqualToComparingFieldByFieldRecursively(Object p0) { return (AbstractObjectAssert) (Object) null; }
  public AbstractObjectAssert returns(Object p0, java.util.function.Function p1) { return (AbstractObjectAssert) (Object) null; }
  public RecursiveComparisonAssert usingRecursiveComparison() { return (RecursiveComparisonAssert) (Object) null; }
  public RecursiveComparisonAssert usingRecursiveComparison(org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration p0) { return (RecursiveComparisonAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractOffsetDateTimeAssert extends AbstractTemporalAssert {
  public static String NULL_OFFSET_DATE_TIME_PARAMETER_MESSAGE;
  public AbstractOffsetDateTimeAssert isBefore(java.time.OffsetDateTime p0) { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public AbstractOffsetDateTimeAssert isBefore(String p0) { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public AbstractOffsetDateTimeAssert isBeforeOrEqualTo(java.time.OffsetDateTime p0) { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public AbstractOffsetDateTimeAssert isBeforeOrEqualTo(String p0) { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public AbstractOffsetDateTimeAssert isAfterOrEqualTo(java.time.OffsetDateTime p0) { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public AbstractOffsetDateTimeAssert isAfterOrEqualTo(String p0) { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public AbstractOffsetDateTimeAssert isAfter(java.time.OffsetDateTime p0) { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public AbstractOffsetDateTimeAssert isAfter(String p0) { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public AbstractOffsetDateTimeAssert isEqualTo(Object p0) { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public AbstractOffsetDateTimeAssert isCloseToUtcNow(org.assertj.core.data.TemporalUnitOffset p0) { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public AbstractOffsetDateTimeAssert isEqualTo(String p0) { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public AbstractOffsetDateTimeAssert isNotEqualTo(Object p0) { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public AbstractOffsetDateTimeAssert isNotEqualTo(String p0) { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public AbstractOffsetDateTimeAssert isIn(String[] p0) { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public AbstractOffsetDateTimeAssert isNotIn(String[] p0) { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public AbstractOffsetDateTimeAssert isEqualToIgnoringNanos(java.time.OffsetDateTime p0) { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public AbstractOffsetDateTimeAssert isEqualToIgnoringTimezone(java.time.OffsetDateTime p0) { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public AbstractOffsetDateTimeAssert isEqualToIgnoringSeconds(java.time.OffsetDateTime p0) { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public AbstractOffsetDateTimeAssert isEqualToIgnoringMinutes(java.time.OffsetDateTime p0) { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public AbstractOffsetDateTimeAssert isEqualToIgnoringHours(java.time.OffsetDateTime p0) { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public AbstractOffsetDateTimeAssert isBetween(java.time.OffsetDateTime p0, java.time.OffsetDateTime p1) { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public AbstractOffsetDateTimeAssert isBetween(String p0, String p1) { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public AbstractOffsetDateTimeAssert isStrictlyBetween(java.time.OffsetDateTime p0, java.time.OffsetDateTime p1) { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public AbstractOffsetDateTimeAssert isStrictlyBetween(String p0, String p1) { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public AbstractOffsetDateTimeAssert usingDefaultComparator() { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public AbstractOffsetDateTimeAssert isAtSameInstantAs(java.time.OffsetDateTime p0) { return (AbstractOffsetDateTimeAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractOffsetTimeAssert extends AbstractTemporalAssert {
  public static String NULL_OFFSET_TIME_PARAMETER_MESSAGE;
  public AbstractOffsetTimeAssert isBefore(java.time.OffsetTime p0) { return (AbstractOffsetTimeAssert) (Object) null; }
  public AbstractOffsetTimeAssert isBefore(String p0) { return (AbstractOffsetTimeAssert) (Object) null; }
  public AbstractOffsetTimeAssert isBeforeOrEqualTo(java.time.OffsetTime p0) { return (AbstractOffsetTimeAssert) (Object) null; }
  public AbstractOffsetTimeAssert isBeforeOrEqualTo(String p0) { return (AbstractOffsetTimeAssert) (Object) null; }
  public AbstractOffsetTimeAssert isAfterOrEqualTo(java.time.OffsetTime p0) { return (AbstractOffsetTimeAssert) (Object) null; }
  public AbstractOffsetTimeAssert isAfterOrEqualTo(String p0) { return (AbstractOffsetTimeAssert) (Object) null; }
  public AbstractOffsetTimeAssert isAfter(java.time.OffsetTime p0) { return (AbstractOffsetTimeAssert) (Object) null; }
  public AbstractOffsetTimeAssert isAfter(String p0) { return (AbstractOffsetTimeAssert) (Object) null; }
  public AbstractOffsetTimeAssert isEqualTo(String p0) { return (AbstractOffsetTimeAssert) (Object) null; }
  public AbstractOffsetTimeAssert isNotEqualTo(String p0) { return (AbstractOffsetTimeAssert) (Object) null; }
  public AbstractOffsetTimeAssert isIn(String[] p0) { return (AbstractOffsetTimeAssert) (Object) null; }
  public AbstractOffsetTimeAssert isNotIn(String[] p0) { return (AbstractOffsetTimeAssert) (Object) null; }
  public AbstractOffsetTimeAssert isEqualToIgnoringNanos(java.time.OffsetTime p0) { return (AbstractOffsetTimeAssert) (Object) null; }
  public AbstractOffsetTimeAssert isEqualToIgnoringSeconds(java.time.OffsetTime p0) { return (AbstractOffsetTimeAssert) (Object) null; }
  public AbstractOffsetTimeAssert isEqualToIgnoringTimezone(java.time.OffsetTime p0) { return (AbstractOffsetTimeAssert) (Object) null; }
  public AbstractOffsetTimeAssert hasSameHourAs(java.time.OffsetTime p0) { return (AbstractOffsetTimeAssert) (Object) null; }
  public AbstractOffsetTimeAssert isBetween(java.time.OffsetTime p0, java.time.OffsetTime p1) { return (AbstractOffsetTimeAssert) (Object) null; }
  public AbstractOffsetTimeAssert isBetween(String p0, String p1) { return (AbstractOffsetTimeAssert) (Object) null; }
  public AbstractOffsetTimeAssert isStrictlyBetween(java.time.OffsetTime p0, java.time.OffsetTime p1) { return (AbstractOffsetTimeAssert) (Object) null; }
  public AbstractOffsetTimeAssert isStrictlyBetween(String p0, String p1) { return (AbstractOffsetTimeAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractOptionalAssert extends AbstractAssert {
  public AbstractOptionalAssert isPresent() { return (AbstractOptionalAssert) (Object) null; }
  public AbstractOptionalAssert isNotEmpty() { return (AbstractOptionalAssert) (Object) null; }
  public AbstractOptionalAssert isEmpty() { return (AbstractOptionalAssert) (Object) null; }
  public AbstractOptionalAssert isNotPresent() { return (AbstractOptionalAssert) (Object) null; }
  public AbstractOptionalAssert contains(Object p0) { return (AbstractOptionalAssert) (Object) null; }
  public AbstractOptionalAssert hasValueSatisfying(java.util.function.Consumer p0) { return (AbstractOptionalAssert) (Object) null; }
  public AbstractOptionalAssert hasValueSatisfying(Condition p0) { return (AbstractOptionalAssert) (Object) null; }
  public AbstractOptionalAssert hasValue(Object p0) { return (AbstractOptionalAssert) (Object) null; }
  public AbstractOptionalAssert containsInstanceOf(Class p0) { return (AbstractOptionalAssert) (Object) null; }
  public AbstractOptionalAssert usingFieldByFieldValueComparator() { return (AbstractOptionalAssert) (Object) null; }
  public AbstractOptionalAssert usingValueComparator(java.util.Comparator p0) { return (AbstractOptionalAssert) (Object) null; }
  public AbstractOptionalAssert usingDefaultValueComparator() { return (AbstractOptionalAssert) (Object) null; }
  public AbstractOptionalAssert containsSame(Object p0) { return (AbstractOptionalAssert) (Object) null; }
  public AbstractOptionalAssert flatMap(java.util.function.Function p0) { return (AbstractOptionalAssert) (Object) null; }
  public AbstractOptionalAssert map(java.util.function.Function p0) { return (AbstractOptionalAssert) (Object) null; }
  public AbstractObjectAssert get() { return (AbstractObjectAssert) (Object) null; }
  public AbstractAssert get(InstanceOfAssertFactory p0) { return (AbstractAssert) (Object) null; }
  public RecursiveComparisonAssert usingRecursiveComparison() { return (RecursiveComparisonAssert) (Object) null; }
  public RecursiveComparisonAssert usingRecursiveComparison(org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration p0) { return (RecursiveComparisonAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractOptionalDoubleAssert extends AbstractAssert {
  public AbstractOptionalDoubleAssert isPresent() { return (AbstractOptionalDoubleAssert) (Object) null; }
  public AbstractOptionalDoubleAssert isNotPresent() { return (AbstractOptionalDoubleAssert) (Object) null; }
  public AbstractOptionalDoubleAssert isEmpty() { return (AbstractOptionalDoubleAssert) (Object) null; }
  public AbstractOptionalDoubleAssert isNotEmpty() { return (AbstractOptionalDoubleAssert) (Object) null; }
  public AbstractOptionalDoubleAssert hasValue(double p0) { return (AbstractOptionalDoubleAssert) (Object) null; }
  public AbstractOptionalDoubleAssert hasValueCloseTo(Double p0, org.assertj.core.data.Offset p1) { return (AbstractOptionalDoubleAssert) (Object) null; }
  public AbstractOptionalDoubleAssert hasValueCloseTo(Double p0, org.assertj.core.data.Percentage p1) { return (AbstractOptionalDoubleAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractOptionalIntAssert extends AbstractAssert {
  public AbstractOptionalIntAssert isPresent() { return (AbstractOptionalIntAssert) (Object) null; }
  public AbstractOptionalIntAssert isNotPresent() { return (AbstractOptionalIntAssert) (Object) null; }
  public AbstractOptionalIntAssert isEmpty() { return (AbstractOptionalIntAssert) (Object) null; }
  public AbstractOptionalIntAssert isNotEmpty() { return (AbstractOptionalIntAssert) (Object) null; }
  public AbstractOptionalIntAssert hasValue(int p0) { return (AbstractOptionalIntAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractOptionalLongAssert extends AbstractAssert {
  public AbstractOptionalLongAssert isPresent() { return (AbstractOptionalLongAssert) (Object) null; }
  public AbstractOptionalLongAssert isNotPresent() { return (AbstractOptionalLongAssert) (Object) null; }
  public AbstractOptionalLongAssert isEmpty() { return (AbstractOptionalLongAssert) (Object) null; }
  public AbstractOptionalLongAssert isNotEmpty() { return (AbstractOptionalLongAssert) (Object) null; }
  public AbstractOptionalLongAssert hasValue(long p0) { return (AbstractOptionalLongAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractPathAssert extends AbstractComparableAssert {
  public AbstractPathAssert hasSameContentAs(java.nio.file.Path p0) { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert hasSameTextualContentAs(java.nio.file.Path p0) { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert hasSameTextualContentAs(java.nio.file.Path p0, java.nio.charset.Charset p1) { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert hasSameContentAs(java.nio.file.Path p0, java.nio.charset.Charset p1) { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert hasBinaryContent(byte[] p0) { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert hasSameBinaryContentAs(java.nio.file.Path p0) { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert usingCharset(String p0) { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert usingCharset(java.nio.charset.Charset p0) { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert hasContent(String p0) { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert isReadable() { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert isWritable() { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert isExecutable() { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert exists() { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert existsNoFollowLinks() { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert doesNotExist() { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert isRegularFile() { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert isDirectory() { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert isSymbolicLink() { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert isAbsolute() { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert isRelative() { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert isNormalized() { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert isCanonical() { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert hasFileName(String p0) { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert hasParent(java.nio.file.Path p0) { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert hasParentRaw(java.nio.file.Path p0) { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert hasNoParent() { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert hasNoParentRaw() { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert startsWith(java.nio.file.Path p0) { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert startsWithRaw(java.nio.file.Path p0) { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert endsWith(java.nio.file.Path p0) { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert endsWithRaw(java.nio.file.Path p0) { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert hasDigest(java.security.MessageDigest p0, byte[] p1) { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert hasDigest(java.security.MessageDigest p0, String p1) { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert hasDigest(String p0, byte[] p1) { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert hasDigest(String p0, String p1) { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert isDirectoryContaining(java.util.function.Predicate p0) { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert isDirectoryContaining(String p0) { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert isDirectoryRecursivelyContaining(String p0) { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert isDirectoryRecursivelyContaining(java.util.function.Predicate p0) { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert isDirectoryNotContaining(java.util.function.Predicate p0) { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert isDirectoryNotContaining(String p0) { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert isEmptyDirectory() { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert isNotEmptyDirectory() { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert isEmptyFile() { return (AbstractPathAssert) (Object) null; }
  public AbstractPathAssert isNotEmptyFile() { return (AbstractPathAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractPeriodAssert extends AbstractAssert {
  public AbstractPeriodAssert hasYears(int p0) { return (AbstractPeriodAssert) (Object) null; }
  public AbstractPeriodAssert hasMonths(int p0) { return (AbstractPeriodAssert) (Object) null; }
  public AbstractPeriodAssert hasDays(int p0) { return (AbstractPeriodAssert) (Object) null; }
  public AbstractPeriodAssert isPositive() { return (AbstractPeriodAssert) (Object) null; }
  public AbstractPeriodAssert isNegative() { return (AbstractPeriodAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractPredicateAssert extends AbstractAssert {
  public AbstractPredicateAssert accepts(Object[] p0) { return (AbstractPredicateAssert) (Object) null; }
  public AbstractPredicateAssert rejects(Object[] p0) { return (AbstractPredicateAssert) (Object) null; }
  public AbstractPredicateAssert acceptsAll(Iterable p0) { return (AbstractPredicateAssert) (Object) null; }
  public AbstractPredicateAssert rejectsAll(Iterable p0) { return (AbstractPredicateAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

abstract class AbstractPredicateLikeAssert extends AbstractAssert {
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractShortArrayAssert extends AbstractArrayAssert {
  public void isNullOrEmpty() {}
  public void isEmpty() {}
  public AbstractShortArrayAssert isNotEmpty() { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert hasSize(int p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert hasSizeGreaterThan(int p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert hasSizeGreaterThanOrEqualTo(int p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert hasSizeLessThan(int p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert hasSizeLessThanOrEqualTo(int p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert hasSizeBetween(int p0, int p1) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert hasSameSizeAs(Iterable p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert contains(short[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert contains(Short[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert contains(int[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert containsOnly(short[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert containsOnly(Short[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert containsOnly(int[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert containsOnlyOnce(short[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert containsOnlyOnce(Short[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert containsOnlyOnce(int[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert containsSequence(short[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert containsSequence(Short[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert containsSequence(int[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert containsSubsequence(short[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert containsSubsequence(Short[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert containsSubsequence(int[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert contains(short p0, org.assertj.core.data.Index p1) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert contains(int p0, org.assertj.core.data.Index p1) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert doesNotContain(short[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert doesNotContain(Short[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert doesNotContain(int[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert doesNotContain(short p0, org.assertj.core.data.Index p1) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert doesNotContain(int p0, org.assertj.core.data.Index p1) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert doesNotHaveDuplicates() { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert startsWith(short[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert startsWith(Short[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert startsWith(int[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert endsWith(short[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert endsWith(Short[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert endsWith(int[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert isSorted() { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert isSortedAccordingTo(java.util.Comparator p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert usingElementComparator(java.util.Comparator p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert usingDefaultElementComparator() { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert containsExactly(short[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert containsExactly(Short[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert containsExactly(int[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert containsExactlyInAnyOrder(short[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert containsExactlyInAnyOrder(Short[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert containsExactlyInAnyOrder(int[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert containsAnyOf(short[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert containsAnyOf(Short[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public AbstractShortArrayAssert containsAnyOf(int[] p0) { return (AbstractShortArrayAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractShortAssert extends AbstractComparableAssert implements NumberAssert {
  public AbstractShortAssert isEqualTo(short p0) { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert isNotEqualTo(short p0) { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert isZero() { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert isNotZero() { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert isOne() { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert isPositive() { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert isNegative() { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert isNotNegative() { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert isNotPositive() { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert isEven() { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert isOdd() { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert isLessThan(short p0) { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert isLessThanOrEqualTo(short p0) { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert isGreaterThan(short p0) { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert isGreaterThanOrEqualTo(short p0) { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert isBetween(Short p0, Short p1) { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert isStrictlyBetween(Short p0, Short p1) { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert isCloseTo(short p0, org.assertj.core.data.Offset p1) { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert isNotCloseTo(short p0, org.assertj.core.data.Offset p1) { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert isCloseTo(Short p0, org.assertj.core.data.Offset p1) { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert isNotCloseTo(Short p0, org.assertj.core.data.Offset p1) { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert isCloseTo(Short p0, org.assertj.core.data.Percentage p1) { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert isNotCloseTo(Short p0, org.assertj.core.data.Percentage p1) { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert isCloseTo(short p0, org.assertj.core.data.Percentage p1) { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert isNotCloseTo(short p0, org.assertj.core.data.Percentage p1) { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert usingComparator(java.util.Comparator p0) { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert usingComparator(java.util.Comparator p0, String p1) { return (AbstractShortAssert) (Object) null; }
  public AbstractShortAssert usingDefaultComparator() { return (AbstractShortAssert) (Object) null; }
  public AbstractComparableAssert isStrictlyBetween(Comparable p0, Comparable p1) { return (AbstractComparableAssert) (Object) null; }
  public AbstractComparableAssert isBetween(Comparable p0, Comparable p1) { return (AbstractComparableAssert) (Object) null; }
  public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Offset p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Offset p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isStrictlyBetween(Number p0, Number p1) { return (NumberAssert) (Object) null; }
  public NumberAssert isBetween(Number p0, Number p1) { return (NumberAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class AbstractSpliteratorAssert extends AbstractAssert {
  public AbstractSpliteratorAssert hasCharacteristics(int[] p0) { return (AbstractSpliteratorAssert) (Object) null; }
  public AbstractSpliteratorAssert hasOnlyCharacteristics(int[] p0) { return (AbstractSpliteratorAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class AbstractStringAssert extends AbstractCharSequenceAssert {
  public AbstractStringAssert isLessThan(String p0) { return (AbstractStringAssert) (Object) null; }
  public AbstractStringAssert isLessThanOrEqualTo(String p0) { return (AbstractStringAssert) (Object) null; }
  public AbstractStringAssert isGreaterThan(String p0) { return (AbstractStringAssert) (Object) null; }
  public AbstractStringAssert isGreaterThanOrEqualTo(String p0) { return (AbstractStringAssert) (Object) null; }
  public AbstractStringAssert isBetween(String p0, String p1) { return (AbstractStringAssert) (Object) null; }
  public AbstractStringAssert isStrictlyBetween(String p0, String p1) { return (AbstractStringAssert) (Object) null; }
  public AbstractStringAssert isBase64() { return (AbstractStringAssert) (Object) null; }
  public AbstractByteArrayAssert decodedAsBase64() { return (AbstractByteArrayAssert) (Object) null; }
  public AbstractStringAssert usingComparator(java.util.Comparator p0) { return (AbstractStringAssert) (Object) null; }
  public AbstractStringAssert usingComparator(java.util.Comparator p0, String p1) { return (AbstractStringAssert) (Object) null; }
  public AbstractStringAssert usingDefaultComparator() { return (AbstractStringAssert) (Object) null; }
  public AbstractStringAssert isEqualTo(String p0, Object[] p1) { return (AbstractStringAssert) (Object) null; }
  public AbstractStringAssert isEqualTo(String p0) { return (AbstractStringAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractTemporalAssert extends AbstractAssert {
  public AbstractTemporalAssert isCloseTo(java.time.temporal.Temporal p0, org.assertj.core.data.TemporalOffset p1) { return (AbstractTemporalAssert) (Object) null; }
  public AbstractTemporalAssert isCloseTo(String p0, org.assertj.core.data.TemporalOffset p1) { return (AbstractTemporalAssert) (Object) null; }
  public AbstractTemporalAssert usingComparator(java.util.Comparator p0) { return (AbstractTemporalAssert) (Object) null; }
  public AbstractTemporalAssert usingComparator(java.util.Comparator p0, String p1) { return (AbstractTemporalAssert) (Object) null; }
  public AbstractTemporalAssert usingDefaultComparator() { return (AbstractTemporalAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractThrowableAssert extends AbstractObjectAssert {
  public interface ThrowingCallable {
    void call() throws Throwable;
  }
  public AbstractThrowableAssert hasMessage(String p0) { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert hasMessage(String p0, Object[] p1) { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert hasCause(Throwable p0) { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert hasCauseReference(Throwable p0) { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert hasNoCause() { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert getCause() { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert getRootCause() { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert hasMessageStartingWith(String p0) { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert hasMessageStartingWith(String p0, Object[] p1) { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert hasMessageContaining(String p0) { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert hasMessageContaining(String p0, Object[] p1) { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert hasMessageContainingAll(CharSequence[] p0) { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert hasMessageNotContaining(String p0) { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert hasMessageNotContainingAny(CharSequence[] p0) { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert hasStackTraceContaining(String p0) { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert hasStackTraceContaining(String p0, Object[] p1) { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert hasMessageMatching(String p0) { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert hasMessageFindingMatch(String p0) { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert hasMessageEndingWith(String p0) { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert hasMessageEndingWith(String p0, Object[] p1) { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert hasCauseInstanceOf(Class p0) { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert hasCauseExactlyInstanceOf(Class p0) { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert hasRootCause(Throwable p0) { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert hasRootCauseInstanceOf(Class p0) { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert hasRootCauseExactlyInstanceOf(Class p0) { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert hasRootCauseMessage(String p0) { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert hasRootCauseMessage(String p0, Object[] p1) { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert hasNoSuppressedExceptions() { return (AbstractThrowableAssert) (Object) null; }
  public AbstractThrowableAssert hasSuppressedException(Throwable p0) { return (AbstractThrowableAssert) (Object) null; }
  public void doesNotThrowAnyException() {}
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractUriAssert extends AbstractAssert {
  public AbstractUriAssert hasPath(String p0) { return (AbstractUriAssert) (Object) null; }
  public AbstractUriAssert hasNoPath() { return (AbstractUriAssert) (Object) null; }
  public AbstractUriAssert hasPort(int p0) { return (AbstractUriAssert) (Object) null; }
  public AbstractUriAssert hasNoPort() { return (AbstractUriAssert) (Object) null; }
  public AbstractUriAssert hasHost(String p0) { return (AbstractUriAssert) (Object) null; }
  public AbstractUriAssert hasAuthority(String p0) { return (AbstractUriAssert) (Object) null; }
  public AbstractUriAssert hasFragment(String p0) { return (AbstractUriAssert) (Object) null; }
  public AbstractUriAssert hasNoFragment() { return (AbstractUriAssert) (Object) null; }
  public AbstractUriAssert hasQuery(String p0) { return (AbstractUriAssert) (Object) null; }
  public AbstractUriAssert hasNoQuery() { return (AbstractUriAssert) (Object) null; }
  public AbstractUriAssert hasScheme(String p0) { return (AbstractUriAssert) (Object) null; }
  public AbstractUriAssert hasUserInfo(String p0) { return (AbstractUriAssert) (Object) null; }
  public AbstractUriAssert hasNoUserInfo() { return (AbstractUriAssert) (Object) null; }
  public AbstractUriAssert hasParameter(String p0) { return (AbstractUriAssert) (Object) null; }
  public AbstractUriAssert hasParameter(String p0, String p1) { return (AbstractUriAssert) (Object) null; }
  public AbstractUriAssert hasNoParameters() { return (AbstractUriAssert) (Object) null; }
  public AbstractUriAssert hasNoParameter(String p0) { return (AbstractUriAssert) (Object) null; }
  public AbstractUriAssert hasNoParameter(String p0, String p1) { return (AbstractUriAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractUrlAssert extends AbstractAssert {
  public AbstractUrlAssert hasProtocol(String p0) { return (AbstractUrlAssert) (Object) null; }
  public AbstractUrlAssert hasPath(String p0) { return (AbstractUrlAssert) (Object) null; }
  public AbstractUrlAssert hasNoPath() { return (AbstractUrlAssert) (Object) null; }
  public AbstractUrlAssert hasPort(int p0) { return (AbstractUrlAssert) (Object) null; }
  public AbstractUrlAssert hasNoPort() { return (AbstractUrlAssert) (Object) null; }
  public AbstractUrlAssert hasHost(String p0) { return (AbstractUrlAssert) (Object) null; }
  public AbstractUrlAssert hasAuthority(String p0) { return (AbstractUrlAssert) (Object) null; }
  public AbstractUrlAssert hasQuery(String p0) { return (AbstractUrlAssert) (Object) null; }
  public AbstractUrlAssert hasNoQuery() { return (AbstractUrlAssert) (Object) null; }
  public AbstractUrlAssert hasAnchor(String p0) { return (AbstractUrlAssert) (Object) null; }
  public AbstractUrlAssert hasNoAnchor() { return (AbstractUrlAssert) (Object) null; }
  public AbstractUrlAssert hasUserInfo(String p0) { return (AbstractUrlAssert) (Object) null; }
  public AbstractUrlAssert hasNoUserInfo() { return (AbstractUrlAssert) (Object) null; }
  public AbstractUrlAssert hasParameter(String p0) { return (AbstractUrlAssert) (Object) null; }
  public AbstractUrlAssert hasParameter(String p0, String p1) { return (AbstractUrlAssert) (Object) null; }
  public AbstractUrlAssert hasNoParameters() { return (AbstractUrlAssert) (Object) null; }
  public AbstractUrlAssert hasNoParameter(String p0) { return (AbstractUrlAssert) (Object) null; }
  public AbstractUrlAssert hasNoParameter(String p0, String p1) { return (AbstractUrlAssert) (Object) null; }
  public AbstractUrlAssert isEqualToWithSortedQueryParameters(java.net.URL p0) { return (AbstractUrlAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public abstract class AbstractZonedDateTimeAssert extends AbstractTemporalAssert {
  public static String NULL_DATE_TIME_PARAMETER_MESSAGE;
  public AbstractZonedDateTimeAssert isBefore(java.time.ZonedDateTime p0) { return (AbstractZonedDateTimeAssert) (Object) null; }
  public AbstractZonedDateTimeAssert isBefore(String p0) { return (AbstractZonedDateTimeAssert) (Object) null; }
  public AbstractZonedDateTimeAssert isBeforeOrEqualTo(java.time.ZonedDateTime p0) { return (AbstractZonedDateTimeAssert) (Object) null; }
  public AbstractZonedDateTimeAssert isBeforeOrEqualTo(String p0) { return (AbstractZonedDateTimeAssert) (Object) null; }
  public AbstractZonedDateTimeAssert isAfterOrEqualTo(java.time.ZonedDateTime p0) { return (AbstractZonedDateTimeAssert) (Object) null; }
  public AbstractZonedDateTimeAssert isAfterOrEqualTo(String p0) { return (AbstractZonedDateTimeAssert) (Object) null; }
  public AbstractZonedDateTimeAssert isAfter(java.time.ZonedDateTime p0) { return (AbstractZonedDateTimeAssert) (Object) null; }
  public AbstractZonedDateTimeAssert isAfter(String p0) { return (AbstractZonedDateTimeAssert) (Object) null; }
  public AbstractZonedDateTimeAssert isEqualToIgnoringNanos(java.time.ZonedDateTime p0) { return (AbstractZonedDateTimeAssert) (Object) null; }
  public AbstractZonedDateTimeAssert isEqualToIgnoringSeconds(java.time.ZonedDateTime p0) { return (AbstractZonedDateTimeAssert) (Object) null; }
  public AbstractZonedDateTimeAssert isEqualToIgnoringMinutes(java.time.ZonedDateTime p0) { return (AbstractZonedDateTimeAssert) (Object) null; }
  public AbstractZonedDateTimeAssert isEqualToIgnoringHours(java.time.ZonedDateTime p0) { return (AbstractZonedDateTimeAssert) (Object) null; }
  public AbstractZonedDateTimeAssert isEqualTo(Object p0) { return (AbstractZonedDateTimeAssert) (Object) null; }
  public AbstractZonedDateTimeAssert isEqualTo(String p0) { return (AbstractZonedDateTimeAssert) (Object) null; }
  public AbstractZonedDateTimeAssert isNotEqualTo(Object p0) { return (AbstractZonedDateTimeAssert) (Object) null; }
  public AbstractZonedDateTimeAssert isNotEqualTo(String p0) { return (AbstractZonedDateTimeAssert) (Object) null; }
  public AbstractZonedDateTimeAssert isIn(java.time.ZonedDateTime[] p0) { return (AbstractZonedDateTimeAssert) (Object) null; }
  public AbstractZonedDateTimeAssert isIn(String[] p0) { return (AbstractZonedDateTimeAssert) (Object) null; }
  public AbstractZonedDateTimeAssert isNotIn(java.time.ZonedDateTime[] p0) { return (AbstractZonedDateTimeAssert) (Object) null; }
  public AbstractZonedDateTimeAssert isNotIn(String[] p0) { return (AbstractZonedDateTimeAssert) (Object) null; }
  public AbstractZonedDateTimeAssert isBetween(java.time.ZonedDateTime p0, java.time.ZonedDateTime p1) { return (AbstractZonedDateTimeAssert) (Object) null; }
  public AbstractZonedDateTimeAssert isBetween(String p0, String p1) { return (AbstractZonedDateTimeAssert) (Object) null; }
  public AbstractZonedDateTimeAssert isStrictlyBetween(java.time.ZonedDateTime p0, java.time.ZonedDateTime p1) { return (AbstractZonedDateTimeAssert) (Object) null; }
  public AbstractZonedDateTimeAssert isStrictlyBetween(String p0, String p1) { return (AbstractZonedDateTimeAssert) (Object) null; }
  public AbstractZonedDateTimeAssert usingDefaultComparator() { return (AbstractZonedDateTimeAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public interface Array2DAssert {
  void isNullOrEmpty();
  void isEmpty();
  Array2DAssert isNotEmpty();
  Array2DAssert hasDimensions(int p0, int p1);
  Array2DAssert hasSameDimensionsAs(Object p0);
}
---
package org.assertj.core.api;
import java.io.*;

public interface ArraySortedAssert {
  ArraySortedAssert isSorted();
  ArraySortedAssert isSortedAccordingTo(java.util.Comparator p0);
}
---
package org.assertj.core.api;
import java.io.*;

public interface Assert extends Descriptable, ExtensionPoints {
  Assert isEqualTo(Object p0);
  Assert isNotEqualTo(Object p0);
  void isNull();
  Assert isNotNull();
  Assert isSameAs(Object p0);
  Assert isNotSameAs(Object p0);
  Assert isIn(Object[] p0);
  Assert isNotIn(Object[] p0);
  Assert isIn(Iterable p0);
  Assert isNotIn(Iterable p0);
  Assert usingComparator(java.util.Comparator p0);
  Assert usingComparator(java.util.Comparator p0, String p1);
  Assert usingDefaultComparator();
  AbstractAssert asInstanceOf(InstanceOfAssertFactory p0);
  Assert isInstanceOf(Class p0);
  Assert isInstanceOfSatisfying(Class p0, java.util.function.Consumer p1);
  Assert isInstanceOfAny(Class[] p0);
  Assert isNotInstanceOf(Class p0);
  Assert isNotInstanceOfAny(Class[] p0);
  Assert hasSameClassAs(Object p0);
  Assert hasToString(String p0);
  Assert doesNotHaveToString(String p0);
  Assert doesNotHaveSameClassAs(Object p0);
  Assert isExactlyInstanceOf(Class p0);
  Assert isNotExactlyInstanceOf(Class p0);
  Assert isOfAnyClassIn(Class[] p0);
  Assert isNotOfAnyClassIn(Class[] p0);
  AbstractListAssert asList();
  AbstractCharSequenceAssert asString();
  boolean equals(Object p0);
  Assert withThreadDumpOnError();
  Assert withRepresentation(org.assertj.core.presentation.Representation p0);
  Assert hasSameHashCodeAs(Object p0);
  Assert doesNotHaveSameHashCodeAs(Object p0);
}
---
package org.assertj.core.api;
import java.io.*;

public interface AssertDelegateTarget {
}
---
package org.assertj.core.api;
import java.io.*;

public interface AssertFactory {
  Assert createAssert(Object p0);
}
---
package org.assertj.core.api;
import java.io.*;

public interface AssertProvider {
  Object assertThat();
}
---
package org.assertj.core.api;
import java.io.*;

public interface AssertionInfo {
  String overridingErrorMessage();
  org.assertj.core.description.Description description();
  org.assertj.core.presentation.Representation representation();
}
---
package org.assertj.core.api;
import java.io.*;

public class Assertions implements InstanceOfAssertFactories {
  public static PredicateAssert assertThat(java.util.function.Predicate p0) { return (PredicateAssert) (Object) null; }
  public static IntPredicateAssert assertThat(java.util.function.IntPredicate p0) { return (IntPredicateAssert) (Object) null; }
  public static LongPredicateAssert assertThat(java.util.function.LongPredicate p0) { return (LongPredicateAssert) (Object) null; }
  public static DoublePredicateAssert assertThat(java.util.function.DoublePredicate p0) { return (DoublePredicateAssert) (Object) null; }
  public static CompletableFutureAssert assertThat(java.util.concurrent.CompletableFuture p0) { return (CompletableFutureAssert) (Object) null; }
  public static CompletableFutureAssert assertThat(java.util.concurrent.CompletionStage p0) { return (CompletableFutureAssert) (Object) null; }
  public static OptionalAssert assertThat(java.util.Optional p0) { return (OptionalAssert) (Object) null; }
  public static OptionalDoubleAssert assertThat(java.util.OptionalDouble p0) { return (OptionalDoubleAssert) (Object) null; }
  public static OptionalIntAssert assertThat(java.util.OptionalInt p0) { return (OptionalIntAssert) (Object) null; }
  public static OptionalLongAssert assertThat(java.util.OptionalLong p0) { return (OptionalLongAssert) (Object) null; }
  public static AbstractBigDecimalAssert assertThat(java.math.BigDecimal p0) { return (AbstractBigDecimalAssert) (Object) null; }
  public static AbstractBigIntegerAssert assertThat(java.math.BigInteger p0) { return (AbstractBigIntegerAssert) (Object) null; }
  public static AbstractUriAssert assertThat(java.net.URI p0) { return (AbstractUriAssert) (Object) null; }
  public static AbstractUrlAssert assertThat(java.net.URL p0) { return (AbstractUrlAssert) (Object) null; }
  public static AbstractBooleanAssert assertThat(boolean p0) { return (AbstractBooleanAssert) (Object) null; }
  public static AbstractBooleanAssert assertThat(Boolean p0) { return (AbstractBooleanAssert) (Object) null; }
  public static AbstractBooleanArrayAssert assertThat(boolean[] p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public static Boolean2DArrayAssert assertThat(boolean[][] p0) { return (Boolean2DArrayAssert) (Object) null; }
  public static AbstractByteAssert assertThat(byte p0) { return (AbstractByteAssert) (Object) null; }
  public static AbstractByteAssert assertThat(Byte p0) { return (AbstractByteAssert) (Object) null; }
  public static AbstractByteArrayAssert assertThat(byte[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public static Byte2DArrayAssert assertThat(byte[][] p0) { return (Byte2DArrayAssert) (Object) null; }
  public static AbstractCharacterAssert assertThat(char p0) { return (AbstractCharacterAssert) (Object) null; }
  public static AbstractCharArrayAssert assertThat(char[] p0) { return (AbstractCharArrayAssert) (Object) null; }
  public static Char2DArrayAssert assertThat(char[][] p0) { return (Char2DArrayAssert) (Object) null; }
  public static AbstractCharacterAssert assertThat(Character p0) { return (AbstractCharacterAssert) (Object) null; }
  public static ClassAssert assertThat(Class p0) { return (ClassAssert) (Object) null; }
  public static AbstractDoubleAssert assertThat(double p0) { return (AbstractDoubleAssert) (Object) null; }
  public static AbstractDoubleAssert assertThat(Double p0) { return (AbstractDoubleAssert) (Object) null; }
  public static AbstractDoubleArrayAssert assertThat(double[] p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public static Double2DArrayAssert assertThat(double[][] p0) { return (Double2DArrayAssert) (Object) null; }
  public static AbstractFileAssert assertThat(File p0) { return (AbstractFileAssert) (Object) null; }
  public static FutureAssert assertThat(java.util.concurrent.Future p0) { return (FutureAssert) (Object) null; }
  public static AbstractInputStreamAssert assertThat(InputStream p0) { return (AbstractInputStreamAssert) (Object) null; }
  public static AbstractFloatAssert assertThat(float p0) { return (AbstractFloatAssert) (Object) null; }
  public static AbstractFloatAssert assertThat(Float p0) { return (AbstractFloatAssert) (Object) null; }
  public static AbstractFloatArrayAssert assertThat(float[] p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public static AbstractIntegerAssert assertThat(int p0) { return (AbstractIntegerAssert) (Object) null; }
  public static AbstractIntArrayAssert assertThat(int[] p0) { return (AbstractIntArrayAssert) (Object) null; }
  public static Int2DArrayAssert assertThat(int[][] p0) { return (Int2DArrayAssert) (Object) null; }
  public static Float2DArrayAssert assertThat(float[][] p0) { return (Float2DArrayAssert) (Object) null; }
  public static AbstractIntegerAssert assertThat(Integer p0) { return (AbstractIntegerAssert) (Object) null; }
  public static FactoryBasedNavigableIterableAssert assertThat(Iterable p0, AssertFactory p1) { return (FactoryBasedNavigableIterableAssert) (Object) null; }
  public static ClassBasedNavigableIterableAssert assertThat(Iterable p0, Class p1) { return (ClassBasedNavigableIterableAssert) (Object) null; }
  public static FactoryBasedNavigableListAssert assertThat(java.util.List p0, AssertFactory p1) { return (FactoryBasedNavigableListAssert) (Object) null; }
  public static ClassBasedNavigableListAssert assertThat(java.util.List p0, Class p1) { return (ClassBasedNavigableListAssert) (Object) null; }
  public static AbstractLongAssert assertThat(long p0) { return (AbstractLongAssert) (Object) null; }
  public static AbstractLongAssert assertThat(Long p0) { return (AbstractLongAssert) (Object) null; }
  public static AbstractLongArrayAssert assertThat(long[] p0) { return (AbstractLongArrayAssert) (Object) null; }
  public static Long2DArrayAssert assertThat(long[][] p0) { return (Long2DArrayAssert) (Object) null; }
  public static ObjectAssert assertThat(Object p0) { return (ObjectAssert) (Object) null; }
  public static ObjectArrayAssert assertThat(Object[] p0) { return (ObjectArrayAssert) (Object) null; }
  public static Object2DArrayAssert assertThat(Object[][] p0) { return (Object2DArrayAssert) (Object) null; }
  public static AbstractShortAssert assertThat(short p0) { return (AbstractShortAssert) (Object) null; }
  public static AbstractShortAssert assertThat(Short p0) { return (AbstractShortAssert) (Object) null; }
  public static AbstractShortArrayAssert assertThat(short[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public static Short2DArrayAssert assertThat(short[][] p0) { return (Short2DArrayAssert) (Object) null; }
  public static AbstractDateAssert assertThat(java.util.Date p0) { return (AbstractDateAssert) (Object) null; }
  public static AbstractZonedDateTimeAssert assertThat(java.time.ZonedDateTime p0) { return (AbstractZonedDateTimeAssert) (Object) null; }
  public static AbstractLocalDateTimeAssert assertThat(java.time.LocalDateTime p0) { return (AbstractLocalDateTimeAssert) (Object) null; }
  public static AbstractOffsetDateTimeAssert assertThat(java.time.OffsetDateTime p0) { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public static AbstractOffsetTimeAssert assertThat(java.time.OffsetTime p0) { return (AbstractOffsetTimeAssert) (Object) null; }
  public static AbstractLocalTimeAssert assertThat(java.time.LocalTime p0) { return (AbstractLocalTimeAssert) (Object) null; }
  public static AbstractLocalDateAssert assertThat(java.time.LocalDate p0) { return (AbstractLocalDateAssert) (Object) null; }
  public static AbstractInstantAssert assertThat(java.time.Instant p0) { return (AbstractInstantAssert) (Object) null; }
  public static AbstractDurationAssert assertThat(java.time.Duration p0) { return (AbstractDurationAssert) (Object) null; }
  public static AbstractPeriodAssert assertThat(java.time.Period p0) { return (AbstractPeriodAssert) (Object) null; }
  public static AtomicBooleanAssert assertThat(java.util.concurrent.atomic.AtomicBoolean p0) { return (AtomicBooleanAssert) (Object) null; }
  public static AtomicIntegerAssert assertThat(java.util.concurrent.atomic.AtomicInteger p0) { return (AtomicIntegerAssert) (Object) null; }
  public static AtomicIntegerArrayAssert assertThat(java.util.concurrent.atomic.AtomicIntegerArray p0) { return (AtomicIntegerArrayAssert) (Object) null; }
  public static AtomicIntegerFieldUpdaterAssert assertThat(java.util.concurrent.atomic.AtomicIntegerFieldUpdater p0) { return (AtomicIntegerFieldUpdaterAssert) (Object) null; }
  public static LongAdderAssert assertThat(java.util.concurrent.atomic.LongAdder p0) { return (LongAdderAssert) (Object) null; }
  public static AtomicLongAssert assertThat(java.util.concurrent.atomic.AtomicLong p0) { return (AtomicLongAssert) (Object) null; }
  public static AtomicLongArrayAssert assertThat(java.util.concurrent.atomic.AtomicLongArray p0) { return (AtomicLongArrayAssert) (Object) null; }
  public static AtomicLongFieldUpdaterAssert assertThat(java.util.concurrent.atomic.AtomicLongFieldUpdater p0) { return (AtomicLongFieldUpdaterAssert) (Object) null; }
  public static AtomicReferenceAssert assertThat(java.util.concurrent.atomic.AtomicReference p0) { return (AtomicReferenceAssert) (Object) null; }
  public static AtomicReferenceArrayAssert assertThat(java.util.concurrent.atomic.AtomicReferenceArray p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public static AtomicReferenceFieldUpdaterAssert assertThat(java.util.concurrent.atomic.AtomicReferenceFieldUpdater p0) { return (AtomicReferenceFieldUpdaterAssert) (Object) null; }
  public static AtomicMarkableReferenceAssert assertThat(java.util.concurrent.atomic.AtomicMarkableReference p0) { return (AtomicMarkableReferenceAssert) (Object) null; }
  public static AtomicStampedReferenceAssert assertThat(java.util.concurrent.atomic.AtomicStampedReference p0) { return (AtomicStampedReferenceAssert) (Object) null; }
  public static AbstractThrowableAssert assertThat(Throwable p0) { return (AbstractThrowableAssert) (Object) null; }
  public static AbstractThrowableAssert assertThatThrownBy(ThrowableAssert.ThrowingCallable p0) { return (AbstractThrowableAssert) (Object) null; }
  public static AbstractThrowableAssert assertThatThrownBy(ThrowableAssert.ThrowingCallable p0, String p1, Object[] p2) { return (AbstractThrowableAssert) (Object) null; }
  public static AbstractThrowableAssert assertThatCode(ThrowableAssert.ThrowingCallable p0) { return (AbstractThrowableAssert) (Object) null; }
  public static ObjectAssert assertThatObject(Object p0) { return (ObjectAssert) (Object) null; }
  public static Throwable catchThrowable(ThrowableAssert.ThrowingCallable p0) { return (Throwable) (Object) null; }
  public static Throwable catchThrowableOfType(ThrowableAssert.ThrowingCallable p0, Class p1) { return (Throwable) (Object) null; }
  public static ThrowableTypeAssert assertThatExceptionOfType(Class p0) { return (ThrowableTypeAssert) (Object) null; }
  public static NotThrownAssert assertThatNoException() { return (NotThrownAssert) (Object) null; }
  public static ThrowableTypeAssert assertThatNullPointerException() { return (ThrowableTypeAssert) (Object) null; }
  public static ThrowableTypeAssert assertThatIllegalArgumentException() { return (ThrowableTypeAssert) (Object) null; }
  public static ThrowableTypeAssert assertThatIOException() { return (ThrowableTypeAssert) (Object) null; }
  public static ThrowableTypeAssert assertThatIllegalStateException() { return (ThrowableTypeAssert) (Object) null; }
  public static void setRemoveAssertJRelatedElementsFromStackTrace(boolean p0) {}
  public static Object fail(String p0) { return (Object) (Object) null; }
  public static Object fail(String p0, Object[] p1) { return (Object) (Object) null; }
  public static Object fail(String p0, Throwable p1) { return (Object) (Object) null; }
  public static Object failBecauseExceptionWasNotThrown(Class p0) { return (Object) (Object) null; }
  public static Object shouldHaveThrown(Class p0) { return (Object) (Object) null; }
  public static void setMaxLengthForSingleLineDescription(int p0) {}
  public static void setMaxElementsForPrinting(int p0) {}
  public static void setPrintAssertionsDescription(boolean p0) {}
  public static void setDescriptionConsumer(java.util.function.Consumer p0) {}
  public static void setMaxStackTraceElementsDisplayed(int p0) {}
  public static org.assertj.core.groups.Properties extractProperty(String p0, Class p1) { return (org.assertj.core.groups.Properties) (Object) null; }
  public static org.assertj.core.groups.Properties extractProperty(String p0) { return (org.assertj.core.groups.Properties) (Object) null; }
  public static org.assertj.core.groups.Tuple tuple(Object[] p0) { return (org.assertj.core.groups.Tuple) (Object) null; }
  public static void setAllowExtractingPrivateFields(boolean p0) {}
  public static void setAllowComparingPrivateFields(boolean p0) {}
  public static void setExtractBareNamePropertyMethods(boolean p0) {}
  public static org.assertj.core.data.MapEntry entry(Object p0, Object p1) { return (org.assertj.core.data.MapEntry) (Object) null; }
  public static org.assertj.core.data.Index atIndex(int p0) { return (org.assertj.core.data.Index) (Object) null; }
  public static org.assertj.core.data.Offset offset(Double p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Offset offset(Float p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Offset within(Double p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Offset withPrecision(Double p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Offset within(Float p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Offset withPrecision(Float p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Offset within(java.math.BigDecimal p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Offset within(java.math.BigInteger p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Offset within(Byte p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Offset within(Integer p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Offset within(Short p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Offset within(Long p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.TemporalUnitOffset within(long p0, java.time.temporal.TemporalUnit p1) { return (org.assertj.core.data.TemporalUnitOffset) (Object) null; }
  public static java.time.Duration withMarginOf(java.time.Duration p0) { return (java.time.Duration) (Object) null; }
  public static org.assertj.core.data.Percentage withinPercentage(Double p0) { return (org.assertj.core.data.Percentage) (Object) null; }
  public static org.assertj.core.data.Percentage withinPercentage(Integer p0) { return (org.assertj.core.data.Percentage) (Object) null; }
  public static org.assertj.core.data.Percentage withinPercentage(Long p0) { return (org.assertj.core.data.Percentage) (Object) null; }
  public static org.assertj.core.data.Offset byLessThan(Double p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Offset byLessThan(Float p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Offset byLessThan(java.math.BigDecimal p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Offset byLessThan(java.math.BigInteger p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Offset byLessThan(Byte p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Offset byLessThan(Integer p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Offset byLessThan(Short p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Offset byLessThan(Long p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.TemporalUnitOffset byLessThan(long p0, java.time.temporal.TemporalUnit p1) { return (org.assertj.core.data.TemporalUnitOffset) (Object) null; }
  public static java.util.function.Function from(java.util.function.Function p0) { return (java.util.function.Function) (Object) null; }
  public static InstanceOfAssertFactory as(InstanceOfAssertFactory p0) { return (InstanceOfAssertFactory) (Object) null; }
  public static Condition allOf(Condition[] p0) { return (Condition) (Object) null; }
  public static Condition allOf(Iterable p0) { return (Condition) (Object) null; }
  public static Condition anyOf(Condition[] p0) { return (Condition) (Object) null; }
  public static Condition anyOf(Iterable p0) { return (Condition) (Object) null; }
  public static org.assertj.core.condition.DoesNotHave doesNotHave(Condition p0) { return (org.assertj.core.condition.DoesNotHave) (Object) null; }
  public static org.assertj.core.condition.Not not(Condition p0) { return (org.assertj.core.condition.Not) (Object) null; }
  public static org.assertj.core.api.filter.Filters filter(Object[] p0) { return (org.assertj.core.api.filter.Filters) (Object) null; }
  public static org.assertj.core.api.filter.Filters filter(Iterable p0) { return (org.assertj.core.api.filter.Filters) (Object) null; }
  public static org.assertj.core.api.filter.InFilter in(Object[] p0) { return (org.assertj.core.api.filter.InFilter) (Object) null; }
  public static org.assertj.core.api.filter.NotInFilter notIn(Object[] p0) { return (org.assertj.core.api.filter.NotInFilter) (Object) null; }
  public static org.assertj.core.api.filter.NotFilter not(Object p0) { return (org.assertj.core.api.filter.NotFilter) (Object) null; }
  public static String contentOf(File p0, java.nio.charset.Charset p1) { return (String) (Object) null; }
  public static String contentOf(File p0, String p1) { return (String) (Object) null; }
  public static String contentOf(File p0) { return (String) (Object) null; }
  public static java.util.List linesOf(File p0) { return (java.util.List) (Object) null; }
  public static java.util.List linesOf(File p0, java.nio.charset.Charset p1) { return (java.util.List) (Object) null; }
  public static java.util.List linesOf(File p0, String p1) { return (java.util.List) (Object) null; }
  public static String contentOf(java.net.URL p0, java.nio.charset.Charset p1) { return (String) (Object) null; }
  public static String contentOf(java.net.URL p0, String p1) { return (String) (Object) null; }
  public static String contentOf(java.net.URL p0) { return (String) (Object) null; }
  public static java.util.List linesOf(java.net.URL p0) { return (java.util.List) (Object) null; }
  public static java.util.List linesOf(java.net.URL p0, java.nio.charset.Charset p1) { return (java.util.List) (Object) null; }
  public static java.util.List linesOf(java.net.URL p0, String p1) { return (java.util.List) (Object) null; }
  public static void setLenientDateParsing(boolean p0) {}
  public static void registerCustomDateFormat(java.text.DateFormat p0) {}
  public static void registerCustomDateFormat(String p0) {}
  public static void useDefaultDateFormatsOnly() {}
  public static Object assertThat(AssertProvider p0) { return (Object) (Object) null; }
  public static AbstractCharSequenceAssert assertThat(CharSequence p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public static AbstractCharSequenceAssert assertThat(StringBuilder p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public static AbstractCharSequenceAssert assertThat(StringBuffer p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public static AbstractStringAssert assertThat(String p0) { return (AbstractStringAssert) (Object) null; }
  public static IterableAssert assertThat(Iterable p0) { return (IterableAssert) (Object) null; }
  public static IteratorAssert assertThat(java.util.Iterator p0) { return (IteratorAssert) (Object) null; }
  public static ListAssert assertThat(java.util.List p0) { return (ListAssert) (Object) null; }
  public static ListAssert assertThat(java.util.stream.Stream p0) { return (ListAssert) (Object) null; }
  public static ListAssert assertThat(java.util.stream.DoubleStream p0) { return (ListAssert) (Object) null; }
  public static ListAssert assertThat(java.util.stream.LongStream p0) { return (ListAssert) (Object) null; }
  public static ListAssert assertThat(java.util.stream.IntStream p0) { return (ListAssert) (Object) null; }
  public static SpliteratorAssert assertThat(java.util.Spliterator p0) { return (SpliteratorAssert) (Object) null; }
  public static AbstractPathAssert assertThat(java.nio.file.Path p0) { return (AbstractPathAssert) (Object) null; }
  public static MapAssert assertThat(java.util.Map p0) { return (MapAssert) (Object) null; }
  public static AbstractComparableAssert assertThat(Comparable p0) { return (AbstractComparableAssert) (Object) null; }
  public static AssertDelegateTarget assertThat(AssertDelegateTarget p0) { return (AssertDelegateTarget) (Object) null; }
  public static void useRepresentation(org.assertj.core.presentation.Representation p0) {}
  public static void registerFormatterForType(Class p0, java.util.function.Function p1) {}
  public static void useDefaultRepresentation() {}
}
---
package org.assertj.core.api;
import java.io.*;

public class AssertionsForClassTypes {
  public static CompletableFutureAssert assertThat(java.util.concurrent.CompletableFuture p0) { return (CompletableFutureAssert) (Object) null; }
  public static OptionalAssert assertThat(java.util.Optional p0) { return (OptionalAssert) (Object) null; }
  public static OptionalDoubleAssert assertThat(java.util.OptionalDouble p0) { return (OptionalDoubleAssert) (Object) null; }
  public static OptionalIntAssert assertThat(java.util.OptionalInt p0) { return (OptionalIntAssert) (Object) null; }
  public static OptionalLongAssert assertThat(java.util.OptionalLong p0) { return (OptionalLongAssert) (Object) null; }
  public static AbstractBigDecimalAssert assertThat(java.math.BigDecimal p0) { return (AbstractBigDecimalAssert) (Object) null; }
  public static AbstractUriAssert assertThat(java.net.URI p0) { return (AbstractUriAssert) (Object) null; }
  public static AbstractUrlAssert assertThat(java.net.URL p0) { return (AbstractUrlAssert) (Object) null; }
  public static AbstractBooleanAssert assertThat(boolean p0) { return (AbstractBooleanAssert) (Object) null; }
  public static AbstractBooleanAssert assertThat(Boolean p0) { return (AbstractBooleanAssert) (Object) null; }
  public static AbstractBooleanArrayAssert assertThat(boolean[] p0) { return (AbstractBooleanArrayAssert) (Object) null; }
  public static Boolean2DArrayAssert assertThat(boolean[][] p0) { return (Boolean2DArrayAssert) (Object) null; }
  public static AbstractByteAssert assertThat(byte p0) { return (AbstractByteAssert) (Object) null; }
  public static AbstractByteAssert assertThat(Byte p0) { return (AbstractByteAssert) (Object) null; }
  public static AbstractByteArrayAssert assertThat(byte[] p0) { return (AbstractByteArrayAssert) (Object) null; }
  public static Byte2DArrayAssert assertThat(byte[][] p0) { return (Byte2DArrayAssert) (Object) null; }
  public static AbstractCharacterAssert assertThat(char p0) { return (AbstractCharacterAssert) (Object) null; }
  public static AbstractCharArrayAssert assertThat(char[] p0) { return (AbstractCharArrayAssert) (Object) null; }
  public static Char2DArrayAssert assertThat(char[][] p0) { return (Char2DArrayAssert) (Object) null; }
  public static AbstractCharacterAssert assertThat(Character p0) { return (AbstractCharacterAssert) (Object) null; }
  public static ClassAssert assertThat(Class p0) { return (ClassAssert) (Object) null; }
  public static AbstractDoubleAssert assertThat(double p0) { return (AbstractDoubleAssert) (Object) null; }
  public static AbstractDoubleAssert assertThat(Double p0) { return (AbstractDoubleAssert) (Object) null; }
  public static AbstractDoubleArrayAssert assertThat(double[] p0) { return (AbstractDoubleArrayAssert) (Object) null; }
  public static Double2DArrayAssert assertThat(double[][] p0) { return (Double2DArrayAssert) (Object) null; }
  public static AbstractFileAssert assertThat(File p0) { return (AbstractFileAssert) (Object) null; }
  public static AbstractInputStreamAssert assertThat(InputStream p0) { return (AbstractInputStreamAssert) (Object) null; }
  public static AbstractFloatAssert assertThat(float p0) { return (AbstractFloatAssert) (Object) null; }
  public static AbstractFloatAssert assertThat(Float p0) { return (AbstractFloatAssert) (Object) null; }
  public static AbstractFloatArrayAssert assertThat(float[] p0) { return (AbstractFloatArrayAssert) (Object) null; }
  public static Float2DArrayAssert assertThat(float[][] p0) { return (Float2DArrayAssert) (Object) null; }
  public static AbstractIntegerAssert assertThat(int p0) { return (AbstractIntegerAssert) (Object) null; }
  public static AbstractIntArrayAssert assertThat(int[] p0) { return (AbstractIntArrayAssert) (Object) null; }
  public static Int2DArrayAssert assertThat(int[][] p0) { return (Int2DArrayAssert) (Object) null; }
  public static AbstractIntegerAssert assertThat(Integer p0) { return (AbstractIntegerAssert) (Object) null; }
  public static AbstractLongAssert assertThat(long p0) { return (AbstractLongAssert) (Object) null; }
  public static AbstractLongAssert assertThat(Long p0) { return (AbstractLongAssert) (Object) null; }
  public static AbstractLongArrayAssert assertThat(long[] p0) { return (AbstractLongArrayAssert) (Object) null; }
  public static Long2DArrayAssert assertThat(long[][] p0) { return (Long2DArrayAssert) (Object) null; }
  public static ObjectAssert assertThat(Object p0) { return (ObjectAssert) (Object) null; }
  public static ObjectArrayAssert assertThat(Object[] p0) { return (ObjectArrayAssert) (Object) null; }
  public static Object2DArrayAssert assertThat(Object[][] p0) { return (Object2DArrayAssert) (Object) null; }
  public static AbstractShortAssert assertThat(short p0) { return (AbstractShortAssert) (Object) null; }
  public static AbstractShortAssert assertThat(Short p0) { return (AbstractShortAssert) (Object) null; }
  public static AbstractShortArrayAssert assertThat(short[] p0) { return (AbstractShortArrayAssert) (Object) null; }
  public static Short2DArrayAssert assertThat(short[][] p0) { return (Short2DArrayAssert) (Object) null; }
  public static AbstractCharSequenceAssert assertThat(StringBuilder p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public static AbstractCharSequenceAssert assertThat(StringBuffer p0) { return (AbstractCharSequenceAssert) (Object) null; }
  public static AbstractStringAssert assertThat(String p0) { return (AbstractStringAssert) (Object) null; }
  public static AbstractDateAssert assertThat(java.util.Date p0) { return (AbstractDateAssert) (Object) null; }
  public static AbstractZonedDateTimeAssert assertThat(java.time.ZonedDateTime p0) { return (AbstractZonedDateTimeAssert) (Object) null; }
  public static AbstractLocalDateTimeAssert assertThat(java.time.LocalDateTime p0) { return (AbstractLocalDateTimeAssert) (Object) null; }
  public static AbstractOffsetDateTimeAssert assertThat(java.time.OffsetDateTime p0) { return (AbstractOffsetDateTimeAssert) (Object) null; }
  public static AbstractOffsetTimeAssert assertThat(java.time.OffsetTime p0) { return (AbstractOffsetTimeAssert) (Object) null; }
  public static AbstractLocalTimeAssert assertThat(java.time.LocalTime p0) { return (AbstractLocalTimeAssert) (Object) null; }
  public static AbstractLocalDateAssert assertThat(java.time.LocalDate p0) { return (AbstractLocalDateAssert) (Object) null; }
  public static AbstractInstantAssert assertThat(java.time.Instant p0) { return (AbstractInstantAssert) (Object) null; }
  public static AbstractDurationAssert assertThat(java.time.Duration p0) { return (AbstractDurationAssert) (Object) null; }
  public static AbstractPeriodAssert assertThat(java.time.Period p0) { return (AbstractPeriodAssert) (Object) null; }
  public static AbstractThrowableAssert assertThat(Throwable p0) { return (AbstractThrowableAssert) (Object) null; }
  public static AbstractThrowableAssert assertThatThrownBy(ThrowableAssert.ThrowingCallable p0) { return (AbstractThrowableAssert) (Object) null; }
  public static AbstractThrowableAssert assertThatThrownBy(ThrowableAssert.ThrowingCallable p0, String p1, Object[] p2) { return (AbstractThrowableAssert) (Object) null; }
  public static ThrowableTypeAssert assertThatExceptionOfType(Class p0) { return (ThrowableTypeAssert) (Object) null; }
  public static NotThrownAssert assertThatNoException() { return (NotThrownAssert) (Object) null; }
  public static AbstractThrowableAssert assertThatCode(ThrowableAssert.ThrowingCallable p0) { return (AbstractThrowableAssert) (Object) null; }
  public static Throwable catchThrowable(ThrowableAssert.ThrowingCallable p0) { return (Throwable) (Object) null; }
  public static Throwable catchThrowableOfType(ThrowableAssert.ThrowingCallable p0, Class p1) { return (Throwable) (Object) null; }
  public static void setRemoveAssertJRelatedElementsFromStackTrace(boolean p0) {}
  public static void fail(String p0) {}
  public static void fail(String p0, Throwable p1) {}
  public static void failBecauseExceptionWasNotThrown(Class p0) {}
  public static void shouldHaveThrown(Class p0) {}
  public static void setMaxLengthForSingleLineDescription(int p0) {}
  public static org.assertj.core.groups.Properties extractProperty(String p0, Class p1) { return (org.assertj.core.groups.Properties) (Object) null; }
  public static org.assertj.core.groups.Properties extractProperty(String p0) { return (org.assertj.core.groups.Properties) (Object) null; }
  public static org.assertj.core.groups.Tuple tuple(Object[] p0) { return (org.assertj.core.groups.Tuple) (Object) null; }
  public static void setAllowExtractingPrivateFields(boolean p0) {}
  public static void setAllowComparingPrivateFields(boolean p0) {}
  public static org.assertj.core.data.MapEntry entry(Object p0, Object p1) { return (org.assertj.core.data.MapEntry) (Object) null; }
  public static org.assertj.core.data.Index atIndex(int p0) { return (org.assertj.core.data.Index) (Object) null; }
  public static org.assertj.core.data.Offset offset(Double p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Offset offset(Float p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Offset within(Double p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Offset within(Float p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Offset within(java.math.BigDecimal p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Offset within(Byte p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Offset within(Integer p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Offset within(Short p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Offset within(Long p0) { return (org.assertj.core.data.Offset) (Object) null; }
  public static org.assertj.core.data.Percentage withinPercentage(Double p0) { return (org.assertj.core.data.Percentage) (Object) null; }
  public static org.assertj.core.data.Percentage withinPercentage(Integer p0) { return (org.assertj.core.data.Percentage) (Object) null; }
  public static org.assertj.core.data.Percentage withinPercentage(Long p0) { return (org.assertj.core.data.Percentage) (Object) null; }
  public static Condition allOf(Condition[] p0) { return (Condition) (Object) null; }
  public static Condition allOf(Iterable p0) { return (Condition) (Object) null; }
  public static Condition anyOf(Condition[] p0) { return (Condition) (Object) null; }
  public static Condition anyOf(Iterable p0) { return (Condition) (Object) null; }
  public static org.assertj.core.condition.DoesNotHave doesNotHave(Condition p0) { return (org.assertj.core.condition.DoesNotHave) (Object) null; }
  public static org.assertj.core.condition.Not not(Condition p0) { return (org.assertj.core.condition.Not) (Object) null; }
  public static org.assertj.core.api.filter.Filters filter(Object[] p0) { return (org.assertj.core.api.filter.Filters) (Object) null; }
  public static org.assertj.core.api.filter.Filters filter(Iterable p0) { return (org.assertj.core.api.filter.Filters) (Object) null; }
  public static org.assertj.core.api.filter.InFilter in(Object[] p0) { return (org.assertj.core.api.filter.InFilter) (Object) null; }
  public static org.assertj.core.api.filter.NotInFilter notIn(Object[] p0) { return (org.assertj.core.api.filter.NotInFilter) (Object) null; }
  public static org.assertj.core.api.filter.NotFilter not(Object p0) { return (org.assertj.core.api.filter.NotFilter) (Object) null; }
  public static String contentOf(File p0, java.nio.charset.Charset p1) { return (String) (Object) null; }
  public static String contentOf(File p0, String p1) { return (String) (Object) null; }
  public static String contentOf(File p0) { return (String) (Object) null; }
  public static java.util.List linesOf(File p0) { return (java.util.List) (Object) null; }
  public static java.util.List linesOf(File p0, java.nio.charset.Charset p1) { return (java.util.List) (Object) null; }
  public static java.util.List linesOf(File p0, String p1) { return (java.util.List) (Object) null; }
  public static String contentOf(java.net.URL p0, java.nio.charset.Charset p1) { return (String) (Object) null; }
  public static String contentOf(java.net.URL p0, String p1) { return (String) (Object) null; }
  public static String contentOf(java.net.URL p0) { return (String) (Object) null; }
  public static java.util.List linesOf(java.net.URL p0) { return (java.util.List) (Object) null; }
  public static java.util.List linesOf(java.net.URL p0, java.nio.charset.Charset p1) { return (java.util.List) (Object) null; }
  public static java.util.List linesOf(java.net.URL p0, String p1) { return (java.util.List) (Object) null; }
  public static void setLenientDateParsing(boolean p0) {}
  public static void registerCustomDateFormat(java.text.DateFormat p0) {}
  public static void registerCustomDateFormat(String p0) {}
  public static void useDefaultDateFormatsOnly() {}
}
---
package org.assertj.core.api;
import java.io.*;

public class AtomicBooleanAssert extends AbstractAssert {
  public void AtomicBooleanAssert(java.util.concurrent.atomic.AtomicBoolean p0) {}
  public AtomicBooleanAssert isTrue() { return (AtomicBooleanAssert) (Object) null; }
  public AtomicBooleanAssert isFalse() { return (AtomicBooleanAssert) (Object) null; }
  public AtomicBooleanAssert usingComparator(java.util.Comparator p0) { return (AtomicBooleanAssert) (Object) null; }
  public AtomicBooleanAssert usingComparator(java.util.Comparator p0, String p1) { return (AtomicBooleanAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class AtomicIntegerArrayAssert extends AbstractEnumerableAssert {
  public void AtomicIntegerArrayAssert(java.util.concurrent.atomic.AtomicIntegerArray p0) {}
  public void isNullOrEmpty() {}
  public void isEmpty() {}
  public AtomicIntegerArrayAssert isNotEmpty() { return (AtomicIntegerArrayAssert) (Object) null; }
  public AtomicIntegerArrayAssert hasArray(int[] p0) { return (AtomicIntegerArrayAssert) (Object) null; }
  public AtomicIntegerArrayAssert hasSize(int p0) { return (AtomicIntegerArrayAssert) (Object) null; }
  public AtomicIntegerArrayAssert hasSizeGreaterThan(int p0) { return (AtomicIntegerArrayAssert) (Object) null; }
  public AtomicIntegerArrayAssert hasSizeGreaterThanOrEqualTo(int p0) { return (AtomicIntegerArrayAssert) (Object) null; }
  public AtomicIntegerArrayAssert hasSizeLessThan(int p0) { return (AtomicIntegerArrayAssert) (Object) null; }
  public AtomicIntegerArrayAssert hasSizeLessThanOrEqualTo(int p0) { return (AtomicIntegerArrayAssert) (Object) null; }
  public AtomicIntegerArrayAssert hasSizeBetween(int p0, int p1) { return (AtomicIntegerArrayAssert) (Object) null; }
  public AtomicIntegerArrayAssert hasSameSizeAs(Iterable p0) { return (AtomicIntegerArrayAssert) (Object) null; }
  public AtomicIntegerArrayAssert contains(int[] p0) { return (AtomicIntegerArrayAssert) (Object) null; }
  public AtomicIntegerArrayAssert containsOnly(int[] p0) { return (AtomicIntegerArrayAssert) (Object) null; }
  public AtomicIntegerArrayAssert containsOnlyOnce(int[] p0) { return (AtomicIntegerArrayAssert) (Object) null; }
  public AtomicIntegerArrayAssert containsSequence(int[] p0) { return (AtomicIntegerArrayAssert) (Object) null; }
  public AtomicIntegerArrayAssert containsSubsequence(int[] p0) { return (AtomicIntegerArrayAssert) (Object) null; }
  public AtomicIntegerArrayAssert contains(int p0, org.assertj.core.data.Index p1) { return (AtomicIntegerArrayAssert) (Object) null; }
  public AtomicIntegerArrayAssert doesNotContain(int[] p0) { return (AtomicIntegerArrayAssert) (Object) null; }
  public AtomicIntegerArrayAssert doesNotContain(int p0, org.assertj.core.data.Index p1) { return (AtomicIntegerArrayAssert) (Object) null; }
  public AtomicIntegerArrayAssert doesNotHaveDuplicates() { return (AtomicIntegerArrayAssert) (Object) null; }
  public AtomicIntegerArrayAssert startsWith(int[] p0) { return (AtomicIntegerArrayAssert) (Object) null; }
  public AtomicIntegerArrayAssert endsWith(int[] p0) { return (AtomicIntegerArrayAssert) (Object) null; }
  public AtomicIntegerArrayAssert isSorted() { return (AtomicIntegerArrayAssert) (Object) null; }
  public AtomicIntegerArrayAssert isSortedAccordingTo(java.util.Comparator p0) { return (AtomicIntegerArrayAssert) (Object) null; }
  public AtomicIntegerArrayAssert usingElementComparator(java.util.Comparator p0) { return (AtomicIntegerArrayAssert) (Object) null; }
  public AtomicIntegerArrayAssert usingDefaultElementComparator() { return (AtomicIntegerArrayAssert) (Object) null; }
  public AtomicIntegerArrayAssert containsExactly(int[] p0) { return (AtomicIntegerArrayAssert) (Object) null; }
  public AtomicIntegerArrayAssert containsExactlyInAnyOrder(int[] p0) { return (AtomicIntegerArrayAssert) (Object) null; }
  public AtomicIntegerArrayAssert containsAnyOf(int[] p0) { return (AtomicIntegerArrayAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class AtomicIntegerAssert extends AbstractAssert {
  public void AtomicIntegerAssert(java.util.concurrent.atomic.AtomicInteger p0) {}
  public AtomicIntegerAssert hasValueBetween(int p0, int p1) { return (AtomicIntegerAssert) (Object) null; }
  public AtomicIntegerAssert hasValueLessThan(int p0) { return (AtomicIntegerAssert) (Object) null; }
  public AtomicIntegerAssert hasValueLessThanOrEqualTo(int p0) { return (AtomicIntegerAssert) (Object) null; }
  public AtomicIntegerAssert hasValueGreaterThan(int p0) { return (AtomicIntegerAssert) (Object) null; }
  public AtomicIntegerAssert hasValueGreaterThanOrEqualTo(int p0) { return (AtomicIntegerAssert) (Object) null; }
  public AtomicIntegerAssert hasPositiveValue() { return (AtomicIntegerAssert) (Object) null; }
  public AtomicIntegerAssert hasNonPositiveValue() { return (AtomicIntegerAssert) (Object) null; }
  public AtomicIntegerAssert hasNegativeValue() { return (AtomicIntegerAssert) (Object) null; }
  public AtomicIntegerAssert hasNonNegativeValue() { return (AtomicIntegerAssert) (Object) null; }
  public AtomicIntegerAssert hasValueCloseTo(int p0, org.assertj.core.data.Percentage p1) { return (AtomicIntegerAssert) (Object) null; }
  public AtomicIntegerAssert hasValueCloseTo(int p0, org.assertj.core.data.Offset p1) { return (AtomicIntegerAssert) (Object) null; }
  public AtomicIntegerAssert hasValue(int p0) { return (AtomicIntegerAssert) (Object) null; }
  public AtomicIntegerAssert doesNotHaveValue(int p0) { return (AtomicIntegerAssert) (Object) null; }
  public AtomicIntegerAssert usingComparator(java.util.Comparator p0) { return (AtomicIntegerAssert) (Object) null; }
  public AtomicIntegerAssert usingComparator(java.util.Comparator p0, String p1) { return (AtomicIntegerAssert) (Object) null; }
  public AtomicIntegerAssert usingDefaultComparator() { return (AtomicIntegerAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class AtomicIntegerFieldUpdaterAssert extends AbstractAtomicFieldUpdaterAssert {
  public void AtomicIntegerFieldUpdaterAssert(java.util.concurrent.atomic.AtomicIntegerFieldUpdater p0) {}
  public AtomicIntegerFieldUpdaterAssert hasValue(Integer p0, Object p1) { return (AtomicIntegerFieldUpdaterAssert) (Object) null; }
  public AbstractAtomicFieldUpdaterAssert hasValue(Object p0, Object p1) { return (AbstractAtomicFieldUpdaterAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class AtomicLongArrayAssert extends AbstractEnumerableAssert {
  public void AtomicLongArrayAssert(java.util.concurrent.atomic.AtomicLongArray p0) {}
  public void isNullOrEmpty() {}
  public void isEmpty() {}
  public AtomicLongArrayAssert isNotEmpty() { return (AtomicLongArrayAssert) (Object) null; }
  public AtomicLongArrayAssert hasArray(long[] p0) { return (AtomicLongArrayAssert) (Object) null; }
  public AtomicLongArrayAssert hasSize(int p0) { return (AtomicLongArrayAssert) (Object) null; }
  public AtomicLongArrayAssert hasSizeGreaterThan(int p0) { return (AtomicLongArrayAssert) (Object) null; }
  public AtomicLongArrayAssert hasSizeGreaterThanOrEqualTo(int p0) { return (AtomicLongArrayAssert) (Object) null; }
  public AtomicLongArrayAssert hasSizeLessThan(int p0) { return (AtomicLongArrayAssert) (Object) null; }
  public AtomicLongArrayAssert hasSizeLessThanOrEqualTo(int p0) { return (AtomicLongArrayAssert) (Object) null; }
  public AtomicLongArrayAssert hasSizeBetween(int p0, int p1) { return (AtomicLongArrayAssert) (Object) null; }
  public AtomicLongArrayAssert hasSameSizeAs(Iterable p0) { return (AtomicLongArrayAssert) (Object) null; }
  public AtomicLongArrayAssert contains(long[] p0) { return (AtomicLongArrayAssert) (Object) null; }
  public AtomicLongArrayAssert containsOnly(long[] p0) { return (AtomicLongArrayAssert) (Object) null; }
  public AtomicLongArrayAssert containsOnlyOnce(long[] p0) { return (AtomicLongArrayAssert) (Object) null; }
  public AtomicLongArrayAssert containsSequence(long[] p0) { return (AtomicLongArrayAssert) (Object) null; }
  public AtomicLongArrayAssert containsSubsequence(long[] p0) { return (AtomicLongArrayAssert) (Object) null; }
  public AtomicLongArrayAssert contains(long p0, org.assertj.core.data.Index p1) { return (AtomicLongArrayAssert) (Object) null; }
  public AtomicLongArrayAssert doesNotContain(long[] p0) { return (AtomicLongArrayAssert) (Object) null; }
  public AtomicLongArrayAssert doesNotContain(long p0, org.assertj.core.data.Index p1) { return (AtomicLongArrayAssert) (Object) null; }
  public AtomicLongArrayAssert doesNotHaveDuplicates() { return (AtomicLongArrayAssert) (Object) null; }
  public AtomicLongArrayAssert startsWith(long[] p0) { return (AtomicLongArrayAssert) (Object) null; }
  public AtomicLongArrayAssert endsWith(long[] p0) { return (AtomicLongArrayAssert) (Object) null; }
  public AtomicLongArrayAssert isSorted() { return (AtomicLongArrayAssert) (Object) null; }
  public AtomicLongArrayAssert isSortedAccordingTo(java.util.Comparator p0) { return (AtomicLongArrayAssert) (Object) null; }
  public AtomicLongArrayAssert usingElementComparator(java.util.Comparator p0) { return (AtomicLongArrayAssert) (Object) null; }
  public AtomicLongArrayAssert usingDefaultElementComparator() { return (AtomicLongArrayAssert) (Object) null; }
  public AtomicLongArrayAssert containsExactly(long[] p0) { return (AtomicLongArrayAssert) (Object) null; }
  public AtomicLongArrayAssert containsExactlyInAnyOrder(long[] p0) { return (AtomicLongArrayAssert) (Object) null; }
  public AtomicLongArrayAssert containsAnyOf(long[] p0) { return (AtomicLongArrayAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class AtomicLongAssert extends AbstractAssert {
  public void AtomicLongAssert(java.util.concurrent.atomic.AtomicLong p0) {}
  public AtomicLongAssert hasValueBetween(long p0, long p1) { return (AtomicLongAssert) (Object) null; }
  public AtomicLongAssert hasValueLessThan(long p0) { return (AtomicLongAssert) (Object) null; }
  public AtomicLongAssert hasValueLessThanOrEqualTo(long p0) { return (AtomicLongAssert) (Object) null; }
  public AtomicLongAssert hasValueGreaterThan(long p0) { return (AtomicLongAssert) (Object) null; }
  public AtomicLongAssert hasValueGreaterThanOrEqualTo(long p0) { return (AtomicLongAssert) (Object) null; }
  public AtomicLongAssert hasPositiveValue() { return (AtomicLongAssert) (Object) null; }
  public AtomicLongAssert hasNonPositiveValue() { return (AtomicLongAssert) (Object) null; }
  public AtomicLongAssert hasNegativeValue() { return (AtomicLongAssert) (Object) null; }
  public AtomicLongAssert hasNonNegativeValue() { return (AtomicLongAssert) (Object) null; }
  public AtomicLongAssert hasValueCloseTo(long p0, org.assertj.core.data.Percentage p1) { return (AtomicLongAssert) (Object) null; }
  public AtomicLongAssert hasValueCloseTo(long p0, org.assertj.core.data.Offset p1) { return (AtomicLongAssert) (Object) null; }
  public AtomicLongAssert hasValue(long p0) { return (AtomicLongAssert) (Object) null; }
  public AtomicLongAssert doesNotHaveValue(long p0) { return (AtomicLongAssert) (Object) null; }
  public AtomicLongAssert usingComparator(java.util.Comparator p0) { return (AtomicLongAssert) (Object) null; }
  public AtomicLongAssert usingComparator(java.util.Comparator p0, String p1) { return (AtomicLongAssert) (Object) null; }
  public AtomicLongAssert usingDefaultComparator() { return (AtomicLongAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class AtomicLongFieldUpdaterAssert extends AbstractAtomicFieldUpdaterAssert {
  public void AtomicLongFieldUpdaterAssert(java.util.concurrent.atomic.AtomicLongFieldUpdater p0) {}
  public AtomicLongFieldUpdaterAssert hasValue(Long p0, Object p1) { return (AtomicLongFieldUpdaterAssert) (Object) null; }
  public AbstractAtomicFieldUpdaterAssert hasValue(Object p0, Object p1) { return (AbstractAtomicFieldUpdaterAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class AtomicMarkableReferenceAssert extends AbstractAtomicReferenceAssert {
  public void AtomicMarkableReferenceAssert(java.util.concurrent.atomic.AtomicMarkableReference p0) {}
  public AtomicMarkableReferenceAssert hasReference(Object p0) { return (AtomicMarkableReferenceAssert) (Object) null; }
  public AtomicMarkableReferenceAssert isMarked() { return (AtomicMarkableReferenceAssert) (Object) null; }
  public AtomicMarkableReferenceAssert isNotMarked() { return (AtomicMarkableReferenceAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class AtomicReferenceArrayAssert extends AbstractAssert implements IndexedObjectEnumerableAssert, ArraySortedAssert {
  public void AtomicReferenceArrayAssert(java.util.concurrent.atomic.AtomicReferenceArray p0) {}
  public AtomicReferenceArrayAssert as(org.assertj.core.description.Description p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert as(String p0, Object[] p1) { return (AtomicReferenceArrayAssert) (Object) null; }
  public void isNullOrEmpty() {}
  public void isEmpty() {}
  public AtomicReferenceArrayAssert isNotEmpty() { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert hasArray(Object[] p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert hasOnlyOneElementSatisfying(java.util.function.Consumer p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert hasSize(int p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert hasSizeGreaterThan(int p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert hasSizeGreaterThanOrEqualTo(int p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert hasSizeLessThan(int p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert hasSizeLessThanOrEqualTo(int p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert hasSizeBetween(int p0, int p1) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert hasSameSizeAs(Object p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert hasSameSizeAs(Iterable p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert contains(Object[] p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert containsOnly(Object[] p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert containsOnlyElementsOf(Iterable p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert containsOnlyNulls() { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert hasSameElementsAs(Iterable p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert containsOnlyOnce(Object[] p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert containsOnlyOnceElementsOf(Iterable p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert containsExactly(Object[] p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert containsExactlyInAnyOrder(Object[] p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert containsExactlyInAnyOrderElementsOf(Iterable p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert containsExactlyElementsOf(Iterable p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert containsSequence(Object[] p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert containsSequence(Iterable p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert doesNotContainSequence(Object[] p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert doesNotContainSequence(Iterable p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert containsSubsequence(Object[] p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert containsSubsequence(Iterable p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert doesNotContainSubsequence(Object[] p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert doesNotContainSubsequence(Iterable p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert contains(Object p0, org.assertj.core.data.Index p1) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert hasOnlyElementsOfTypes(Class[] p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert doesNotContain(Object p0, org.assertj.core.data.Index p1) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert doesNotContain(Object[] p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert doesNotContainAnyElementsOf(Iterable p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert doesNotHaveDuplicates() { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert startsWith(Object[] p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert endsWith(Object p0, Object[] p1) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert endsWith(Object[] p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert isSubsetOf(Iterable p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert isSubsetOf(Object[] p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert containsNull() { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert doesNotContainNull() { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert are(Condition p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert areNot(Condition p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert have(Condition p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert doNotHave(Condition p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert areAtLeast(int p0, Condition p1) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert areAtLeastOne(Condition p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert areAtMost(int p0, Condition p1) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert areExactly(int p0, Condition p1) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert haveAtLeastOne(Condition p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert haveAtLeast(int p0, Condition p1) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert haveAtMost(int p0, Condition p1) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert haveExactly(int p0, Condition p1) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert hasAtLeastOneElementOfType(Class p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert hasOnlyElementsOfType(Class p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert doesNotHaveAnyElementsOfTypes(Class[] p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert isSorted() { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert isSortedAccordingTo(java.util.Comparator p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert containsAll(Iterable p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert usingElementComparator(java.util.Comparator p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert usingDefaultElementComparator() { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert usingComparatorForElementFieldsWithNames(java.util.Comparator p0, String[] p1) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert usingComparatorForElementFieldsWithType(java.util.Comparator p0, Class p1) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert usingComparatorForType(java.util.Comparator p0, Class p1) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert usingFieldByFieldElementComparator() { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert usingRecursiveFieldByFieldElementComparator() { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert usingElementComparatorOnFields(String[] p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert usingElementComparatorIgnoringFields(String[] p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public ObjectArrayAssert extracting(String p0) { return (ObjectArrayAssert) (Object) null; }
  public ObjectArrayAssert extracting(String p0, Class p1) { return (ObjectArrayAssert) (Object) null; }
  public ObjectArrayAssert extracting(String[] p0) { return (ObjectArrayAssert) (Object) null; }
  public ObjectArrayAssert extracting(java.util.function.Function p0) { return (ObjectArrayAssert) (Object) null; }
  public ObjectArrayAssert extracting(org.assertj.core.api.iterable.ThrowingExtractor p0) { return (ObjectArrayAssert) (Object) null; }
  public ObjectArrayAssert flatExtracting(java.util.function.Function p0) { return (ObjectArrayAssert) (Object) null; }
  public ObjectArrayAssert flatExtracting(org.assertj.core.api.iterable.ThrowingExtractor p0) { return (ObjectArrayAssert) (Object) null; }
  public ObjectArrayAssert flatExtracting(String p0) { return (ObjectArrayAssert) (Object) null; }
  public ObjectArrayAssert extractingResultOf(String p0) { return (ObjectArrayAssert) (Object) null; }
  public ObjectArrayAssert extractingResultOf(String p0, Class p1) { return (ObjectArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert inHexadecimal() { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert inBinary() { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert filteredOn(String p0, Object p1) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert filteredOnNull(String p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert filteredOn(String p0, org.assertj.core.api.filter.FilterOperator p1) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert filteredOn(Condition p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert filteredOn(java.util.function.Predicate p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert filteredOn(java.util.function.Function p0, Object p1) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert allMatch(java.util.function.Predicate p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert allMatch(java.util.function.Predicate p0, String p1) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert allSatisfy(java.util.function.Consumer p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert anyMatch(java.util.function.Predicate p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert anySatisfy(java.util.function.Consumer p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert noneSatisfy(java.util.function.Consumer p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert satisfiesExactly(java.util.function.Consumer[] p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert satisfiesExactlyInAnyOrder(java.util.function.Consumer[] p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert containsAnyOf(Object[] p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert containsAnyElementsOf(Iterable p0) { return (AtomicReferenceArrayAssert) (Object) null; }
  public AtomicReferenceArrayAssert noneMatch(java.util.function.Predicate p0) { return (AtomicReferenceArrayAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class AtomicReferenceAssert extends AbstractAssert {
  public void AtomicReferenceAssert(java.util.concurrent.atomic.AtomicReference p0) {}
  public AtomicReferenceAssert hasValue(Object p0) { return (AtomicReferenceAssert) (Object) null; }
  public AtomicReferenceAssert doesNotHaveValue(Object p0) { return (AtomicReferenceAssert) (Object) null; }
  public AtomicReferenceAssert hasValueMatching(java.util.function.Predicate p0) { return (AtomicReferenceAssert) (Object) null; }
  public AtomicReferenceAssert hasValueMatching(java.util.function.Predicate p0, String p1) { return (AtomicReferenceAssert) (Object) null; }
  public AtomicReferenceAssert hasValueSatisfying(java.util.function.Consumer p0) { return (AtomicReferenceAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class AtomicReferenceFieldUpdaterAssert extends AbstractAtomicFieldUpdaterAssert {
  public void AtomicReferenceFieldUpdaterAssert(java.util.concurrent.atomic.AtomicReferenceFieldUpdater p0) {}
  public AtomicReferenceFieldUpdaterAssert hasValue(Object p0, Object p1) { return (AtomicReferenceFieldUpdaterAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class AtomicStampedReferenceAssert extends AbstractAtomicReferenceAssert {
  public void AtomicStampedReferenceAssert(java.util.concurrent.atomic.AtomicStampedReference p0) {}
  public AtomicStampedReferenceAssert hasReference(Object p0) { return (AtomicStampedReferenceAssert) (Object) null; }
  public AtomicStampedReferenceAssert hasStamp(int p0) { return (AtomicStampedReferenceAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class Boolean2DArrayAssert extends Abstract2DArrayAssert {
  public void Boolean2DArrayAssert(boolean[][] p0) {}
  public Boolean2DArrayAssert isDeepEqualTo(boolean[][] p0) { return (Boolean2DArrayAssert) (Object) null; }
  public Boolean2DArrayAssert isEqualTo(Object p0) { return (Boolean2DArrayAssert) (Object) null; }
  public void isNullOrEmpty() {}
  public void isEmpty() {}
  public Boolean2DArrayAssert isNotEmpty() { return (Boolean2DArrayAssert) (Object) null; }
  public Boolean2DArrayAssert hasDimensions(int p0, int p1) { return (Boolean2DArrayAssert) (Object) null; }
  public Boolean2DArrayAssert hasSameDimensionsAs(Object p0) { return (Boolean2DArrayAssert) (Object) null; }
  public Boolean2DArrayAssert contains(boolean[] p0, org.assertj.core.data.Index p1) { return (Boolean2DArrayAssert) (Object) null; }
  public Boolean2DArrayAssert doesNotContain(boolean[] p0, org.assertj.core.data.Index p1) { return (Boolean2DArrayAssert) (Object) null; }
  public Abstract2DArrayAssert isDeepEqualTo(Object p0) { return (Abstract2DArrayAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class BooleanAssert extends AbstractBooleanAssert {
  public void BooleanAssert(Boolean p0) {}
  public void BooleanAssert(java.util.concurrent.atomic.AtomicBoolean p0) {}
}
---
package org.assertj.core.api;
import java.io.*;

public class Byte2DArrayAssert extends Abstract2DArrayAssert {
  public void Byte2DArrayAssert(byte[][] p0) {}
  public Byte2DArrayAssert isDeepEqualTo(byte[][] p0) { return (Byte2DArrayAssert) (Object) null; }
  public Byte2DArrayAssert isEqualTo(Object p0) { return (Byte2DArrayAssert) (Object) null; }
  public void isNullOrEmpty() {}
  public void isEmpty() {}
  public Byte2DArrayAssert isNotEmpty() { return (Byte2DArrayAssert) (Object) null; }
  public Byte2DArrayAssert hasDimensions(int p0, int p1) { return (Byte2DArrayAssert) (Object) null; }
  public Byte2DArrayAssert hasSameDimensionsAs(Object p0) { return (Byte2DArrayAssert) (Object) null; }
  public Byte2DArrayAssert contains(byte[] p0, org.assertj.core.data.Index p1) { return (Byte2DArrayAssert) (Object) null; }
  public Byte2DArrayAssert doesNotContain(byte[] p0, org.assertj.core.data.Index p1) { return (Byte2DArrayAssert) (Object) null; }
  public Abstract2DArrayAssert isDeepEqualTo(Object p0) { return (Abstract2DArrayAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class Char2DArrayAssert extends Abstract2DArrayAssert {
  public void Char2DArrayAssert(char[][] p0) {}
  public Char2DArrayAssert isDeepEqualTo(char[][] p0) { return (Char2DArrayAssert) (Object) null; }
  public Char2DArrayAssert isEqualTo(Object p0) { return (Char2DArrayAssert) (Object) null; }
  public void isNullOrEmpty() {}
  public void isEmpty() {}
  public Char2DArrayAssert isNotEmpty() { return (Char2DArrayAssert) (Object) null; }
  public Char2DArrayAssert hasDimensions(int p0, int p1) { return (Char2DArrayAssert) (Object) null; }
  public Char2DArrayAssert hasSameDimensionsAs(Object p0) { return (Char2DArrayAssert) (Object) null; }
  public Char2DArrayAssert contains(char[] p0, org.assertj.core.data.Index p1) { return (Char2DArrayAssert) (Object) null; }
  public Char2DArrayAssert doesNotContain(char[] p0, org.assertj.core.data.Index p1) { return (Char2DArrayAssert) (Object) null; }
  public Char2DArrayAssert inUnicode() { return (Char2DArrayAssert) (Object) null; }
  public Abstract2DArrayAssert isDeepEqualTo(Object p0) { return (Abstract2DArrayAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class ClassAssert extends AbstractClassAssert {
  public void ClassAssert(Class p0) {}
  public ClassAssert hasAnnotations(Class[] p0) { return (ClassAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class ClassBasedNavigableIterableAssert extends AbstractIterableAssert {
  public void ClassBasedNavigableIterableAssert(Iterable p0, Class p1, Class p2) {}
  public AbstractAssert toAssert(Object p0, String p1) { return (AbstractAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class ClassBasedNavigableListAssert extends AbstractListAssert {
  public void ClassBasedNavigableListAssert(java.util.List p0, Class p1) {}
  public AbstractAssert toAssert(Object p0, String p1) { return (AbstractAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public interface ComparableAssert {
  ComparableAssert isEqualByComparingTo(Comparable p0);
  ComparableAssert isNotEqualByComparingTo(Comparable p0);
  ComparableAssert isLessThan(Comparable p0);
  ComparableAssert isLessThanOrEqualTo(Comparable p0);
  ComparableAssert isGreaterThan(Comparable p0);
  ComparableAssert isGreaterThanOrEqualTo(Comparable p0);
  ComparableAssert isBetween(Comparable p0, Comparable p1);
  ComparableAssert isStrictlyBetween(Comparable p0, Comparable p1);
}
---
package org.assertj.core.api;
import java.io.*;

public class CompletableFutureAssert extends AbstractCompletableFutureAssert {
}
---
package org.assertj.core.api;
import java.io.*;

public class Condition implements Descriptable {
  public void Condition() {}
  public void Condition(String p0) {}
  public void Condition(java.util.function.Predicate p0, String p1, Object[] p2) {}
  public void Condition(org.assertj.core.description.Description p0) {}
  public Condition describedAs(org.assertj.core.description.Description p0) { return (Condition) (Object) null; }
  public org.assertj.core.description.Description description() { return (org.assertj.core.description.Description) (Object) null; }
  public boolean matches(Object p0) { return (boolean) (Object) null; }
  public String toString() { return (String) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public interface Descriptable {
  Object describedAs(org.assertj.core.description.Description p0);
  default Object as(java.util.function.Supplier<String> descriptionSupplier) { return null;}

}
---
package org.assertj.core.api;
import java.io.*;

public class Double2DArrayAssert extends Abstract2DArrayAssert {
  public void Double2DArrayAssert(double[][] p0) {}
  public Double2DArrayAssert isDeepEqualTo(double[][] p0) { return (Double2DArrayAssert) (Object) null; }
  public Double2DArrayAssert isEqualTo(Object p0) { return (Double2DArrayAssert) (Object) null; }
  public void isNullOrEmpty() {}
  public void isEmpty() {}
  public Double2DArrayAssert isNotEmpty() { return (Double2DArrayAssert) (Object) null; }
  public Double2DArrayAssert hasDimensions(int p0, int p1) { return (Double2DArrayAssert) (Object) null; }
  public Double2DArrayAssert hasSameDimensionsAs(Object p0) { return (Double2DArrayAssert) (Object) null; }
  public Double2DArrayAssert contains(double[] p0, org.assertj.core.data.Index p1) { return (Double2DArrayAssert) (Object) null; }
  public Double2DArrayAssert doesNotContain(double[] p0, org.assertj.core.data.Index p1) { return (Double2DArrayAssert) (Object) null; }
  public Abstract2DArrayAssert isDeepEqualTo(Object p0) { return (Abstract2DArrayAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class DoublePredicateAssert extends AbstractPredicateLikeAssert {
  public void DoublePredicateAssert(java.util.function.DoublePredicate p0) {}
  public DoublePredicateAssert accepts(double[] p0) { return (DoublePredicateAssert) (Object) null; }
  public DoublePredicateAssert rejects(double[] p0) { return (DoublePredicateAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public interface EnumerableAssert {
  void isNullOrEmpty();
  void isEmpty();
  EnumerableAssert isNotEmpty();
  EnumerableAssert hasSize(int p0);
  EnumerableAssert hasSizeGreaterThan(int p0);
  EnumerableAssert hasSizeGreaterThanOrEqualTo(int p0);
  EnumerableAssert hasSizeLessThan(int p0);
  EnumerableAssert hasSizeLessThanOrEqualTo(int p0);
  EnumerableAssert hasSizeBetween(int p0, int p1);
  EnumerableAssert hasSameSizeAs(Iterable p0);
  EnumerableAssert hasSameSizeAs(Object p0);
  EnumerableAssert usingElementComparator(java.util.Comparator p0);
  EnumerableAssert usingDefaultElementComparator();
}
---
package org.assertj.core.api;
import java.io.*;

public interface ExtensionPoints {
  ExtensionPoints is(Condition p0);
  ExtensionPoints isNot(Condition p0);
  ExtensionPoints has(Condition p0);
  ExtensionPoints doesNotHave(Condition p0);
  ExtensionPoints satisfies(Condition p0);
}
---
package org.assertj.core.api;
import java.io.*;

public class FactoryBasedNavigableIterableAssert extends AbstractIterableAssert {
  public void FactoryBasedNavigableIterableAssert(Iterable p0, Class p1, AssertFactory p2) {}
  public AbstractAssert toAssert(Object p0, String p1) { return (AbstractAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class FactoryBasedNavigableListAssert extends AbstractListAssert {
  public void FactoryBasedNavigableListAssert(java.util.List p0, Class p1, AssertFactory p2) {}
  public AbstractAssert toAssert(Object p0, String p1) { return (AbstractAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class Float2DArrayAssert extends Abstract2DArrayAssert {
  public void Float2DArrayAssert(float[][] p0) {}
  public Float2DArrayAssert isDeepEqualTo(float[][] p0) { return (Float2DArrayAssert) (Object) null; }
  public Float2DArrayAssert isEqualTo(Object p0) { return (Float2DArrayAssert) (Object) null; }
  public void isNullOrEmpty() {}
  public void isEmpty() {}
  public Float2DArrayAssert isNotEmpty() { return (Float2DArrayAssert) (Object) null; }
  public Float2DArrayAssert hasDimensions(int p0, int p1) { return (Float2DArrayAssert) (Object) null; }
  public Float2DArrayAssert hasSameDimensionsAs(Object p0) { return (Float2DArrayAssert) (Object) null; }
  public Float2DArrayAssert contains(float[] p0, org.assertj.core.data.Index p1) { return (Float2DArrayAssert) (Object) null; }
  public Float2DArrayAssert doesNotContain(float[] p0, org.assertj.core.data.Index p1) { return (Float2DArrayAssert) (Object) null; }
  public Abstract2DArrayAssert isDeepEqualTo(Object p0) { return (Abstract2DArrayAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public interface FloatingPointNumberAssert extends NumberAssert {
  FloatingPointNumberAssert isEqualTo(Number p0, org.assertj.core.data.Offset p1);
  FloatingPointNumberAssert isCloseTo(Number p0, org.assertj.core.data.Offset p1);
  FloatingPointNumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Offset p1);
  FloatingPointNumberAssert isNaN();
  FloatingPointNumberAssert isNotNaN();
  FloatingPointNumberAssert isFinite();
  FloatingPointNumberAssert isInfinite();
}
---
package org.assertj.core.api;
import java.io.*;

public class FutureAssert extends AbstractFutureAssert {
}
---
package org.assertj.core.api;
import java.io.*;

public interface IndexedObjectEnumerableAssert extends ObjectEnumerableAssert {
  IndexedObjectEnumerableAssert contains(Object p0, org.assertj.core.data.Index p1);
  IndexedObjectEnumerableAssert doesNotContain(Object p0, org.assertj.core.data.Index p1);
}
---
package org.assertj.core.api;
import java.io.*;

public interface InstanceOfAssertFactories {
}
---
package org.assertj.core.api;
import java.io.*;

public class InstanceOfAssertFactory implements AssertFactory {
  public void InstanceOfAssertFactory(Class p0, AssertFactory p1) {}
  public AbstractAssert createAssert(Object p0) { return (AbstractAssert) (Object) null; }
  public String toString() { return (String) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class Int2DArrayAssert extends Abstract2DArrayAssert {
  public void Int2DArrayAssert(int[][] p0) {}
  public Int2DArrayAssert isDeepEqualTo(int[][] p0) { return (Int2DArrayAssert) (Object) null; }
  public Int2DArrayAssert isEqualTo(Object p0) { return (Int2DArrayAssert) (Object) null; }
  public void isNullOrEmpty() {}
  public void isEmpty() {}
  public Int2DArrayAssert isNotEmpty() { return (Int2DArrayAssert) (Object) null; }
  public Int2DArrayAssert hasDimensions(int p0, int p1) { return (Int2DArrayAssert) (Object) null; }
  public Int2DArrayAssert hasSameDimensionsAs(Object p0) { return (Int2DArrayAssert) (Object) null; }
  public Int2DArrayAssert contains(int[] p0, org.assertj.core.data.Index p1) { return (Int2DArrayAssert) (Object) null; }
  public Int2DArrayAssert doesNotContain(int[] p0, org.assertj.core.data.Index p1) { return (Int2DArrayAssert) (Object) null; }
  public Abstract2DArrayAssert isDeepEqualTo(Object p0) { return (Abstract2DArrayAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class IntPredicateAssert extends AbstractPredicateLikeAssert {
  public void IntPredicateAssert(java.util.function.IntPredicate p0) {}
  public IntPredicateAssert accepts(int[] p0) { return (IntPredicateAssert) (Object) null; }
  public IntPredicateAssert rejects(int[] p0) { return (IntPredicateAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class IterableAssert extends FactoryBasedNavigableIterableAssert {
  public void IterableAssert(Iterable p0) {}
  public IterableAssert contains(Object[] p0) { return (IterableAssert) (Object) null; }
  public IterableAssert containsOnly(Object[] p0) { return (IterableAssert) (Object) null; }
  public IterableAssert containsOnlyOnce(Object[] p0) { return (IterableAssert) (Object) null; }
  public IterableAssert containsExactly(Object[] p0) { return (IterableAssert) (Object) null; }
  public IterableAssert containsExactlyInAnyOrder(Object[] p0) { return (IterableAssert) (Object) null; }
  public IterableAssert containsAnyOf(Object[] p0) { return (IterableAssert) (Object) null; }
  public IterableAssert isSubsetOf(Object[] p0) { return (IterableAssert) (Object) null; }
  public IterableAssert containsSequence(Object[] p0) { return (IterableAssert) (Object) null; }
  public IterableAssert doesNotContainSequence(Object[] p0) { return (IterableAssert) (Object) null; }
  public IterableAssert containsSubsequence(Object[] p0) { return (IterableAssert) (Object) null; }
  public IterableAssert doesNotContainSubsequence(Object[] p0) { return (IterableAssert) (Object) null; }
  public IterableAssert doesNotContain(Object[] p0) { return (IterableAssert) (Object) null; }
  public IterableAssert endsWith(Object p0, Object[] p1) { return (IterableAssert) (Object) null; }
  public AbstractListAssert flatExtracting(org.assertj.core.api.iterable.ThrowingExtractor[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert flatMap(org.assertj.core.api.iterable.ThrowingExtractor[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert flatExtracting(java.util.function.Function[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert flatMap(java.util.function.Function[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert extracting(java.util.function.Function[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert map(java.util.function.Function[] p0) { return (AbstractListAssert) (Object) null; }
  public IterableAssert satisfiesExactly(java.util.function.Consumer[] p0) { return (IterableAssert) (Object) null; }
  public IterableAssert satisfiesExactlyInAnyOrder(java.util.function.Consumer[] p0) { return (IterableAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class IteratorAssert extends AbstractIteratorAssert {
  public void IteratorAssert(java.util.Iterator p0) {}
}
---
package org.assertj.core.api;
import java.io.*;

public class ListAssert extends FactoryBasedNavigableListAssert {
  public void ListAssert(java.util.List p0) {}
  public void ListAssert(java.util.stream.Stream p0) {}
  public void ListAssert(java.util.stream.IntStream p0) {}
  public void ListAssert(java.util.stream.LongStream p0) {}
  public void ListAssert(java.util.stream.DoubleStream p0) {}
  public ListAssert isEqualTo(Object p0) { return (ListAssert) (Object) null; }
  public ListAssert isInstanceOf(Class p0) { return (ListAssert) (Object) null; }
  public ListAssert isInstanceOfAny(Class[] p0) { return (ListAssert) (Object) null; }
  public ListAssert isOfAnyClassIn(Class[] p0) { return (ListAssert) (Object) null; }
  public ListAssert isExactlyInstanceOf(Class p0) { return (ListAssert) (Object) null; }
  public ListAssert isNotInstanceOf(Class p0) { return (ListAssert) (Object) null; }
  public ListAssert isNotInstanceOfAny(Class[] p0) { return (ListAssert) (Object) null; }
  public ListAssert isNotOfAnyClassIn(Class[] p0) { return (ListAssert) (Object) null; }
  public ListAssert isNotExactlyInstanceOf(Class p0) { return (ListAssert) (Object) null; }
  public ListAssert isSameAs(Object p0) { return (ListAssert) (Object) null; }
  public ListAssert isNotSameAs(Object p0) { return (ListAssert) (Object) null; }
  public ListAssert startsWith(Object[] p0) { return (ListAssert) (Object) null; }
  public ListAssert contains(Object[] p0) { return (ListAssert) (Object) null; }
  public ListAssert containsOnly(Object[] p0) { return (ListAssert) (Object) null; }
  public ListAssert containsOnlyOnce(Object[] p0) { return (ListAssert) (Object) null; }
  public ListAssert containsExactly(Object[] p0) { return (ListAssert) (Object) null; }
  public ListAssert containsExactlyInAnyOrder(Object[] p0) { return (ListAssert) (Object) null; }
  public ListAssert containsAnyOf(Object[] p0) { return (ListAssert) (Object) null; }
  public ListAssert isSubsetOf(Object[] p0) { return (ListAssert) (Object) null; }
  public ListAssert containsSequence(Object[] p0) { return (ListAssert) (Object) null; }
  public ListAssert doesNotContainSequence(Object[] p0) { return (ListAssert) (Object) null; }
  public ListAssert containsSubsequence(Object[] p0) { return (ListAssert) (Object) null; }
  public ListAssert doesNotContainSubsequence(Object[] p0) { return (ListAssert) (Object) null; }
  public ListAssert doesNotContain(Object[] p0) { return (ListAssert) (Object) null; }
  public ListAssert endsWith(Object p0, Object[] p1) { return (ListAssert) (Object) null; }
  public AbstractListAssert extracting(java.util.function.Function[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert map(java.util.function.Function[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert flatExtracting(org.assertj.core.api.iterable.ThrowingExtractor[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert flatMap(org.assertj.core.api.iterable.ThrowingExtractor[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert flatExtracting(java.util.function.Function[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert flatMap(java.util.function.Function[] p0) { return (AbstractListAssert) (Object) null; }
  public ListAssert satisfiesExactly(java.util.function.Consumer[] p0) { return (ListAssert) (Object) null; }
  public ListAssert satisfiesExactlyInAnyOrder(java.util.function.Consumer[] p0) { return (ListAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class Long2DArrayAssert extends Abstract2DArrayAssert {
  public void Long2DArrayAssert(long[][] p0) {}
  public Long2DArrayAssert isDeepEqualTo(long[][] p0) { return (Long2DArrayAssert) (Object) null; }
  public Long2DArrayAssert isEqualTo(Object p0) { return (Long2DArrayAssert) (Object) null; }
  public void isNullOrEmpty() {}
  public void isEmpty() {}
  public Long2DArrayAssert isNotEmpty() { return (Long2DArrayAssert) (Object) null; }
  public Long2DArrayAssert hasDimensions(int p0, int p1) { return (Long2DArrayAssert) (Object) null; }
  public Long2DArrayAssert hasSameDimensionsAs(Object p0) { return (Long2DArrayAssert) (Object) null; }
  public Long2DArrayAssert contains(long[] p0, org.assertj.core.data.Index p1) { return (Long2DArrayAssert) (Object) null; }
  public Long2DArrayAssert doesNotContain(long[] p0, org.assertj.core.data.Index p1) { return (Long2DArrayAssert) (Object) null; }
  public Abstract2DArrayAssert isDeepEqualTo(Object p0) { return (Abstract2DArrayAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class LongAdderAssert extends AbstractLongAdderAssert {
  public void LongAdderAssert(java.util.concurrent.atomic.LongAdder p0) {}
}
---
package org.assertj.core.api;
import java.io.*;

public class LongPredicateAssert extends AbstractPredicateLikeAssert {
  public void LongPredicateAssert(java.util.function.LongPredicate p0) {}
  public LongPredicateAssert accepts(long[] p0) { return (LongPredicateAssert) (Object) null; }
  public LongPredicateAssert rejects(long[] p0) { return (LongPredicateAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class MapAssert extends AbstractMapAssert {
  public void MapAssert(java.util.Map p0) {}
  public MapAssert contains(java.util.Map.Entry[] p0) { return (MapAssert) (Object) null; }
  public MapAssert containsAnyOf(java.util.Map.Entry[] p0) { return (MapAssert) (Object) null; }
  public MapAssert containsOnly(java.util.Map.Entry[] p0) { return (MapAssert) (Object) null; }
  public MapAssert containsExactly(java.util.Map.Entry[] p0) { return (MapAssert) (Object) null; }
  public MapAssert containsKeys(Object[] p0) { return (MapAssert) (Object) null; }
  public MapAssert containsOnlyKeys(Object[] p0) { return (MapAssert) (Object) null; }
  public MapAssert containsValues(Object[] p0) { return (MapAssert) (Object) null; }
  public MapAssert doesNotContainKeys(Object[] p0) { return (MapAssert) (Object) null; }
  public MapAssert doesNotContain(java.util.Map.Entry[] p0) { return (MapAssert) (Object) null; }
  public AbstractListAssert extracting(java.util.function.Function[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert extractingByKeys(Object[] p0) { return (AbstractListAssert) (Object) null; }
  public AbstractListAssert extractingFromEntries(java.util.function.Function[] p0) { return (AbstractListAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class NotThrownAssert implements Descriptable {
  public void NotThrownAssert() {}
  public void isThrownBy(ThrowableAssert.ThrowingCallable p0) {}
  public NotThrownAssert describedAs(org.assertj.core.description.Description p0) { return (NotThrownAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public interface NumberAssert {
  NumberAssert isZero();
  NumberAssert isNotZero();
  NumberAssert isOne();
  NumberAssert isPositive();
  NumberAssert isNegative();
  NumberAssert isNotNegative();
  NumberAssert isNotPositive();
  NumberAssert isBetween(Number p0, Number p1);
  NumberAssert isStrictlyBetween(Number p0, Number p1);
  NumberAssert isCloseTo(Number p0, org.assertj.core.data.Offset p1);
  NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Offset p1);
  NumberAssert isCloseTo(Number p0, org.assertj.core.data.Percentage p1);
  NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Percentage p1);
}
---
package org.assertj.core.api;
import java.io.*;

public class Object2DArrayAssert extends Abstract2DArrayAssert {
  public void Object2DArrayAssert(Object[][] p0) {}
  public Object2DArrayAssert isDeepEqualTo(Object[][] p0) { return (Object2DArrayAssert) (Object) null; }
  public Object2DArrayAssert isEqualTo(Object p0) { return (Object2DArrayAssert) (Object) null; }
  public void isNullOrEmpty() {}
  public void isEmpty() {}
  public Object2DArrayAssert isNotEmpty() { return (Object2DArrayAssert) (Object) null; }
  public Object2DArrayAssert hasDimensions(int p0, int p1) { return (Object2DArrayAssert) (Object) null; }
  public Object2DArrayAssert hasSameDimensionsAs(Object p0) { return (Object2DArrayAssert) (Object) null; }
  public Object2DArrayAssert contains(Object[] p0, org.assertj.core.data.Index p1) { return (Object2DArrayAssert) (Object) null; }
  public Object2DArrayAssert doesNotContain(Object[] p0, org.assertj.core.data.Index p1) { return (Object2DArrayAssert) (Object) null; }
  public Abstract2DArrayAssert isDeepEqualTo(Object p0) { return (Abstract2DArrayAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class ObjectArrayAssert extends AbstractObjectArrayAssert {
  public void ObjectArrayAssert(Object[] p0) {}
  public void ObjectArrayAssert(java.util.concurrent.atomic.AtomicReferenceArray p0) {}
  public AbstractListAssert extracting(java.util.function.Function[] p0) { return (AbstractListAssert) (Object) null; }
  public ObjectArrayAssert contains(Object[] p0) { return (ObjectArrayAssert) (Object) null; }
  public ObjectArrayAssert containsOnly(Object[] p0) { return (ObjectArrayAssert) (Object) null; }
  public ObjectArrayAssert containsOnlyOnce(Object[] p0) { return (ObjectArrayAssert) (Object) null; }
  public ObjectArrayAssert containsExactly(Object[] p0) { return (ObjectArrayAssert) (Object) null; }
  public ObjectArrayAssert containsExactlyInAnyOrder(Object[] p0) { return (ObjectArrayAssert) (Object) null; }
  public ObjectArrayAssert containsAnyOf(Object[] p0) { return (ObjectArrayAssert) (Object) null; }
  public ObjectArrayAssert isSubsetOf(Object[] p0) { return (ObjectArrayAssert) (Object) null; }
  public ObjectArrayAssert containsSequence(Object[] p0) { return (ObjectArrayAssert) (Object) null; }
  public ObjectArrayAssert doesNotContainSequence(Object[] p0) { return (ObjectArrayAssert) (Object) null; }
  public ObjectArrayAssert containsSubsequence(Object[] p0) { return (ObjectArrayAssert) (Object) null; }
  public ObjectArrayAssert doesNotContainSubsequence(Object[] p0) { return (ObjectArrayAssert) (Object) null; }
  public ObjectArrayAssert doesNotContain(Object[] p0) { return (ObjectArrayAssert) (Object) null; }
  public ObjectArrayAssert endsWith(Object p0, Object[] p1) { return (ObjectArrayAssert) (Object) null; }
  public ObjectArrayAssert satisfiesExactly(java.util.function.Consumer[] p0) { return (ObjectArrayAssert) (Object) null; }
  public ObjectArrayAssert satisfiesExactlyInAnyOrder(java.util.function.Consumer[] p0) { return (ObjectArrayAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class ObjectAssert extends AbstractObjectAssert {
  public void ObjectAssert(Object p0) {}
  public void ObjectAssert(java.util.concurrent.atomic.AtomicReference p0) {}
  public AbstractListAssert extracting(java.util.function.Function[] p0) { return (AbstractListAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public interface ObjectEnumerableAssert extends EnumerableAssert {
  ObjectEnumerableAssert contains(Object[] p0);
  ObjectEnumerableAssert containsOnly(Object[] p0);
  ObjectEnumerableAssert containsOnlyNulls();
  ObjectEnumerableAssert containsOnlyOnce(Object[] p0);
  ObjectEnumerableAssert containsExactly(Object[] p0);
  ObjectEnumerableAssert containsExactlyInAnyOrder(Object[] p0);
  ObjectEnumerableAssert containsExactlyInAnyOrderElementsOf(Iterable p0);
  ObjectEnumerableAssert containsSequence(Object[] p0);
  ObjectEnumerableAssert containsSequence(Iterable p0);
  ObjectEnumerableAssert doesNotContainSequence(Object[] p0);
  ObjectEnumerableAssert doesNotContainSequence(Iterable p0);
  ObjectEnumerableAssert containsSubsequence(Object[] p0);
  ObjectEnumerableAssert containsSubsequence(Iterable p0);
  ObjectEnumerableAssert doesNotContainSubsequence(Object[] p0);
  ObjectEnumerableAssert doesNotContainSubsequence(Iterable p0);
  ObjectEnumerableAssert doesNotContain(Object[] p0);
  ObjectEnumerableAssert doesNotHaveDuplicates();
  ObjectEnumerableAssert startsWith(Object[] p0);
  ObjectEnumerableAssert endsWith(Object p0, Object[] p1);
  ObjectEnumerableAssert endsWith(Object[] p0);
  ObjectEnumerableAssert containsNull();
  ObjectEnumerableAssert doesNotContainNull();
  ObjectEnumerableAssert are(Condition p0);
  ObjectEnumerableAssert areNot(Condition p0);
  ObjectEnumerableAssert have(Condition p0);
  ObjectEnumerableAssert doNotHave(Condition p0);
  ObjectEnumerableAssert areAtLeast(int p0, Condition p1);
  ObjectEnumerableAssert areAtLeastOne(Condition p0);
  ObjectEnumerableAssert areAtMost(int p0, Condition p1);
  ObjectEnumerableAssert areExactly(int p0, Condition p1);
  ObjectEnumerableAssert haveAtLeastOne(Condition p0);
  ObjectEnumerableAssert haveAtLeast(int p0, Condition p1);
  ObjectEnumerableAssert haveAtMost(int p0, Condition p1);
  ObjectEnumerableAssert haveExactly(int p0, Condition p1);
  ObjectEnumerableAssert containsAll(Iterable p0);
  ObjectEnumerableAssert hasOnlyOneElementSatisfying(java.util.function.Consumer p0);
  ObjectEnumerableAssert hasOnlyElementsOfTypes(Class[] p0);
  ObjectEnumerableAssert hasAtLeastOneElementOfType(Class p0);
  ObjectEnumerableAssert hasOnlyElementsOfType(Class p0);
  ObjectEnumerableAssert doesNotHaveAnyElementsOfTypes(Class[] p0);
  ObjectEnumerableAssert containsExactlyElementsOf(Iterable p0);
  ObjectEnumerableAssert containsOnlyElementsOf(Iterable p0);
  ObjectEnumerableAssert containsOnlyOnceElementsOf(Iterable p0);
  ObjectEnumerableAssert hasSameElementsAs(Iterable p0);
  ObjectEnumerableAssert doesNotContainAnyElementsOf(Iterable p0);
  ObjectEnumerableAssert isSubsetOf(Iterable p0);
  ObjectEnumerableAssert isSubsetOf(Object[] p0);
  ObjectEnumerableAssert allMatch(java.util.function.Predicate p0);
  ObjectEnumerableAssert allMatch(java.util.function.Predicate p0, String p1);
  ObjectEnumerableAssert allSatisfy(java.util.function.Consumer p0);
  ObjectEnumerableAssert satisfiesExactly(java.util.function.Consumer[] p0);
  ObjectEnumerableAssert satisfiesExactlyInAnyOrder(java.util.function.Consumer[] p0);
  ObjectEnumerableAssert anyMatch(java.util.function.Predicate p0);
  ObjectEnumerableAssert anySatisfy(java.util.function.Consumer p0);
  ObjectEnumerableAssert noneSatisfy(java.util.function.Consumer p0);
  ObjectEnumerableAssert containsAnyOf(Object[] p0);
  ObjectEnumerableAssert containsAnyElementsOf(Iterable p0);
  ObjectEnumerableAssert noneMatch(java.util.function.Predicate p0);
}
---
package org.assertj.core.api;
import java.io.*;

public class OptionalAssert extends AbstractOptionalAssert {
}
---
package org.assertj.core.api;
import java.io.*;

public class OptionalDoubleAssert extends AbstractOptionalDoubleAssert {
}
---
package org.assertj.core.api;
import java.io.*;

public class OptionalIntAssert extends AbstractOptionalIntAssert {
}
---
package org.assertj.core.api;
import java.io.*;

public class OptionalLongAssert extends AbstractOptionalLongAssert {
}
---
package org.assertj.core.api;
import java.io.*;

public class PredicateAssert extends AbstractPredicateAssert {
  public PredicateAssert accepts(Object[] p0) { return (PredicateAssert) (Object) null; }
  public PredicateAssert rejects(Object[] p0) { return (PredicateAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class RecursiveComparisonAssert extends AbstractAssert {
  public void RecursiveComparisonAssert(Object p0, org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration p1) {}
  public RecursiveComparisonAssert isEqualTo(Object p0) { return (RecursiveComparisonAssert) (Object) null; }
  public RecursiveComparisonAssert isNotEqualTo(Object p0) { return (RecursiveComparisonAssert) (Object) null; }
  public RecursiveComparisonAssert ignoringActualNullFields() { return (RecursiveComparisonAssert) (Object) null; }
  public RecursiveComparisonAssert ignoringActualEmptyOptionalFields() { return (RecursiveComparisonAssert) (Object) null; }
  public RecursiveComparisonAssert ignoringExpectedNullFields() { return (RecursiveComparisonAssert) (Object) null; }
  public RecursiveComparisonAssert ignoringFields(String[] p0) { return (RecursiveComparisonAssert) (Object) null; }
  public RecursiveComparisonAssert ignoringFieldsMatchingRegexes(String[] p0) { return (RecursiveComparisonAssert) (Object) null; }
  public RecursiveComparisonAssert ignoringFieldsOfTypes(Class[] p0) { return (RecursiveComparisonAssert) (Object) null; }
  public RecursiveComparisonAssert ignoringAllOverriddenEquals() { return (RecursiveComparisonAssert) (Object) null; }
  public RecursiveComparisonAssert usingOverriddenEquals() { return (RecursiveComparisonAssert) (Object) null; }
  public RecursiveComparisonAssert ignoringOverriddenEqualsForFields(String[] p0) { return (RecursiveComparisonAssert) (Object) null; }
  public RecursiveComparisonAssert ignoringOverriddenEqualsForTypes(Class[] p0) { return (RecursiveComparisonAssert) (Object) null; }
  public RecursiveComparisonAssert ignoringOverriddenEqualsForFieldsMatchingRegexes(String[] p0) { return (RecursiveComparisonAssert) (Object) null; }
  public RecursiveComparisonAssert ignoringCollectionOrder() { return (RecursiveComparisonAssert) (Object) null; }
  public RecursiveComparisonAssert ignoringCollectionOrderInFields(String[] p0) { return (RecursiveComparisonAssert) (Object) null; }
  public RecursiveComparisonAssert ignoringCollectionOrderInFieldsMatchingRegexes(String[] p0) { return (RecursiveComparisonAssert) (Object) null; }
  public RecursiveComparisonAssert withStrictTypeChecking() { return (RecursiveComparisonAssert) (Object) null; }
  public RecursiveComparisonAssert withEqualsForFields(java.util.function.BiPredicate p0, String[] p1) { return (RecursiveComparisonAssert) (Object) null; }
  public RecursiveComparisonAssert withComparatorForFields(java.util.Comparator p0, String[] p1) { return (RecursiveComparisonAssert) (Object) null; }
  public RecursiveComparisonAssert withComparatorForType(java.util.Comparator p0, Class p1) { return (RecursiveComparisonAssert) (Object) null; }
  public RecursiveComparisonAssert withEqualsForType(java.util.function.BiPredicate p0, Class p1) { return (RecursiveComparisonAssert) (Object) null; }
  public org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration getRecursiveComparisonConfiguration() { return (org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class Short2DArrayAssert extends Abstract2DArrayAssert {
  public void Short2DArrayAssert(short[][] p0) {}
  public Short2DArrayAssert isDeepEqualTo(short[][] p0) { return (Short2DArrayAssert) (Object) null; }
  public Short2DArrayAssert isEqualTo(Object p0) { return (Short2DArrayAssert) (Object) null; }
  public void isNullOrEmpty() {}
  public void isEmpty() {}
  public Short2DArrayAssert isNotEmpty() { return (Short2DArrayAssert) (Object) null; }
  public Short2DArrayAssert hasDimensions(int p0, int p1) { return (Short2DArrayAssert) (Object) null; }
  public Short2DArrayAssert hasSameDimensionsAs(Object p0) { return (Short2DArrayAssert) (Object) null; }
  public Short2DArrayAssert contains(short[] p0, org.assertj.core.data.Index p1) { return (Short2DArrayAssert) (Object) null; }
  public Short2DArrayAssert contains(int[] p0, org.assertj.core.data.Index p1) { return (Short2DArrayAssert) (Object) null; }
  public Short2DArrayAssert doesNotContain(short[] p0, org.assertj.core.data.Index p1) { return (Short2DArrayAssert) (Object) null; }
  public Short2DArrayAssert doesNotContain(int[] p0, org.assertj.core.data.Index p1) { return (Short2DArrayAssert) (Object) null; }
  public Abstract2DArrayAssert isDeepEqualTo(Object p0) { return (Abstract2DArrayAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class SpliteratorAssert extends AbstractSpliteratorAssert {
}
---
package org.assertj.core.api;
import java.io.*;

public class ThrowableAssert extends AbstractThrowableAssert {
  public interface ThrowingCallable {
    void call() throws Throwable;
  }
  public void ThrowableAssert(Throwable p0) {}
  public void ThrowableAssert(java.util.concurrent.Callable p0) {}
  public static Throwable catchThrowable(ThrowableAssert.ThrowingCallable p0) { return (Throwable) (Object) null; }
  public static Throwable catchThrowableOfType(ThrowableAssert.ThrowingCallable p0, Class p1) { return (Throwable) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class ThrowableAssertAlternative extends AbstractAssert {
  public void ThrowableAssertAlternative(Throwable p0) {}
  public ThrowableAssertAlternative withMessage(String p0) { return (ThrowableAssertAlternative) (Object) null; }
  public ThrowableAssertAlternative withMessage(String p0, Object[] p1) { return (ThrowableAssertAlternative) (Object) null; }
  public ThrowableAssertAlternative withCause(Throwable p0) { return (ThrowableAssertAlternative) (Object) null; }
  public ThrowableAssertAlternative withNoCause() { return (ThrowableAssertAlternative) (Object) null; }
  public ThrowableAssertAlternative withMessageStartingWith(String p0) { return (ThrowableAssertAlternative) (Object) null; }
  public ThrowableAssertAlternative withMessageStartingWith(String p0, Object[] p1) { return (ThrowableAssertAlternative) (Object) null; }
  public ThrowableAssertAlternative withMessageContaining(String p0) { return (ThrowableAssertAlternative) (Object) null; }
  public ThrowableAssertAlternative withMessageContaining(String p0, Object[] p1) { return (ThrowableAssertAlternative) (Object) null; }
  public ThrowableAssertAlternative withMessageContainingAll(CharSequence[] p0) { return (ThrowableAssertAlternative) (Object) null; }
  public ThrowableAssertAlternative withMessageNotContaining(String p0) { return (ThrowableAssertAlternative) (Object) null; }
  public ThrowableAssertAlternative withMessageNotContainingAny(CharSequence[] p0) { return (ThrowableAssertAlternative) (Object) null; }
  public ThrowableAssertAlternative withStackTraceContaining(String p0) { return (ThrowableAssertAlternative) (Object) null; }
  public ThrowableAssertAlternative withStackTraceContaining(String p0, Object[] p1) { return (ThrowableAssertAlternative) (Object) null; }
  public ThrowableAssertAlternative withMessageMatching(String p0) { return (ThrowableAssertAlternative) (Object) null; }
  public ThrowableAssertAlternative withMessageEndingWith(String p0) { return (ThrowableAssertAlternative) (Object) null; }
  public ThrowableAssertAlternative withMessageEndingWith(String p0, Object[] p1) { return (ThrowableAssertAlternative) (Object) null; }
  public ThrowableAssertAlternative withCauseInstanceOf(Class p0) { return (ThrowableAssertAlternative) (Object) null; }
  public ThrowableAssertAlternative withCauseExactlyInstanceOf(Class p0) { return (ThrowableAssertAlternative) (Object) null; }
  public ThrowableAssertAlternative withRootCauseInstanceOf(Class p0) { return (ThrowableAssertAlternative) (Object) null; }
  public ThrowableAssertAlternative withRootCauseExactlyInstanceOf(Class p0) { return (ThrowableAssertAlternative) (Object) null; }
  public ThrowableAssertAlternative describedAs(String p0, Object[] p1) { return (ThrowableAssertAlternative) (Object) null; }
  public ThrowableAssertAlternative describedAs(org.assertj.core.description.Description p0) { return (ThrowableAssertAlternative) (Object) null; }
  public ThrowableAssertAlternative havingCause() { return (ThrowableAssertAlternative) (Object) null; }
  public ThrowableAssertAlternative havingRootCause() { return (ThrowableAssertAlternative) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class ThrowableTypeAssert implements Descriptable {
  public void ThrowableTypeAssert(Class p0) {}
  public ThrowableAssertAlternative isThrownBy(ThrowableAssert.ThrowingCallable p0) { return (ThrowableAssertAlternative) (Object) null; }
  public ThrowableTypeAssert describedAs(org.assertj.core.description.Description p0) { return (ThrowableTypeAssert) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class WithThrowable {
  public ThrowableAssertAlternative withThrowableOfType(Class p0) { return (ThrowableAssertAlternative) (Object) null; }
}
---
package org.assertj.core.api;
import java.io.*;

public class WritableAssertionInfo implements AssertionInfo {
  public void WritableAssertionInfo(org.assertj.core.presentation.Representation p0) {}
  public void WritableAssertionInfo() {}
  public String overridingErrorMessage() { return (String) (Object) null; }
  public void overridingErrorMessage(String p0) {}
  public void overridingErrorMessage(java.util.function.Supplier p0) {}
  public org.assertj.core.description.Description description() { return (org.assertj.core.description.Description) (Object) null; }
  public String descriptionText() { return (String) (Object) null; }
  public boolean hasDescription() { return (boolean) (Object) null; }
  public void description(String p0, Object[] p1) {}
  public void description(org.assertj.core.description.Description p0) {}
  public org.assertj.core.presentation.Representation representation() { return (org.assertj.core.presentation.Representation) (Object) null; }
  public void useHexadecimalRepresentation() {}
  public void useUnicodeRepresentation() {}
  public void useBinaryRepresentation() {}
  public void useRepresentation(org.assertj.core.presentation.Representation p0) {}
  public static String mostRelevantDescriptionIn(WritableAssertionInfo p0, String p1) { return (String) (Object) null; }
  public String toString() { return (String) (Object) null; }
}
---
package org.assertj.core.api.filter;
import java.io.*;

public abstract class FilterOperator {
  public abstract Filters applyOn(Filters p0);
}
---
package org.assertj.core.api.filter;
import java.io.*;

public class Filters {
  public static Filters filter(Iterable p0) { return (Filters) (Object) null; }
  public static Filters filter(Object[] p0) { return (Filters) (Object) null; }
  public Filters being(org.assertj.core.api.Condition p0) { return (Filters) (Object) null; }
  public Filters having(org.assertj.core.api.Condition p0) { return (Filters) (Object) null; }
  public Filters with(String p0, Object p1) { return (Filters) (Object) null; }
  public Filters with(String p0) { return (Filters) (Object) null; }
  public Filters and(String p0) { return (Filters) (Object) null; }
  public Filters equalsTo(Object p0) { return (Filters) (Object) null; }
  public Filters notEqualsTo(Object p0) { return (Filters) (Object) null; }
  public Filters in(Object[] p0) { return (Filters) (Object) null; }
  public Filters notIn(Object[] p0) { return (Filters) (Object) null; }
  public java.util.List get() { return (java.util.List) (Object) null; }
}
---
package org.assertj.core.api.filter;
import java.io.*;

public class InFilter extends FilterOperator {
  public static InFilter in(Object[] p0) { return (InFilter) (Object) null; }
  public Filters applyOn(Filters p0) { return (Filters) (Object) null; }
}
---
package org.assertj.core.api.filter;
import java.io.*;

public class NotFilter extends FilterOperator {
  public static NotFilter not(Object p0) { return (NotFilter) (Object) null; }
  public Filters applyOn(Filters p0) { return (Filters) (Object) null; }
}
---
package org.assertj.core.api.filter;
import java.io.*;

public class NotInFilter extends FilterOperator {
  public static NotInFilter notIn(Object[] p0) { return (NotInFilter) (Object) null; }
  public boolean filter(Object p0) { return (boolean) (Object) null; }
  public Filters applyOn(Filters p0) { return (Filters) (Object) null; }
}
---
package org.assertj.core.api.iterable;
import java.io.*;

public interface ThrowingExtractor extends java.util.function.Function {
  Object extractThrows(Object p0) throws Exception;
}
---
package org.assertj.core.api.recursive.comparison;
import java.io.*;

public class RecursiveComparisonConfiguration {
  public class Builder{}
  public static String INDENT_LEVEL_2;
  public void RecursiveComparisonConfiguration() {}
  public boolean hasComparatorForField(String p0) { return (boolean) (Object) null; }
  public java.util.Comparator getComparatorForField(String p0) { return (java.util.Comparator) (Object) null; }
  public FieldComparators getFieldComparators() { return (FieldComparators) (Object) null; }
  public boolean hasComparatorForType(Class p0) { return (boolean) (Object) null; }
  public boolean hasCustomComparators() { return (boolean) (Object) null; }
  public java.util.Comparator getComparatorForType(Class p0) { return (java.util.Comparator) (Object) null; }
  public org.assertj.core.internal.TypeComparators getTypeComparators() { return (org.assertj.core.internal.TypeComparators) (Object) null; }
  public void setIgnoreAllActualEmptyOptionalFields(boolean p0) {}
  public void setIgnoreAllActualNullFields(boolean p0) {}
  public void setIgnoreAllExpectedNullFields(boolean p0) {}
  public void ignoreFields(String[] p0) {}
  public void ignoreFieldsMatchingRegexes(String[] p0) {}
  public void ignoreFieldsOfTypes(Class[] p0) {}
  public java.util.Set getIgnoredFields() { return (java.util.Set) (Object) null; }
  public java.util.Set getIgnoredTypes() { return (java.util.Set) (Object) null; }
  public void ignoreAllOverriddenEquals() {}
  public void useOverriddenEquals() {}
  public void ignoreOverriddenEqualsForFields(String[] p0) {}
  public void ignoreOverriddenEqualsForFieldsMatchingRegexes(String[] p0) {}
  public void ignoreOverriddenEqualsForTypes(Class[] p0) {}
  public void ignoreCollectionOrder(boolean p0) {}
  public void ignoreCollectionOrderInFields(String[] p0) {}
  public java.util.Set getIgnoredCollectionOrderInFields() { return (java.util.Set) (Object) null; }
  public void ignoreCollectionOrderInFieldsMatchingRegexes(String[] p0) {}
  public java.util.List getIgnoredCollectionOrderInFieldsMatchingRegexes() { return (java.util.List) (Object) null; }
  public void registerComparatorForType(java.util.Comparator p0, Class p1) {}
  public void registerEqualsForType(java.util.function.BiPredicate p0, Class p1) {}
  public void registerComparatorForFields(java.util.Comparator p0, String[] p1) {}
  public void registerEqualsForFields(java.util.function.BiPredicate p0, String[] p1) {}
  public void strictTypeChecking(boolean p0) {}
  public boolean isInStrictTypeCheckingMode() { return (boolean) (Object) null; }
  public java.util.List getIgnoredFieldsRegexes() { return (java.util.List) (Object) null; }
  public java.util.List getIgnoredOverriddenEqualsForTypes() { return (java.util.List) (Object) null; }
  public java.util.List getIgnoredOverriddenEqualsForFields() { return (java.util.List) (Object) null; }
  public java.util.List getIgnoredOverriddenEqualsForFieldsMatchingRegexes() { return (java.util.List) (Object) null; }
  public java.util.stream.Stream comparatorByFields() { return (java.util.stream.Stream) (Object) null; }
  public String toString() { return (String) (Object) null; }
  public int hashCode() { return (int) (Object) null; }
  public boolean equals(Object p0) { return (boolean) (Object) null; }
  public String multiLineDescription(org.assertj.core.presentation.Representation p0) { return (String) (Object) null; }
  public static RecursiveComparisonConfiguration.Builder builder() { return (RecursiveComparisonConfiguration.Builder) (Object) null; }
}
---
package org.assertj.core.condition;
import java.io.*;

public class DoesNotHave extends Negative {
  public static DoesNotHave doesNotHave(org.assertj.core.api.Condition p0) { return (DoesNotHave) (Object) null; }
}
---
package org.assertj.core.condition;
import java.io.*;

public abstract class Negative extends org.assertj.core.api.Condition {
  public boolean matches(Object p0) { return (boolean) (Object) null; }
}
---
package org.assertj.core.condition;
import java.io.*;

public class Not extends Negative {
  public static Not not(org.assertj.core.api.Condition p0) { return (Not) (Object) null; }
}
---
package org.assertj.core.data;
import java.io.*;

public class Index {
  public int value;
  public static Index atIndex(int p0) { return (Index) (Object) null; }
  public boolean equals(Object p0) { return (boolean) (Object) null; }
  public int hashCode() { return (int) (Object) null; }
  public String toString() { return (String) (Object) null; }
}
---
package org.assertj.core.data;
import java.io.*;

public class MapEntry implements java.util.Map.Entry {
  public static MapEntry entry(Object p0, Object p1) { return (MapEntry) (Object) null; }
  public boolean equals(Object p0) { return (boolean) (Object) null; }
  public int hashCode() { return (int) (Object) null; }
  public String toString() { return (String) (Object) null; }
  public Object getKey() { return (Object) (Object) null; }
  public Object getValue() { return (Object) (Object) null; }
  public Object setValue(Object p0) { return (Object) (Object) null; }
}
---
package org.assertj.core.data;
import java.io.*;

public class Offset {
  public boolean strict;
  public static Offset offset(Number p0) { return (Offset) (Object) null; }
  public static Offset strictOffset(Number p0) { return (Offset) (Object) null; }
  public boolean equals(Object p0) { return (boolean) (Object) null; }
  public int hashCode() { return (int) (Object) null; }
  public String toString() { return (String) (Object) null; }
}
---
package org.assertj.core.data;
import java.io.*;

public class Percentage {
  public double value;
  public static Percentage withPercentage(double p0) { return (Percentage) (Object) null; }
  public boolean equals(Object p0) { return (boolean) (Object) null; }
  public int hashCode() { return (int) (Object) null; }
  public String toString() { return (String) (Object) null; }
}
---
package org.assertj.core.data;
import java.io.*;

public interface TemporalOffset {
  boolean isBeyondOffset(java.time.temporal.Temporal p0, java.time.temporal.Temporal p1);
  String getBeyondOffsetDifferenceDescription(java.time.temporal.Temporal p0, java.time.temporal.Temporal p1);
}
---
package org.assertj.core.data;
import java.io.*;

public abstract class TemporalUnitOffset implements TemporalOffset {
  public void TemporalUnitOffset(long p0, java.time.temporal.TemporalUnit p1) {}
  public String getBeyondOffsetDifferenceDescription(java.time.temporal.Temporal p0, java.time.temporal.Temporal p1) { return (String) (Object) null; }
  public java.time.temporal.TemporalUnit getUnit() { return (java.time.temporal.TemporalUnit) (Object) null; }
}
---
package org.assertj.core.description;
import java.io.*;

public abstract class Description {
  public void Description() {}
  public abstract String value();
  public String toString() { return (String) (Object) null; }
  public static Description emptyIfNull(Description p0) { return (Description) (Object) null; }
  public static String mostRelevantDescription(Description p0, String p1) { return (String) (Object) null; }
}
---
package org.assertj.core.description;
import java.io.*;

public class LazyTextDescription extends Description {
  public void LazyTextDescription(java.util.function.Supplier p0) {}
  public String value() { return (String) (Object) null; }
}
---
package org.assertj.core.description;
import java.io.*;

public class TextDescription extends Description {
  public void TextDescription(String p0, Object[] p1) {}
  public String value() { return (String) (Object) null; }
  public int hashCode() { return (int) (Object) null; }
  public boolean equals(Object p0) { return (boolean) (Object) null; }
}
---
package org.assertj.core.groups;
import java.io.*;

public class Properties {
  public static Properties extractProperty(String p0, Class p1) { return (Properties) (Object) null; }
  public static Properties extractProperty(String p0) { return (Properties) (Object) null; }
  public Properties ofType(Class p0) { return (Properties) (Object) null; }
  public java.util.List from(Iterable p0) { return (java.util.List) (Object) null; }
  public java.util.List from(Object[] p0) { return (java.util.List) (Object) null; }
}
---
package org.assertj.core.groups;
import java.io.*;

public class Tuple {
  public void Tuple(Object[] p0) {}
  public Object[] toArray() { return (Object[]) (Object) null; }
  public java.util.List toList() { return (java.util.List) (Object) null; }
  public boolean equals(Object p0) { return (boolean) (Object) null; }
  public int hashCode() { return (int) (Object) null; }
  public String toString() { return (String) (Object) null; }
  public static Tuple tuple(Object[] p0) { return (Tuple) (Object) null; }
}
---
package org.assertj.core.internal;
import java.io.*;

public class TypeComparators {
  public static TypeComparators defaultTypeComparators() { return (TypeComparators) (Object) null; }
  public void TypeComparators() {}
  public java.util.Comparator get(Class p0) { return (java.util.Comparator) (Object) null; }
  public boolean hasComparatorForType(Class p0) { return (boolean) (Object) null; }
  public void put(Class p0, java.util.Comparator p1) {}
  public boolean isEmpty() { return (boolean) (Object) null; }
  public void clear() {}
  public java.util.stream.Stream comparatorByTypes() { return (java.util.stream.Stream) (Object) null; }
  public int hashCode() { return (int) (Object) null; }
  public boolean equals(Object p0) { return (boolean) (Object) null; }
  public String toString() { return (String) (Object) null; }
}
---
package org.assertj.core.presentation;
import java.io.*;

public interface Representation {
  String toStringOf(Object p0);
  String unambiguousToStringOf(Object p0);
}

