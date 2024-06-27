package org.openrewrite.java.testing.jmockit;

enum JMockitBlockType {

    Expectations("mockit.Expectations"), Verifications("mockit.Verifications"); // Add NonStrictExpectations later

    private final String fqn;

    JMockitBlockType(String fqn) {
        this.fqn = fqn;
    }

    String getFqn() {
        return fqn;
    }
}
