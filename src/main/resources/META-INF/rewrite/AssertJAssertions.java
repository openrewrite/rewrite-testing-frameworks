/*
 * Copyright 2020 the original author or authors.
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

public abstract class Abstract2DArrayAssert implements Array2DAssert {
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractArrayAssert implements ArraySortedAssert {
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractAssert implements Assert {
    public AbstractAssert usingComparator(java.util.Comparator p0) { return null; }
    public AbstractAssert isNotIn(Object[] p0) { return null; }
    public AbstractAssert isNotOfAnyClassIn(Class[] p0) { return null; }
    public AbstractAssert isNotSameAs(Object p0) { return null; }
    public boolean equals(Object p0) { return false; }
    public AbstractAssert hasToString(String p0) { return null; }
    public AbstractAssert matches(java.util.function.Predicate p0, String p1) { return null; }
    public AbstractAssert doesNotHave(Condition p0) { return null; }
    public AbstractAssert hasSameHashCodeAs(Object p0) { return null; }
    public AbstractAssert isNot(Condition p0) { return null; }
    public AbstractStringAssert asString() { return null; }
    public String descriptionText() { return null; }
    public AbstractAssert satisfiesAnyOf(java.util.function.Consumer p0, java.util.function.Consumer p1) { return null; }
    public void isNull() {}
    public AbstractAssert hasSameClassAs(Object p0) { return null; }
    public AbstractAssert isNotNull() { return null; }
    public AbstractAssert is(Condition p0) { return null; }
    public AbstractAssert doesNotHaveSameHashCodeAs(Object p0) { return null; }
    public AbstractAssert isIn(Object[] p0) { return null; }
    public AbstractListAssert asList() { return null; }
    public AbstractAssert withFailMessage(java.util.function.Supplier p0) { return null; }
    public AbstractAssert overridingErrorMessage(java.util.function.Supplier p0) { return null; }
    public AbstractAssert isNotInstanceOfAny(Class[] p0) { return null; }
    public AbstractAssert isEqualTo(Object p0) { return null; }
    public static void setDescriptionConsumer(java.util.function.Consumer p0) {}
    public AbstractAssert isOfAnyClassIn(Class[] p0) { return null; }
    public AbstractAssert isNotExactlyInstanceOf(Class p0) { return null; }
    public AbstractAssert withRepresentation(org.assertj.core.presentation.Representation p0) { return null; }
    public AbstractAssert isInstanceOfSatisfying(Class p0, java.util.function.Consumer p1) { return null; }
    public AbstractAssert doesNotHaveSameClassAs(Object p0) { return null; }
    public AbstractAssert satisfiesAnyOf(java.util.function.Consumer p0, java.util.function.Consumer p1, java.util.function.Consumer p2) { return null; }
    public AbstractAssert satisfies(Condition p0) { return null; }
    public AbstractAssert satisfiesAnyOf(java.util.function.Consumer p0, java.util.function.Consumer p1, java.util.function.Consumer p2, java.util.function.Consumer p3) { return null; }
    public AbstractAssert isNotEqualTo(Object p0) { return null; }
    public AbstractAssert satisfies(java.util.function.Consumer p0) { return null; }
    public AbstractAssert withFailMessage(String p0, Object[] p1) { return null; }
    public AbstractAssert isExactlyInstanceOf(Class p0) { return null; }
    public AbstractAssert isNotIn(Iterable p0) { return null; }
    public AbstractAssert doesNotHaveToString(String p0) { return null; }
    public AbstractAssert has(Condition p0) { return null; }
    public AbstractAssert isSameAs(Object p0) { return null; }
    public AbstractAssert usingDefaultComparator() { return null; }
    public AbstractAssert withThreadDumpOnError() { return null; }
    public AbstractAssert usingComparator(java.util.Comparator p0, String p1) { return null; }
    public int hashCode() { return 0; }
    public static void setPrintAssertionsDescription(boolean p0) {}
    public AbstractAssert asInstanceOf(InstanceOfAssertFactory p0) { return null; }
    public AbstractAssert describedAs(org.assertj.core.description.Description p0) { return null; }
    public AbstractAssert overridingErrorMessage(String p0, Object[] p1) { return null; }
    public AbstractAssert isInstanceOfAny(Class[] p0) { return null; }
    public static void setCustomRepresentation(org.assertj.core.presentation.Representation p0) {}
    public AbstractAssert isInstanceOf(Class p0) { return null; }
    public AbstractAssert matches(java.util.function.Predicate p0) { return null; }
    public AbstractAssert isNotInstanceOf(Class p0) { return null; }
    public AbstractAssert isIn(Iterable p0) { return null; }
    public WritableAssertionInfo getWritableAssertionInfo() { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractAtomicFieldUpdaterAssert {
    public AbstractAtomicFieldUpdaterAssert hasValue(Object p0, Object p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractBigDecimalAssert implements NumberAssert {
    public AbstractBigDecimalAssert isCloseTo(java.math.BigDecimal p0, org.assertj.core.data.Offset p1) { return null; }
    public NumberAssert isStrictlyBetween(Number p0, Number p1) { return null; }
    public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractBigDecimalAssert usingComparator(java.util.Comparator p0) { return null; }
    public AbstractBigDecimalAssert isStrictlyBetween(java.math.BigDecimal p0, java.math.BigDecimal p1) { return null; }
    public AbstractBigDecimalAssert isNotEqualByComparingTo(String p0) { return null; }
    public AbstractBigDecimalAssert isGreaterThanOrEqualTo(java.math.BigDecimal p0) { return null; }
    public AbstractBigDecimalAssert isNegative() { return null; }
    public AbstractBigDecimalAssert isCloseTo(java.math.BigDecimal p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractBigDecimalAssert isLessThanOrEqualTo(java.math.BigDecimal p0) { return null; }
    public AbstractBigDecimalAssert isEqualTo(String p0) { return null; }
    public AbstractBigDecimalAssert isZero() { return null; }
    public AbstractBigDecimalAssert isNotZero() { return null; }
    public AbstractBigDecimalAssert isOne() { return null; }
    public AbstractBigDecimalAssert isBetween(java.math.BigDecimal p0, java.math.BigDecimal p1) { return null; }
    public AbstractBigDecimalAssert isPositive() { return null; }
    public AbstractBigDecimalAssert isNotCloseTo(java.math.BigDecimal p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractComparableAssert isStrictlyBetween(Comparable p0, Comparable p1) { return null; }
    public AbstractBigDecimalAssert isNotNegative() { return null; }
    public AbstractComparableAssert isLessThanOrEqualTo(Comparable p0) { return null; }
    public AbstractBigDecimalAssert isNotCloseTo(java.math.BigDecimal p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractBigDecimalAssert usingDefaultComparator() { return null; }
    public AbstractComparableAssert isBetween(Comparable p0, Comparable p1) { return null; }
    public NumberAssert isBetween(Number p0, Number p1) { return null; }
    public AbstractBigDecimalAssert isEqualByComparingTo(String p0) { return null; }
    public AbstractBigDecimalAssert isNotPositive() { return null; }
    public AbstractComparableAssert isGreaterThanOrEqualTo(Comparable p0) { return null; }
    public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return null; }
    public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractBigDecimalAssert usingComparator(java.util.Comparator p0, String p1) { return null; }
    public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Offset p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class AbstractBigIntegerAssert implements NumberAssert {
    public AbstractBigIntegerAssert isNotCloseTo(java.math.BigInteger p0, org.assertj.core.data.Percentage p1) { return null; }
    public NumberAssert isStrictlyBetween(Number p0, Number p1) { return null; }
    public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractBigIntegerAssert isEqualTo(long p0) { return null; }
    public AbstractBigIntegerAssert usingComparator(java.util.Comparator p0) { return null; }
    public AbstractBigIntegerAssert isCloseTo(java.math.BigInteger p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractBigIntegerAssert isZero() { return null; }
    public AbstractBigIntegerAssert isPositive() { return null; }
    public AbstractBigIntegerAssert isOne() { return null; }
    public AbstractBigIntegerAssert isNotCloseTo(java.math.BigInteger p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractBigIntegerAssert isBetween(java.math.BigInteger p0, java.math.BigInteger p1) { return null; }
    public AbstractBigIntegerAssert isStrictlyBetween(java.math.BigInteger p0, java.math.BigInteger p1) { return null; }
    public AbstractBigIntegerAssert usingDefaultComparator() { return null; }
    public AbstractBigIntegerAssert isEqualTo(int p0) { return null; }
    public AbstractBigIntegerAssert isNotZero() { return null; }
    public AbstractBigIntegerAssert isCloseTo(java.math.BigInteger p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractComparableAssert isStrictlyBetween(Comparable p0, Comparable p1) { return null; }
    public AbstractBigIntegerAssert isNotPositive() { return null; }
    public AbstractBigIntegerAssert usingComparator(java.util.Comparator p0, String p1) { return null; }
    public AbstractBigIntegerAssert isNotNegative() { return null; }
    public AbstractComparableAssert isBetween(Comparable p0, Comparable p1) { return null; }
    public NumberAssert isBetween(Number p0, Number p1) { return null; }
    public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return null; }
    public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Offset p1) { return null; }
    public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractBigIntegerAssert isNegative() { return null; }
    public AbstractBigIntegerAssert isEqualTo(String p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractBooleanArrayAssert {
    public AbstractBooleanArrayAssert containsOnly(Boolean[] p0) { return null; }
    public AbstractBooleanArrayAssert hasSameSizeAs(Iterable p0) { return null; }
    public AbstractBooleanArrayAssert isSorted() { return null; }
    public AbstractBooleanArrayAssert hasSizeBetween(int p0, int p1) { return null; }
    public AbstractBooleanArrayAssert containsAnyOf(boolean[] p0) { return null; }
    public AbstractBooleanArrayAssert hasSizeLessThanOrEqualTo(int p0) { return null; }
    public AbstractBooleanArrayAssert startsWith(boolean[] p0) { return null; }
    public AbstractBooleanArrayAssert hasSizeGreaterThanOrEqualTo(int p0) { return null; }
    public AbstractBooleanArrayAssert isNotEmpty() { return null; }
    public AbstractBooleanArrayAssert containsSequence(boolean[] p0) { return null; }
    public AbstractBooleanArrayAssert doesNotContain(Boolean[] p0) { return null; }
    public AbstractBooleanArrayAssert usingElementComparator(java.util.Comparator p0) { return null; }
    public void isNullOrEmpty() {}
    public AbstractBooleanArrayAssert startsWith(Boolean[] p0) { return null; }
    public AbstractBooleanArrayAssert containsExactly(Boolean[] p0) { return null; }
    public AbstractBooleanArrayAssert containsOnlyOnce(Boolean[] p0) { return null; }
    public AbstractBooleanArrayAssert containsAnyOf(Boolean[] p0) { return null; }
    public AbstractBooleanArrayAssert containsExactlyInAnyOrder(Boolean[] p0) { return null; }
    public AbstractBooleanArrayAssert hasSize(int p0) { return null; }
    public AbstractBooleanArrayAssert containsSequence(Boolean[] p0) { return null; }
    public AbstractBooleanArrayAssert contains(Boolean[] p0) { return null; }
    public AbstractBooleanArrayAssert usingDefaultElementComparator() { return null; }
    public AbstractBooleanArrayAssert endsWith(boolean[] p0) { return null; }
    public AbstractBooleanArrayAssert containsExactly(boolean[] p0) { return null; }
    public void isEmpty() {}
    public AbstractBooleanArrayAssert containsOnlyOnce(boolean[] p0) { return null; }
    public AbstractBooleanArrayAssert contains(boolean[] p0) { return null; }
    public AbstractBooleanArrayAssert doesNotContain(boolean[] p0) { return null; }
    public AbstractBooleanArrayAssert contains(boolean p0, org.assertj.core.data.Index p1) { return null; }
    public AbstractBooleanArrayAssert hasSizeGreaterThan(int p0) { return null; }
    public AbstractBooleanArrayAssert containsSubsequence(Boolean[] p0) { return null; }
    public AbstractBooleanArrayAssert containsOnly(boolean[] p0) { return null; }
    public AbstractBooleanArrayAssert doesNotContain(boolean p0, org.assertj.core.data.Index p1) { return null; }
    public AbstractBooleanArrayAssert isSortedAccordingTo(java.util.Comparator p0) { return null; }
    public AbstractBooleanArrayAssert endsWith(Boolean[] p0) { return null; }
    public AbstractBooleanArrayAssert containsSubsequence(boolean[] p0) { return null; }
    public AbstractBooleanArrayAssert containsExactlyInAnyOrder(boolean[] p0) { return null; }
    public AbstractBooleanArrayAssert hasSizeLessThan(int p0) { return null; }
    public AbstractBooleanArrayAssert doesNotHaveDuplicates() { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractBooleanAssert {
    public AbstractBooleanAssert usingComparator(java.util.Comparator p0, String p1) { return null; }
    public AbstractBooleanAssert isTrue() { return null; }
    public AbstractBooleanAssert usingComparator(java.util.Comparator p0) { return null; }
    public AbstractBooleanAssert isNotEqualTo(boolean p0) { return null; }
    public AbstractBooleanAssert isEqualTo(boolean p0) { return null; }
    public AbstractBooleanAssert isFalse() { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractByteArrayAssert {
    public AbstractByteArrayAssert containsExactly(byte[] p0) { return null; }
    public AbstractByteArrayAssert containsExactly(Byte[] p0) { return null; }
    public AbstractByteArrayAssert doesNotContain(byte[] p0) { return null; }
    public AbstractByteArrayAssert containsExactly(int[] p0) { return null; }
    public AbstractByteArrayAssert containsOnlyOnce(int[] p0) { return null; }
    public AbstractByteArrayAssert doesNotContain(Byte[] p0) { return null; }
    public AbstractByteArrayAssert contains(byte p0, org.assertj.core.data.Index p1) { return null; }
    public AbstractStringAssert asHexString() { return null; }
    public AbstractByteArrayAssert containsOnly(Byte[] p0) { return null; }
    public AbstractByteArrayAssert containsAnyOf(int[] p0) { return null; }
    public AbstractByteArrayAssert containsOnly(byte[] p0) { return null; }
    public AbstractByteArrayAssert containsOnly(int[] p0) { return null; }
    public AbstractByteArrayAssert startsWith(Byte[] p0) { return null; }
    public AbstractByteArrayAssert startsWith(byte[] p0) { return null; }
    public AbstractByteArrayAssert containsOnlyOnce(Byte[] p0) { return null; }
    public AbstractByteArrayAssert containsOnlyOnce(byte[] p0) { return null; }
    public AbstractStringAssert asString() { return null; }
    public AbstractByteArrayAssert hasSizeBetween(int p0, int p1) { return null; }
    public AbstractByteArrayAssert containsSubsequence(byte[] p0) { return null; }
    public AbstractByteArrayAssert containsExactlyInAnyOrder(Byte[] p0) { return null; }
    public AbstractStringAssert encodedAsBase64() { return null; }
    public AbstractByteArrayAssert isSortedAccordingTo(java.util.Comparator p0) { return null; }
    public AbstractByteArrayAssert doesNotHaveDuplicates() { return null; }
    public AbstractByteArrayAssert containsSequence(int[] p0) { return null; }
    public AbstractByteArrayAssert hasSizeGreaterThanOrEqualTo(int p0) { return null; }
    public AbstractByteArrayAssert containsExactlyInAnyOrder(int[] p0) { return null; }
    public AbstractByteArrayAssert hasSizeGreaterThan(int p0) { return null; }
    public AbstractByteArrayAssert startsWith(int[] p0) { return null; }
    public AbstractByteArrayAssert containsAnyOf(byte[] p0) { return null; }
    public AbstractByteArrayAssert doesNotContain(int p0, org.assertj.core.data.Index p1) { return null; }
    public AbstractByteArrayAssert contains(Byte[] p0) { return null; }
    public AbstractByteArrayAssert contains(int[] p0) { return null; }
    public AbstractByteArrayAssert contains(byte[] p0) { return null; }
    public AbstractStringAssert asString(java.nio.charset.Charset p0) { return null; }
    public AbstractByteArrayAssert hasSizeLessThan(int p0) { return null; }
    public AbstractByteArrayAssert containsSubsequence(int[] p0) { return null; }
    public AbstractByteArrayAssert hasSizeLessThanOrEqualTo(int p0) { return null; }
    public AbstractByteArrayAssert endsWith(byte[] p0) { return null; }
    public void isNullOrEmpty() {}
    public AbstractByteArrayAssert hasSize(int p0) { return null; }
    public AbstractByteArrayAssert contains(int p0, org.assertj.core.data.Index p1) { return null; }
    public AbstractByteArrayAssert endsWith(Byte[] p0) { return null; }
    public AbstractByteArrayAssert containsSequence(Byte[] p0) { return null; }
    public AbstractByteArrayAssert containsSequence(byte[] p0) { return null; }
    public AbstractByteArrayAssert containsSubsequence(Byte[] p0) { return null; }
    public AbstractByteArrayAssert doesNotContain(int[] p0) { return null; }
    public AbstractByteArrayAssert doesNotContain(byte p0, org.assertj.core.data.Index p1) { return null; }
    public AbstractByteArrayAssert containsExactlyInAnyOrder(byte[] p0) { return null; }
    public void isEmpty() {}
    public AbstractByteArrayAssert hasSameSizeAs(Iterable p0) { return null; }
    public AbstractByteArrayAssert endsWith(int[] p0) { return null; }
    public AbstractByteArrayAssert usingDefaultElementComparator() { return null; }
    public AbstractByteArrayAssert usingElementComparator(java.util.Comparator p0) { return null; }
    public AbstractByteArrayAssert containsAnyOf(Byte[] p0) { return null; }
    public AbstractByteArrayAssert isNotEmpty() { return null; }
    public AbstractByteArrayAssert isSorted() { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractByteAssert implements NumberAssert {
    public AbstractByteAssert isNegative() { return null; }
    public NumberAssert isStrictlyBetween(Number p0, Number p1) { return null; }
    public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractByteAssert isGreaterThanOrEqualTo(byte p0) { return null; }
    public AbstractByteAssert isNotZero() { return null; }
    public AbstractByteAssert isBetween(Byte p0, Byte p1) { return null; }
    public AbstractByteAssert isCloseTo(byte p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractByteAssert usingComparator(java.util.Comparator p0) { return null; }
    public AbstractByteAssert isCloseTo(Byte p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractByteAssert isNotCloseTo(byte p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractByteAssert isCloseTo(Byte p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractByteAssert isNotPositive() { return null; }
    public AbstractByteAssert isEven() { return null; }
    public AbstractByteAssert isOdd() { return null; }
    public AbstractByteAssert isLessThanOrEqualTo(byte p0) { return null; }
    public AbstractByteAssert isZero() { return null; }
    public AbstractByteAssert isOne() { return null; }
    public AbstractByteAssert isNotCloseTo(Byte p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractByteAssert isLessThan(byte p0) { return null; }
    public AbstractByteAssert isEqualTo(byte p0) { return null; }
    public AbstractByteAssert isNotCloseTo(Byte p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractComparableAssert isStrictlyBetween(Comparable p0, Comparable p1) { return null; }
    public AbstractByteAssert isNotEqualTo(byte p0) { return null; }
    public AbstractByteAssert usingDefaultComparator() { return null; }
    public AbstractByteAssert isGreaterThan(byte p0) { return null; }
    public AbstractByteAssert isNotNegative() { return null; }
    public AbstractComparableAssert isBetween(Comparable p0, Comparable p1) { return null; }
    public AbstractByteAssert isPositive() { return null; }
    public NumberAssert isBetween(Number p0, Number p1) { return null; }
    public AbstractByteAssert isCloseTo(byte p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractByteAssert usingComparator(java.util.Comparator p0, String p1) { return null; }
    public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return null; }
    public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractByteAssert isStrictlyBetween(Byte p0, Byte p1) { return null; }
    public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractByteAssert isNotCloseTo(byte p0, org.assertj.core.data.Percentage p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractCharArrayAssert {
    public AbstractCharArrayAssert isSorted() { return null; }
    public AbstractCharArrayAssert containsSubsequence(char[] p0) { return null; }
    public AbstractCharArrayAssert usingElementComparator(java.util.Comparator p0) { return null; }
    public AbstractCharArrayAssert containsExactly(char[] p0) { return null; }
    public AbstractCharArrayAssert containsOnly(Character[] p0) { return null; }
    public AbstractCharArrayAssert contains(char p0, org.assertj.core.data.Index p1) { return null; }
    public AbstractCharArrayAssert containsAnyOf(char[] p0) { return null; }
    public AbstractCharArrayAssert isNotEmpty() { return null; }
    public AbstractCharArrayAssert isSortedAccordingTo(java.util.Comparator p0) { return null; }
    public AbstractCharArrayAssert containsSubsequence(Character[] p0) { return null; }
    public AbstractCharArrayAssert usingDefaultElementComparator() { return null; }
    public AbstractCharArrayAssert hasSizeBetween(int p0, int p1) { return null; }
    public AbstractCharArrayAssert containsSequence(Character[] p0) { return null; }
    public AbstractCharArrayAssert doesNotContain(Character[] p0) { return null; }
    public void isNullOrEmpty() {}
    public AbstractCharArrayAssert containsOnlyOnce(char[] p0) { return null; }
    public AbstractCharArrayAssert hasSizeGreaterThan(int p0) { return null; }
    public AbstractCharArrayAssert hasSizeGreaterThanOrEqualTo(int p0) { return null; }
    public AbstractCharArrayAssert containsOnly(char[] p0) { return null; }
    public AbstractCharArrayAssert containsSequence(char[] p0) { return null; }
    public AbstractCharArrayAssert contains(Character[] p0) { return null; }
    public AbstractCharArrayAssert containsExactlyInAnyOrder(Character[] p0) { return null; }
    public void isEmpty() {}
    public AbstractCharArrayAssert startsWith(Character[] p0) { return null; }
    public AbstractCharArrayAssert endsWith(Character[] p0) { return null; }
    public AbstractCharArrayAssert hasSizeLessThanOrEqualTo(int p0) { return null; }
    public AbstractCharArrayAssert doesNotContain(char p0, org.assertj.core.data.Index p1) { return null; }
    public AbstractCharArrayAssert startsWith(char[] p0) { return null; }
    public AbstractCharArrayAssert hasSize(int p0) { return null; }
    public AbstractCharArrayAssert doesNotHaveDuplicates() { return null; }
    public AbstractCharArrayAssert containsExactlyInAnyOrder(char[] p0) { return null; }
    public AbstractCharArrayAssert endsWith(char[] p0) { return null; }
    public AbstractCharArrayAssert hasSizeLessThan(int p0) { return null; }
    public AbstractCharArrayAssert containsExactly(Character[] p0) { return null; }
    public AbstractCharArrayAssert hasSameSizeAs(Iterable p0) { return null; }
    public AbstractCharArrayAssert containsAnyOf(Character[] p0) { return null; }
    public AbstractCharArrayAssert doesNotContain(char[] p0) { return null; }
    public AbstractCharArrayAssert contains(char[] p0) { return null; }
    public AbstractCharArrayAssert inUnicode() { return null; }
    public AbstractCharArrayAssert containsOnlyOnce(Character[] p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractCharSequenceAssert implements EnumerableAssert {
    public AbstractCharSequenceAssert startsWith(CharSequence p0) { return null; }
    public AbstractCharSequenceAssert containsPattern(CharSequence p0) { return null; }
    public AbstractCharSequenceAssert isJavaBlank() { return null; }
    public AbstractCharSequenceAssert hasSizeBetween(int p0, int p1) { return null; }
    public AbstractCharSequenceAssert containsOnlyOnce(CharSequence p0) { return null; }
    public AbstractCharSequenceAssert inHexadecimal() { return null; }
    public AbstractCharSequenceAssert isXmlEqualToContentOf(File p0) { return null; }
    public AbstractCharSequenceAssert isNotEqualToNormalizingWhitespace(CharSequence p0) { return null; }
    public AbstractCharSequenceAssert containsSubsequence(Iterable p0) { return null; }
    public AbstractCharSequenceAssert isEqualToIgnoringCase(CharSequence p0) { return null; }
    public AbstractCharSequenceAssert usingComparator(java.util.Comparator p0) { return null; }
    public AbstractCharSequenceAssert hasSameSizeAs(CharSequence p0) { return null; }
    public AbstractCharSequenceAssert isBlank() { return null; }
    public AbstractCharSequenceAssert contains(Iterable p0) { return null; }
    public AbstractCharSequenceAssert isLowerCase() { return null; }
    public AbstractCharSequenceAssert usingDefaultComparator() { return null; }
    public AbstractCharSequenceAssert isUpperCase() { return null; }
    public AbstractCharSequenceAssert usingComparator(java.util.Comparator p0, String p1) { return null; }
    public AbstractCharSequenceAssert isNotEqualToIgnoringCase(CharSequence p0) { return null; }
    public AbstractCharSequenceAssert matches(CharSequence p0) { return null; }
    public AbstractCharSequenceAssert doesNotMatch(java.util.regex.Pattern p0) { return null; }
    public AbstractCharSequenceAssert matches(java.util.regex.Pattern p0) { return null; }
    public AbstractCharSequenceAssert usingElementComparator(java.util.Comparator p0) { return null; }
    public AbstractCharSequenceAssert doesNotContainIgnoringCase(CharSequence[] p0) { return null; }
    public AbstractCharSequenceAssert doesNotContainAnyWhitespaces() { return null; }
    public AbstractCharSequenceAssert hasSameSizeAs(Iterable p0) { return null; }
    public AbstractCharSequenceAssert doesNotContain(Iterable p0) { return null; }
    public AbstractCharSequenceAssert containsSequence(Iterable p0) { return null; }
    public AbstractCharSequenceAssert endsWith(CharSequence p0) { return null; }
    public AbstractCharSequenceAssert doesNotEndWith(CharSequence p0) { return null; }
    public AbstractCharSequenceAssert doesNotContain(CharSequence[] p0) { return null; }
    public AbstractCharSequenceAssert isEqualToIgnoringNewLines(CharSequence p0) { return null; }
    public AbstractCharSequenceAssert isNotEmpty() { return null; }
    public AbstractCharSequenceAssert hasLineCount(int p0) { return null; }
    public AbstractCharSequenceAssert isNotBlank() { return null; }
    public AbstractCharSequenceAssert hasSizeLessThanOrEqualTo(int p0) { return null; }
    public AbstractCharSequenceAssert isNotEqualToIgnoringWhitespace(CharSequence p0) { return null; }
    public AbstractCharSequenceAssert isEqualToNormalizingNewlines(CharSequence p0) { return null; }
    public AbstractCharSequenceAssert isEqualToNormalizingWhitespace(CharSequence p0) { return null; }
    public AbstractCharSequenceAssert doesNotContainPattern(java.util.regex.Pattern p0) { return null; }
    public AbstractCharSequenceAssert containsWhitespaces() { return null; }
    public AbstractCharSequenceAssert doesNotStartWith(CharSequence p0) { return null; }
    public AbstractCharSequenceAssert contains(CharSequence[] p0) { return null; }
    public AbstractCharSequenceAssert doesNotContainPattern(CharSequence p0) { return null; }
    public AbstractCharSequenceAssert isXmlEqualTo(CharSequence p0) { return null; }
    public AbstractCharSequenceAssert isEqualToNormalizingPunctuationAndWhitespace(CharSequence p0) { return null; }
    public void isNullOrEmpty() {}
    public AbstractCharSequenceAssert hasSizeGreaterThanOrEqualTo(int p0) { return null; }
    public AbstractCharSequenceAssert containsSequence(CharSequence[] p0) { return null; }
    public AbstractCharSequenceAssert hasSize(int p0) { return null; }
    public AbstractCharSequenceAssert hasSizeGreaterThan(int p0) { return null; }
    public AbstractCharSequenceAssert isEqualToNormalizingUnicode(CharSequence p0) { return null; }
    public AbstractCharSequenceAssert doesNotContainOnlyWhitespaces() { return null; }
    public AbstractCharSequenceAssert isEqualToIgnoringWhitespace(CharSequence p0) { return null; }
    public AbstractCharSequenceAssert hasSizeLessThan(int p0) { return null; }
    public void isEmpty() {}
    public AbstractCharSequenceAssert containsOnlyDigits() { return null; }
    public AbstractCharSequenceAssert containsOnlyWhitespaces() { return null; }
    public AbstractCharSequenceAssert hasSameSizeAs(Object p0) { return null; }
    public AbstractCharSequenceAssert inUnicode() { return null; }
    public AbstractCharSequenceAssert usingDefaultElementComparator() { return null; }
    public AbstractCharSequenceAssert containsIgnoringCase(CharSequence p0) { return null; }
    public AbstractCharSequenceAssert isSubstringOf(CharSequence p0) { return null; }
    public AbstractCharSequenceAssert containsSubsequence(CharSequence[] p0) { return null; }
    public AbstractCharSequenceAssert isNotJavaBlank() { return null; }
    public AbstractCharSequenceAssert containsPattern(java.util.regex.Pattern p0) { return null; }
    public AbstractCharSequenceAssert doesNotMatch(CharSequence p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractCharacterAssert {
    public AbstractCharacterAssert isNotEqualTo(char p0) { return null; }
    public AbstractCharacterAssert isGreaterThan(char p0) { return null; }
    public AbstractCharacterAssert usingDefaultComparator() { return null; }
    public AbstractCharacterAssert isLowerCase() { return null; }
    public AbstractCharacterAssert inUnicode() { return null; }
    public AbstractCharacterAssert usingComparator(java.util.Comparator p0, String p1) { return null; }
    public AbstractCharacterAssert isLessThanOrEqualTo(char p0) { return null; }
    public AbstractCharacterAssert isEqualTo(char p0) { return null; }
    public AbstractCharacterAssert isGreaterThanOrEqualTo(char p0) { return null; }
    public AbstractCharacterAssert isUpperCase() { return null; }
    public AbstractCharacterAssert isLessThan(char p0) { return null; }
    public AbstractCharacterAssert usingComparator(java.util.Comparator p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractComparableAssert implements ComparableAssert {
    public AbstractComparableAssert usingComparator(java.util.Comparator p0, String p1) { return null; }
    public AbstractComparableAssert inHexadecimal() { return null; }
    public AbstractComparableAssert isNotEqualByComparingTo(Comparable p0) { return null; }
    public AbstractComparableAssert isStrictlyBetween(Comparable p0, Comparable p1) { return null; }
    public AbstractComparableAssert usingDefaultComparator() { return null; }
    public AbstractComparableAssert isLessThanOrEqualTo(Comparable p0) { return null; }
    public AbstractComparableAssert usingComparator(java.util.Comparator p0) { return null; }
    public AbstractComparableAssert isEqualByComparingTo(Comparable p0) { return null; }
    public AbstractComparableAssert isLessThan(Comparable p0) { return null; }
    public AbstractComparableAssert isGreaterThan(Comparable p0) { return null; }
    public AbstractComparableAssert isBetween(Comparable p0, Comparable p1) { return null; }
    public AbstractComparableAssert inBinary() { return null; }
    public AbstractComparableAssert isGreaterThanOrEqualTo(Comparable p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractDateAssert {
    public AbstractDateAssert isNotBetween(java.util.Date p0, java.util.Date p1, boolean p2, boolean p3) { return null; }
    public AbstractDateAssert isCloseTo(java.time.Instant p0, long p1) { return null; }
    public AbstractDateAssert isInSameMinuteAs(String p0) { return null; }
    public AbstractDateAssert isInSameSecondAs(java.util.Date p0) { return null; }
    public AbstractDateAssert hasMillisecond(int p0) { return null; }
    public AbstractDateAssert hasHourOfDay(int p0) { return null; }
    public AbstractDateAssert isAfter(String p0) { return null; }
    public AbstractDateAssert isEqualTo(String p0) { return null; }
    public AbstractDateAssert isNotBetween(java.time.Instant p0, java.time.Instant p1) { return null; }
    public AbstractDateAssert isEqualToIgnoringHours(java.util.Date p0) { return null; }
    public AbstractDateAssert isBetween(java.time.Instant p0, java.time.Instant p1) { return null; }
    public AbstractDateAssert isInSameYearAs(String p0) { return null; }
    public AbstractDateAssert isBetween(java.time.Instant p0, java.time.Instant p1, boolean p2, boolean p3) { return null; }
    public AbstractDateAssert isInSameYearAs(java.util.Date p0) { return null; }
    public AbstractDateAssert isBefore(String p0) { return null; }
    public AbstractDateAssert isEqualToIgnoringHours(java.time.Instant p0) { return null; }
    public AbstractDateAssert isNotIn(String[] p0) { return null; }
    public AbstractDateAssert isEqualTo(java.time.Instant p0) { return null; }
    public AbstractDateAssert isNotInWithStringDateCollection(java.util.Collection p0) { return null; }
    public AbstractDateAssert isNotEqualTo(java.time.Instant p0) { return null; }
    public AbstractDateAssert isIn(java.time.Instant[] p0) { return null; }
    public AbstractDateAssert isInSameSecondWindowAs(java.util.Date p0) { return null; }
    public AbstractDateAssert isAfterYear(int p0) { return null; }
    public AbstractDateAssert isBeforeYear(int p0) { return null; }
    public AbstractDateAssert isIn(String[] p0) { return null; }
    public AbstractDateAssert hasMinute(int p0) { return null; }
    public AbstractDateAssert isInSameMonthAs(java.time.Instant p0) { return null; }
    public AbstractDateAssert isBetween(String p0, String p1) { return null; }
    public AbstractDateAssert isInSameSecondAs(String p0) { return null; }
    public AbstractDateAssert isWithinSecond(int p0) { return null; }
    public AbstractDateAssert isEqualToIgnoringMinutes(java.time.Instant p0) { return null; }
    public AbstractDateAssert isCloseTo(java.util.Date p0, long p1) { return null; }
    public AbstractDateAssert isEqualToIgnoringMillis(java.time.Instant p0) { return null; }
    public AbstractDateAssert isAfterOrEqualsTo(java.util.Date p0) { return null; }
    public AbstractDateAssert isCloseTo(String p0, long p1) { return null; }
    public AbstractDateAssert isWithinHourOfDay(int p0) { return null; }
    public AbstractDateAssert isNotBetween(java.time.Instant p0, java.time.Instant p1, boolean p2, boolean p3) { return null; }
    public AbstractDateAssert isInTheFuture() { return null; }
    public AbstractDateAssert hasTime(long p0) { return null; }
    public AbstractDateAssert usingComparator(java.util.Comparator p0) { return null; }
    public AbstractDateAssert isToday() { return null; }
    public AbstractDateAssert isInWithStringDateCollection(java.util.Collection p0) { return null; }
    public AbstractDateAssert usingDefaultComparator() { return null; }
    public AbstractDateAssert isInSameSecondWindowAs(String p0) { return null; }
    public AbstractDateAssert isAfterOrEqualTo(String p0) { return null; }
    public AbstractDateAssert isInSameMonthAs(java.util.Date p0) { return null; }
    public AbstractDateAssert isAfterOrEqualTo(java.time.Instant p0) { return null; }
    public AbstractDateAssert isEqualToIgnoringSeconds(String p0) { return null; }
    public AbstractDateAssert withDateFormat(String p0) { return null; }
    public AbstractDateAssert isBeforeOrEqualTo(java.time.Instant p0) { return null; }
    public AbstractDateAssert isNotIn(java.time.Instant[] p0) { return null; }
    public AbstractDateAssert isInSameDayAs(java.time.Instant p0) { return null; }
    public AbstractDateAssert isNotBetween(java.util.Date p0, java.util.Date p1) { return null; }
    public static void registerCustomDateFormat(java.text.DateFormat p0) {}
    public AbstractDateAssert isInSameMonthAs(String p0) { return null; }
    public AbstractDateAssert isInSameSecondWindowAs(java.time.Instant p0) { return null; }
    public AbstractDateAssert isBefore(java.util.Date p0) { return null; }
    public AbstractDateAssert hasSameTimeAs(java.util.Date p0) { return null; }
    public AbstractDateAssert isInSameDayAs(java.util.Date p0) { return null; }
    public static void registerCustomDateFormat(String p0) {}
    public AbstractDateAssert hasDayOfWeek(int p0) { return null; }
    public AbstractDateAssert isBetween(String p0, String p1, boolean p2, boolean p3) { return null; }
    public AbstractDateAssert isNotBetween(String p0, String p1) { return null; }
    public AbstractDateAssert isInSameMinuteWindowAs(String p0) { return null; }
    public AbstractDateAssert withDateFormat(java.text.DateFormat p0) { return null; }
    public AbstractDateAssert isEqualToIgnoringSeconds(java.time.Instant p0) { return null; }
    public AbstractDateAssert isBetween(java.util.Date p0, java.util.Date p1) { return null; }
    public AbstractDateAssert isInSameHourAs(String p0) { return null; }
    public AbstractDateAssert isInSameHourWindowAs(String p0) { return null; }
    public AbstractDateAssert isNotEqualTo(String p0) { return null; }
    public AbstractDateAssert isInSameHourAs(java.util.Date p0) { return null; }
    public AbstractDateAssert isInThePast() { return null; }
    public AbstractDateAssert isWithinDayOfMonth(int p0) { return null; }
    public static void useDefaultDateFormatsOnly() {}
    public AbstractDateAssert isEqualToIgnoringMillis(String p0) { return null; }
    public AbstractDateAssert usingComparator(java.util.Comparator p0, String p1) { return null; }
    public AbstractDateAssert hasSameTimeAs(String p0) { return null; }
    public AbstractDateAssert hasDayOfMonth(int p0) { return null; }
    public AbstractDateAssert isWithinYear(int p0) { return null; }
    public AbstractDateAssert withDefaultDateFormatsOnly() { return null; }
    public AbstractDateAssert isBeforeOrEqualsTo(String p0) { return null; }
    public AbstractDateAssert isBefore(java.time.Instant p0) { return null; }
    public AbstractDateAssert isWithinDayOfWeek(int p0) { return null; }
    public AbstractDateAssert isInSameYearAs(java.time.Instant p0) { return null; }
    public AbstractDateAssert isEqualToIgnoringSeconds(java.util.Date p0) { return null; }
    public AbstractDateAssert isBetween(java.util.Date p0, java.util.Date p1, boolean p2, boolean p3) { return null; }
    public AbstractDateAssert isAfterOrEqualsTo(String p0) { return null; }
    public AbstractDateAssert hasYear(int p0) { return null; }
    public AbstractDateAssert isInSameMinuteWindowAs(java.util.Date p0) { return null; }
    public AbstractDateAssert isEqualToIgnoringMinutes(String p0) { return null; }
    public AbstractDateAssert isBeforeOrEqualTo(java.util.Date p0) { return null; }
    public AbstractDateAssert isWithinMinute(int p0) { return null; }
    public AbstractDateAssert isEqualToIgnoringHours(String p0) { return null; }
    public AbstractDateAssert isEqualToIgnoringMillis(java.util.Date p0) { return null; }
    public AbstractDateAssert isInSameMinuteAs(java.util.Date p0) { return null; }
    public AbstractDateAssert isBeforeOrEqualsTo(java.util.Date p0) { return null; }
    public AbstractDateAssert isEqualToIgnoringMinutes(java.util.Date p0) { return null; }
    public AbstractDateAssert isInSameHourWindowAs(java.util.Date p0) { return null; }
    public AbstractDateAssert hasMonth(int p0) { return null; }
    public AbstractDateAssert isNotBetween(String p0, String p1, boolean p2, boolean p3) { return null; }
    public AbstractDateAssert isBeforeOrEqualTo(String p0) { return null; }
    public static void setLenientDateParsing(boolean p0) {}
    public AbstractDateAssert isAfterOrEqualTo(java.util.Date p0) { return null; }
    public AbstractDateAssert isInSameHourWindowAs(java.time.Instant p0) { return null; }
    public AbstractDateAssert isAfter(java.util.Date p0) { return null; }
    public AbstractDateAssert isWithinMonth(int p0) { return null; }
    public AbstractDateAssert isInSameDayAs(String p0) { return null; }
    public AbstractDateAssert isInSameMinuteWindowAs(java.time.Instant p0) { return null; }
    public AbstractDateAssert isWithinMillisecond(int p0) { return null; }
    public AbstractDateAssert hasSecond(int p0) { return null; }
    public AbstractDateAssert isAfter(java.time.Instant p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractDoubleArrayAssert {
    public AbstractDoubleArrayAssert containsOnlyOnce(double[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractDoubleArrayAssert hasSizeBetween(int p0, int p1) { return null; }
    public AbstractDoubleArrayAssert containsOnly(Double[] p0) { return null; }
    public AbstractDoubleArrayAssert isSortedAccordingTo(java.util.Comparator p0) { return null; }
    public AbstractDoubleArrayAssert containsExactly(Double[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractDoubleArrayAssert doesNotHaveDuplicates() { return null; }
    public AbstractDoubleArrayAssert doesNotContain(double p0, org.assertj.core.data.Index p1, org.assertj.core.data.Offset p2) { return null; }
    public AbstractDoubleArrayAssert doesNotHaveDuplicates(org.assertj.core.data.Offset p0) { return null; }
    public AbstractDoubleArrayAssert containsSubsequence(Double[] p0) { return null; }
    public AbstractDoubleArrayAssert startsWith(double[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractDoubleArrayAssert contains(double p0, org.assertj.core.data.Index p1) { return null; }
    public AbstractDoubleArrayAssert hasSizeLessThanOrEqualTo(int p0) { return null; }
    public AbstractDoubleArrayAssert doesNotContain(Double[] p0) { return null; }
    public AbstractDoubleArrayAssert hasSizeGreaterThanOrEqualTo(int p0) { return null; }
    public AbstractDoubleArrayAssert contains(Double[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractDoubleArrayAssert containsSubsequence(double[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractDoubleArrayAssert startsWith(Double[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractDoubleArrayAssert endsWith(Double[] p0) { return null; }
    public AbstractDoubleArrayAssert containsExactly(double[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractDoubleArrayAssert containsExactlyInAnyOrder(double[] p0) { return null; }
    public AbstractDoubleArrayAssert containsOnlyOnce(Double[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractDoubleArrayAssert containsSequence(double[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractDoubleArrayAssert contains(double[] p0) { return null; }
    public AbstractDoubleArrayAssert contains(double p0, org.assertj.core.data.Index p1, org.assertj.core.data.Offset p2) { return null; }
    public AbstractDoubleArrayAssert hasSameSizeAs(Iterable p0) { return null; }
    public AbstractDoubleArrayAssert endsWith(double[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractDoubleArrayAssert containsOnlyOnce(double[] p0) { return null; }
    public AbstractDoubleArrayAssert startsWith(Double[] p0) { return null; }
    public AbstractDoubleArrayAssert containsAnyOf(double[] p0) { return null; }
    public AbstractDoubleArrayAssert doesNotContain(double[] p0) { return null; }
    public AbstractDoubleArrayAssert containsOnly(Double[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractDoubleArrayAssert endsWith(double[] p0) { return null; }
    public AbstractDoubleArrayAssert containsExactly(double[] p0) { return null; }
    public AbstractDoubleArrayAssert containsOnly(double[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractDoubleArrayAssert endsWith(Double[] p0, org.assertj.core.data.Offset p1) { return null; }
    public void isNullOrEmpty() {}
    public AbstractDoubleArrayAssert contains(Double[] p0) { return null; }
    public AbstractDoubleArrayAssert containsSubsequence(Double[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractDoubleArrayAssert doesNotContain(double[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractDoubleArrayAssert usingComparatorWithPrecision(Double p0) { return null; }
    public AbstractDoubleArrayAssert hasSize(int p0) { return null; }
    public AbstractDoubleArrayAssert containsExactlyInAnyOrder(Double[] p0) { return null; }
    public AbstractDoubleArrayAssert hasSizeLessThan(int p0) { return null; }
    public AbstractDoubleArrayAssert containsSequence(double[] p0) { return null; }
    public AbstractDoubleArrayAssert startsWith(double[] p0) { return null; }
    public AbstractDoubleArrayAssert containsAnyOf(Double[] p0) { return null; }
    public AbstractDoubleArrayAssert doesNotContain(double p0, org.assertj.core.data.Index p1) { return null; }
    public void isEmpty() {}
    public AbstractDoubleArrayAssert containsOnlyOnce(Double[] p0) { return null; }
    public AbstractDoubleArrayAssert hasSizeGreaterThan(int p0) { return null; }
    public AbstractDoubleArrayAssert containsOnly(double[] p0) { return null; }
    public AbstractDoubleArrayAssert containsSequence(Double[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractDoubleArrayAssert containsExactly(Double[] p0) { return null; }
    public AbstractDoubleArrayAssert isSorted() { return null; }
    public AbstractDoubleArrayAssert usingDefaultElementComparator() { return null; }
    public AbstractDoubleArrayAssert doesNotContain(Double[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractDoubleArrayAssert isNotEmpty() { return null; }
    public AbstractDoubleArrayAssert contains(double[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractDoubleArrayAssert containsSequence(Double[] p0) { return null; }
    public AbstractDoubleArrayAssert containsSubsequence(double[] p0) { return null; }
    public AbstractDoubleArrayAssert usingElementComparator(java.util.Comparator p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractDoubleAssert implements FloatingPointNumberAssert {
    public AbstractDoubleAssert isNegative() { return null; }
    public AbstractDoubleAssert isGreaterThanOrEqualTo(Double p0) { return null; }
    public NumberAssert isStrictlyBetween(Number p0, Number p1) { return null; }
    public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractDoubleAssert isEqualTo(Double p0) { return null; }
    public FloatingPointNumberAssert isEqualTo(Number p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractDoubleAssert isStrictlyBetween(Double p0, Double p1) { return null; }
    public AbstractDoubleAssert isNotPositive() { return null; }
    public AbstractDoubleAssert isCloseTo(Double p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractDoubleAssert usingComparator(java.util.Comparator p0) { return null; }
    public void AbstractDoubleAssert(double p0, Class p1) {}
    public AbstractDoubleAssert isNotEqualTo(Double p0) { return null; }
    public AbstractDoubleAssert usingDefaultComparator() { return null; }
    public AbstractDoubleAssert isNotCloseTo(Double p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractDoubleAssert isNotEqualTo(double p0) { return null; }
    public AbstractDoubleAssert isNotCloseTo(double p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractDoubleAssert isNotCloseTo(Double p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractDoubleAssert isNotCloseTo(double p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractDoubleAssert isLessThanOrEqualTo(double p0) { return null; }
    public AbstractDoubleAssert isCloseTo(double p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractDoubleAssert isBetween(Double p0, Double p1) { return null; }
    public AbstractDoubleAssert isOne() { return null; }
    public AbstractDoubleAssert isPositive() { return null; }
    public AbstractDoubleAssert isLessThanOrEqualTo(Double p0) { return null; }
    public AbstractDoubleAssert isNotNaN() { return null; }
    public AbstractDoubleAssert isZero() { return null; }
    public AbstractDoubleAssert isGreaterThan(double p0) { return null; }
    public AbstractDoubleAssert isEqualTo(double p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractDoubleAssert isNaN() { return null; }
    public AbstractDoubleAssert isGreaterThanOrEqualTo(double p0) { return null; }
    public AbstractDoubleAssert isNotZero() { return null; }
    public AbstractDoubleAssert isLessThan(double p0) { return null; }
    public AbstractDoubleAssert isEqualTo(Double p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractDoubleAssert usingComparator(java.util.Comparator p0, String p1) { return null; }
    public AbstractComparableAssert isStrictlyBetween(Comparable p0, Comparable p1) { return null; }
    public AbstractComparableAssert isLessThanOrEqualTo(Comparable p0) { return null; }
    public FloatingPointNumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractDoubleAssert isInfinite() { return null; }
    public AbstractComparableAssert isBetween(Comparable p0, Comparable p1) { return null; }
    public AbstractDoubleAssert isEqualTo(double p0) { return null; }
    public AbstractDoubleAssert isFinite() { return null; }
    public NumberAssert isBetween(Number p0, Number p1) { return null; }
    public FloatingPointNumberAssert isCloseTo(Number p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractComparableAssert isGreaterThanOrEqualTo(Comparable p0) { return null; }
    public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractDoubleAssert isNotNegative() { return null; }
    public AbstractDoubleAssert isCloseTo(double p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractDoubleAssert isCloseTo(Double p0, org.assertj.core.data.Percentage p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractDurationAssert {
    public AbstractDurationAssert hasNanos(long p0) { return null; }
    public AbstractDurationAssert hasDays(long p0) { return null; }
    public AbstractDurationAssert isPositive() { return null; }
    public AbstractDurationAssert hasHours(long p0) { return null; }
    public AbstractDurationAssert isZero() { return null; }
    public AbstractDurationAssert isCloseTo(java.time.Duration p0, java.time.Duration p1) { return null; }
    public AbstractDurationAssert isNegative() { return null; }
    public AbstractDurationAssert hasSeconds(long p0) { return null; }
    public AbstractDurationAssert hasMinutes(long p0) { return null; }
    public AbstractDurationAssert hasMillis(long p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractEnumerableAssert implements EnumerableAssert {
    public AbstractEnumerableAssert hasSameSizeAs(Object p0) { return null; }
    public AbstractEnumerableAssert inBinary() { return null; }
    public AbstractEnumerableAssert inHexadecimal() { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractFileAssert {
    public AbstractFileAssert isDirectory() { return null; }
    public AbstractFileAssert hasDigest(java.security.MessageDigest p0, String p1) { return null; }
    public AbstractFileAssert hasNoParent() { return null; }
    public AbstractFileAssert isDirectoryContaining(java.util.function.Predicate p0) { return null; }
    public AbstractFileAssert hasDigest(String p0, String p1) { return null; }
    public AbstractFileAssert isEmptyDirectory() { return null; }
    public AbstractFileAssert hasSameTextualContentAs(File p0, java.nio.charset.Charset p1) { return null; }
    public AbstractFileAssert hasParent(File p0) { return null; }
    public AbstractFileAssert usingCharset(String p0) { return null; }
    public AbstractFileAssert hasBinaryContent(byte[] p0) { return null; }
    public AbstractFileAssert hasContentEqualTo(File p0) { return null; }
    public AbstractFileAssert hasDigest(String p0, byte[] p1) { return null; }
    public AbstractFileAssert isDirectoryRecursivelyContaining(String p0) { return null; }
    public AbstractFileAssert isRelative() { return null; }
    public AbstractFileAssert canWrite() { return null; }
    public AbstractFileAssert hasSameContentAs(File p0) { return null; }
    public AbstractFileAssert exists() { return null; }
    public AbstractFileAssert hasSize(long p0) { return null; }
    public AbstractFileAssert hasParent(String p0) { return null; }
    public AbstractFileAssert isDirectoryNotContaining(java.util.function.Predicate p0) { return null; }
    public AbstractFileAssert hasContent(String p0) { return null; }
    public AbstractFileAssert isDirectoryContaining(String p0) { return null; }
    public AbstractFileAssert isAbsolute() { return null; }
    public AbstractFileAssert isEmpty() { return null; }
    public AbstractFileAssert doesNotExist() { return null; }
    public AbstractFileAssert isDirectoryRecursivelyContaining(java.util.function.Predicate p0) { return null; }
    public AbstractFileAssert isDirectoryNotContaining(String p0) { return null; }
    public AbstractFileAssert canRead() { return null; }
    public AbstractFileAssert hasExtension(String p0) { return null; }
    public AbstractFileAssert isFile() { return null; }
    public AbstractFileAssert hasName(String p0) { return null; }
    public AbstractFileAssert hasSameTextualContentAs(File p0) { return null; }
    public AbstractFileAssert hasSameContentAs(File p0, java.nio.charset.Charset p1) { return null; }
    public AbstractFileAssert isNotEmpty() { return null; }
    public AbstractFileAssert usingCharset(java.nio.charset.Charset p0) { return null; }
    public AbstractFileAssert hasSameBinaryContentAs(File p0) { return null; }
    public AbstractFileAssert hasDigest(java.security.MessageDigest p0, byte[] p1) { return null; }
    public AbstractFileAssert isNotEmptyDirectory() { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractFloatArrayAssert {
    public AbstractFloatArrayAssert containsSequence(float[] p0) { return null; }
    public AbstractFloatArrayAssert contains(float p0, org.assertj.core.data.Index p1, org.assertj.core.data.Offset p2) { return null; }
    public AbstractFloatArrayAssert containsSubsequence(float[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractFloatArrayAssert isNotEmpty() { return null; }
    public AbstractFloatArrayAssert endsWith(Float[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractFloatArrayAssert doesNotHaveDuplicates(org.assertj.core.data.Offset p0) { return null; }
    public AbstractFloatArrayAssert hasSizeGreaterThan(int p0) { return null; }
    public AbstractFloatArrayAssert contains(Float[] p0) { return null; }
    public AbstractFloatArrayAssert containsExactly(Float[] p0) { return null; }
    public AbstractFloatArrayAssert hasSize(int p0) { return null; }
    public AbstractFloatArrayAssert hasSizeLessThanOrEqualTo(int p0) { return null; }
    public AbstractFloatArrayAssert hasSizeGreaterThanOrEqualTo(int p0) { return null; }
    public AbstractFloatArrayAssert doesNotContain(Float[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractFloatArrayAssert isSortedAccordingTo(java.util.Comparator p0) { return null; }
    public AbstractFloatArrayAssert hasSizeBetween(int p0, int p1) { return null; }
    public AbstractFloatArrayAssert doesNotContain(Float[] p0) { return null; }
    public AbstractFloatArrayAssert startsWith(float[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractFloatArrayAssert containsOnlyOnce(float[] p0) { return null; }
    public AbstractFloatArrayAssert containsOnlyOnce(Float[] p0) { return null; }
    public AbstractFloatArrayAssert endsWith(float[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractFloatArrayAssert doesNotContain(float[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractFloatArrayAssert containsOnlyOnce(float[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractFloatArrayAssert contains(Float[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractFloatArrayAssert containsSubsequence(Float[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractFloatArrayAssert isSorted() { return null; }
    public AbstractFloatArrayAssert doesNotHaveDuplicates() { return null; }
    public AbstractFloatArrayAssert startsWith(Float[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractFloatArrayAssert startsWith(Float[] p0) { return null; }
    public AbstractFloatArrayAssert startsWith(float[] p0) { return null; }
    public AbstractFloatArrayAssert contains(float p0, org.assertj.core.data.Index p1) { return null; }
    public AbstractFloatArrayAssert usingComparatorWithPrecision(Float p0) { return null; }
    public AbstractFloatArrayAssert hasSameSizeAs(Iterable p0) { return null; }
    public AbstractFloatArrayAssert containsSequence(Float[] p0) { return null; }
    public AbstractFloatArrayAssert endsWith(float[] p0) { return null; }
    public AbstractFloatArrayAssert usingElementComparator(java.util.Comparator p0) { return null; }
    public AbstractFloatArrayAssert doesNotContain(float p0, org.assertj.core.data.Index p1) { return null; }
    public AbstractFloatArrayAssert containsSequence(Float[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractFloatArrayAssert hasSizeLessThan(int p0) { return null; }
    public AbstractFloatArrayAssert containsExactly(float[] p0) { return null; }
    public AbstractFloatArrayAssert doesNotContain(float p0, org.assertj.core.data.Index p1, org.assertj.core.data.Offset p2) { return null; }
    public AbstractFloatArrayAssert contains(float[] p0) { return null; }
    public AbstractFloatArrayAssert containsOnly(float[] p0, org.assertj.core.data.Offset p1) { return null; }
    public void isNullOrEmpty() {}
    public AbstractFloatArrayAssert containsExactly(Float[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractFloatArrayAssert doesNotContain(float[] p0) { return null; }
    public AbstractFloatArrayAssert containsSequence(float[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractFloatArrayAssert containsAnyOf(float[] p0) { return null; }
    public AbstractFloatArrayAssert containsAnyOf(Float[] p0) { return null; }
    public AbstractFloatArrayAssert containsExactly(float[] p0, org.assertj.core.data.Offset p1) { return null; }
    public void isEmpty() {}
    public AbstractFloatArrayAssert containsOnly(Float[] p0) { return null; }
    public AbstractFloatArrayAssert containsOnly(float[] p0) { return null; }
    public AbstractFloatArrayAssert containsSubsequence(Float[] p0) { return null; }
    public AbstractFloatArrayAssert containsSubsequence(float[] p0) { return null; }
    public AbstractFloatArrayAssert containsExactlyInAnyOrder(float[] p0) { return null; }
    public AbstractFloatArrayAssert containsExactlyInAnyOrder(Float[] p0) { return null; }
    public AbstractFloatArrayAssert contains(float[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractFloatArrayAssert endsWith(Float[] p0) { return null; }
    public AbstractFloatArrayAssert usingDefaultElementComparator() { return null; }
    public AbstractFloatArrayAssert containsOnlyOnce(Float[] p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractFloatArrayAssert containsOnly(Float[] p0, org.assertj.core.data.Offset p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractFloatAssert implements FloatingPointNumberAssert {
    public AbstractFloatAssert isNotCloseTo(Float p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractFloatAssert isEqualTo(Float p0, org.assertj.core.data.Offset p1) { return null; }
    public NumberAssert isStrictlyBetween(Number p0, Number p1) { return null; }
    public AbstractFloatAssert isNotNegative() { return null; }
    public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractFloatAssert isZero() { return null; }
    public FloatingPointNumberAssert isEqualTo(Number p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractFloatAssert isCloseTo(Float p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractFloatAssert usingComparator(java.util.Comparator p0, String p1) { return null; }
    public AbstractFloatAssert isLessThan(float p0) { return null; }
    public AbstractFloatAssert isGreaterThan(float p0) { return null; }
    public AbstractFloatAssert isNotZero() { return null; }
    public AbstractFloatAssert isNotPositive() { return null; }
    public AbstractFloatAssert isCloseTo(float p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractFloatAssert isNotEqualTo(float p0) { return null; }
    public AbstractFloatAssert isLessThanOrEqualTo(float p0) { return null; }
    public AbstractFloatAssert isNaN() { return null; }
    public AbstractFloatAssert isPositive() { return null; }
    public AbstractFloatAssert isCloseTo(Float p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractFloatAssert isNotEqualTo(Float p0) { return null; }
    public AbstractFloatAssert isLessThanOrEqualTo(Float p0) { return null; }
    public void AbstractFloatAssert(float p0, Class p1) {}
    public AbstractFloatAssert isNotNaN() { return null; }
    public AbstractFloatAssert isNotCloseTo(Float p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractFloatAssert isInfinite() { return null; }
    public AbstractFloatAssert isOne() { return null; }
    public AbstractFloatAssert isBetween(Float p0, Float p1) { return null; }
    public AbstractFloatAssert usingComparator(java.util.Comparator p0) { return null; }
    public AbstractComparableAssert isStrictlyBetween(Comparable p0, Comparable p1) { return null; }
    public AbstractFloatAssert isNegative() { return null; }
    public AbstractComparableAssert isLessThanOrEqualTo(Comparable p0) { return null; }
    public AbstractFloatAssert isEqualTo(float p0) { return null; }
    public AbstractFloatAssert isStrictlyBetween(Float p0, Float p1) { return null; }
    public FloatingPointNumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractFloatAssert isEqualTo(Float p0) { return null; }
    public AbstractFloatAssert isNotCloseTo(float p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractFloatAssert isFinite() { return null; }
    public AbstractFloatAssert isGreaterThanOrEqualTo(Float p0) { return null; }
    public AbstractComparableAssert isBetween(Comparable p0, Comparable p1) { return null; }
    public AbstractFloatAssert isCloseTo(float p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractFloatAssert isGreaterThanOrEqualTo(float p0) { return null; }
    public NumberAssert isBetween(Number p0, Number p1) { return null; }
    public FloatingPointNumberAssert isCloseTo(Number p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractFloatAssert isEqualTo(float p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractFloatAssert usingDefaultComparator() { return null; }
    public AbstractComparableAssert isGreaterThanOrEqualTo(Comparable p0) { return null; }
    public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractFloatAssert isNotCloseTo(float p0, org.assertj.core.data.Offset p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractInputStreamAssert {
    public AbstractInputStreamAssert hasDigest(String p0, String p1) { return null; }
    public AbstractInputStreamAssert isEmpty() { return null; }
    public AbstractInputStreamAssert hasDigest(java.security.MessageDigest p0, byte[] p1) { return null; }
    public AbstractInputStreamAssert hasSameContentAs(InputStream p0) { return null; }
    public AbstractInputStreamAssert hasContent(String p0) { return null; }
    public AbstractInputStreamAssert hasBinaryContent(byte[] p0) { return null; }
    public AbstractInputStreamAssert isNotEmpty() { return null; }
    public AbstractInputStreamAssert hasDigest(String p0, byte[] p1) { return null; }
    public AbstractInputStreamAssert hasContentEqualTo(InputStream p0) { return null; }
    public AbstractInputStreamAssert hasDigest(java.security.MessageDigest p0, String p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class AbstractInstantAssert {
    public AbstractInstantAssert isAfterOrEqualTo(String p0) { return null; }
    public AbstractInstantAssert isAfter(java.time.Instant p0) { return null; }
    public AbstractInstantAssert isStrictlyBetween(String p0, String p1) { return null; }
    public AbstractInstantAssert isBetween(String p0, String p1) { return null; }
    public AbstractInstantAssert isStrictlyBetween(java.time.Instant p0, java.time.Instant p1) { return null; }
    public AbstractInstantAssert isBefore(String p0) { return null; }
    public AbstractInstantAssert isEqualTo(String p0) { return null; }
    public AbstractInstantAssert isBeforeOrEqualTo(java.time.Instant p0) { return null; }
    public AbstractInstantAssert isNotIn(String[] p0) { return null; }
    public AbstractInstantAssert isNotEqualTo(String p0) { return null; }
    public AbstractInstantAssert isBetween(java.time.Instant p0, java.time.Instant p1) { return null; }
    public AbstractInstantAssert isBeforeOrEqualTo(String p0) { return null; }
    public AbstractInstantAssert isBefore(java.time.Instant p0) { return null; }
    public AbstractInstantAssert isAfter(String p0) { return null; }
    public AbstractInstantAssert isAfterOrEqualTo(java.time.Instant p0) { return null; }
    public AbstractInstantAssert isIn(String[] p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractIntArrayAssert {
    public AbstractIntArrayAssert doesNotContain(int p0, org.assertj.core.data.Index p1) { return null; }
    public AbstractIntArrayAssert containsExactly(int[] p0) { return null; }
    public AbstractIntArrayAssert containsAnyOf(Integer[] p0) { return null; }
    public AbstractIntArrayAssert containsExactlyInAnyOrder(Integer[] p0) { return null; }
    public AbstractIntArrayAssert containsOnlyOnce(Integer[] p0) { return null; }
    public AbstractIntArrayAssert containsSubsequence(Integer[] p0) { return null; }
    public AbstractIntArrayAssert hasSizeGreaterThan(int p0) { return null; }
    public AbstractIntArrayAssert usingElementComparator(java.util.Comparator p0) { return null; }
    public AbstractIntArrayAssert hasSize(int p0) { return null; }
    public AbstractIntArrayAssert hasSizeGreaterThanOrEqualTo(int p0) { return null; }
    public AbstractIntArrayAssert containsExactly(Integer[] p0) { return null; }
    public AbstractIntArrayAssert endsWith(int[] p0) { return null; }
    public void isNullOrEmpty() {}
    public AbstractIntArrayAssert containsOnly(Integer[] p0) { return null; }
    public AbstractIntArrayAssert containsSequence(Integer[] p0) { return null; }
    public AbstractIntArrayAssert isNotEmpty() { return null; }
    public AbstractIntArrayAssert containsAnyOf(int[] p0) { return null; }
    public AbstractIntArrayAssert isSortedAccordingTo(java.util.Comparator p0) { return null; }
    public AbstractIntArrayAssert startsWith(int[] p0) { return null; }
    public AbstractIntArrayAssert containsOnly(int[] p0) { return null; }
    public AbstractIntArrayAssert doesNotHaveDuplicates() { return null; }
    public AbstractIntArrayAssert hasSizeLessThanOrEqualTo(int p0) { return null; }
    public AbstractIntArrayAssert doesNotContain(Integer[] p0) { return null; }
    public void isEmpty() {}
    public AbstractIntArrayAssert hasSizeLessThan(int p0) { return null; }
    public AbstractIntArrayAssert containsSubsequence(int[] p0) { return null; }
    public AbstractIntArrayAssert doesNotContain(int[] p0) { return null; }
    public AbstractIntArrayAssert endsWith(Integer[] p0) { return null; }
    public AbstractIntArrayAssert startsWith(Integer[] p0) { return null; }
    public AbstractIntArrayAssert contains(int[] p0) { return null; }
    public AbstractIntArrayAssert containsSequence(int[] p0) { return null; }
    public AbstractIntArrayAssert isSorted() { return null; }
    public AbstractIntArrayAssert contains(Integer[] p0) { return null; }
    public AbstractIntArrayAssert hasSizeBetween(int p0, int p1) { return null; }
    public AbstractIntArrayAssert contains(int p0, org.assertj.core.data.Index p1) { return null; }
    public AbstractIntArrayAssert hasSameSizeAs(Iterable p0) { return null; }
    public AbstractIntArrayAssert containsOnlyOnce(int[] p0) { return null; }
    public AbstractIntArrayAssert usingDefaultElementComparator() { return null; }
    public AbstractIntArrayAssert containsExactlyInAnyOrder(int[] p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractIntegerAssert implements NumberAssert {
    public AbstractIntegerAssert isStrictlyBetween(Integer p0, Integer p1) { return null; }
    public NumberAssert isStrictlyBetween(Number p0, Number p1) { return null; }
    public AbstractIntegerAssert isOne() { return null; }
    public AbstractIntegerAssert isCloseTo(int p0, org.assertj.core.data.Offset p1) { return null; }
    public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractIntegerAssert isGreaterThan(int p0) { return null; }
    public AbstractIntegerAssert isGreaterThanOrEqualTo(int p0) { return null; }
    public AbstractIntegerAssert isNotNegative() { return null; }
    public AbstractIntegerAssert isLessThan(int p0) { return null; }
    public AbstractIntegerAssert usingDefaultComparator() { return null; }
    public AbstractIntegerAssert isEqualTo(long p0) { return null; }
    public AbstractIntegerAssert isLessThanOrEqualTo(int p0) { return null; }
    public AbstractIntegerAssert isZero() { return null; }
    public AbstractIntegerAssert isBetween(Integer p0, Integer p1) { return null; }
    public AbstractIntegerAssert isEven() { return null; }
    public AbstractIntegerAssert usingComparator(java.util.Comparator p0) { return null; }
    public AbstractIntegerAssert isCloseTo(int p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractIntegerAssert isNotPositive() { return null; }
    public AbstractIntegerAssert isCloseTo(Integer p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractIntegerAssert isPositive() { return null; }
    public AbstractIntegerAssert isNotCloseTo(Integer p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractIntegerAssert isNotCloseTo(int p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractIntegerAssert isCloseTo(Integer p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractIntegerAssert isNotEqualTo(int p0) { return null; }
    public AbstractIntegerAssert usingComparator(java.util.Comparator p0, String p1) { return null; }
    public AbstractIntegerAssert isNegative() { return null; }
    public AbstractComparableAssert isStrictlyBetween(Comparable p0, Comparable p1) { return null; }
    public AbstractIntegerAssert isNotZero() { return null; }
    public AbstractIntegerAssert isEqualTo(int p0) { return null; }
    public AbstractIntegerAssert isNotCloseTo(Integer p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractIntegerAssert isNotCloseTo(int p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractComparableAssert isBetween(Comparable p0, Comparable p1) { return null; }
    public AbstractIntegerAssert isOdd() { return null; }
    public NumberAssert isBetween(Number p0, Number p1) { return null; }
    public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return null; }
    public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Offset p1) { return null; }
    public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Offset p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractIterableAssert implements ObjectEnumerableAssert {
    public AbstractIterableAssert doesNotHave(Condition p0) { return null; }
    public AbstractIterableAssert isNotOfAnyClassIn(Class[] p0) { return null; }
    public AbstractIterableAssert isNotExactlyInstanceOf(Class p0) { return null; }
    public AbstractIterableAssert areAtLeastOne(Condition p0) { return null; }
    public AbstractIterableAssert noneSatisfy(java.util.function.Consumer p0) { return null; }
    public AbstractIterableAssert areNot(Condition p0) { return null; }
    public AbstractIterableAssert containsOnlyNulls() { return null; }
    public AbstractIterableAssert containsSequence(Iterable p0) { return null; }
    public AbstractIterableAssert satisfiesExactly(java.util.function.Consumer[] p0) { return null; }
    public AbstractIterableAssert isNotNull() { return null; }
    public AbstractListAssert flatMap(java.util.function.Function[] p0) { return null; }
    public AbstractIterableAssert usingDefaultComparator() { return null; }
    public AbstractIterableAssert areExactly(int p0, Condition p1) { return null; }
    public AbstractIterableAssert doesNotHaveDuplicates() { return null; }
    public AbstractIterableAssert contains(Object[] p0) { return null; }
    public AbstractIterableAssert hasSameClassAs(Object p0) { return null; }
    public AbstractIterableAssert endsWith(Object p0, Object[] p1) { return null; }
    public AbstractListAssert extractingResultOf(String p0, Class p1) { return null; }
    public AbstractIterableAssert filteredOnAssertions(java.util.function.Consumer p0) { return null; }
    public AbstractIterableAssert describedAs(org.assertj.core.description.Description p0) { return null; }
    public AbstractIterableAssert describedAs(String p0, Object[] p1) { return null; }
    public AbstractIterableAssert withThreadDumpOnError() { return null; }
    public AbstractIterableAssert usingComparatorForElementFieldsWithType(java.util.Comparator p0, Class p1) { return null; }
    public AbstractListAssert map(java.util.function.Function[] p0) { return null; }
    public AbstractIterableAssert isSubsetOf(Object[] p0) { return null; }
    public AbstractListAssert extracting(java.util.function.Function[] p0) { return null; }
    public AbstractIterableAssert filteredOn(java.util.function.Function p0, Object p1) { return null; }
    public AbstractIterableAssert endsWith(Object[] p0) { return null; }
    public AbstractIterableAssert allMatch(java.util.function.Predicate p0, String p1) { return null; }
    public AbstractIterableAssert usingFieldByFieldElementComparator() { return null; }
    public AbstractAssert last() { return null; }
    public AbstractIterableAssert anyMatch(java.util.function.Predicate p0) { return null; }
    public AbstractIterableAssert filteredOn(String p0, org.assertj.core.api.filter.FilterOperator p1) { return null; }
    public AbstractIterableAssert doesNotHaveSameClassAs(Object p0) { return null; }
    public AbstractListAssert extracting(java.util.function.Function p0) { return null; }
    public AbstractAssert singleElement() { return null; }
    public AbstractIterableAssert usingElementComparator(java.util.Comparator p0) { return null; }
    public AbstractAssert singleElement(InstanceOfAssertFactory p0) { return null; }
    public AbstractIterableAssert areAtMost(int p0, Condition p1) { return null; }
    public AbstractListAssert flatMap(java.util.function.Function p0) { return null; }
    public AbstractListAssert flatExtracting(org.assertj.core.api.iterable.ThrowingExtractor[] p0) { return null; }
    public AbstractListAssert flatExtracting(String p0) { return null; }
    public AbstractIterableAssert usingElementComparatorOnFields(String[] p0) { return null; }
    public AbstractIterableAssert startsWith(Object[] p0) { return null; }
    public AbstractIterableAssert hasSameElementsAs(Iterable p0) { return null; }
    public AbstractListAssert flatExtracting(org.assertj.core.api.iterable.ThrowingExtractor p0) { return null; }
    public AbstractIterableAssert isExactlyInstanceOf(Class p0) { return null; }
    public AbstractIterableAssert satisfiesExactlyInAnyOrder(java.util.function.Consumer[] p0) { return null; }
    public AbstractIterableAssert are(Condition p0) { return null; }
    public AbstractIterableAssert noneMatch(java.util.function.Predicate p0) { return null; }
    public AbstractIterableAssert containsSequence(Object[] p0) { return null; }
    public AbstractIterableAssert isIn(Object[] p0) { return null; }
    public AbstractListAssert extracting(String p0) { return null; }
    public AbstractIterableAssert isNotInstanceOfAny(Class[] p0) { return null; }
    public AbstractIterableAssert filteredOnNull(String p0) { return null; }
    public AbstractIterableAssert usingComparatorForElementFieldsWithNames(java.util.Comparator p0, String[] p1) { return null; }
    public AbstractIterableAssert allSatisfy(java.util.function.Consumer p0) { return null; }
    public AbstractIterableAssert isSameAs(Object p0) { return null; }
    public AbstractIterableAssert haveAtLeastOne(Condition p0) { return null; }
    public AbstractIterableAssert filteredOn(String p0, Object p1) { return null; }
    public AbstractListAssert extractingResultOf(String p0) { return null; }
    public AbstractListAssert flatExtracting(java.util.function.Function[] p0) { return null; }
    public AbstractIterableAssert doesNotContainSubsequence(Iterable p0) { return null; }
    public AbstractIterableAssert containsOnlyOnceElementsOf(Iterable p0) { return null; }
    public AbstractIterableAssert hasToString(String p0) { return null; }
    public AbstractIterableAssert containsOnly(Object[] p0) { return null; }
    public AbstractIterableAssert doesNotHaveAnyElementsOfTypes(Class[] p0) { return null; }
    public AbstractIterableAssert inBinary() { return null; }
    public AbstractIterableAssert filteredOn(java.util.function.Predicate p0) { return null; }
    public AbstractListAssert flatMap(org.assertj.core.api.iterable.ThrowingExtractor[] p0) { return null; }
    public AbstractIterableAssert containsOnlyElementsOf(Iterable p0) { return null; }
    public AbstractIterableAssert usingComparatorForType(java.util.Comparator p0, Class p1) { return null; }
    public AbstractIterableAssert usingDefaultElementComparator() { return null; }
    public AbstractIterableAssert containsAnyElementsOf(Iterable p0) { return null; }
    public AbstractIterableAssert containsNull() { return null; }
    public AbstractAssert last(InstanceOfAssertFactory p0) { return null; }
    public AbstractIterableAssert doesNotContain(Object[] p0) { return null; }
    public AbstractIterableAssert as(String p0, Object[] p1) { return null; }
    public AbstractIterableAssert containsAll(Iterable p0) { return null; }
    public AbstractListAssert extracting(String[] p0) { return null; }
    public AbstractIterableSizeAssert size() { return null; }
    public AbstractIterableAssert hasSize(int p0) { return null; }
    public AbstractIterableAssert containsExactlyInAnyOrderElementsOf(Iterable p0) { return null; }
    public AbstractIterableAssert isNot(Condition p0) { return null; }
    public AbstractIterableAssert usingComparator(java.util.Comparator p0, String p1) { return null; }
    public AbstractIterableAssert isInstanceOfAny(Class[] p0) { return null; }
    public AbstractIterableAssert isOfAnyClassIn(Class[] p0) { return null; }
    public AbstractIterableAssert hasSizeBetween(int p0, int p1) { return null; }
    public AbstractIterableAssert filteredOn(Condition p0) { return null; }
    public AbstractIterableAssert anySatisfy(java.util.function.Consumer p0) { return null; }
    public AbstractIterableAssert as(org.assertj.core.description.Description p0) { return null; }
    public AbstractIterableAssert haveExactly(int p0, Condition p1) { return null; }
    public RecursiveComparisonAssert usingRecursiveComparison() { return null; }
    public AbstractIterableAssert isNotIn(Object[] p0) { return null; }
    public AbstractAssert first(InstanceOfAssertFactory p0) { return null; }
    public AbstractIterableAssert containsSubsequence(Object[] p0) { return null; }
    public AbstractIterableAssert have(Condition p0) { return null; }
    public AbstractIterableAssert hasSameSizeAs(Iterable p0) { return null; }
    public AbstractIterableAssert haveAtMost(int p0, Condition p1) { return null; }
    public AbstractIterableAssert containsExactly(Object[] p0) { return null; }
    public AbstractIterableAssert usingRecursiveFieldByFieldElementComparator(org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration p0) { return null; }
    public AbstractIterableAssert containsOnlyOnce(Object[] p0) { return null; }
    public AbstractIterableAssert doesNotContainSequence(Object[] p0) { return null; }
    public AbstractIterableAssert hasOnlyElementsOfTypes(Class[] p0) { return null; }
    public AbstractIterableAssert isIn(Iterable p0) { return null; }
    public AbstractIterableAssert isNotIn(Iterable p0) { return null; }
    public AbstractIterableAssert hasAtLeastOneElementOfType(Class p0) { return null; }
    public AbstractListAssert flatExtracting(String[] p0) { return null; }
    public AbstractIterableAssert hasOnlyOneElementSatisfying(java.util.function.Consumer p0) { return null; }
    public AbstractIterableAssert has(Condition p0) { return null; }
    public AbstractIterableAssert withFailMessage(String p0, Object[] p1) { return null; }
    public AbstractListAssert map(org.assertj.core.api.iterable.ThrowingExtractor p0) { return null; }
    public AbstractIterableAssert isNotInstanceOf(Class p0) { return null; }
    public AbstractIterableAssert overridingErrorMessage(String p0, Object[] p1) { return null; }
    public AbstractIterableAssert is(Condition p0) { return null; }
    public AbstractIterableAssert doesNotContainSubsequence(Object[] p0) { return null; }
    public AbstractAssert first() { return null; }
    public AbstractIterableAssert containsAnyOf(Object[] p0) { return null; }
    public AbstractListAssert flatMap(org.assertj.core.api.iterable.ThrowingExtractor p0) { return null; }
    public AbstractIterableAssert isNotEqualTo(Object p0) { return null; }
    public AbstractIterableAssert hasSizeLessThan(int p0) { return null; }
    public AbstractListAssert extracting(org.assertj.core.api.iterable.ThrowingExtractor p0) { return null; }
    public AbstractIterableAssert containsExactlyInAnyOrder(Object[] p0) { return null; }
    public AbstractIterableAssert hasOnlyElementsOfType(Class p0) { return null; }
    public AbstractIterableAssert allMatch(java.util.function.Predicate p0) { return null; }
    public AbstractAssert element(int p0) { return null; }
    public void isNullOrEmpty() {}
    public AbstractIterableAssert hasSizeGreaterThan(int p0) { return null; }
    public AbstractIterableAssert isEqualTo(Object p0) { return null; }
    public AbstractIterableAssert isNotSameAs(Object p0) { return null; }
    public AbstractIterableAssert doesNotContainSequence(Iterable p0) { return null; }
    public AbstractIterableAssert usingElementComparatorIgnoringFields(String[] p0) { return null; }
    public RecursiveComparisonAssert usingRecursiveComparison(org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration p0) { return null; }
    public AbstractIterableAssert usingRecursiveFieldByFieldElementComparator() { return null; }
    public AbstractIterableAssert usingComparator(java.util.Comparator p0) { return null; }
    public AbstractListAssert extracting(String p0, Class p1) { return null; }
    public AbstractIterableAssert hasSizeGreaterThanOrEqualTo(int p0) { return null; }
    public AbstractIterableAssert isNotEmpty() { return null; }
    public void isEmpty() {}
    public AbstractListAssert map(java.util.function.Function p0) { return null; }
    public AbstractIterableAssert isInstanceOf(Class p0) { return null; }
    public AbstractIterableAssert containsSubsequence(Iterable p0) { return null; }
    public AbstractIterableAssert doesNotContainAnyElementsOf(Iterable p0) { return null; }
    public AbstractIterableAssert isSubsetOf(Iterable p0) { return null; }
    public AbstractIterableAssert doNotHave(Condition p0) { return null; }
    public AbstractIterableAssert containsExactlyElementsOf(Iterable p0) { return null; }
    public AbstractIterableAssert hasSizeLessThanOrEqualTo(int p0) { return null; }
    public AbstractIterableAssert zipSatisfy(Iterable p0, java.util.function.BiConsumer p1) { return null; }
    public AbstractListAssert flatExtracting(java.util.function.Function p0) { return null; }
    public AbstractIterableAssert haveAtLeast(int p0, Condition p1) { return null; }
    public AbstractIterableAssert inHexadecimal() { return null; }
    public AbstractIterableAssert hasSameSizeAs(Object p0) { return null; }
    public AbstractIterableAssert doesNotContainNull() { return null; }
    public AbstractIterableAssert areAtLeast(int p0, Condition p1) { return null; }
    public AbstractAssert element(int p0, InstanceOfAssertFactory p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractIterableSizeAssert {
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractListAssert implements IndexedObjectEnumerableAssert {
    public AbstractListAssert doesNotHave(Condition p0) { return null; }
    public AbstractListAssert isSortedAccordingTo(java.util.Comparator p0) { return null; }
    public AbstractListAssert isNotNull() { return null; }
    public AbstractListAssert as(org.assertj.core.description.Description p0) { return null; }
    public AbstractListAssert isNotOfAnyClassIn(Class[] p0) { return null; }
    public AbstractListAssert doesNotContain(Object p0, org.assertj.core.data.Index p1) { return null; }
    public AbstractListAssert isIn(Iterable p0) { return null; }
    public AbstractListAssert isInstanceOfAny(Class[] p0) { return null; }
    public AbstractListAssert isExactlyInstanceOf(Class p0) { return null; }
    public AbstractListAssert doesNotHaveSameClassAs(Object p0) { return null; }
    public AbstractListAssert isNotInstanceOf(Class p0) { return null; }
    public AbstractListAssert usingElementComparator(java.util.Comparator p0) { return null; }
    public AbstractListAssert isInstanceOf(Class p0) { return null; }
    public AbstractListAssert contains(Object p0, org.assertj.core.data.Index p1) { return null; }
    public AbstractListAssert hasSameClassAs(Object p0) { return null; }
    public AbstractListAssert isNotIn(Iterable p0) { return null; }
    public AbstractListAssert has(Condition p0, org.assertj.core.data.Index p1) { return null; }
    public AbstractListAssert describedAs(String p0, Object[] p1) { return null; }
    public AbstractListAssert as(String p0, Object[] p1) { return null; }
    public AbstractListAssert isNot(Condition p0) { return null; }
    public AbstractListAssert isSameAs(Object p0) { return null; }
    public AbstractListAssert isNotExactlyInstanceOf(Class p0) { return null; }
    public AbstractListAssert describedAs(org.assertj.core.description.Description p0) { return null; }
    public AbstractListAssert isIn(Object[] p0) { return null; }
    public AbstractListAssert usingComparator(java.util.Comparator p0) { return null; }
    public AbstractListAssert overridingErrorMessage(String p0, Object[] p1) { return null; }
    public AbstractListAssert isSorted() { return null; }
    public AbstractListAssert isEqualTo(Object p0) { return null; }
    public AbstractListAssert hasToString(String p0) { return null; }
    public AbstractListAssert satisfies(java.util.function.Consumer p0, org.assertj.core.data.Index p1) { return null; }
    public AbstractListAssert has(Condition p0) { return null; }
    public AbstractListAssert isNotIn(Object[] p0) { return null; }
    public AbstractListAssert withFailMessage(String p0, Object[] p1) { return null; }
    public AbstractListAssert isNotSameAs(Object p0) { return null; }
    public AbstractListAssert is(Condition p0) { return null; }
    public AbstractListAssert withThreadDumpOnError() { return null; }
    public AbstractListAssert isNotEqualTo(Object p0) { return null; }
    public AbstractListAssert usingDefaultComparator() { return null; }
    public AbstractListAssert isNotInstanceOfAny(Class[] p0) { return null; }
    public AbstractListAssert isOfAnyClassIn(Class[] p0) { return null; }
    public AbstractListAssert is(Condition p0, org.assertj.core.data.Index p1) { return null; }
    public AbstractListAssert usingDefaultElementComparator() { return null; }
    public AbstractListAssert usingComparator(java.util.Comparator p0, String p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractLocalDateAssert {
    public AbstractLocalDateAssert isToday() { return null; }
    public AbstractLocalDateAssert isAfterOrEqualTo(String p0) { return null; }
    public AbstractLocalDateAssert isBeforeOrEqualTo(java.time.LocalDate p0) { return null; }
    public AbstractLocalDateAssert isBefore(java.time.LocalDate p0) { return null; }
    public AbstractLocalDateAssert isStrictlyBetween(String p0, String p1) { return null; }
    public AbstractLocalDateAssert isAfter(java.time.LocalDate p0) { return null; }
    public AbstractLocalDateAssert isEqualTo(String p0) { return null; }
    public AbstractLocalDateAssert isIn(String[] p0) { return null; }
    public AbstractLocalDateAssert isNotIn(String[] p0) { return null; }
    public AbstractLocalDateAssert isBefore(String p0) { return null; }
    public AbstractLocalDateAssert isStrictlyBetween(java.time.LocalDate p0, java.time.LocalDate p1) { return null; }
    public AbstractLocalDateAssert isBeforeOrEqualTo(String p0) { return null; }
    public AbstractLocalDateAssert isAfterOrEqualTo(java.time.LocalDate p0) { return null; }
    public AbstractLocalDateAssert isBetween(java.time.LocalDate p0, java.time.LocalDate p1) { return null; }
    public AbstractLocalDateAssert isAfter(String p0) { return null; }
    public AbstractLocalDateAssert isNotEqualTo(String p0) { return null; }
    public AbstractLocalDateAssert isBetween(String p0, String p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractLocalDateTimeAssert {
    public AbstractLocalDateTimeAssert isNotIn(String[] p0) { return null; }
    public AbstractLocalDateTimeAssert isBetween(String p0, String p1) { return null; }
    public AbstractLocalDateTimeAssert isBeforeOrEqualTo(String p0) { return null; }
    public AbstractLocalDateTimeAssert isEqualTo(String p0) { return null; }
    public AbstractLocalDateTimeAssert isEqualToIgnoringHours(java.time.LocalDateTime p0) { return null; }
    public AbstractLocalDateTimeAssert isBefore(java.time.LocalDateTime p0) { return null; }
    public AbstractLocalDateTimeAssert isCloseToUtcNow(org.assertj.core.data.TemporalUnitOffset p0) { return null; }
    public AbstractLocalDateTimeAssert isAfter(java.time.LocalDateTime p0) { return null; }
    public AbstractLocalDateTimeAssert isAfter(String p0) { return null; }
    public AbstractLocalDateTimeAssert isBefore(String p0) { return null; }
    public AbstractLocalDateTimeAssert isBeforeOrEqualTo(java.time.LocalDateTime p0) { return null; }
    public AbstractLocalDateTimeAssert isStrictlyBetween(String p0, String p1) { return null; }
    public AbstractLocalDateTimeAssert isAfterOrEqualTo(java.time.LocalDateTime p0) { return null; }
    public AbstractLocalDateTimeAssert isEqualToIgnoringSeconds(java.time.LocalDateTime p0) { return null; }
    public AbstractLocalDateTimeAssert isStrictlyBetween(java.time.LocalDateTime p0, java.time.LocalDateTime p1) { return null; }
    public AbstractLocalDateTimeAssert isAfterOrEqualTo(String p0) { return null; }
    public AbstractLocalDateTimeAssert isNotEqualTo(String p0) { return null; }
    public AbstractLocalDateTimeAssert usingDefaultComparator() { return null; }
    public AbstractLocalDateTimeAssert isEqualToIgnoringNanos(java.time.LocalDateTime p0) { return null; }
    public AbstractLocalDateTimeAssert isIn(String[] p0) { return null; }
    public AbstractLocalDateTimeAssert isBetween(java.time.LocalDateTime p0, java.time.LocalDateTime p1) { return null; }
    public AbstractLocalDateTimeAssert isEqualTo(Object p0) { return null; }
    public AbstractLocalDateTimeAssert isNotEqualTo(Object p0) { return null; }
    public AbstractLocalDateTimeAssert isEqualToIgnoringMinutes(java.time.LocalDateTime p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractLocalTimeAssert {
    public AbstractLocalTimeAssert isAfterOrEqualTo(java.time.LocalTime p0) { return null; }
    public AbstractLocalTimeAssert isBetween(String p0, String p1) { return null; }
    public AbstractLocalTimeAssert isStrictlyBetween(String p0, String p1) { return null; }
    public AbstractLocalTimeAssert isIn(String[] p0) { return null; }
    public AbstractLocalTimeAssert isBefore(String p0) { return null; }
    public AbstractLocalTimeAssert hasSameHourAs(java.time.LocalTime p0) { return null; }
    public AbstractLocalTimeAssert isBefore(java.time.LocalTime p0) { return null; }
    public AbstractLocalTimeAssert isBeforeOrEqualTo(java.time.LocalTime p0) { return null; }
    public AbstractLocalTimeAssert isNotIn(String[] p0) { return null; }
    public AbstractLocalTimeAssert isEqualToIgnoringSeconds(java.time.LocalTime p0) { return null; }
    public AbstractLocalTimeAssert isBeforeOrEqualTo(String p0) { return null; }
    public AbstractLocalTimeAssert isBetween(java.time.LocalTime p0, java.time.LocalTime p1) { return null; }
    public AbstractLocalTimeAssert isAfter(String p0) { return null; }
    public AbstractLocalTimeAssert isAfterOrEqualTo(String p0) { return null; }
    public AbstractLocalTimeAssert isEqualTo(String p0) { return null; }
    public AbstractLocalTimeAssert isStrictlyBetween(java.time.LocalTime p0, java.time.LocalTime p1) { return null; }
    public AbstractLocalTimeAssert isNotEqualTo(String p0) { return null; }
    public AbstractLocalTimeAssert isAfter(java.time.LocalTime p0) { return null; }
    public AbstractLocalTimeAssert isEqualToIgnoringNanos(java.time.LocalTime p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractLongArrayAssert {
    public AbstractLongArrayAssert hasSizeLessThan(int p0) { return null; }
    public AbstractLongArrayAssert containsExactly(long[] p0) { return null; }
    public AbstractLongArrayAssert containsExactly(Long[] p0) { return null; }
    public AbstractLongArrayAssert endsWith(long[] p0) { return null; }
    public AbstractLongArrayAssert containsSequence(Long[] p0) { return null; }
    public AbstractLongArrayAssert containsSubsequence(long[] p0) { return null; }
    public AbstractLongArrayAssert doesNotHaveDuplicates() { return null; }
    public AbstractLongArrayAssert containsSequence(long[] p0) { return null; }
    public AbstractLongArrayAssert containsSubsequence(Long[] p0) { return null; }
    public AbstractLongArrayAssert containsAnyOf(Long[] p0) { return null; }
    public AbstractLongArrayAssert hasSizeGreaterThan(int p0) { return null; }
    public AbstractLongArrayAssert endsWith(Long[] p0) { return null; }
    public AbstractLongArrayAssert isSorted() { return null; }
    public void isNullOrEmpty() {}
    public AbstractLongArrayAssert containsAnyOf(long[] p0) { return null; }
    public AbstractLongArrayAssert hasSizeBetween(int p0, int p1) { return null; }
    public AbstractLongArrayAssert isNotEmpty() { return null; }
    public AbstractLongArrayAssert containsOnlyOnce(Long[] p0) { return null; }
    public AbstractLongArrayAssert hasSizeGreaterThanOrEqualTo(int p0) { return null; }
    public AbstractLongArrayAssert containsOnlyOnce(long[] p0) { return null; }
    public AbstractLongArrayAssert containsOnly(Long[] p0) { return null; }
    public AbstractLongArrayAssert contains(long p0, org.assertj.core.data.Index p1) { return null; }
    public AbstractLongArrayAssert hasSameSizeAs(Iterable p0) { return null; }
    public AbstractLongArrayAssert containsOnly(long[] p0) { return null; }
    public AbstractLongArrayAssert doesNotContain(long p0, org.assertj.core.data.Index p1) { return null; }
    public AbstractLongArrayAssert usingDefaultElementComparator() { return null; }
    public void isEmpty() {}
    public AbstractLongArrayAssert doesNotContain(long[] p0) { return null; }
    public AbstractLongArrayAssert startsWith(Long[] p0) { return null; }
    public AbstractLongArrayAssert doesNotContain(Long[] p0) { return null; }
    public AbstractLongArrayAssert isSortedAccordingTo(java.util.Comparator p0) { return null; }
    public AbstractLongArrayAssert contains(long[] p0) { return null; }
    public AbstractLongArrayAssert containsExactlyInAnyOrder(long[] p0) { return null; }
    public AbstractLongArrayAssert startsWith(long[] p0) { return null; }
    public AbstractLongArrayAssert containsExactlyInAnyOrder(Long[] p0) { return null; }
    public AbstractLongArrayAssert hasSize(int p0) { return null; }
    public AbstractLongArrayAssert hasSizeLessThanOrEqualTo(int p0) { return null; }
    public AbstractLongArrayAssert contains(Long[] p0) { return null; }
    public AbstractLongArrayAssert usingElementComparator(java.util.Comparator p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractLongAssert implements NumberAssert {
    public AbstractLongAssert usingDefaultComparator() { return null; }
    public NumberAssert isStrictlyBetween(Number p0, Number p1) { return null; }
    public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractLongAssert isNotNegative() { return null; }
    public AbstractLongAssert isNotCloseTo(Long p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractLongAssert isCloseTo(long p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractLongAssert isCloseTo(Long p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractLongAssert isNegative() { return null; }
    public AbstractLongAssert isGreaterThanOrEqualTo(long p0) { return null; }
    public AbstractLongAssert usingComparator(java.util.Comparator p0) { return null; }
    public AbstractLongAssert isNotEqualTo(long p0) { return null; }
    public AbstractLongAssert isEqualTo(long p0) { return null; }
    public AbstractLongAssert isBetween(Long p0, Long p1) { return null; }
    public AbstractLongAssert isNotCloseTo(long p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractLongAssert isGreaterThan(long p0) { return null; }
    public AbstractLongAssert isCloseTo(long p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractLongAssert isOne() { return null; }
    public AbstractLongAssert isOdd() { return null; }
    public AbstractLongAssert isStrictlyBetween(Long p0, Long p1) { return null; }
    public AbstractComparableAssert isStrictlyBetween(Comparable p0, Comparable p1) { return null; }
    public AbstractLongAssert isLessThan(long p0) { return null; }
    public AbstractLongAssert usingComparator(java.util.Comparator p0, String p1) { return null; }
    public AbstractLongAssert isNotCloseTo(long p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractLongAssert isNotPositive() { return null; }
    public AbstractLongAssert isNotCloseTo(Long p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractLongAssert isCloseTo(Long p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractComparableAssert isBetween(Comparable p0, Comparable p1) { return null; }
    public AbstractLongAssert isZero() { return null; }
    public NumberAssert isBetween(Number p0, Number p1) { return null; }
    public AbstractLongAssert isNotZero() { return null; }
    public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return null; }
    public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractLongAssert isPositive() { return null; }
    public AbstractLongAssert isLessThanOrEqualTo(long p0) { return null; }
    public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractLongAssert isEven() { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractOffsetDateTimeAssert {
    public AbstractOffsetDateTimeAssert isEqualToIgnoringMinutes(java.time.OffsetDateTime p0) { return null; }
    public AbstractOffsetDateTimeAssert isBefore(java.time.OffsetDateTime p0) { return null; }
    public AbstractOffsetDateTimeAssert isIn(String[] p0) { return null; }
    public AbstractOffsetDateTimeAssert isBetween(String p0, String p1) { return null; }
    public AbstractOffsetDateTimeAssert isCloseToUtcNow(org.assertj.core.data.TemporalUnitOffset p0) { return null; }
    public AbstractOffsetDateTimeAssert isBefore(String p0) { return null; }
    public AbstractOffsetDateTimeAssert isStrictlyBetween(String p0, String p1) { return null; }
    public AbstractOffsetDateTimeAssert isAfter(java.time.OffsetDateTime p0) { return null; }
    public AbstractOffsetDateTimeAssert usingDefaultComparator() { return null; }
    public AbstractOffsetDateTimeAssert isBeforeOrEqualTo(java.time.OffsetDateTime p0) { return null; }
    public AbstractOffsetDateTimeAssert isAtSameInstantAs(java.time.OffsetDateTime p0) { return null; }
    public AbstractOffsetDateTimeAssert isBeforeOrEqualTo(String p0) { return null; }
    public AbstractOffsetDateTimeAssert isNotEqualTo(String p0) { return null; }
    public AbstractOffsetDateTimeAssert isEqualTo(String p0) { return null; }
    public AbstractOffsetDateTimeAssert isStrictlyBetween(java.time.OffsetDateTime p0, java.time.OffsetDateTime p1) { return null; }
    public AbstractOffsetDateTimeAssert isEqualToIgnoringTimezone(java.time.OffsetDateTime p0) { return null; }
    public AbstractOffsetDateTimeAssert isAfter(String p0) { return null; }
    public AbstractOffsetDateTimeAssert isEqualToIgnoringNanos(java.time.OffsetDateTime p0) { return null; }
    public AbstractOffsetDateTimeAssert isAfterOrEqualTo(java.time.OffsetDateTime p0) { return null; }
    public AbstractOffsetDateTimeAssert isAfterOrEqualTo(String p0) { return null; }
    public AbstractOffsetDateTimeAssert isEqualToIgnoringSeconds(java.time.OffsetDateTime p0) { return null; }
    public AbstractOffsetDateTimeAssert isNotIn(String[] p0) { return null; }
    public AbstractOffsetDateTimeAssert isNotEqualTo(Object p0) { return null; }
    public AbstractOffsetDateTimeAssert isEqualTo(Object p0) { return null; }
    public AbstractOffsetDateTimeAssert isEqualToIgnoringHours(java.time.OffsetDateTime p0) { return null; }
    public AbstractOffsetDateTimeAssert isBetween(java.time.OffsetDateTime p0, java.time.OffsetDateTime p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractOffsetTimeAssert {
    public AbstractOffsetTimeAssert isEqualToIgnoringSeconds(java.time.OffsetTime p0) { return null; }
    public AbstractOffsetTimeAssert isAfter(String p0) { return null; }
    public AbstractOffsetTimeAssert isEqualToIgnoringNanos(java.time.OffsetTime p0) { return null; }
    public AbstractOffsetTimeAssert isNotIn(String[] p0) { return null; }
    public AbstractOffsetTimeAssert isEqualToIgnoringTimezone(java.time.OffsetTime p0) { return null; }
    public AbstractOffsetTimeAssert isBefore(java.time.OffsetTime p0) { return null; }
    public AbstractOffsetTimeAssert isBeforeOrEqualTo(String p0) { return null; }
    public AbstractOffsetTimeAssert isEqualTo(String p0) { return null; }
    public AbstractOffsetTimeAssert isAfterOrEqualTo(java.time.OffsetTime p0) { return null; }
    public AbstractOffsetTimeAssert isBetween(java.time.OffsetTime p0, java.time.OffsetTime p1) { return null; }
    public AbstractOffsetTimeAssert hasSameHourAs(java.time.OffsetTime p0) { return null; }
    public AbstractOffsetTimeAssert isStrictlyBetween(java.time.OffsetTime p0, java.time.OffsetTime p1) { return null; }
    public AbstractOffsetTimeAssert isAfter(java.time.OffsetTime p0) { return null; }
    public AbstractOffsetTimeAssert isIn(String[] p0) { return null; }
    public AbstractOffsetTimeAssert isStrictlyBetween(String p0, String p1) { return null; }
    public AbstractOffsetTimeAssert isAfterOrEqualTo(String p0) { return null; }
    public AbstractOffsetTimeAssert isBetween(String p0, String p1) { return null; }
    public AbstractOffsetTimeAssert isBeforeOrEqualTo(java.time.OffsetTime p0) { return null; }
    public AbstractOffsetTimeAssert isBefore(String p0) { return null; }
    public AbstractOffsetTimeAssert isNotEqualTo(String p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractPathAssert {
    public AbstractPathAssert hasDigest(String p0, String p1) { return null; }
    public AbstractPathAssert hasSameContentAs(java.nio.file.Path p0) { return null; }
    public AbstractPathAssert hasSameContentAs(java.nio.file.Path p0, java.nio.charset.Charset p1) { return null; }
    public AbstractPathAssert isExecutable() { return null; }
    public AbstractPathAssert hasNoParentRaw() { return null; }
    public AbstractPathAssert isSymbolicLink() { return null; }
    public AbstractPathAssert isNotEmptyDirectory() { return null; }
    public AbstractPathAssert isRegularFile() { return null; }
    public AbstractPathAssert endsWithRaw(java.nio.file.Path p0) { return null; }
    public AbstractPathAssert usingCharset(java.nio.charset.Charset p0) { return null; }
    public AbstractPathAssert hasContent(String p0) { return null; }
    public AbstractPathAssert startsWith(java.nio.file.Path p0) { return null; }
    public AbstractPathAssert endsWith(java.nio.file.Path p0) { return null; }
    public AbstractPathAssert isDirectoryContaining(java.util.function.Predicate p0) { return null; }
    public AbstractPathAssert isNotEmptyFile() { return null; }
    public AbstractPathAssert hasBinaryContent(byte[] p0) { return null; }
    public AbstractPathAssert hasDigest(java.security.MessageDigest p0, byte[] p1) { return null; }
    public AbstractPathAssert startsWithRaw(java.nio.file.Path p0) { return null; }
    public AbstractPathAssert hasParentRaw(java.nio.file.Path p0) { return null; }
    public AbstractPathAssert isNormalized() { return null; }
    public AbstractPathAssert isCanonical() { return null; }
    public AbstractPathAssert isAbsolute() { return null; }
    public AbstractPathAssert isEmptyDirectory() { return null; }
    public AbstractPathAssert doesNotExist() { return null; }
    public AbstractPathAssert usingCharset(String p0) { return null; }
    public AbstractPathAssert isReadable() { return null; }
    public AbstractPathAssert hasSameTextualContentAs(java.nio.file.Path p0, java.nio.charset.Charset p1) { return null; }
    public AbstractPathAssert exists() { return null; }
    public AbstractPathAssert isDirectory() { return null; }
    public AbstractPathAssert hasFileName(String p0) { return null; }
    public AbstractPathAssert isDirectoryContaining(String p0) { return null; }
    public AbstractPathAssert existsNoFollowLinks() { return null; }
    public AbstractPathAssert isEmptyFile() { return null; }
    public AbstractPathAssert hasNoParent() { return null; }
    public AbstractPathAssert isDirectoryRecursivelyContaining(String p0) { return null; }
    public AbstractPathAssert hasParent(java.nio.file.Path p0) { return null; }
    public AbstractPathAssert isDirectoryNotContaining(java.util.function.Predicate p0) { return null; }
    public AbstractPathAssert hasDigest(String p0, byte[] p1) { return null; }
    public AbstractPathAssert hasSameBinaryContentAs(java.nio.file.Path p0) { return null; }
    public AbstractPathAssert isDirectoryNotContaining(String p0) { return null; }
    public AbstractPathAssert isDirectoryRecursivelyContaining(java.util.function.Predicate p0) { return null; }
    public AbstractPathAssert hasSameTextualContentAs(java.nio.file.Path p0) { return null; }
    public AbstractPathAssert isRelative() { return null; }
    public AbstractPathAssert isWritable() { return null; }
    public AbstractPathAssert hasDigest(java.security.MessageDigest p0, String p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractPeriodAssert {
    public AbstractPeriodAssert hasMonths(int p0) { return null; }
    public AbstractPeriodAssert hasDays(int p0) { return null; }
    public AbstractPeriodAssert isNegative() { return null; }
    public AbstractPeriodAssert hasYears(int p0) { return null; }
    public AbstractPeriodAssert isPositive() { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractShortArrayAssert {
    public AbstractShortArrayAssert hasSizeGreaterThanOrEqualTo(int p0) { return null; }
    public AbstractShortArrayAssert endsWith(Short[] p0) { return null; }
    public AbstractShortArrayAssert isSortedAccordingTo(java.util.Comparator p0) { return null; }
    public AbstractShortArrayAssert hasSizeLessThanOrEqualTo(int p0) { return null; }
    public AbstractShortArrayAssert containsSubsequence(short[] p0) { return null; }
    public AbstractShortArrayAssert containsOnly(int[] p0) { return null; }
    public AbstractShortArrayAssert containsOnly(Short[] p0) { return null; }
    public AbstractShortArrayAssert containsOnlyOnce(int[] p0) { return null; }
    public AbstractShortArrayAssert containsAnyOf(short[] p0) { return null; }
    public AbstractShortArrayAssert doesNotContain(short p0, org.assertj.core.data.Index p1) { return null; }
    public AbstractShortArrayAssert startsWith(short[] p0) { return null; }
    public AbstractShortArrayAssert doesNotHaveDuplicates() { return null; }
    public AbstractShortArrayAssert contains(Short[] p0) { return null; }
    public AbstractShortArrayAssert containsExactly(short[] p0) { return null; }
    public AbstractShortArrayAssert endsWith(int[] p0) { return null; }
    public AbstractShortArrayAssert doesNotContain(Short[] p0) { return null; }
    public AbstractShortArrayAssert hasSizeLessThan(int p0) { return null; }
    public AbstractShortArrayAssert containsAnyOf(int[] p0) { return null; }
    public AbstractShortArrayAssert hasSameSizeAs(Iterable p0) { return null; }
    public AbstractShortArrayAssert contains(int p0, org.assertj.core.data.Index p1) { return null; }
    public AbstractShortArrayAssert isSorted() { return null; }
    public AbstractShortArrayAssert doesNotContain(int p0, org.assertj.core.data.Index p1) { return null; }
    public AbstractShortArrayAssert containsSequence(short[] p0) { return null; }
    public AbstractShortArrayAssert usingElementComparator(java.util.Comparator p0) { return null; }
    public AbstractShortArrayAssert containsSequence(Short[] p0) { return null; }
    public AbstractShortArrayAssert containsExactlyInAnyOrder(int[] p0) { return null; }
    public AbstractShortArrayAssert endsWith(short[] p0) { return null; }
    public AbstractShortArrayAssert containsSubsequence(Short[] p0) { return null; }
    public AbstractShortArrayAssert contains(short p0, org.assertj.core.data.Index p1) { return null; }
    public AbstractShortArrayAssert containsExactly(int[] p0) { return null; }
    public AbstractShortArrayAssert containsOnly(short[] p0) { return null; }
    public AbstractShortArrayAssert startsWith(Short[] p0) { return null; }
    public AbstractShortArrayAssert containsAnyOf(Short[] p0) { return null; }
    public AbstractShortArrayAssert doesNotContain(int[] p0) { return null; }
    public void isNullOrEmpty() {}
    public AbstractShortArrayAssert contains(short[] p0) { return null; }
    public AbstractShortArrayAssert containsSequence(int[] p0) { return null; }
    public AbstractShortArrayAssert containsExactly(Short[] p0) { return null; }
    public AbstractShortArrayAssert doesNotContain(short[] p0) { return null; }
    public AbstractShortArrayAssert containsExactlyInAnyOrder(short[] p0) { return null; }
    public AbstractShortArrayAssert containsExactlyInAnyOrder(Short[] p0) { return null; }
    public void isEmpty() {}
    public AbstractShortArrayAssert hasSize(int p0) { return null; }
    public AbstractShortArrayAssert usingDefaultElementComparator() { return null; }
    public AbstractShortArrayAssert hasSizeGreaterThan(int p0) { return null; }
    public AbstractShortArrayAssert contains(int[] p0) { return null; }
    public AbstractShortArrayAssert containsOnlyOnce(short[] p0) { return null; }
    public AbstractShortArrayAssert containsOnlyOnce(Short[] p0) { return null; }
    public AbstractShortArrayAssert startsWith(int[] p0) { return null; }
    public AbstractShortArrayAssert hasSizeBetween(int p0, int p1) { return null; }
    public AbstractShortArrayAssert containsSubsequence(int[] p0) { return null; }
    public AbstractShortArrayAssert isNotEmpty() { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractShortAssert implements NumberAssert {
    public AbstractShortAssert isNotEqualTo(short p0) { return null; }
    public AbstractShortAssert isNotNegative() { return null; }
    public NumberAssert isStrictlyBetween(Number p0, Number p1) { return null; }
    public AbstractShortAssert isZero() { return null; }
    public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractShortAssert isOdd() { return null; }
    public AbstractShortAssert usingDefaultComparator() { return null; }
    public AbstractShortAssert isCloseTo(Short p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractShortAssert isEqualTo(short p0) { return null; }
    public AbstractShortAssert isNotPositive() { return null; }
    public AbstractShortAssert isPositive() { return null; }
    public AbstractShortAssert isLessThan(short p0) { return null; }
    public AbstractShortAssert isNotCloseTo(Short p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractShortAssert usingComparator(java.util.Comparator p0) { return null; }
    public AbstractShortAssert isEven() { return null; }
    public AbstractShortAssert isCloseTo(short p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractShortAssert isNegative() { return null; }
    public AbstractShortAssert isNotZero() { return null; }
    public AbstractShortAssert isCloseTo(Short p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractShortAssert isNotCloseTo(short p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractShortAssert isGreaterThan(short p0) { return null; }
    public AbstractComparableAssert isStrictlyBetween(Comparable p0, Comparable p1) { return null; }
    public AbstractShortAssert isOne() { return null; }
    public AbstractShortAssert isLessThanOrEqualTo(short p0) { return null; }
    public AbstractShortAssert isBetween(Short p0, Short p1) { return null; }
    public AbstractShortAssert isNotCloseTo(short p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractComparableAssert isBetween(Comparable p0, Comparable p1) { return null; }
    public NumberAssert isBetween(Number p0, Number p1) { return null; }
    public AbstractShortAssert isNotCloseTo(Short p0, org.assertj.core.data.Percentage p1) { return null; }
    public AbstractShortAssert isCloseTo(short p0, org.assertj.core.data.Percentage p1) { return null; }
    public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Percentage p1) { return null; }
    public NumberAssert isNotCloseTo(Number p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractShortAssert isGreaterThanOrEqualTo(short p0) { return null; }
    public AbstractShortAssert usingComparator(java.util.Comparator p0, String p1) { return null; }
    public NumberAssert isCloseTo(Number p0, org.assertj.core.data.Offset p1) { return null; }
    public AbstractShortAssert isStrictlyBetween(Short p0, Short p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class AbstractStringAssert {
    public AbstractByteArrayAssert decodedAsBase64() { return null; }
    public AbstractStringAssert isBetween(String p0, String p1) { return null; }
    public AbstractStringAssert usingComparator(java.util.Comparator p0, String p1) { return null; }
    public AbstractStringAssert isLessThanOrEqualTo(String p0) { return null; }
    public AbstractStringAssert isLessThan(String p0) { return null; }
    public AbstractStringAssert isGreaterThan(String p0) { return null; }
    public AbstractStringAssert isStrictlyBetween(String p0, String p1) { return null; }
    public AbstractStringAssert usingComparator(java.util.Comparator p0) { return null; }
    public AbstractStringAssert isEqualTo(String p0) { return null; }
    public AbstractStringAssert isGreaterThanOrEqualTo(String p0) { return null; }
    public AbstractStringAssert isBase64() { return null; }
    public AbstractStringAssert usingDefaultComparator() { return null; }
    public AbstractStringAssert isEqualTo(String p0, Object[] p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractThrowableAssert {
    public AbstractThrowableAssert hasRootCause(Throwable p0) { return null; }
    public AbstractThrowableAssert hasMessageMatching(String p0) { return null; }
    public AbstractThrowableAssert hasMessageContaining(String p0, Object[] p1) { return null; }
    public AbstractThrowableAssert hasStackTraceContaining(String p0, Object[] p1) { return null; }
    public void doesNotThrowAnyException() {}
    public AbstractThrowableAssert hasMessageContainingAll(CharSequence[] p0) { return null; }
    public AbstractThrowableAssert hasMessage(String p0, Object[] p1) { return null; }
    public AbstractThrowableAssert hasRootCauseMessage(String p0, Object[] p1) { return null; }
    public AbstractThrowableAssert hasMessage(String p0) { return null; }
    public AbstractThrowableAssert hasStackTraceContaining(String p0) { return null; }
    public AbstractThrowableAssert hasRootCauseExactlyInstanceOf(Class p0) { return null; }
    public AbstractThrowableAssert hasCauseExactlyInstanceOf(Class p0) { return null; }
    public AbstractThrowableAssert hasRootCauseMessage(String p0) { return null; }
    public AbstractThrowableAssert hasSuppressedException(Throwable p0) { return null; }
    public AbstractThrowableAssert hasMessageEndingWith(String p0, Object[] p1) { return null; }
    public AbstractThrowableAssert hasRootCauseInstanceOf(Class p0) { return null; }
    public AbstractThrowableAssert hasNoCause() { return null; }
    public AbstractThrowableAssert getCause() { return null; }
    public AbstractThrowableAssert hasMessageStartingWith(String p0, Object[] p1) { return null; }
    public AbstractThrowableAssert hasMessageContaining(String p0) { return null; }
    public AbstractThrowableAssert hasMessageNotContaining(String p0) { return null; }
    public AbstractThrowableAssert hasCauseInstanceOf(Class p0) { return null; }
    public AbstractThrowableAssert hasMessageFindingMatch(String p0) { return null; }
    public AbstractThrowableAssert hasMessageEndingWith(String p0) { return null; }
    public AbstractThrowableAssert hasNoSuppressedExceptions() { return null; }
    public AbstractThrowableAssert hasCause(Throwable p0) { return null; }
    public AbstractThrowableAssert hasCauseReference(Throwable p0) { return null; }
    public AbstractThrowableAssert getRootCause() { return null; }
    public AbstractThrowableAssert hasMessageNotContainingAny(CharSequence[] p0) { return null; }
    public AbstractThrowableAssert hasMessageStartingWith(String p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractUriAssert {
    public AbstractUriAssert hasNoPort() { return null; }
    public AbstractUriAssert hasNoUserInfo() { return null; }
    public AbstractUriAssert hasPort(int p0) { return null; }
    public AbstractUriAssert hasQuery(String p0) { return null; }
    public AbstractUriAssert hasParameter(String p0) { return null; }
    public AbstractUriAssert hasNoFragment() { return null; }
    public AbstractUriAssert hasFragment(String p0) { return null; }
    public AbstractUriAssert hasParameter(String p0, String p1) { return null; }
    public AbstractUriAssert hasNoParameters() { return null; }
    public AbstractUriAssert hasNoPath() { return null; }
    public AbstractUriAssert hasHost(String p0) { return null; }
    public AbstractUriAssert hasNoParameter(String p0, String p1) { return null; }
    public AbstractUriAssert hasNoQuery() { return null; }
    public AbstractUriAssert hasNoParameter(String p0) { return null; }
    public AbstractUriAssert hasAuthority(String p0) { return null; }
    public AbstractUriAssert hasScheme(String p0) { return null; }
    public AbstractUriAssert hasUserInfo(String p0) { return null; }
    public AbstractUriAssert hasPath(String p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractUrlAssert {
    public AbstractUrlAssert hasQuery(String p0) { return null; }
    public AbstractUrlAssert hasPort(int p0) { return null; }
    public AbstractUrlAssert hasNoPort() { return null; }
    public AbstractUrlAssert hasNoUserInfo() { return null; }
    public AbstractUrlAssert hasParameter(String p0) { return null; }
    public AbstractUrlAssert hasNoParameter(String p0) { return null; }
    public AbstractUrlAssert hasUserInfo(String p0) { return null; }
    public AbstractUrlAssert hasNoQuery() { return null; }
    public AbstractUrlAssert hasNoAnchor() { return null; }
    public AbstractUrlAssert hasParameter(String p0, String p1) { return null; }
    public AbstractUrlAssert hasAnchor(String p0) { return null; }
    public AbstractUrlAssert hasProtocol(String p0) { return null; }
    public AbstractUrlAssert hasNoParameter(String p0, String p1) { return null; }
    public AbstractUrlAssert hasHost(String p0) { return null; }
    public AbstractUrlAssert isEqualToWithSortedQueryParameters(java.net.URL p0) { return null; }
    public AbstractUrlAssert hasNoPath() { return null; }
    public AbstractUrlAssert hasPath(String p0) { return null; }
    public AbstractUrlAssert hasAuthority(String p0) { return null; }
    public AbstractUrlAssert hasNoParameters() { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public abstract class AbstractZonedDateTimeAssert {
    public AbstractZonedDateTimeAssert isNotIn(String[] p0) { return null; }
    public AbstractZonedDateTimeAssert usingDefaultComparator() { return null; }
    public AbstractZonedDateTimeAssert isNotEqualTo(Object p0) { return null; }
    public AbstractZonedDateTimeAssert isBetween(java.time.ZonedDateTime p0, java.time.ZonedDateTime p1) { return null; }
    public AbstractZonedDateTimeAssert isBefore(String p0) { return null; }
    public AbstractZonedDateTimeAssert isAfterOrEqualTo(java.time.ZonedDateTime p0) { return null; }
    public AbstractZonedDateTimeAssert isIn(java.time.ZonedDateTime[] p0) { return null; }
    public AbstractZonedDateTimeAssert isEqualToIgnoringHours(java.time.ZonedDateTime p0) { return null; }
    public AbstractZonedDateTimeAssert isNotEqualTo(String p0) { return null; }
    public AbstractZonedDateTimeAssert isEqualTo(Object p0) { return null; }
    public AbstractZonedDateTimeAssert isEqualToIgnoringNanos(java.time.ZonedDateTime p0) { return null; }
    public AbstractZonedDateTimeAssert isIn(String[] p0) { return null; }
    public AbstractZonedDateTimeAssert isEqualTo(String p0) { return null; }
    public AbstractZonedDateTimeAssert isAfter(String p0) { return null; }
    public AbstractZonedDateTimeAssert isBeforeOrEqualTo(String p0) { return null; }
    public AbstractZonedDateTimeAssert isEqualToIgnoringSeconds(java.time.ZonedDateTime p0) { return null; }
    public AbstractZonedDateTimeAssert isNotIn(java.time.ZonedDateTime[] p0) { return null; }
    public AbstractZonedDateTimeAssert isAfterOrEqualTo(String p0) { return null; }
    public AbstractZonedDateTimeAssert isEqualToIgnoringMinutes(java.time.ZonedDateTime p0) { return null; }
    public AbstractZonedDateTimeAssert isAfter(java.time.ZonedDateTime p0) { return null; }
    public AbstractZonedDateTimeAssert isStrictlyBetween(java.time.ZonedDateTime p0, java.time.ZonedDateTime p1) { return null; }
    public AbstractZonedDateTimeAssert isStrictlyBetween(String p0, String p1) { return null; }
    public AbstractZonedDateTimeAssert isBeforeOrEqualTo(java.time.ZonedDateTime p0) { return null; }
    public AbstractZonedDateTimeAssert isBetween(String p0, String p1) { return null; }
    public AbstractZonedDateTimeAssert isBefore(java.time.ZonedDateTime p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public interface ArraySortedAssert {
}
---
        package org.assertj.core.api;
        import java.io.*;

public interface Assert extends Descriptable, ExtensionPoints {
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
}
---
        package org.assertj.core.api;
        import java.io.*;

public interface AssertProvider {
}
---
        package org.assertj.core.api;
        import java.io.*;

public interface AssertionInfo {
}
---
        package org.assertj.core.api;
        import java.io.*;

public class Assertions implements InstanceOfAssertFactories {
    public static String contentOf(java.net.URL p0, java.nio.charset.Charset p1) { return null; }
    public static Object assertThat(AssertProvider p0) { return null; }
    public static ClassBasedNavigableIterableAssert assertThat(Iterable p0, Class p1) { return null; }
    public static Object2DArrayAssert assertThat(Object[][] p0) { return null; }
    public static void setMaxStackTraceElementsDisplayed(int p0) {}
    public static Char2DArrayAssert assertThat(char[][] p0) { return null; }
    public static org.assertj.core.data.Offset within(Integer p0) { return null; }
    public static AbstractBooleanAssert assertThat(Boolean p0) { return null; }
    public static AtomicIntegerArrayAssert assertThat(java.util.concurrent.atomic.AtomicIntegerArray p0) { return null; }
    public static AbstractFloatAssert assertThat(Float p0) { return null; }
    public static org.assertj.core.data.Offset byLessThan(java.math.BigInteger p0) { return null; }
    public static AbstractDoubleArrayAssert assertThat(double[] p0) { return null; }
    public static void setMaxLengthForSingleLineDescription(int p0) {}
    public static org.assertj.core.data.Offset within(Long p0) { return null; }
    public static org.assertj.core.api.filter.Filters filter(Iterable p0) { return null; }
    public static IterableAssert assertThat(Iterable p0) { return null; }
    public static AbstractFileAssert assertThat(File p0) { return null; }
    public static AbstractThrowableAssert assertThatCode(ThrowableAssert.ThrowingCallable p0) { return null; }
    public static void setExtractBareNamePropertyMethods(boolean p0) {}
    public static AtomicReferenceFieldUpdaterAssert assertThat(java.util.concurrent.atomic.AtomicReferenceFieldUpdater p0) { return null; }
    public static Throwable catchThrowable(ThrowableAssert.ThrowingCallable p0) { return null; }
    public static AtomicLongArrayAssert assertThat(java.util.concurrent.atomic.AtomicLongArray p0) { return null; }
    public static org.assertj.core.data.TemporalUnitOffset within(long p0, java.time.temporal.TemporalUnit p1) { return null; }
    public static org.assertj.core.api.filter.NotFilter not(Object p0) { return null; }
    public static AbstractCharacterAssert assertThat(Character p0) { return null; }
    public static String contentOf(java.net.URL p0) { return null; }
    public static AbstractThrowableAssert assertThatThrownBy(ThrowableAssert.ThrowingCallable p0) { return null; }
    public static void setAllowComparingPrivateFields(boolean p0) {}
    public static ListAssert assertThat(java.util.stream.Stream p0) { return null; }
    public static AbstractDurationAssert assertThat(java.time.Duration p0) { return null; }
    public static ThrowableTypeAssert assertThatIOException() { return null; }
    public static ObjectAssert assertThat(Object p0) { return null; }
    public static IntPredicateAssert assertThat(java.util.function.IntPredicate p0) { return null; }
    public static org.assertj.core.api.filter.NotInFilter notIn(Object[] p0) { return null; }
    public static AbstractInputStreamAssert assertThat(InputStream p0) { return null; }
    public static AbstractUrlAssert assertThat(java.net.URL p0) { return null; }
    public static Condition anyOf(Iterable p0) { return null; }
    public static String contentOf(File p0, String p1) { return null; }
    public static AbstractIntegerAssert assertThat(Integer p0) { return null; }
    public static Object fail(String p0) { return null; }
    public static Condition allOf(Iterable p0) { return null; }
    public static void setDescriptionConsumer(java.util.function.Consumer p0) {}
    public static org.assertj.core.api.filter.Filters filter(Object[] p0) { return null; }
    public static AbstractDoubleAssert assertThat(Double p0) { return null; }
    public static java.time.Duration withMarginOf(java.time.Duration p0) { return null; }
    public static AbstractIntegerAssert assertThat(int p0) { return null; }
    public static Object fail(String p0, Throwable p1) { return null; }
    public static org.assertj.core.data.Offset within(Float p0) { return null; }
    public static OptionalDoubleAssert assertThat(java.util.OptionalDouble p0) { return null; }
    public static AbstractUriAssert assertThat(java.net.URI p0) { return null; }
    public static org.assertj.core.data.Offset withPrecision(Double p0) { return null; }
    public static org.assertj.core.data.Offset byLessThan(java.math.BigDecimal p0) { return null; }
    public static AbstractLocalTimeAssert assertThat(java.time.LocalTime p0) { return null; }
    public static java.util.List linesOf(File p0, java.nio.charset.Charset p1) { return null; }
    public static org.assertj.core.data.Offset withPrecision(Float p0) { return null; }
    public static org.assertj.core.data.Offset within(Byte p0) { return null; }
    public static AbstractStringAssert assertThat(String p0) { return null; }
    public static DoublePredicateAssert assertThat(java.util.function.DoublePredicate p0) { return null; }
    public static org.assertj.core.condition.Not not(Condition p0) { return null; }
    public static Object failBecauseExceptionWasNotThrown(Class p0) { return null; }
    public static AbstractByteArrayAssert assertThat(byte[] p0) { return null; }
    public static AbstractFloatAssert assertThat(float p0) { return null; }
    public static CompletableFutureAssert assertThat(java.util.concurrent.CompletionStage p0) { return null; }
    public static ClassBasedNavigableListAssert assertThat(java.util.List p0, Class p1) { return null; }
    public static ThrowableTypeAssert assertThatIllegalStateException() { return null; }
    public static org.assertj.core.data.Offset byLessThan(Double p0) { return null; }
    public static Throwable catchThrowableOfType(ThrowableAssert.ThrowingCallable p0, Class p1) { return null; }
    public static org.assertj.core.data.Percentage withinPercentage(Integer p0) { return null; }
    public static org.assertj.core.api.filter.InFilter in(Object[] p0) { return null; }
    public static AbstractLocalDateAssert assertThat(java.time.LocalDate p0) { return null; }
    public static OptionalAssert assertThat(java.util.Optional p0) { return null; }
    public static org.assertj.core.groups.Tuple tuple(Object[] p0) { return null; }
    public static AbstractLongAssert assertThat(long p0) { return null; }
    public static OptionalIntAssert assertThat(java.util.OptionalInt p0) { return null; }
    public static AbstractShortAssert assertThat(Short p0) { return null; }
    public static AbstractByteAssert assertThat(Byte p0) { return null; }
    public static ClassAssert assertThat(Class p0) { return null; }
    public static org.assertj.core.data.Offset offset(Double p0) { return null; }
    public static AtomicIntegerFieldUpdaterAssert assertThat(java.util.concurrent.atomic.AtomicIntegerFieldUpdater p0) { return null; }
    public static FutureAssert assertThat(java.util.concurrent.Future p0) { return null; }
    public static AbstractDateAssert assertThat(java.util.Date p0) { return null; }
    public static org.assertj.core.data.Offset byLessThan(Float p0) { return null; }
    public static ListAssert assertThat(java.util.List p0) { return null; }
    public static void setPrintAssertionsDescription(boolean p0) {}
    public static org.assertj.core.data.Offset offset(Float p0) { return null; }
    public static org.assertj.core.data.Percentage withinPercentage(Double p0) { return null; }
    public static InstanceOfAssertFactory as(InstanceOfAssertFactory p0) { return null; }
    public static AbstractLongAssert assertThat(Long p0) { return null; }
    public static ListAssert assertThat(java.util.stream.LongStream p0) { return null; }
    public static String contentOf(java.net.URL p0, String p1) { return null; }
    public static AbstractLongArrayAssert assertThat(long[] p0) { return null; }
    public static java.util.List linesOf(java.net.URL p0, java.nio.charset.Charset p1) { return null; }
    public static AssertDelegateTarget assertThat(AssertDelegateTarget p0) { return null; }
    public static void registerFormatterForType(Class p0, java.util.function.Function p1) {}
    public static AbstractThrowableAssert assertThat(Throwable p0) { return null; }
    public static ThrowableTypeAssert assertThatIllegalArgumentException() { return null; }
    public static java.util.List linesOf(File p0, String p1) { return null; }
    public static IteratorAssert assertThat(java.util.Iterator p0) { return null; }
    public static org.assertj.core.data.Percentage withinPercentage(Long p0) { return null; }
    public static FactoryBasedNavigableListAssert assertThat(java.util.List p0, AssertFactory p1) { return null; }
    public static AtomicLongAssert assertThat(java.util.concurrent.atomic.AtomicLong p0) { return null; }
    public static void registerCustomDateFormat(java.text.DateFormat p0) {}
    public static ObjectAssert assertThatObject(Object p0) { return null; }
    public static Object shouldHaveThrown(Class p0) { return null; }
    public static AbstractBooleanArrayAssert assertThat(boolean[] p0) { return null; }
    public static java.util.List linesOf(File p0) { return null; }
    public static org.assertj.core.groups.Properties extractProperty(String p0, Class p1) { return null; }
    public static NotThrownAssert assertThatNoException() { return null; }
    public static org.assertj.core.data.Offset within(java.math.BigDecimal p0) { return null; }
    public static org.assertj.core.data.Offset byLessThan(Short p0) { return null; }
    public static void registerCustomDateFormat(String p0) {}
    public static java.util.function.Function from(java.util.function.Function p0) { return null; }
    public static AbstractByteAssert assertThat(byte p0) { return null; }
    public static Double2DArrayAssert assertThat(double[][] p0) { return null; }
    public static AbstractBigDecimalAssert assertThat(java.math.BigDecimal p0) { return null; }
    public static AbstractIntArrayAssert assertThat(int[] p0) { return null; }
    public static Long2DArrayAssert assertThat(long[][] p0) { return null; }
    public static ThrowableTypeAssert assertThatExceptionOfType(Class p0) { return null; }
    public static Object fail(String p0, Object[] p1) { return null; }
    public static AbstractCharSequenceAssert assertThat(StringBuilder p0) { return null; }
    public static org.assertj.core.data.Offset within(Short p0) { return null; }
    public static void useRepresentation(org.assertj.core.presentation.Representation p0) {}
    public static AtomicReferenceAssert assertThat(java.util.concurrent.atomic.AtomicReference p0) { return null; }
    public static AbstractComparableAssert assertThat(Comparable p0) { return null; }
    public static Int2DArrayAssert assertThat(int[][] p0) { return null; }
    public static AbstractFloatArrayAssert assertThat(float[] p0) { return null; }
    public static AbstractPeriodAssert assertThat(java.time.Period p0) { return null; }
    public static Condition anyOf(Condition[] p0) { return null; }
    public static AbstractCharSequenceAssert assertThat(StringBuffer p0) { return null; }
    public static AtomicLongFieldUpdaterAssert assertThat(java.util.concurrent.atomic.AtomicLongFieldUpdater p0) { return null; }
    public static org.assertj.core.data.Offset byLessThan(Long p0) { return null; }
    public static AbstractShortAssert assertThat(short p0) { return null; }
    public static void useDefaultDateFormatsOnly() {}
    public static Condition allOf(Condition[] p0) { return null; }
    public static Byte2DArrayAssert assertThat(byte[][] p0) { return null; }
    public static AtomicIntegerAssert assertThat(java.util.concurrent.atomic.AtomicInteger p0) { return null; }
    public static void setAllowExtractingPrivateFields(boolean p0) {}
    public static AbstractCharacterAssert assertThat(char p0) { return null; }
    public static org.assertj.core.data.MapEntry entry(Object p0, Object p1) { return null; }
    public static MapAssert assertThat(java.util.Map p0) { return null; }
    public static AbstractThrowableAssert assertThatThrownBy(ThrowableAssert.ThrowingCallable p0, String p1, Object[] p2) { return null; }
    public static ObjectArrayAssert assertThat(Object[] p0) { return null; }
    public static AbstractOffsetTimeAssert assertThat(java.time.OffsetTime p0) { return null; }
    public static void setMaxElementsForPrinting(int p0) {}
    public static AbstractZonedDateTimeAssert assertThat(java.time.ZonedDateTime p0) { return null; }
    public static AbstractInstantAssert assertThat(java.time.Instant p0) { return null; }
    public static org.assertj.core.data.Offset within(java.math.BigInteger p0) { return null; }
    public static AbstractCharSequenceAssert assertThat(CharSequence p0) { return null; }
    public static AtomicReferenceArrayAssert assertThat(java.util.concurrent.atomic.AtomicReferenceArray p0) { return null; }
    public static org.assertj.core.data.Offset within(Double p0) { return null; }
    public static AbstractLocalDateTimeAssert assertThat(java.time.LocalDateTime p0) { return null; }
    public static AbstractBooleanAssert assertThat(boolean p0) { return null; }
    public static org.assertj.core.data.Offset byLessThan(Integer p0) { return null; }
    public static AbstractBigIntegerAssert assertThat(java.math.BigInteger p0) { return null; }
    public static String contentOf(File p0, java.nio.charset.Charset p1) { return null; }
    public static CompletableFutureAssert assertThat(java.util.concurrent.CompletableFuture p0) { return null; }
    public static AbstractPathAssert assertThat(java.nio.file.Path p0) { return null; }
    public static void setRemoveAssertJRelatedElementsFromStackTrace(boolean p0) {}
    public static LongPredicateAssert assertThat(java.util.function.LongPredicate p0) { return null; }
    public static Short2DArrayAssert assertThat(short[][] p0) { return null; }
    public static org.assertj.core.data.TemporalUnitOffset byLessThan(long p0, java.time.temporal.TemporalUnit p1) { return null; }
    public static ListAssert assertThat(java.util.stream.DoubleStream p0) { return null; }
    public static org.assertj.core.groups.Properties extractProperty(String p0) { return null; }
    public static OptionalLongAssert assertThat(java.util.OptionalLong p0) { return null; }
    public static ThrowableTypeAssert assertThatNullPointerException() { return null; }
    public static void useDefaultRepresentation() {}
    public static void setLenientDateParsing(boolean p0) {}
    public static PredicateAssert assertThat(java.util.function.Predicate p0) { return null; }
    public static org.assertj.core.data.Index atIndex(int p0) { return null; }
    public static LongAdderAssert assertThat(java.util.concurrent.atomic.LongAdder p0) { return null; }
    public static FactoryBasedNavigableIterableAssert assertThat(Iterable p0, AssertFactory p1) { return null; }
    public static AtomicMarkableReferenceAssert assertThat(java.util.concurrent.atomic.AtomicMarkableReference p0) { return null; }
    public static String contentOf(File p0) { return null; }
    public static ListAssert assertThat(java.util.stream.IntStream p0) { return null; }
    public static java.util.List linesOf(java.net.URL p0) { return null; }
    public static Boolean2DArrayAssert assertThat(boolean[][] p0) { return null; }
    public static java.util.List linesOf(java.net.URL p0, String p1) { return null; }
    public static SpliteratorAssert assertThat(java.util.Spliterator p0) { return null; }
    public static AbstractDoubleAssert assertThat(double p0) { return null; }
    public static org.assertj.core.condition.DoesNotHave doesNotHave(Condition p0) { return null; }
    public static org.assertj.core.data.Offset byLessThan(Byte p0) { return null; }
    public static AtomicBooleanAssert assertThat(java.util.concurrent.atomic.AtomicBoolean p0) { return null; }
    public static Float2DArrayAssert assertThat(float[][] p0) { return null; }
    public static AtomicStampedReferenceAssert assertThat(java.util.concurrent.atomic.AtomicStampedReference p0) { return null; }
    public static AbstractOffsetDateTimeAssert assertThat(java.time.OffsetDateTime p0) { return null; }
    public static AbstractCharArrayAssert assertThat(char[] p0) { return null; }
    public static AbstractShortArrayAssert assertThat(short[] p0) { return null; }
    public InstanceOfAssertFactory atomicLongFieldUpdater(Class p0)  {return null;}
    public InstanceOfAssertFactory completionStage(Class p0)  {return null;}
    public InstanceOfAssertFactory optional(Class p0)  {return null;}
    public InstanceOfAssertFactory atomicReferenceFieldUpdater(Class p0, Class p1)  {return null;}
    public InstanceOfAssertFactory stream(Class p0)  {return null;}
    public InstanceOfAssertFactory completableFuture(Class p0)  {return null;}
    public InstanceOfAssertFactory list(Class p0)  {return null;}
    public InstanceOfAssertFactory spliterator(Class p0)  {return null;}
    public InstanceOfAssertFactory atomicMarkableReference(Class p0)  {return null;}
    public InstanceOfAssertFactory predicate(Class p0)  {return null;}
    public InstanceOfAssertFactory array2D(Class p0)  {return null;}
    public InstanceOfAssertFactory atomicStampedReference(Class p0)  {return null;}
    public InstanceOfAssertFactory future(Class p0)  {return null;}
    public InstanceOfAssertFactory atomicIntegerFieldUpdater(Class p0)  {return null;}
    public InstanceOfAssertFactory comparable(Class p0)  {return null;}
    public InstanceOfAssertFactory atomicReference(Class p0)  {return null;}
    public InstanceOfAssertFactory map(Class p0, Class p1)  {return null;}
    public InstanceOfAssertFactory iterable(Class p0)  {return null;}
    public InstanceOfAssertFactory array(Class p0)  {return null;}
    public InstanceOfAssertFactory type(Class p0)  {return null;}
    public InstanceOfAssertFactory atomicReferenceArray(Class p0)  {return null;}
    public InstanceOfAssertFactory iterator(Class p0)  {return null;}
}
---
        package org.assertj.core.api;
        import java.io.*;

public class AtomicBooleanAssert {
    public AtomicBooleanAssert usingComparator(java.util.Comparator p0, String p1) { return null; }
    public AtomicBooleanAssert isFalse() { return null; }
    public AtomicBooleanAssert isTrue() { return null; }
    public AtomicBooleanAssert usingComparator(java.util.Comparator p0) { return null; }
    public void AtomicBooleanAssert(java.util.concurrent.atomic.AtomicBoolean p0) {}
}
---
        package org.assertj.core.api;
        import java.io.*;

public class AtomicIntegerArrayAssert {
    public AtomicIntegerArrayAssert isSorted() { return null; }
    public AtomicIntegerArrayAssert containsOnlyOnce(int[] p0) { return null; }
    public AtomicIntegerArrayAssert hasSizeLessThan(int p0) { return null; }
    public AtomicIntegerArrayAssert doesNotHaveDuplicates() { return null; }
    public AtomicIntegerArrayAssert hasSize(int p0) { return null; }
    public AtomicIntegerArrayAssert startsWith(int[] p0) { return null; }
    public AtomicIntegerArrayAssert endsWith(int[] p0) { return null; }
    public AtomicIntegerArrayAssert containsExactlyInAnyOrder(int[] p0) { return null; }
    public AtomicIntegerArrayAssert containsAnyOf(int[] p0) { return null; }
    public AtomicIntegerArrayAssert contains(int p0, org.assertj.core.data.Index p1) { return null; }
    public AtomicIntegerArrayAssert containsSequence(int[] p0) { return null; }
    public AtomicIntegerArrayAssert hasSizeLessThanOrEqualTo(int p0) { return null; }
    public AtomicIntegerArrayAssert containsSubsequence(int[] p0) { return null; }
    public void isNullOrEmpty() {}
    public void AtomicIntegerArrayAssert(java.util.concurrent.atomic.AtomicIntegerArray p0) {}
    public AtomicIntegerArrayAssert containsExactly(int[] p0) { return null; }
    public AtomicIntegerArrayAssert doesNotContain(int[] p0) { return null; }
    public AtomicIntegerArrayAssert isNotEmpty() { return null; }
    public AtomicIntegerArrayAssert hasSameSizeAs(Iterable p0) { return null; }
    public AtomicIntegerArrayAssert containsOnly(int[] p0) { return null; }
    public void isEmpty() {}
    public AtomicIntegerArrayAssert usingDefaultElementComparator() { return null; }
    public AtomicIntegerArrayAssert contains(int[] p0) { return null; }
    public AtomicIntegerArrayAssert hasSizeBetween(int p0, int p1) { return null; }
    public AtomicIntegerArrayAssert doesNotContain(int p0, org.assertj.core.data.Index p1) { return null; }
    public AtomicIntegerArrayAssert isSortedAccordingTo(java.util.Comparator p0) { return null; }
    public AtomicIntegerArrayAssert hasArray(int[] p0) { return null; }
    public AtomicIntegerArrayAssert hasSizeGreaterThan(int p0) { return null; }
    public AtomicIntegerArrayAssert hasSizeGreaterThanOrEqualTo(int p0) { return null; }
    public AtomicIntegerArrayAssert usingElementComparator(java.util.Comparator p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class AtomicIntegerAssert {
    public AtomicIntegerAssert hasValueGreaterThanOrEqualTo(int p0) { return null; }
    public AtomicIntegerAssert usingDefaultComparator() { return null; }
    public AtomicIntegerAssert hasValueGreaterThan(int p0) { return null; }
    public AtomicIntegerAssert usingComparator(java.util.Comparator p0, String p1) { return null; }
    public AtomicIntegerAssert hasPositiveValue() { return null; }
    public AtomicIntegerAssert hasNonPositiveValue() { return null; }
    public AtomicIntegerAssert hasValueCloseTo(int p0, org.assertj.core.data.Offset p1) { return null; }
    public void AtomicIntegerAssert(java.util.concurrent.atomic.AtomicInteger p0) {}
    public AtomicIntegerAssert doesNotHaveValue(int p0) { return null; }
    public AtomicIntegerAssert hasValue(int p0) { return null; }
    public AtomicIntegerAssert usingComparator(java.util.Comparator p0) { return null; }
    public AtomicIntegerAssert hasNegativeValue() { return null; }
    public AtomicIntegerAssert hasValueBetween(int p0, int p1) { return null; }
    public AtomicIntegerAssert hasValueLessThan(int p0) { return null; }
    public AtomicIntegerAssert hasValueCloseTo(int p0, org.assertj.core.data.Percentage p1) { return null; }
    public AtomicIntegerAssert hasNonNegativeValue() { return null; }
    public AtomicIntegerAssert hasValueLessThanOrEqualTo(int p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class AtomicIntegerFieldUpdaterAssert {
    public void AtomicIntegerFieldUpdaterAssert(java.util.concurrent.atomic.AtomicIntegerFieldUpdater p0) {}
    public AbstractAtomicFieldUpdaterAssert hasValue(Object p0, Object p1) { return null; }
    public AtomicIntegerFieldUpdaterAssert hasValue(Integer p0, Object p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class AtomicLongArrayAssert {
    public AtomicLongArrayAssert containsExactly(long[] p0) { return null; }
    public AtomicLongArrayAssert usingElementComparator(java.util.Comparator p0) { return null; }
    public AtomicLongArrayAssert usingDefaultElementComparator() { return null; }
    public AtomicLongArrayAssert hasSize(int p0) { return null; }
    public AtomicLongArrayAssert doesNotHaveDuplicates() { return null; }
    public AtomicLongArrayAssert isSortedAccordingTo(java.util.Comparator p0) { return null; }
    public AtomicLongArrayAssert hasSizeLessThanOrEqualTo(int p0) { return null; }
    public AtomicLongArrayAssert containsSubsequence(long[] p0) { return null; }
    public void isNullOrEmpty() {}
    public AtomicLongArrayAssert containsAnyOf(long[] p0) { return null; }
    public AtomicLongArrayAssert startsWith(long[] p0) { return null; }
    public AtomicLongArrayAssert isSorted() { return null; }
    public AtomicLongArrayAssert containsOnly(long[] p0) { return null; }
    public AtomicLongArrayAssert containsSequence(long[] p0) { return null; }
    public AtomicLongArrayAssert containsExactlyInAnyOrder(long[] p0) { return null; }
    public AtomicLongArrayAssert hasSameSizeAs(Iterable p0) { return null; }
    public AtomicLongArrayAssert containsOnlyOnce(long[] p0) { return null; }
    public void AtomicLongArrayAssert(java.util.concurrent.atomic.AtomicLongArray p0) {}
    public void isEmpty() {}
    public AtomicLongArrayAssert contains(long p0, org.assertj.core.data.Index p1) { return null; }
    public AtomicLongArrayAssert hasSizeLessThan(int p0) { return null; }
    public AtomicLongArrayAssert hasSizeGreaterThanOrEqualTo(int p0) { return null; }
    public AtomicLongArrayAssert hasArray(long[] p0) { return null; }
    public AtomicLongArrayAssert hasSizeGreaterThan(int p0) { return null; }
    public AtomicLongArrayAssert doesNotContain(long p0, org.assertj.core.data.Index p1) { return null; }
    public AtomicLongArrayAssert contains(long[] p0) { return null; }
    public AtomicLongArrayAssert doesNotContain(long[] p0) { return null; }
    public AtomicLongArrayAssert endsWith(long[] p0) { return null; }
    public AtomicLongArrayAssert isNotEmpty() { return null; }
    public AtomicLongArrayAssert hasSizeBetween(int p0, int p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class AtomicLongAssert {
    public AtomicLongAssert hasPositiveValue() { return null; }
    public AtomicLongAssert usingDefaultComparator() { return null; }
    public AtomicLongAssert hasValue(long p0) { return null; }
    public AtomicLongAssert usingComparator(java.util.Comparator p0) { return null; }
    public AtomicLongAssert hasNonPositiveValue() { return null; }
    public AtomicLongAssert hasValueLessThanOrEqualTo(long p0) { return null; }
    public AtomicLongAssert hasValueGreaterThanOrEqualTo(long p0) { return null; }
    public AtomicLongAssert hasValueCloseTo(long p0, org.assertj.core.data.Offset p1) { return null; }
    public AtomicLongAssert usingComparator(java.util.Comparator p0, String p1) { return null; }
    public AtomicLongAssert doesNotHaveValue(long p0) { return null; }
    public AtomicLongAssert hasValueGreaterThan(long p0) { return null; }
    public AtomicLongAssert hasValueCloseTo(long p0, org.assertj.core.data.Percentage p1) { return null; }
    public AtomicLongAssert hasValueBetween(long p0, long p1) { return null; }
    public void AtomicLongAssert(java.util.concurrent.atomic.AtomicLong p0) {}
    public AtomicLongAssert hasValueLessThan(long p0) { return null; }
    public AtomicLongAssert hasNegativeValue() { return null; }
    public AtomicLongAssert hasNonNegativeValue() { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class AtomicLongFieldUpdaterAssert {
    public AtomicLongFieldUpdaterAssert hasValue(Long p0, Object p1) { return null; }
    public void AtomicLongFieldUpdaterAssert(java.util.concurrent.atomic.AtomicLongFieldUpdater p0) {}
    public AbstractAtomicFieldUpdaterAssert hasValue(Object p0, Object p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class AtomicMarkableReferenceAssert {
    public AtomicMarkableReferenceAssert hasReference(Object p0) { return null; }
    public AtomicMarkableReferenceAssert isNotMarked() { return null; }
    public void AtomicMarkableReferenceAssert(java.util.concurrent.atomic.AtomicMarkableReference p0) {}
    public AtomicMarkableReferenceAssert isMarked() { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class AtomicReferenceArrayAssert implements IndexedObjectEnumerableAssert, ArraySortedAssert {
    public AtomicReferenceArrayAssert containsSubsequence(Iterable p0) { return null; }
    public ObjectArrayAssert extracting(String p0) { return null; }
    public AtomicReferenceArrayAssert doesNotContainSequence(Object[] p0) { return null; }
    public AtomicReferenceArrayAssert containsExactlyInAnyOrder(Object[] p0) { return null; }
    public ObjectArrayAssert extractingResultOf(String p0, Class p1) { return null; }
    public AtomicReferenceArrayAssert usingRecursiveFieldByFieldElementComparator() { return null; }
    public AtomicReferenceArrayAssert filteredOn(String p0, Object p1) { return null; }
    public AtomicReferenceArrayAssert hasOnlyElementsOfType(Class p0) { return null; }
    public AtomicReferenceArrayAssert usingComparatorForElementFieldsWithType(java.util.Comparator p0, Class p1) { return null; }
    public ObjectArrayAssert extracting(java.util.function.Function p0) { return null; }
    public AtomicReferenceArrayAssert containsOnlyOnceElementsOf(Iterable p0) { return null; }
    public AtomicReferenceArrayAssert containsOnlyElementsOf(Iterable p0) { return null; }
    public AtomicReferenceArrayAssert satisfiesExactly(java.util.function.Consumer[] p0) { return null; }
    public AtomicReferenceArrayAssert allSatisfy(java.util.function.Consumer p0) { return null; }
    public AtomicReferenceArrayAssert containsExactlyInAnyOrderElementsOf(Iterable p0) { return null; }
    public AtomicReferenceArrayAssert containsSequence(Object[] p0) { return null; }
    public AtomicReferenceArrayAssert haveAtLeast(int p0, Condition p1) { return null; }
    public AtomicReferenceArrayAssert hasArray(Object[] p0) { return null; }
    public AtomicReferenceArrayAssert hasAtLeastOneElementOfType(Class p0) { return null; }
    public AtomicReferenceArrayAssert have(Condition p0) { return null; }
    public AtomicReferenceArrayAssert containsExactly(Object[] p0) { return null; }
    public AtomicReferenceArrayAssert usingElementComparator(java.util.Comparator p0) { return null; }
    public AtomicReferenceArrayAssert usingDefaultElementComparator() { return null; }
    public AtomicReferenceArrayAssert hasSizeBetween(int p0, int p1) { return null; }
    public AtomicReferenceArrayAssert doesNotContainSubsequence(Object[] p0) { return null; }
    public AtomicReferenceArrayAssert hasSizeLessThan(int p0) { return null; }
    public AtomicReferenceArrayAssert isSubsetOf(Object[] p0) { return null; }
    public AtomicReferenceArrayAssert as(String p0, Object[] p1) { return null; }
    public AtomicReferenceArrayAssert doesNotContainNull() { return null; }
    public AtomicReferenceArrayAssert satisfiesExactlyInAnyOrder(java.util.function.Consumer[] p0) { return null; }
    public AtomicReferenceArrayAssert areAtLeastOne(Condition p0) { return null; }
    public ObjectArrayAssert flatExtracting(java.util.function.Function p0) { return null; }
    public AtomicReferenceArrayAssert hasOnlyElementsOfTypes(Class[] p0) { return null; }
    public AtomicReferenceArrayAssert doesNotContain(Object[] p0) { return null; }
    public AtomicReferenceArrayAssert haveExactly(int p0, Condition p1) { return null; }
    public AtomicReferenceArrayAssert hasOnlyOneElementSatisfying(java.util.function.Consumer p0) { return null; }
    public AtomicReferenceArrayAssert containsOnlyNulls() { return null; }
    public ObjectArrayAssert extractingResultOf(String p0) { return null; }
    public AtomicReferenceArrayAssert contains(Object p0, org.assertj.core.data.Index p1) { return null; }
    public AtomicReferenceArrayAssert anyMatch(java.util.function.Predicate p0) { return null; }
    public AtomicReferenceArrayAssert endsWith(Object p0, Object[] p1) { return null; }
    public AtomicReferenceArrayAssert doNotHave(Condition p0) { return null; }
    public AtomicReferenceArrayAssert filteredOn(java.util.function.Function p0, Object p1) { return null; }
    public AtomicReferenceArrayAssert allMatch(java.util.function.Predicate p0) { return null; }
    public AtomicReferenceArrayAssert containsAnyElementsOf(Iterable p0) { return null; }
    public AtomicReferenceArrayAssert containsExactlyElementsOf(Iterable p0) { return null; }
    public AtomicReferenceArrayAssert containsOnlyOnce(Object[] p0) { return null; }
    public AtomicReferenceArrayAssert isSorted() { return null; }
    public AtomicReferenceArrayAssert anySatisfy(java.util.function.Consumer p0) { return null; }
    public AtomicReferenceArrayAssert inHexadecimal() { return null; }
    public AtomicReferenceArrayAssert containsAnyOf(Object[] p0) { return null; }
    public AtomicReferenceArrayAssert hasSizeLessThanOrEqualTo(int p0) { return null; }
    public ObjectArrayAssert extracting(String[] p0) { return null; }
    public AtomicReferenceArrayAssert areAtLeast(int p0, Condition p1) { return null; }
    public AtomicReferenceArrayAssert areAtMost(int p0, Condition p1) { return null; }
    public void AtomicReferenceArrayAssert(java.util.concurrent.atomic.AtomicReferenceArray p0) {}
    public AtomicReferenceArrayAssert endsWith(Object[] p0) { return null; }
    public AtomicReferenceArrayAssert hasSizeGreaterThan(int p0) { return null; }
    public AtomicReferenceArrayAssert hasSize(int p0) { return null; }
    public AtomicReferenceArrayAssert isSubsetOf(Iterable p0) { return null; }
    public AtomicReferenceArrayAssert filteredOn(java.util.function.Predicate p0) { return null; }
    public AtomicReferenceArrayAssert contains(Object[] p0) { return null; }
    public AtomicReferenceArrayAssert containsNull() { return null; }
    public AtomicReferenceArrayAssert allMatch(java.util.function.Predicate p0, String p1) { return null; }
    public AtomicReferenceArrayAssert noneSatisfy(java.util.function.Consumer p0) { return null; }
    public AtomicReferenceArrayAssert doesNotHaveAnyElementsOfTypes(Class[] p0) { return null; }
    public AtomicReferenceArrayAssert usingElementComparatorOnFields(String[] p0) { return null; }
    public AtomicReferenceArrayAssert haveAtMost(int p0, Condition p1) { return null; }
    public AtomicReferenceArrayAssert areNot(Condition p0) { return null; }
    public AtomicReferenceArrayAssert filteredOn(Condition p0) { return null; }
    public AtomicReferenceArrayAssert hasSameSizeAs(Object p0) { return null; }
    public AtomicReferenceArrayAssert are(Condition p0) { return null; }
    public AtomicReferenceArrayAssert usingComparatorForElementFieldsWithNames(java.util.Comparator p0, String[] p1) { return null; }
    public AtomicReferenceArrayAssert containsAll(Iterable p0) { return null; }
    public ObjectArrayAssert extracting(String p0, Class p1) { return null; }
    public AtomicReferenceArrayAssert usingComparatorForType(java.util.Comparator p0, Class p1) { return null; }
    public AtomicReferenceArrayAssert usingFieldByFieldElementComparator() { return null; }
    public AtomicReferenceArrayAssert haveAtLeastOne(Condition p0) { return null; }
    public AtomicReferenceArrayAssert isSortedAccordingTo(java.util.Comparator p0) { return null; }
    public AtomicReferenceArrayAssert hasSameElementsAs(Iterable p0) { return null; }
    public AtomicReferenceArrayAssert doesNotContainAnyElementsOf(Iterable p0) { return null; }
    public ObjectArrayAssert flatExtracting(String p0) { return null; }
    public AtomicReferenceArrayAssert doesNotContainSubsequence(Iterable p0) { return null; }
    public ObjectArrayAssert extracting(org.assertj.core.api.iterable.ThrowingExtractor p0) { return null; }
    public AtomicReferenceArrayAssert containsSequence(Iterable p0) { return null; }
    public void isNullOrEmpty() {}
    public AtomicReferenceArrayAssert filteredOn(String p0, org.assertj.core.api.filter.FilterOperator p1) { return null; }
    public AtomicReferenceArrayAssert containsSubsequence(Object[] p0) { return null; }
    public AtomicReferenceArrayAssert isNotEmpty() { return null; }
    public AtomicReferenceArrayAssert hasSameSizeAs(Iterable p0) { return null; }
    public AtomicReferenceArrayAssert areExactly(int p0, Condition p1) { return null; }
    public AtomicReferenceArrayAssert as(org.assertj.core.description.Description p0) { return null; }
    public ObjectArrayAssert flatExtracting(org.assertj.core.api.iterable.ThrowingExtractor p0) { return null; }
    public AtomicReferenceArrayAssert startsWith(Object[] p0) { return null; }
    public AtomicReferenceArrayAssert noneMatch(java.util.function.Predicate p0) { return null; }
    public AtomicReferenceArrayAssert filteredOnNull(String p0) { return null; }
    public void isEmpty() {}
    public AtomicReferenceArrayAssert doesNotContain(Object p0, org.assertj.core.data.Index p1) { return null; }
    public AtomicReferenceArrayAssert inBinary() { return null; }
    public AtomicReferenceArrayAssert usingElementComparatorIgnoringFields(String[] p0) { return null; }
    public AtomicReferenceArrayAssert hasSizeGreaterThanOrEqualTo(int p0) { return null; }
    public AtomicReferenceArrayAssert containsOnly(Object[] p0) { return null; }
    public AtomicReferenceArrayAssert doesNotContainSequence(Iterable p0) { return null; }
    public AtomicReferenceArrayAssert doesNotHaveDuplicates() { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class AtomicReferenceAssert {
    public void AtomicReferenceAssert(java.util.concurrent.atomic.AtomicReference p0) {}
    public AtomicReferenceAssert hasValue(Object p0) { return null; }
    public AtomicReferenceAssert hasValueMatching(java.util.function.Predicate p0, String p1) { return null; }
    public AtomicReferenceAssert hasValueMatching(java.util.function.Predicate p0) { return null; }
    public AtomicReferenceAssert doesNotHaveValue(Object p0) { return null; }
    public AtomicReferenceAssert hasValueSatisfying(java.util.function.Consumer p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class AtomicReferenceFieldUpdaterAssert {
    public void AtomicReferenceFieldUpdaterAssert(java.util.concurrent.atomic.AtomicReferenceFieldUpdater p0) {}
    public AtomicReferenceFieldUpdaterAssert hasValue(Object p0, Object p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class AtomicStampedReferenceAssert {
    public void AtomicStampedReferenceAssert(java.util.concurrent.atomic.AtomicStampedReference p0) {}
    public AtomicStampedReferenceAssert hasStamp(int p0) { return null; }
    public AtomicStampedReferenceAssert hasReference(Object p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class Boolean2DArrayAssert {
    public Boolean2DArrayAssert isNotEmpty() { return null; }
    public Abstract2DArrayAssert isDeepEqualTo(Object p0) { return null; }
    public void Boolean2DArrayAssert(boolean[][] p0) {}
    public Boolean2DArrayAssert isDeepEqualTo(boolean[][] p0) { return null; }
    public void isNullOrEmpty() {}
    public Boolean2DArrayAssert isEqualTo(Object p0) { return null; }
    public void isEmpty() {}
    public Boolean2DArrayAssert hasSameDimensionsAs(Object p0) { return null; }
    public Boolean2DArrayAssert hasDimensions(int p0, int p1) { return null; }
    public Boolean2DArrayAssert doesNotContain(boolean[] p0, org.assertj.core.data.Index p1) { return null; }
    public Boolean2DArrayAssert contains(boolean[] p0, org.assertj.core.data.Index p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class BooleanAssert {
    public void BooleanAssert(Boolean p0) {}
    public void BooleanAssert(java.util.concurrent.atomic.AtomicBoolean p0) {}
}
---
        package org.assertj.core.api;
        import java.io.*;

public class Byte2DArrayAssert {
    public void Byte2DArrayAssert(byte[][] p0) {}
    public Byte2DArrayAssert isNotEmpty() { return null; }
    public Abstract2DArrayAssert isDeepEqualTo(Object p0) { return null; }
    public Byte2DArrayAssert isDeepEqualTo(byte[][] p0) { return null; }
    public Byte2DArrayAssert doesNotContain(byte[] p0, org.assertj.core.data.Index p1) { return null; }
    public void isNullOrEmpty() {}
    public void isEmpty() {}
    public Byte2DArrayAssert hasDimensions(int p0, int p1) { return null; }
    public Byte2DArrayAssert hasSameDimensionsAs(Object p0) { return null; }
    public Byte2DArrayAssert isEqualTo(Object p0) { return null; }
    public Byte2DArrayAssert contains(byte[] p0, org.assertj.core.data.Index p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class Char2DArrayAssert {
    public Abstract2DArrayAssert isDeepEqualTo(Object p0) { return null; }
    public Char2DArrayAssert isDeepEqualTo(char[][] p0) { return null; }
    public void isNullOrEmpty() {}
    public Char2DArrayAssert hasDimensions(int p0, int p1) { return null; }
    public Char2DArrayAssert doesNotContain(char[] p0, org.assertj.core.data.Index p1) { return null; }
    public Char2DArrayAssert inUnicode() { return null; }
    public void isEmpty() {}
    public void Char2DArrayAssert(char[][] p0) {}
    public Char2DArrayAssert hasSameDimensionsAs(Object p0) { return null; }
    public Char2DArrayAssert contains(char[] p0, org.assertj.core.data.Index p1) { return null; }
    public Char2DArrayAssert isNotEmpty() { return null; }
    public Char2DArrayAssert isEqualTo(Object p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class ClassAssert {
    public ClassAssert hasAnnotations(Class[] p0) { return null; }
    public void ClassAssert(Class p0) {}
}
---
        package org.assertj.core.api;
        import java.io.*;

public class ClassBasedNavigableIterableAssert {
    public void ClassBasedNavigableIterableAssert(Iterable p0, Class p1, Class p2) {}
    public AbstractAssert toAssert(Object p0, String p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class ClassBasedNavigableListAssert {
    public void ClassBasedNavigableListAssert(java.util.List p0, Class p1) {}
    public AbstractAssert toAssert(Object p0, String p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public interface ComparableAssert {
}
---
        package org.assertj.core.api;
        import java.io.*;

public class CompletableFutureAssert {
}
---
        package org.assertj.core.api;
        import java.io.*;

public class Condition implements Descriptable {
    public boolean matches(Object p0) { return false; }
    public void Condition(org.assertj.core.description.Description p0) {}
    public void Condition(java.util.function.Predicate p0, String p1, Object[] p2) {}
    public String toString() { return null; }
    public void Condition() {}
    public Condition describedAs(org.assertj.core.description.Description p0) { return null; }
    public org.assertj.core.description.Description description() { return null; }
    public void Condition(String p0) {}
}
---
        package org.assertj.core.api;
        import java.io.*;

public interface Descriptable {
}
---
        package org.assertj.core.api;
        import java.io.*;

public class Double2DArrayAssert {
    public Abstract2DArrayAssert isDeepEqualTo(Object p0) { return null; }
    public Double2DArrayAssert isNotEmpty() { return null; }
    public Double2DArrayAssert doesNotContain(double[] p0, org.assertj.core.data.Index p1) { return null; }
    public Double2DArrayAssert hasDimensions(int p0, int p1) { return null; }
    public void isNullOrEmpty() {}
    public Double2DArrayAssert hasSameDimensionsAs(Object p0) { return null; }
    public Double2DArrayAssert contains(double[] p0, org.assertj.core.data.Index p1) { return null; }
    public void isEmpty() {}
    public Double2DArrayAssert isEqualTo(Object p0) { return null; }
    public void Double2DArrayAssert(double[][] p0) {}
    public Double2DArrayAssert isDeepEqualTo(double[][] p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class DoublePredicateAssert {
    public void DoublePredicateAssert(java.util.function.DoublePredicate p0) {}
    public DoublePredicateAssert rejects(double[] p0) { return null; }
    public DoublePredicateAssert accepts(double[] p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public interface EnumerableAssert {
}
---
        package org.assertj.core.api;
        import java.io.*;

public interface ExtensionPoints {
}
---
        package org.assertj.core.api;
        import java.io.*;

public class FactoryBasedNavigableIterableAssert {
    public void FactoryBasedNavigableIterableAssert(Iterable p0, Class p1, AssertFactory p2) {}
    public AbstractAssert toAssert(Object p0, String p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class FactoryBasedNavigableListAssert {
    public void FactoryBasedNavigableListAssert(java.util.List p0, Class p1, AssertFactory p2) {}
    public AbstractAssert toAssert(Object p0, String p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class Float2DArrayAssert {
    public Float2DArrayAssert contains(float[] p0, org.assertj.core.data.Index p1) { return null; }
    public Abstract2DArrayAssert isDeepEqualTo(Object p0) { return null; }
    public void Float2DArrayAssert(float[][] p0) {}
    public Float2DArrayAssert isEqualTo(Object p0) { return null; }
    public void isNullOrEmpty() {}
    public Float2DArrayAssert isNotEmpty() { return null; }
    public void isEmpty() {}
    public Float2DArrayAssert hasDimensions(int p0, int p1) { return null; }
    public Float2DArrayAssert isDeepEqualTo(float[][] p0) { return null; }
    public Float2DArrayAssert hasSameDimensionsAs(Object p0) { return null; }
    public Float2DArrayAssert doesNotContain(float[] p0, org.assertj.core.data.Index p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public interface FloatingPointNumberAssert extends NumberAssert {
}
---
        package org.assertj.core.api;
        import java.io.*;

public class FutureAssert {
}
---
        package org.assertj.core.api;
        import java.io.*;

public interface IndexedObjectEnumerableAssert extends ObjectEnumerableAssert {
}
---
        package org.assertj.core.api;
        import java.io.*;

public interface InstanceOfAssertFactories {
    InstanceOfAssertFactory atomicLongFieldUpdater(Class p0);
    InstanceOfAssertFactory completionStage(Class p0);
    InstanceOfAssertFactory optional(Class p0);
    InstanceOfAssertFactory atomicReferenceFieldUpdater(Class p0, Class p1);
    InstanceOfAssertFactory stream(Class p0);
    InstanceOfAssertFactory completableFuture(Class p0);
    InstanceOfAssertFactory list(Class p0);
    InstanceOfAssertFactory spliterator(Class p0);
    InstanceOfAssertFactory atomicMarkableReference(Class p0);
    InstanceOfAssertFactory predicate(Class p0);
    InstanceOfAssertFactory array2D(Class p0);
    InstanceOfAssertFactory atomicStampedReference(Class p0);
    InstanceOfAssertFactory future(Class p0);
    InstanceOfAssertFactory atomicIntegerFieldUpdater(Class p0);
    InstanceOfAssertFactory comparable(Class p0);
    InstanceOfAssertFactory atomicReference(Class p0);
    InstanceOfAssertFactory map(Class p0, Class p1);
    InstanceOfAssertFactory iterable(Class p0);
    InstanceOfAssertFactory array(Class p0);
    InstanceOfAssertFactory type(Class p0);
    InstanceOfAssertFactory atomicReferenceArray(Class p0);
    InstanceOfAssertFactory iterator(Class p0);
}
---
        package org.assertj.core.api;
        import java.io.*;

public class InstanceOfAssertFactory implements AssertFactory {
    public String toString() { return null; }
    public void InstanceOfAssertFactory(Class p0, AssertFactory p1) {}
    public AbstractAssert createAssert(Object p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class Int2DArrayAssert {
    public Int2DArrayAssert doesNotContain(int[] p0, org.assertj.core.data.Index p1) { return null; }
    public Abstract2DArrayAssert isDeepEqualTo(Object p0) { return null; }
    public Int2DArrayAssert isDeepEqualTo(int[][] p0) { return null; }
    public void isNullOrEmpty() {}
    public Int2DArrayAssert contains(int[] p0, org.assertj.core.data.Index p1) { return null; }
    public void isEmpty() {}
    public Int2DArrayAssert isEqualTo(Object p0) { return null; }
    public Int2DArrayAssert isNotEmpty() { return null; }
    public Int2DArrayAssert hasDimensions(int p0, int p1) { return null; }
    public Int2DArrayAssert hasSameDimensionsAs(Object p0) { return null; }
    public void Int2DArrayAssert(int[][] p0) {}
}
---
        package org.assertj.core.api;
        import java.io.*;

public class IntPredicateAssert {
    public IntPredicateAssert accepts(int[] p0) { return null; }
    public void IntPredicateAssert(java.util.function.IntPredicate p0) {}
    public IntPredicateAssert rejects(int[] p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class IterableAssert {
    public IterableAssert doesNotContain(Object[] p0) { return null; }
    public IterableAssert containsSubsequence(Object[] p0) { return null; }
    public AbstractListAssert map(java.util.function.Function[] p0) { return null; }
    public IterableAssert containsOnlyOnce(Object[] p0) { return null; }
    public IterableAssert containsAnyOf(Object[] p0) { return null; }
    public IterableAssert doesNotContainSubsequence(Object[] p0) { return null; }
    public AbstractListAssert extracting(java.util.function.Function[] p0) { return null; }
    public IterableAssert containsExactlyInAnyOrder(Object[] p0) { return null; }
    public IterableAssert isSubsetOf(Object[] p0) { return null; }
    public IterableAssert containsSequence(Object[] p0) { return null; }
    public AbstractListAssert flatExtracting(org.assertj.core.api.iterable.ThrowingExtractor[] p0) { return null; }
    public AbstractListAssert flatExtracting(java.util.function.Function[] p0) { return null; }
    public IterableAssert contains(Object[] p0) { return null; }
    public IterableAssert containsExactly(Object[] p0) { return null; }
    public IterableAssert satisfiesExactlyInAnyOrder(java.util.function.Consumer[] p0) { return null; }
    public AbstractListAssert flatMap(java.util.function.Function[] p0) { return null; }
    public IterableAssert containsOnly(Object[] p0) { return null; }
    public void IterableAssert(Iterable p0) {}
    public IterableAssert satisfiesExactly(java.util.function.Consumer[] p0) { return null; }
    public AbstractListAssert flatMap(org.assertj.core.api.iterable.ThrowingExtractor[] p0) { return null; }
    public IterableAssert doesNotContainSequence(Object[] p0) { return null; }
    public IterableAssert endsWith(Object p0, Object[] p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class IteratorAssert {
    public void IteratorAssert(java.util.Iterator p0) {}
}
---
        package org.assertj.core.api;
        import java.io.*;

public class ListAssert {
    public ListAssert contains(Object[] p0) { return null; }
    public ListAssert isEqualTo(Object p0) { return null; }
    public ListAssert isNotSameAs(Object p0) { return null; }
    public ListAssert doesNotContain(Object[] p0) { return null; }
    public ListAssert isOfAnyClassIn(Class[] p0) { return null; }
    public ListAssert isNotExactlyInstanceOf(Class p0) { return null; }
    public ListAssert containsSequence(Object[] p0) { return null; }
    public AbstractListAssert flatExtracting(org.assertj.core.api.iterable.ThrowingExtractor[] p0) { return null; }
    public ListAssert doesNotContainSubsequence(Object[] p0) { return null; }
    public ListAssert satisfiesExactlyInAnyOrder(java.util.function.Consumer[] p0) { return null; }
    public void ListAssert(java.util.stream.IntStream p0) {}
    public ListAssert isInstanceOf(Class p0) { return null; }
    public ListAssert isNotInstanceOfAny(Class[] p0) { return null; }
    public ListAssert containsAnyOf(Object[] p0) { return null; }
    public AbstractListAssert flatMap(java.util.function.Function[] p0) { return null; }
    public void ListAssert(java.util.stream.LongStream p0) {}
    public ListAssert startsWith(Object[] p0) { return null; }
    public ListAssert isNotInstanceOf(Class p0) { return null; }
    public ListAssert isNotOfAnyClassIn(Class[] p0) { return null; }
    public ListAssert containsExactlyInAnyOrder(Object[] p0) { return null; }
    public void ListAssert(java.util.stream.DoubleStream p0) {}
    public AbstractListAssert map(java.util.function.Function[] p0) { return null; }
    public ListAssert containsOnly(Object[] p0) { return null; }
    public AbstractListAssert extracting(java.util.function.Function[] p0) { return null; }
    public void ListAssert(java.util.List p0) {}
    public ListAssert containsSubsequence(Object[] p0) { return null; }
    public ListAssert satisfiesExactly(java.util.function.Consumer[] p0) { return null; }
    public AbstractListAssert flatExtracting(java.util.function.Function[] p0) { return null; }
    public ListAssert isInstanceOfAny(Class[] p0) { return null; }
    public ListAssert isSubsetOf(Object[] p0) { return null; }
    public ListAssert isExactlyInstanceOf(Class p0) { return null; }
    public void ListAssert(java.util.stream.Stream p0) {}
    public ListAssert containsOnlyOnce(Object[] p0) { return null; }
    public ListAssert containsExactly(Object[] p0) { return null; }
    public ListAssert doesNotContainSequence(Object[] p0) { return null; }
    public ListAssert endsWith(Object p0, Object[] p1) { return null; }
    public AbstractListAssert flatMap(org.assertj.core.api.iterable.ThrowingExtractor[] p0) { return null; }
    public ListAssert isSameAs(Object p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class Long2DArrayAssert {
    public Abstract2DArrayAssert isDeepEqualTo(Object p0) { return null; }
    public void Long2DArrayAssert(long[][] p0) {}
    public Long2DArrayAssert hasSameDimensionsAs(Object p0) { return null; }
    public Long2DArrayAssert contains(long[] p0, org.assertj.core.data.Index p1) { return null; }
    public Long2DArrayAssert isDeepEqualTo(long[][] p0) { return null; }
    public void isNullOrEmpty() {}
    public Long2DArrayAssert isNotEmpty() { return null; }
    public void isEmpty() {}
    public Long2DArrayAssert hasDimensions(int p0, int p1) { return null; }
    public Long2DArrayAssert isEqualTo(Object p0) { return null; }
    public Long2DArrayAssert doesNotContain(long[] p0, org.assertj.core.data.Index p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class LongAdderAssert {
    public void LongAdderAssert(java.util.concurrent.atomic.LongAdder p0) {}
}
---
        package org.assertj.core.api;
        import java.io.*;

public class LongPredicateAssert {
    public LongPredicateAssert accepts(long[] p0) { return null; }
    public LongPredicateAssert rejects(long[] p0) { return null; }
    public void LongPredicateAssert(java.util.function.LongPredicate p0) {}
}
---
        package org.assertj.core.api;
        import java.io.*;

public class MapAssert {
    public void MapAssert(java.util.Map p0) {}
    public MapAssert contains(java.util.Map.Entry[] p0) { return null; }
    public MapAssert containsOnly(java.util.Map.Entry[] p0) { return null; }
    public MapAssert containsExactly(java.util.Map.Entry[] p0) { return null; }
    public AbstractListAssert extracting(java.util.function.Function[] p0) { return null; }
    public AbstractListAssert extractingByKeys(Object[] p0) { return null; }
    public MapAssert containsAnyOf(java.util.Map.Entry[] p0) { return null; }
    public MapAssert containsOnlyKeys(Object[] p0) { return null; }
    public AbstractListAssert extractingFromEntries(java.util.function.Function[] p0) { return null; }
    public MapAssert containsValues(Object[] p0) { return null; }
    public MapAssert containsKeys(Object[] p0) { return null; }
    public MapAssert doesNotContainKeys(Object[] p0) { return null; }
    public MapAssert doesNotContain(java.util.Map.Entry[] p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class NotThrownAssert implements Descriptable {
    public void isThrownBy(ThrowableAssert.ThrowingCallable p0) {}
    public NotThrownAssert describedAs(org.assertj.core.description.Description p0) { return null; }
    public void NotThrownAssert() {}
}
---
        package org.assertj.core.api;
        import java.io.*;

public interface NumberAssert {
}
---
        package org.assertj.core.api;
        import java.io.*;

public class Object2DArrayAssert {
    public Abstract2DArrayAssert isDeepEqualTo(Object p0) { return null; }
    public Object2DArrayAssert doesNotContain(Object[] p0, org.assertj.core.data.Index p1) { return null; }
    public void isNullOrEmpty() {}
    public Object2DArrayAssert hasDimensions(int p0, int p1) { return null; }
    public Object2DArrayAssert isNotEmpty() { return null; }
    public void isEmpty() {}
    public Object2DArrayAssert hasSameDimensionsAs(Object p0) { return null; }
    public void Object2DArrayAssert(Object[][] p0) {}
    public Object2DArrayAssert isEqualTo(Object p0) { return null; }
    public Object2DArrayAssert isDeepEqualTo(Object[][] p0) { return null; }
    public Object2DArrayAssert contains(Object[] p0, org.assertj.core.data.Index p1) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class ObjectArrayAssert {
    public ObjectArrayAssert containsAnyOf(Object[] p0) { return null; }
    public ObjectArrayAssert endsWith(Object p0, Object[] p1) { return null; }
    public AbstractListAssert extracting(java.util.function.Function[] p0) { return null; }
    public ObjectArrayAssert contains(Object[] p0) { return null; }
    public void ObjectArrayAssert(Object[] p0) {}
    public ObjectArrayAssert containsExactlyInAnyOrder(Object[] p0) { return null; }
    public ObjectArrayAssert containsOnly(Object[] p0) { return null; }
    public ObjectArrayAssert doesNotContainSequence(Object[] p0) { return null; }
    public ObjectArrayAssert doesNotContainSubsequence(Object[] p0) { return null; }
    public ObjectArrayAssert satisfiesExactly(java.util.function.Consumer[] p0) { return null; }
    public ObjectArrayAssert containsSequence(Object[] p0) { return null; }
    public ObjectArrayAssert containsOnlyOnce(Object[] p0) { return null; }
    public ObjectArrayAssert isSubsetOf(Object[] p0) { return null; }
    public ObjectArrayAssert doesNotContain(Object[] p0) { return null; }
    public ObjectArrayAssert containsExactly(Object[] p0) { return null; }
    public ObjectArrayAssert containsSubsequence(Object[] p0) { return null; }
    public ObjectArrayAssert satisfiesExactlyInAnyOrder(java.util.function.Consumer[] p0) { return null; }
    public void ObjectArrayAssert(java.util.concurrent.atomic.AtomicReferenceArray p0) {}
}
---
        package org.assertj.core.api;
        import java.io.*;

public class ObjectAssert {
    public void ObjectAssert(java.util.concurrent.atomic.AtomicReference p0) {}
    public void ObjectAssert(Object p0) {}
    public AbstractListAssert extracting(java.util.function.Function[] p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public interface ObjectEnumerableAssert extends EnumerableAssert {
}
---
        package org.assertj.core.api;
        import java.io.*;

public class OptionalAssert {
}
---
        package org.assertj.core.api;
        import java.io.*;

public class OptionalDoubleAssert {
}
---
        package org.assertj.core.api;
        import java.io.*;

public class OptionalIntAssert {
}
---
        package org.assertj.core.api;
        import java.io.*;

public class OptionalLongAssert {
}
---
        package org.assertj.core.api;
        import java.io.*;

public class PredicateAssert {
    public PredicateAssert rejects(Object[] p0) { return null; }
    public PredicateAssert accepts(Object[] p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class RecursiveComparisonAssert {
    public RecursiveComparisonAssert isNotEqualTo(Object p0) { return null; }
    public RecursiveComparisonAssert withEqualsForFields(java.util.function.BiPredicate p0, String[] p1) { return null; }
    public RecursiveComparisonAssert withComparatorForType(java.util.Comparator p0, Class p1) { return null; }
    public org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration getRecursiveComparisonConfiguration() { return null; }
    public RecursiveComparisonAssert ignoringFields(String[] p0) { return null; }
    public RecursiveComparisonAssert withComparatorForFields(java.util.Comparator p0, String[] p1) { return null; }
    public RecursiveComparisonAssert ignoringCollectionOrder() { return null; }
    public RecursiveComparisonAssert ignoringFieldsMatchingRegexes(String[] p0) { return null; }
    public RecursiveComparisonAssert withEqualsForType(java.util.function.BiPredicate p0, Class p1) { return null; }
    public RecursiveComparisonAssert ignoringFieldsOfTypes(Class[] p0) { return null; }
    public RecursiveComparisonAssert ignoringOverriddenEqualsForFields(String[] p0) { return null; }
    public RecursiveComparisonAssert ignoringOverriddenEqualsForFieldsMatchingRegexes(String[] p0) { return null; }
    public RecursiveComparisonAssert ignoringCollectionOrderInFieldsMatchingRegexes(String[] p0) { return null; }
    public RecursiveComparisonAssert ignoringCollectionOrderInFields(String[] p0) { return null; }
    public RecursiveComparisonAssert ignoringActualNullFields() { return null; }
    public RecursiveComparisonAssert ignoringOverriddenEqualsForTypes(Class[] p0) { return null; }
    public RecursiveComparisonAssert isEqualTo(Object p0) { return null; }
    public RecursiveComparisonAssert ignoringActualEmptyOptionalFields() { return null; }
    public RecursiveComparisonAssert ignoringExpectedNullFields() { return null; }
    public RecursiveComparisonAssert withStrictTypeChecking() { return null; }
    public RecursiveComparisonAssert ignoringAllOverriddenEquals() { return null; }
    public void RecursiveComparisonAssert(Object p0, org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration p1) {}
    public RecursiveComparisonAssert usingOverriddenEquals() { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class Short2DArrayAssert {
    public Short2DArrayAssert isDeepEqualTo(short[][] p0) { return null; }
    public Abstract2DArrayAssert isDeepEqualTo(Object p0) { return null; }
    public void isEmpty() {}
    public Short2DArrayAssert isNotEmpty() { return null; }
    public Short2DArrayAssert contains(int[] p0, org.assertj.core.data.Index p1) { return null; }
    public Short2DArrayAssert doesNotContain(int[] p0, org.assertj.core.data.Index p1) { return null; }
    public void Short2DArrayAssert(short[][] p0) {}
    public Short2DArrayAssert contains(short[] p0, org.assertj.core.data.Index p1) { return null; }
    public Short2DArrayAssert hasDimensions(int p0, int p1) { return null; }
    public Short2DArrayAssert doesNotContain(short[] p0, org.assertj.core.data.Index p1) { return null; }
    public void isNullOrEmpty() {}
    public Short2DArrayAssert hasSameDimensionsAs(Object p0) { return null; }
    public Short2DArrayAssert isEqualTo(Object p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class SpliteratorAssert {
}
---
        package org.assertj.core.api;
        import java.io.*;

public class ThrowableAssert {
    public void ThrowableAssert(Throwable p0) {}
    public static Throwable catchThrowableOfType(ThrowableAssert.ThrowingCallable p0, Class p1) { return null; }
    public void ThrowableAssert(java.util.concurrent.Callable p0) {}
    public static Throwable catchThrowable(ThrowableAssert.ThrowingCallable p0) { return null; }
    public class ThrowingCallable{}
}
---
        package org.assertj.core.api;
        import java.io.*;

public class ThrowableTypeAssert implements Descriptable {
    public void ThrowableTypeAssert(Class p0) {}
    public ThrowableTypeAssert describedAs(org.assertj.core.description.Description p0) { return null; }
    public ThrowableAssertAlternative isThrownBy(ThrowableAssert.ThrowingCallable p0) { return null; }
}
---
        package org.assertj.core.api;
        import java.io.*;

public class WritableAssertionInfo implements AssertionInfo {
    public boolean hasDescription() { return false; }
    public void useRepresentation(org.assertj.core.presentation.Representation p0) {}
    public void useHexadecimalRepresentation() {}
    public void useBinaryRepresentation() {}
    public void overridingErrorMessage(String p0) {}
    public void useUnicodeRepresentation() {}
    public void WritableAssertionInfo(org.assertj.core.presentation.Representation p0) {}
    public org.assertj.core.description.Description description() { return null; }
    public static String mostRelevantDescriptionIn(WritableAssertionInfo p0, String p1) { return null; }
    public void description(org.assertj.core.description.Description p0) {}
    public void description(String p0, Object[] p1) {}
    public String toString() { return null; }
    public String overridingErrorMessage() { return null; }
    public void WritableAssertionInfo() {}
    public void overridingErrorMessage(java.util.function.Supplier p0) {}
    public String descriptionText() { return null; }
    public org.assertj.core.presentation.Representation representation() { return null; }
}
---
        package org.assertj.core.api.filter;
        import java.io.*;

public abstract class FilterOperator {
}
---
        package org.assertj.core.api.filter;
        import java.io.*;

public class Filters {
    public java.util.List get() { return null; }
    public static Filters filter(Iterable p0) { return null; }
    public Filters having(org.assertj.core.api.Condition p0) { return null; }
    public Filters equalsTo(Object p0) { return null; }
    public Filters with(String p0, Object p1) { return null; }
    public Filters and(String p0) { return null; }
    public static Filters filter(Object[] p0) { return null; }
    public Filters notEqualsTo(Object p0) { return null; }
    public Filters being(org.assertj.core.api.Condition p0) { return null; }
    public Filters in(Object[] p0) { return null; }
    public Filters with(String p0) { return null; }
    public Filters notIn(Object[] p0) { return null; }
}
---
        package org.assertj.core.api.filter;
        import java.io.*;

public class InFilter {
    public Filters applyOn(Filters p0) { return null; }
    public static InFilter in(Object[] p0) { return null; }
}
---
        package org.assertj.core.api.filter;
        import java.io.*;

public class NotFilter {
    public static NotFilter not(Object p0) { return null; }
    public Filters applyOn(Filters p0) { return null; }
}
---
        package org.assertj.core.api.filter;
        import java.io.*;

public class NotInFilter {
    public boolean filter(Object p0) { return false; }
    public Filters applyOn(Filters p0) { return null; }
    public static NotInFilter notIn(Object[] p0) { return null; }
}
---
        package org.assertj.core.api.iterable;
        import java.io.*;

public interface ThrowingExtractor extends java.util.function.Function {
}
---
        package org.assertj.core.api.recursive.comparison;
        import java.io.*;

public class RecursiveComparisonConfiguration {
    public java.util.Set getIgnoredCollectionOrderInFields() { return null; }
    public void ignoreOverriddenEqualsForFields(String[] p0) {}
    public void ignoreOverriddenEqualsForFieldsMatchingRegexes(String[] p0) {}
    public void registerEqualsForType(java.util.function.BiPredicate p0, Class p1) {}
    public java.util.Comparator getComparatorForType(Class p0) { return null; }
    public void ignoreCollectionOrderInFieldsMatchingRegexes(String[] p0) {}
    public boolean equals(Object p0) { return false; }
    public void setIgnoreAllExpectedNullFields(boolean p0) {}
    public java.util.List getIgnoredOverriddenEqualsForFieldsMatchingRegexes() { return null; }
    public void ignoreCollectionOrderInFields(String[] p0) {}
    public boolean hasComparatorForField(String p0) { return false; }
    public void setIgnoreAllActualEmptyOptionalFields(boolean p0) {}
    public void ignoreAllOverriddenEquals() {}
    public java.util.Set getIgnoredTypes() { return null; }
    public void registerComparatorForType(java.util.Comparator p0, Class p1) {}
    public void useOverriddenEquals() {}
    public void ignoreCollectionOrder(boolean p0) {}
    public java.util.List getIgnoredOverriddenEqualsForFields() { return null; }
    public java.util.List getIgnoredOverriddenEqualsForTypes() { return null; }
    public FieldComparators getFieldComparators() { return null; }
    public org.assertj.core.internal.TypeComparators getTypeComparators() { return null; }
    public void ignoreFields(String[] p0) {}
    public int hashCode() { return 0; }
    public void registerComparatorForFields(java.util.Comparator p0, String[] p1) {}
    public boolean hasComparatorForType(Class p0) { return false; }
    public java.util.List getIgnoredFieldsRegexes() { return null; }
    public java.util.List getIgnoredCollectionOrderInFieldsMatchingRegexes() { return null; }
    public void strictTypeChecking(boolean p0) {}
    public java.util.Comparator getComparatorForField(String p0) { return null; }
    public boolean hasCustomComparators() { return false; }
    public String toString() { return null; }
    public void RecursiveComparisonConfiguration() {}
    public java.util.stream.Stream comparatorByFields() { return null; }
    public void ignoreFieldsMatchingRegexes(String[] p0) {}
    public void ignoreFieldsOfTypes(Class[] p0) {}
    public java.util.Set getIgnoredFields() { return null; }
    public String multiLineDescription(org.assertj.core.presentation.Representation p0) { return null; }
    public void setIgnoreAllActualNullFields(boolean p0) {}
    public void ignoreOverriddenEqualsForTypes(Class[] p0) {}
    public void registerEqualsForFields(java.util.function.BiPredicate p0, String[] p1) {}
    public boolean isInStrictTypeCheckingMode() { return false; }
}
---
        package org.assertj.core.condition;
        import java.io.*;

public class DoesNotHave {
    public static DoesNotHave doesNotHave(org.assertj.core.api.Condition p0) { return null; }
}
---
        package org.assertj.core.condition;
        import java.io.*;

public class Not {
    public static Not not(org.assertj.core.api.Condition p0) { return null; }
}
---
        package org.assertj.core.data;
        import java.io.*;

public class Index {
    public static Index atIndex(int p0) { return null; }
    public String toString() { return null; }
    public int hashCode() { return 0; }
    public boolean equals(Object p0) { return false; }
}
---
        package org.assertj.core.data;
        import java.io.*;

public class MapEntry implements java.util.Map.Entry {
    public Object setValue(Object p0) { return null; }
    public static MapEntry entry(Object p0, Object p1) { return null; }
    public String toString() { return null; }
    public int hashCode() { return 0; }
    public Object getValue() { return null; }
    public boolean equals(Object p0) { return false; }
    public Object getKey() { return null; }
}
---
        package org.assertj.core.data;
        import java.io.*;

public class Offset {
    public String toString() { return null; }
    public static Offset strictOffset(Number p0) { return null; }
    public static Offset offset(Number p0) { return null; }
    public int hashCode() { return 0; }
    public boolean equals(Object p0) { return false; }
}
---
        package org.assertj.core.data;
        import java.io.*;

public class Percentage {
    public String toString() { return null; }
    public static Percentage withPercentage(double p0) { return null; }
    public int hashCode() { return 0; }
    public boolean equals(Object p0) { return false; }
}
---
        package org.assertj.core.data;
        import java.io.*;

public abstract class TemporalUnitOffset implements TemporalOffset {
    public java.time.temporal.TemporalUnit getUnit() { return null; }
    public String getBeyondOffsetDifferenceDescription(java.time.temporal.Temporal p0, java.time.temporal.Temporal p1) { return null; }
    public void TemporalUnitOffset(long p0, java.time.temporal.TemporalUnit p1) {}
}
---
        package org.assertj.core.description;
        import java.io.*;

public abstract class Description {
    public static String mostRelevantDescription(Description p0, String p1) { return null; }
    public void Description() {}
    public String toString() { return null; }
    public static Description emptyIfNull(Description p0) { return null; }
}
---
        package org.assertj.core.groups;
        import java.io.*;

public class Properties {
    public static Properties extractProperty(String p0) { return null; }
    public java.util.List from(Object[] p0) { return null; }
    public Properties ofType(Class p0) { return null; }
    public java.util.List from(Iterable p0) { return null; }
    public static Properties extractProperty(String p0, Class p1) { return null; }
}
---
        package org.assertj.core.groups;
        import java.io.*;

public class Tuple {
    public Object[] toArray() { return null; }
    public void Tuple(Object[] p0) {}
    public String toString() { return null; }
    public int hashCode() { return 0; }
    public boolean equals(Object p0) { return false; }
    public java.util.List toList() { return null; }
    public static Tuple tuple(Object[] p0) { return null; }
}
---
        package org.assertj.core.internal;
        import java.io.*;

public class TypeComparators {
    public void TypeComparators() {}
    public String toString() { return null; }
    public java.util.Comparator get(Class p0) { return null; }
    public int hashCode() { return 0; }
    public static TypeComparators defaultTypeComparators() { return null; }
    public void put(Class p0, java.util.Comparator p1) {}
    public boolean hasComparatorForType(Class p0) { return false; }
    public boolean isEmpty() { return false; }
    public boolean equals(Object p0) { return false; }
    public void clear() {}
    public java.util.stream.Stream comparatorByTypes() { return null; }
}
---
        package org.assertj.core.presentation;
        import java.io.*;

public interface Representation {
}
---
