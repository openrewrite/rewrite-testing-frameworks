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
package org.openrewrite.java.testing.junit5

import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class TestRuleToTestInfoTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .classpath("junit")
        .build()
    override val recipe: Recipe
        get() = TestRuleToTestInfo()
    @Test
    fun testRuleToTestInfo() = assertChanged(
        before = """
            import org.junit.Rule;
            import org.junit.rules.TestName;
            public class SomeTest {
                @Rule
                public TestName name = new TestName();
                protected String randomName() {
                    return name.getMethodName();
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.TestInfo;
            
            public class SomeTest {
                
                public String name;
                protected String randomName() {
                    return name;
                }
            
                @BeforeEach
                public void setup(TestInfo testInfo) {
                    Optional<Method> testMethod = testInfo.getTestMethod();
                    if (testMethod.isPresent()) {
                        this.name = testMethod.get().getName();
                    }
                }
            }
        """
    )
}