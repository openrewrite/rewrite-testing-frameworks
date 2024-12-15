/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.testing.jmockit;

import lombok.Getter;

import java.util.Arrays;

@Getter
enum JMockitBlockType {

    Expectations,
    NonStrictExpectations,
    Verifications,
    VerificationsInOrder,
    FullVerifications;

    private final String fqn = "mockit." + this.name();

    boolean isVerifications() {
        return this == Verifications || this == FullVerifications || this == VerificationsInOrder;
    }

    static String getSupportedTypesStr() {
        StringBuilder sb = new StringBuilder();
        Arrays.stream(values()).forEach(value -> sb.append(value).append(", "));
        return sb.substring(0, sb.length() - 2);
    }
}
