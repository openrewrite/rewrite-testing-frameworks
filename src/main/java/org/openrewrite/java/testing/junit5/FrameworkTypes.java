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
package org.openrewrite.java.testing.junit5;

import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Tree.randomId;

class FrameworkTypes {
    private FrameworkTypes() {}

    public static final JavaType.Class runWithType = JavaType.Class.build("org.junit.runner.RunWith");
    public static final J.Ident runWithIdent = J.Ident.build(
            randomId(),
            "RunWith",
            runWithType,
            EMPTY);
    public static final JavaType.Class extendWithType = JavaType.Class.build("org.junit.jupiter.api.extension.ExtendWith");
    public static final J.Ident extendWithIdent = J.Ident.build(
            randomId(),
            "ExtendWith",
            extendWithType,
            EMPTY
    );
}
