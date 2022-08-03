/*
 * Copyright 2022 the original author or authors.
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
package org.openrewrite.java.testing.cleanup

import org.junit.jupiter.api.Test
import org.openrewrite.Issue
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class AssertFalseNullToAssertNotNullTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .logCompilationWarningsAndErrors(true)
        .classpath("junit-jupiter-api")
        .build()

    override val recipe: Recipe
        get() = AssertFalseNullToAssertNotNull()

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/202")
    @Suppress("ConstantConditions", "SimplifiableAssertion")
    @Test
    fun simplifyToAssertNull() = assertChanged(
        before = """
            import static org.junit.jupiter.api.Assertions.assertFalse;
            
            public class Test {
                void test() {
                    String a = null;
                    assertFalse(a == null);
                    
                    String b = null;
                    assertFalse(null == b);
                }
            }
        """,
        after = """
            import static org.junit.jupiter.api.Assertions.assertNotNull;
            
            public class Test {
                void test() {
                    String a = null;
                    assertNotNull(a);
                    
                    String b = null;
                    assertNotNull(b);
                }
            }
        """,
    )

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/202")
    @Suppress("ConstantConditions", "SimplifiableAssertion")
    @Test
    fun preserveStyleOfStaticImportOrNot() = assertChanged(
        before = """
            import org.junit.jupiter.api.Assertions;
            
            public class Test {
                void test() {
                    String a = null;
                    Assertions.assertFalse(a == null);
                    
                    String b = null;
                    Assertions.assertFalse(null == b);
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.Assertions;
            
            public class Test {
                void test() {
                    String a = null;
                    Assertions.assertNotNull(a);
                    
                    String b = null;
                    Assertions.assertNotNull(b);
                }
            }
        """,
    )
}
