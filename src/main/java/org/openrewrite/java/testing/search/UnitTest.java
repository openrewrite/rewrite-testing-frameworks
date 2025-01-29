package org.openrewrite.java.testing.search;

import lombok.Value;

@Value
public class UnitTest {
    String clazz;
    String unitTestName;
    String unitTest;
}
