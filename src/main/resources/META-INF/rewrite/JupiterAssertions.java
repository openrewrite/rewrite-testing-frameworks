/*
 * Copyright 2015-2021 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * https://www.eclipse.org/legal/epl-v20.html
 */
package org.junit.jupiter.api;
import java.io.*;

public class Assertions {
    public static Object fail() { return (Object) (Object) null; }
    public static Object fail(String p0) { return (Object) (Object) null; }
    public static Object fail(String p0, Throwable p1) { return (Object) (Object) null; }
    public static Object fail(Throwable p0) { return (Object) (Object) null; }
    public static Object fail(java.util.function.Supplier p0) { return (Object) (Object) null; }
    public static void assertTrue(boolean p0) {}
    public static void assertTrue(boolean p0, java.util.function.Supplier p1) {}
    public static void assertTrue(java.util.function.BooleanSupplier p0) {}
    public static void assertTrue(java.util.function.BooleanSupplier p0, String p1) {}
    public static void assertTrue(boolean p0, String p1) {}
    public static void assertTrue(java.util.function.BooleanSupplier p0, java.util.function.Supplier p1) {}
    public static void assertFalse(boolean p0) {}
    public static void assertFalse(boolean p0, String p1) {}
    public static void assertFalse(boolean p0, java.util.function.Supplier p1) {}
    public static void assertFalse(java.util.function.BooleanSupplier p0) {}
    public static void assertFalse(java.util.function.BooleanSupplier p0, String p1) {}
    public static void assertFalse(java.util.function.BooleanSupplier p0, java.util.function.Supplier p1) {}
    public static void assertNull(Object p0) {}
    public static void assertNull(Object p0, String p1) {}
    public static void assertNull(Object p0, java.util.function.Supplier p1) {}
    public static void assertNotNull(Object p0) {}
    public static void assertNotNull(Object p0, String p1) {}
    public static void assertNotNull(Object p0, java.util.function.Supplier p1) {}
    public static void assertEquals(short p0, short p1) {}
    public static void assertEquals(short p0, Short p1) {}
    public static void assertEquals(Short p0, short p1) {}
    public static void assertEquals(Short p0, Short p1) {}
    public static void assertEquals(short p0, short p1, String p2) {}
    public static void assertEquals(short p0, Short p1, String p2) {}
    public static void assertEquals(Short p0, short p1, String p2) {}
    public static void assertEquals(Short p0, Short p1, String p2) {}
    public static void assertEquals(short p0, short p1, java.util.function.Supplier p2) {}
    public static void assertEquals(short p0, Short p1, java.util.function.Supplier p2) {}
    public static void assertEquals(Short p0, short p1, java.util.function.Supplier p2) {}
    public static void assertEquals(Short p0, Short p1, java.util.function.Supplier p2) {}
    public static void assertEquals(byte p0, byte p1) {}
    public static void assertEquals(byte p0, Byte p1) {}
    public static void assertEquals(Byte p0, byte p1) {}
    public static void assertEquals(Byte p0, Byte p1) {}
    public static void assertEquals(byte p0, byte p1, String p2) {}
    public static void assertEquals(byte p0, Byte p1, String p2) {}
    public static void assertEquals(Byte p0, byte p1, String p2) {}
    public static void assertEquals(Byte p0, Byte p1, String p2) {}
    public static void assertEquals(byte p0, byte p1, java.util.function.Supplier p2) {}
    public static void assertEquals(byte p0, Byte p1, java.util.function.Supplier p2) {}
    public static void assertEquals(Byte p0, byte p1, java.util.function.Supplier p2) {}
    public static void assertEquals(Byte p0, Byte p1, java.util.function.Supplier p2) {}
    public static void assertEquals(int p0, int p1) {}
    public static void assertEquals(int p0, Integer p1) {}
    public static void assertEquals(Integer p0, int p1) {}
    public static void assertEquals(Integer p0, Integer p1) {}
    public static void assertEquals(int p0, int p1, String p2) {}
    public static void assertEquals(int p0, Integer p1, String p2) {}
    public static void assertEquals(Integer p0, int p1, String p2) {}
    public static void assertEquals(Integer p0, Integer p1, String p2) {}
    public static void assertEquals(int p0, int p1, java.util.function.Supplier p2) {}
    public static void assertEquals(int p0, Integer p1, java.util.function.Supplier p2) {}
    public static void assertEquals(Integer p0, int p1, java.util.function.Supplier p2) {}
    public static void assertEquals(Integer p0, Integer p1, java.util.function.Supplier p2) {}
    public static void assertEquals(long p0, long p1) {}
    public static void assertEquals(long p0, Long p1) {}
    public static void assertEquals(Long p0, long p1) {}
    public static void assertEquals(Long p0, Long p1) {}
    public static void assertEquals(long p0, long p1, String p2) {}
    public static void assertEquals(long p0, Long p1, String p2) {}
    public static void assertEquals(Long p0, long p1, String p2) {}
    public static void assertEquals(Long p0, Long p1, String p2) {}
    public static void assertEquals(long p0, long p1, java.util.function.Supplier p2) {}
    public static void assertEquals(long p0, Long p1, java.util.function.Supplier p2) {}
    public static void assertEquals(Long p0, long p1, java.util.function.Supplier p2) {}
    public static void assertEquals(Long p0, Long p1, java.util.function.Supplier p2) {}
    public static void assertEquals(float p0, float p1) {}
    public static void assertEquals(float p0, Float p1) {}
    public static void assertEquals(Float p0, float p1) {}
    public static void assertEquals(Float p0, Float p1) {}
    public static void assertEquals(float p0, float p1, String p2) {}
    public static void assertEquals(float p0, Float p1, String p2) {}
    public static void assertEquals(Float p0, float p1, String p2) {}
    public static void assertEquals(Float p0, Float p1, String p2) {}
    public static void assertEquals(float p0, float p1, java.util.function.Supplier p2) {}
    public static void assertEquals(float p0, Float p1, java.util.function.Supplier p2) {}
    public static void assertEquals(Float p0, float p1, java.util.function.Supplier p2) {}
    public static void assertEquals(Float p0, Float p1, java.util.function.Supplier p2) {}
    public static void assertEquals(float p0, float p1, float p2) {}
    public static void assertEquals(float p0, float p1, float p2, String p3) {}
    public static void assertEquals(float p0, float p1, float p2, java.util.function.Supplier p3) {}
    public static void assertEquals(double p0, double p1) {}
    public static void assertEquals(double p0, Double p1) {}
    public static void assertEquals(Double p0, double p1) {}
    public static void assertEquals(Double p0, Double p1) {}
    public static void assertEquals(double p0, double p1, String p2) {}
    public static void assertEquals(double p0, Double p1, String p2) {}
    public static void assertEquals(Double p0, double p1, String p2) {}
    public static void assertEquals(Double p0, Double p1, String p2) {}
    public static void assertEquals(double p0, double p1, java.util.function.Supplier p2) {}
    public static void assertEquals(double p0, Double p1, java.util.function.Supplier p2) {}
    public static void assertEquals(Double p0, double p1, java.util.function.Supplier p2) {}
    public static void assertEquals(Double p0, Double p1, java.util.function.Supplier p2) {}
    public static void assertEquals(double p0, double p1, double p2) {}
    public static void assertEquals(double p0, double p1, double p2, String p3) {}
    public static void assertEquals(double p0, double p1, double p2, java.util.function.Supplier p3) {}
    public static void assertEquals(char p0, char p1) {}
    public static void assertEquals(char p0, Character p1) {}
    public static void assertEquals(Character p0, char p1) {}
    public static void assertEquals(Character p0, Character p1) {}
    public static void assertEquals(char p0, char p1, String p2) {}
    public static void assertEquals(char p0, Character p1, String p2) {}
    public static void assertEquals(Character p0, char p1, String p2) {}
    public static void assertEquals(Character p0, Character p1, String p2) {}
    public static void assertEquals(char p0, char p1, java.util.function.Supplier p2) {}
    public static void assertEquals(char p0, Character p1, java.util.function.Supplier p2) {}
    public static void assertEquals(Character p0, char p1, java.util.function.Supplier p2) {}
    public static void assertEquals(Character p0, Character p1, java.util.function.Supplier p2) {}
    public static void assertEquals(Object p0, Object p1) {}
    public static void assertEquals(Object p0, Object p1, String p2) {}
    public static void assertEquals(Object p0, Object p1, java.util.function.Supplier p2) {}
    public static void assertArrayEquals(boolean[] p0, boolean[] p1) {}
    public static void assertArrayEquals(boolean[] p0, boolean[] p1, String p2) {}
    public static void assertArrayEquals(boolean[] p0, boolean[] p1, java.util.function.Supplier p2) {}
    public static void assertArrayEquals(char[] p0, char[] p1) {}
    public static void assertArrayEquals(char[] p0, char[] p1, String p2) {}
    public static void assertArrayEquals(char[] p0, char[] p1, java.util.function.Supplier p2) {}
    public static void assertArrayEquals(byte[] p0, byte[] p1) {}
    public static void assertArrayEquals(byte[] p0, byte[] p1, String p2) {}
    public static void assertArrayEquals(byte[] p0, byte[] p1, java.util.function.Supplier p2) {}
    public static void assertArrayEquals(short[] p0, short[] p1) {}
    public static void assertArrayEquals(short[] p0, short[] p1, String p2) {}
    public static void assertArrayEquals(short[] p0, short[] p1, java.util.function.Supplier p2) {}
    public static void assertArrayEquals(int[] p0, int[] p1) {}
    public static void assertArrayEquals(int[] p0, int[] p1, String p2) {}
    public static void assertArrayEquals(int[] p0, int[] p1, java.util.function.Supplier p2) {}
    public static void assertArrayEquals(long[] p0, long[] p1) {}
    public static void assertArrayEquals(long[] p0, long[] p1, String p2) {}
    public static void assertArrayEquals(long[] p0, long[] p1, java.util.function.Supplier p2) {}
    public static void assertArrayEquals(float[] p0, float[] p1) {}
    public static void assertArrayEquals(float[] p0, float[] p1, String p2) {}
    public static void assertArrayEquals(float[] p0, float[] p1, java.util.function.Supplier p2) {}
    public static void assertArrayEquals(float[] p0, float[] p1, float p2) {}
    public static void assertArrayEquals(float[] p0, float[] p1, float p2, String p3) {}
    public static void assertArrayEquals(float[] p0, float[] p1, float p2, java.util.function.Supplier p3) {}
    public static void assertArrayEquals(double[] p0, double[] p1) {}
    public static void assertArrayEquals(double[] p0, double[] p1, String p2) {}
    public static void assertArrayEquals(double[] p0, double[] p1, java.util.function.Supplier p2) {}
    public static void assertArrayEquals(double[] p0, double[] p1, double p2) {}
    public static void assertArrayEquals(double[] p0, double[] p1, double p2, String p3) {}
    public static void assertArrayEquals(double[] p0, double[] p1, double p2, java.util.function.Supplier p3) {}
    public static void assertArrayEquals(Object[] p0, Object[] p1) {}
    public static void assertArrayEquals(Object[] p0, Object[] p1, String p2) {}
    public static void assertArrayEquals(Object[] p0, Object[] p1, java.util.function.Supplier p2) {}
    public static void assertIterableEquals(Iterable p0, Iterable p1) {}
    public static void assertIterableEquals(Iterable p0, Iterable p1, String p2) {}
    public static void assertIterableEquals(Iterable p0, Iterable p1, java.util.function.Supplier p2) {}
    public static void assertLinesMatch(java.util.List p0, java.util.List p1) {}
    public static void assertLinesMatch(java.util.List p0, java.util.List p1, String p2) {}
    public static void assertLinesMatch(java.util.List p0, java.util.List p1, java.util.function.Supplier p2) {}
    public static void assertLinesMatch(java.util.stream.Stream p0, java.util.stream.Stream p1) {}
    public static void assertLinesMatch(java.util.stream.Stream p0, java.util.stream.Stream p1, String p2) {}
    public static void assertLinesMatch(java.util.stream.Stream p0, java.util.stream.Stream p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(byte p0, byte p1) {}
    public static void assertNotEquals(byte p0, Byte p1) {}
    public static void assertNotEquals(Byte p0, byte p1) {}
    public static void assertNotEquals(Byte p0, Byte p1) {}
    public static void assertNotEquals(byte p0, byte p1, String p2) {}
    public static void assertNotEquals(byte p0, Byte p1, String p2) {}
    public static void assertNotEquals(Byte p0, byte p1, String p2) {}
    public static void assertNotEquals(Byte p0, Byte p1, String p2) {}
    public static void assertNotEquals(byte p0, byte p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(byte p0, Byte p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(Byte p0, byte p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(Byte p0, Byte p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(short p0, short p1) {}
    public static void assertNotEquals(short p0, Short p1) {}
    public static void assertNotEquals(Short p0, short p1) {}
    public static void assertNotEquals(Short p0, Short p1) {}
    public static void assertNotEquals(short p0, short p1, String p2) {}
    public static void assertNotEquals(short p0, Short p1, String p2) {}
    public static void assertNotEquals(Short p0, short p1, String p2) {}
    public static void assertNotEquals(Short p0, Short p1, String p2) {}
    public static void assertNotEquals(short p0, short p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(short p0, Short p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(Short p0, short p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(Short p0, Short p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(int p0, int p1) {}
    public static void assertNotEquals(int p0, Integer p1) {}
    public static void assertNotEquals(Integer p0, int p1) {}
    public static void assertNotEquals(Integer p0, Integer p1) {}
    public static void assertNotEquals(int p0, int p1, String p2) {}
    public static void assertNotEquals(int p0, Integer p1, String p2) {}
    public static void assertNotEquals(Integer p0, int p1, String p2) {}
    public static void assertNotEquals(Integer p0, Integer p1, String p2) {}
    public static void assertNotEquals(int p0, int p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(int p0, Integer p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(Integer p0, int p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(Integer p0, Integer p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(long p0, long p1) {}
    public static void assertNotEquals(long p0, Long p1) {}
    public static void assertNotEquals(Long p0, long p1) {}
    public static void assertNotEquals(Long p0, Long p1) {}
    public static void assertNotEquals(long p0, long p1, String p2) {}
    public static void assertNotEquals(long p0, Long p1, String p2) {}
    public static void assertNotEquals(Long p0, long p1, String p2) {}
    public static void assertNotEquals(Long p0, Long p1, String p2) {}
    public static void assertNotEquals(long p0, long p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(long p0, Long p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(Long p0, long p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(Long p0, Long p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(float p0, float p1) {}
    public static void assertNotEquals(float p0, Float p1) {}
    public static void assertNotEquals(Float p0, float p1) {}
    public static void assertNotEquals(Float p0, Float p1) {}
    public static void assertNotEquals(float p0, float p1, String p2) {}
    public static void assertNotEquals(float p0, Float p1, String p2) {}
    public static void assertNotEquals(Float p0, float p1, String p2) {}
    public static void assertNotEquals(Float p0, Float p1, String p2) {}
    public static void assertNotEquals(float p0, float p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(float p0, Float p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(Float p0, float p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(Float p0, Float p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(float p0, float p1, float p2) {}
    public static void assertNotEquals(float p0, float p1, float p2, String p3) {}
    public static void assertNotEquals(float p0, float p1, float p2, java.util.function.Supplier p3) {}
    public static void assertNotEquals(double p0, double p1) {}
    public static void assertNotEquals(double p0, Double p1) {}
    public static void assertNotEquals(Double p0, double p1) {}
    public static void assertNotEquals(Double p0, Double p1) {}
    public static void assertNotEquals(double p0, double p1, String p2) {}
    public static void assertNotEquals(double p0, Double p1, String p2) {}
    public static void assertNotEquals(Double p0, double p1, String p2) {}
    public static void assertNotEquals(Double p0, Double p1, String p2) {}
    public static void assertNotEquals(double p0, double p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(double p0, Double p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(Double p0, double p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(Double p0, Double p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(double p0, double p1, double p2) {}
    public static void assertNotEquals(double p0, double p1, double p2, String p3) {}
    public static void assertNotEquals(double p0, double p1, double p2, java.util.function.Supplier p3) {}
    public static void assertNotEquals(char p0, char p1) {}
    public static void assertNotEquals(char p0, Character p1) {}
    public static void assertNotEquals(Character p0, char p1) {}
    public static void assertNotEquals(Character p0, Character p1) {}
    public static void assertNotEquals(char p0, char p1, String p2) {}
    public static void assertNotEquals(char p0, Character p1, String p2) {}
    public static void assertNotEquals(Character p0, char p1, String p2) {}
    public static void assertNotEquals(Character p0, Character p1, String p2) {}
    public static void assertNotEquals(char p0, char p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(char p0, Character p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(Character p0, char p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(Character p0, Character p1, java.util.function.Supplier p2) {}
    public static void assertNotEquals(Object p0, Object p1) {}
    public static void assertNotEquals(Object p0, Object p1, String p2) {}
    public static void assertNotEquals(Object p0, Object p1, java.util.function.Supplier p2) {}
    public static void assertSame(Object p0, Object p1) {}
    public static void assertSame(Object p0, Object p1, String p2) {}
    public static void assertSame(Object p0, Object p1, java.util.function.Supplier p2) {}
    public static void assertNotSame(Object p0, Object p1) {}
    public static void assertNotSame(Object p0, Object p1, String p2) {}
    public static void assertNotSame(Object p0, Object p1, java.util.function.Supplier p2) {}
    public static void assertAll(org.junit.jupiter.api.function.Executable[] p0) throws org.opentest4j.MultipleFailuresError {}
    public static void assertAll(String p0, org.junit.jupiter.api.function.Executable[] p1) throws org.opentest4j.MultipleFailuresError {}
    public static void assertAll(java.util.Collection p0) throws org.opentest4j.MultipleFailuresError {}
    public static void assertAll(String p0, java.util.Collection p1) throws org.opentest4j.MultipleFailuresError {}
    public static void assertAll(java.util.stream.Stream p0) throws org.opentest4j.MultipleFailuresError {}
    public static void assertAll(String p0, java.util.stream.Stream p1) throws org.opentest4j.MultipleFailuresError {}
    public static Throwable assertThrows(Class p0, org.junit.jupiter.api.function.Executable p1) { return (Throwable) (Object) null; }
    public static Throwable assertThrows(Class p0, org.junit.jupiter.api.function.Executable p1, String p2) { return (Throwable) (Object) null; }
    public static Throwable assertThrows(Class p0, org.junit.jupiter.api.function.Executable p1, java.util.function.Supplier p2) { return (Throwable) (Object) null; }
    public static void assertDoesNotThrow(org.junit.jupiter.api.function.Executable p0) {}
    public static void assertDoesNotThrow(org.junit.jupiter.api.function.Executable p0, String p1) {}
    public static void assertDoesNotThrow(org.junit.jupiter.api.function.Executable p0, java.util.function.Supplier p1) {}
    public static Object assertDoesNotThrow(org.junit.jupiter.api.function.ThrowingSupplier p0) { return (Object) (Object) null; }
    public static Object assertDoesNotThrow(org.junit.jupiter.api.function.ThrowingSupplier p0, String p1) { return (Object) (Object) null; }
    public static Object assertDoesNotThrow(org.junit.jupiter.api.function.ThrowingSupplier p0, java.util.function.Supplier p1) { return (Object) (Object) null; }
    public static void assertTimeout(java.time.Duration p0, org.junit.jupiter.api.function.Executable p1) {}
    public static void assertTimeout(java.time.Duration p0, org.junit.jupiter.api.function.Executable p1, String p2) {}
    public static void assertTimeout(java.time.Duration p0, org.junit.jupiter.api.function.Executable p1, java.util.function.Supplier p2) {}
    public static Object assertTimeout(java.time.Duration p0, org.junit.jupiter.api.function.ThrowingSupplier p1) { return (Object) (Object) null; }
    public static Object assertTimeout(java.time.Duration p0, org.junit.jupiter.api.function.ThrowingSupplier p1, String p2) { return (Object) (Object) null; }
    public static Object assertTimeout(java.time.Duration p0, org.junit.jupiter.api.function.ThrowingSupplier p1, java.util.function.Supplier p2) { return (Object) (Object) null; }
    public static void assertTimeoutPreemptively(java.time.Duration p0, org.junit.jupiter.api.function.Executable p1) {}
    public static void assertTimeoutPreemptively(java.time.Duration p0, org.junit.jupiter.api.function.Executable p1, String p2) {}
    public static void assertTimeoutPreemptively(java.time.Duration p0, org.junit.jupiter.api.function.Executable p1, java.util.function.Supplier p2) {}
    public static Object assertTimeoutPreemptively(java.time.Duration p0, org.junit.jupiter.api.function.ThrowingSupplier p1) { return (Object) (Object) null; }
    public static Object assertTimeoutPreemptively(java.time.Duration p0, org.junit.jupiter.api.function.ThrowingSupplier p1, String p2) { return (Object) (Object) null; }
    public static Object assertTimeoutPreemptively(java.time.Duration p0, org.junit.jupiter.api.function.ThrowingSupplier p1, java.util.function.Supplier p2) { return (Object) (Object) null; }
}

---

package org.junit.jupiter.api.function;
import java.io.*;

public interface Executable {
    void execute() throws Throwable;
}

public interface ThrowingSupplier {
    Object get() throws Throwable;
}

---

package org.opentest4j;

public class MultipleFailuresError extends Throwable {}
