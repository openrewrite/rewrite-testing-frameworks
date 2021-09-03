/*
 * Copyright 2021 the original author or authors.
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

class RunnerToExtensionTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion().classpath("junit", "mockito-all").build()

    @Test
    fun mockito() = assertChanged(
        recipe = RunnerToExtension(
            listOf("org.mockito.runners.MockitoJUnitRunner"),
            "org.mockito.junit.jupiter.MockitoExtension"
        ),
        before = """
            import org.junit.runner.RunWith;
            import org.mockito.runners.MockitoJUnitRunner;
            
            @RunWith(MockitoJUnitRunner.class)
            public class Test {
            }
        """,
        after = """
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.mockito.junit.jupiter.MockitoExtension;
            
            @ExtendWith(MockitoExtension.class)
            public class Test {
            }
        """
    )
}
