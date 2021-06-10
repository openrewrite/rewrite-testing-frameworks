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
import org.openrewrite.Issue
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
    fun temporaryFolderInstantiatedWithParentFolder() = assertChanged(
        before = """
            import org.junit.Rule;
            import org.junit.rules.TemporaryFolder;
            
            import java.io.File;
            
            class A {
                File parentDir = new File();
                @Rule
                TemporaryFolder tempDir = new TemporaryFolder(parentDir);
            }
        """,
        after = """
            import org.junit.jupiter.api.io.TempDir;
            
            import java.io.File;
            
            class A {
                File parentDir = new File();
                @TempDir
                File tempDir;
            }
        """
    )
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
            
                File file2 = new File(tempDir2, "sam");
            
                void foo() throws IOException {
                    File file1 = new File(tempDir1, "junit");
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
            
            class A {
            
                @TempDir
                File tempDir1;
            
                void foo() throws IOException {
                    File file1 = newFolder(tempDir1, "junit");
                }
            
                private static File newFolder(File root, String name) throws IOException {
                    File result = new File(root, name);
                    if (!result.mkdirs()) {
                        throw new IOException("Couldn't create folders " + root);
                    }
                    return result;
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
                File subDirs = newFolder(tempDir1, "foo/bar/baz");
            
                private static File newFolder(File root, String name) throws IOException {
                    File result = new File(root, name);
                    if (!result.mkdirs()) {
                        throw new IOException("Couldn't create folders " + root);
                    }
                    return result;
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/142")
    @Test
    fun newFileNameIsJIdentifier() = assertChanged(
        before = """
            import org.junit.Test;
            import org.junit.rules.TemporaryFolder;
            
            import java.io.File;
            import java.io.IOException;
            public class T {
                @Test
                public void newNamedFileIsCreatedUnderRootFolder() throws IOException {
                    final String fileName = "SampleFile.txt";
                    TemporaryFolder tempFolder = new TemporaryFolder();
                    tempFolder.create();
                    File f = tempFolder.newFile(fileName);
                }
            }
        """,
        after = """
            import org.junit.Test;
            import org.junit.jupiter.api.io.TempDir;
            
            import java.io.File;
            import java.io.IOException;
            
            public class T {
                @Test
                public void newNamedFileIsCreatedUnderRootFolder(@TempDir File tempFolder) throws IOException {
                    final String fileName = "SampleFile.txt";
                    File f = new File(tempFolder, fileName);
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/143")
    @Test
    fun fieldRetainsModifiers() = assertChanged(
        before = """
            import org.junit.ClassRule;
            import org.junit.rules.TemporaryFolder;
            
            public class T {
                @ClassRule
                public static final TemporaryFolder temporaryFolder = new TemporaryFolder();
                
                public static void init() {
                    File aDir = temporaryFolder.getRoot();
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.io.TempDir;
            
            import java.io.File;
            
            public class T {
                @TempDir
                public static final File temporaryFolder;
            
                public static void init() {
                    File aDir = temporaryFolder;
                }
            }
        """
    )
}
