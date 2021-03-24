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
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class RemoveObsoleteRunnersTest: JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .classpath("junit")
        .build()

    override val recipe = RemoveObsoleteRunners()

    @Test
    fun removesRunWithJunit4() = assertChanged(
        before = """
            import org.junit.runner.RunWith;
            import org.junit.runners.JUnit4;
            
            @RunWith(JUnit4.class)
            public class Foo {
            }
        """,
        after = """
            
            public class Foo {
            }
        """
    )

    @Test
    fun removeRunWithBlockRunner() = assertChanged(
        before = """
            import org.junit.runner.RunWith;
            import org.junit.runners.BlockJUnit4ClassRunner;
            
            @RunWith(BlockJUnit4ClassRunner.class)
            public class Foo {
            }
        """,
        after = """
            
            public class Foo {
            }
        """
    )
}
