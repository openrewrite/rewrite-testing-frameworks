/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.testing.jmockit;

import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Optional;

import static java.util.Optional.empty;

class JMockitUtils {

    static Optional<JMockitBlockType> getJMockitBlock(Statement s) {
        if (!(s instanceof J.NewClass)) {
            return empty();
        }
        J.NewClass nc = (J.NewClass) s;
        if (!(nc.getClazz() instanceof J.Identifier)) {
            return empty();
        }
        J.Identifier clazz = (J.Identifier) nc.getClazz();

        // JMockit block should be composed of a block within another block
        if (nc.getBody() == null ||
            (nc.getBody().getStatements().size() != 1 &&
             !TypeUtils.isAssignableTo("mockit.Expectations", clazz.getType()) &&
             !TypeUtils.isAssignableTo("mockit.Verifications", clazz.getType()))) {
            return empty();
        }

        return Optional.of(JMockitBlockType.valueOf(clazz.getSimpleName()));
    }
}
