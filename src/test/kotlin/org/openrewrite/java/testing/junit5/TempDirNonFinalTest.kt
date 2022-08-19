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
import org.openrewrite.Issue
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class TempDirNonFinalTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .classpath("junit")
        .build()

    override val recipe: Recipe
        get() = TempDirNonFinal()

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/241")
    fun tempDirStaticFinalFile() = assertChanged(
        before = """
            import org.junit.jupiter.api.io.TempDir;
            
            import java.io.File;
            
            class A {
                @TempDir
                static final File tempDir;
            }
        """,
        after = """
            import org.junit.jupiter.api.io.TempDir;
            
            import java.io.File;
            
            class A {
                @TempDir
                static File tempDir;
            }
        """
    )

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/241")
    fun tempDirStaticFinalPath() = assertChanged(
        before = """
            import org.junit.jupiter.api.io.TempDir;
            
            import java.nio.file.Path;
            
            class A {
                @TempDir
                static final Path tempDir;
            }
        """,
        after = """
            import org.junit.jupiter.api.io.TempDir;
            
            import java.nio.file.Path;
            
            class A {
                @TempDir
                static Path tempDir;
            }
        """
    )

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/241")
    fun tempDirFileParameter() = assertUnchanged(
        before = """
            import org.junit.jupiter.api.Test
            import org.junit.jupiter.api.io.TempDir;
            
            import java.nio.file.Path;
            
            class A {
                @Test
                void fileTest(@TempDir File tempDir) {
                }
            }
        """
    )

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/241")
    fun tempDirStaticFile() = assertUnchanged(
        before = """
            import org.junit.jupiter.api.io.TempDir;
            
            import java.io.File;
            
            class A {
                @TempDir
                static File tempDir;
            }
        """
    )

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/241")
    fun tempDirStaticPath() = assertUnchanged(
        before = """
            import org.junit.jupiter.api.io.TempDir;
            
            import java.nio.file.Path;
            
            class A {
                @TempDir
                static Path tempDir;
            }
        """
    )
}
