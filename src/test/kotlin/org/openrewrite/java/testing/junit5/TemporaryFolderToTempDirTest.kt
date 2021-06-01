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

class TemporaryFolderToTempDirTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .classpath("junit")
        .build()

    override val recipe: Recipe
        get() = TemporaryFolderToTempDir()

    @Test
    fun basicReplace() = assertChanged(
        before = """
            import org.junit.Rule;
            import org.junit.rules.TemporaryFolder;
            
            class A {
            
                @Rule
                TemporaryFolder tempDir = new TemporaryFolder();
            }
        """,
        after = """
            import org.junit.jupiter.api.io.TempDir;
            
            import java.io.File;
            
            class A {
            
                @TempDir
                File tempDir;
            }
        """
    )

    @Test
    fun multiVarReplace() = assertChanged(
        before = """
            import org.junit.Rule;
            import org.junit.rules.TemporaryFolder;
            
            class A {
            
                @Rule
                TemporaryFolder tempDir1 = new TemporaryFolder(), tempDir2 = new TemporaryFolder();
            }
        """,
        after = """
            import org.junit.jupiter.api.io.TempDir;
            
            import java.io.File;
            
            class A {
            
                @TempDir
                File tempDir1, tempDir2;
            }
        """
    )

    @Test
    fun newFile() = assertChanged(
        before = """
            import org.junit.Rule;
            import org.junit.rules.TemporaryFolder;
            
            import java.io.File;
            import java.io.IOException;
            
            class A {
            
                @Rule
                TemporaryFolder tempDir1 = new TemporaryFolder();
            
                @Rule
                TemporaryFolder tempDir2 = new TemporaryFolder();
            
                File file2 = tempDir2.newFile("sam");
            
                void foo() throws IOException {
                    File file1 = tempDir1.newFile();
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.io.TempDir;
            
            import java.io.File;
            import java.io.IOException;
            
            class A {
            
                @TempDir
                File tempDir1;
            
                @TempDir
                File tempDir2;
            
                File file2 = newFile(tempDir2, "sam");
            
                void foo() throws IOException {
                    File file1 = File.createTempFile("junit", null, tempDir1);
                }
            
                private static File newFile(File root, String fileName) throws IOException {
                    File file = new File(root, fileName);
                    file.createNewFile();
                    return file;
                }
            }
        """
    )

    @Test
    fun getRoot() = assertChanged(
        before = """
            import org.junit.Rule;
            import org.junit.rules.TemporaryFolder;
            
            import java.io.File;
            
            class A {
            
                @Rule
                TemporaryFolder tempDir = new TemporaryFolder();
                
                void foo() {
                    File root = tempDir.getRoot();
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.io.TempDir;
            
            import java.io.File;
            
            class A {
            
                @TempDir
                File tempDir;
            
                void foo() {
                    File root = tempDir;
                }
            }
        """
    )

    @Test
    fun newFolder() = assertChanged(
        before = """
            import org.junit.Rule;
            import org.junit.rules.TemporaryFolder;
            
            import java.io.File;
            import java.io.IOException;
            
            class A {
            
                @Rule
                TemporaryFolder tempDir1 = new TemporaryFolder();

                void foo() throws IOException {
                    File file1 = tempDir1.newFolder();
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.io.TempDir;

            import java.io.File;
            import java.io.IOException;
            import java.nio.file.Files;
            
            class A {
            
                @TempDir
                File tempDir1;
            
                void foo() throws IOException {
                    File file1 = Files.createTempDirectory(tempDir1.toPath(), "junit").toFile();
                }
            }
        """
    )

    @Test
    fun newFolderWithArgs() = assertChanged(
        before = """
            import org.junit.Rule;
            import org.junit.rules.TemporaryFolder;
            
            import java.io.File;
            import java.io.IOException;
            
            class A {
            
                @Rule
                TemporaryFolder tempDir1 = new TemporaryFolder();
                
                File subDir = tempDir1.newFolder("sub");
                File subDirs = tempDir1.newFolder("foo", "bar", "baz");
            }
        """,
        after = """
            import org.junit.jupiter.api.io.TempDir;

            import java.io.File;
            import java.io.IOException;
            
            class A {
            
                @TempDir
                File tempDir1;
            
                File subDir = newFolder(tempDir1, "sub");
                File subDirs = newFolder(tempDir1, "foo", "bar", "baz");
            
                private static File newFolder(File root, String ... folders) throws IOException {
                    File result = new File(root, String.join("/", folders));
                    if (!result.mkdirs()) {
                        throw new IOException("Couldn't create folders " + root);
                    }
                    return result;
                }
            }
        """
    )
}
