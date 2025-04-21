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
package org.openrewrite.java.testing.junit5;

import lombok.experimental.UtilityClass;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;

import java.util.*;

/**
 * Utility class containing JUnit4 related helper methods used in JUnit4 to JUnit5 migration recipes
 */
@UtilityClass
public class Junit4Utils {
    static final String AFTER = "org.junit.After";
    static final String AFTER_CLASS = "org.junit.AfterClass";
    static final String BEFORE = "org.junit.Before";
    static final String BEFORE_CLASS = "org.junit.BeforeClass";
    static final String CLASS_RULE = "org.junit.ClassRule";
    static final String FIX_METHOD_ORDER = "org.junit.FixMethodOrder";
    static final String IGNORE = "org.junit.Ignore";
    static final String PARAMETERIZED_PARAMETERS = "org.junit.runners.Parameterized.Parameters";
    static final String RULE = "org.junit.Rule";
    static final String RUN_WITH = "org.junit.runner.RunWith";
    static final String TEST = "org.junit.Test";

    static Set<String> classAnnotations() {
        return new HashSet<>(Arrays.asList(RUN_WITH, FIX_METHOD_ORDER, IGNORE));
    }

    static Set<String> methodAnnotations() {
        return new HashSet<>(Arrays.asList(
                BEFORE,
                AFTER,
                BEFORE_CLASS,
                AFTER_CLASS,
                TEST,
                PARAMETERIZED_PARAMETERS,
                IGNORE,
                RULE,
                CLASS_RULE));
    }

    static Set<String> fieldAnnotations() {
        return new HashSet<>(Arrays.asList(RULE, CLASS_RULE));
    }
}
