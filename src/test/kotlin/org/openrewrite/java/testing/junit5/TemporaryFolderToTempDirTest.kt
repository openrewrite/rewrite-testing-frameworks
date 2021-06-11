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

import org.junit.jupiter.api.Disabled
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
            import org.junit.ClassRule;
            import org.junit.rules.TemporaryFolder;
            
            class A {
            
                @ClassRule
                public static TemporaryFolder tempDir = new TemporaryFolder();
            }
        """,
        after = """
            import org.junit.jupiter.api.io.TempDir;
            
            import java.io.File;
            
            class A {
            
                @TempDir
                public static File tempDir;
            }
        """
    )

    @Test
    fun multiVarReplace() = assertChanged(
        before = """
            import org.junit.ClassRule;
            import org.junit.rules.TemporaryFolder;
            
            class A {
            
                @ClassRule
                static TemporaryFolder tempDir1 = new TemporaryFolder(), tempDir2 = new TemporaryFolder();
            }
        """,
        after = """
            import org.junit.jupiter.api.io.TempDir;
            
            import java.io.File;
            
            class A {
            
                @TempDir
                static File tempDir1, tempDir2;
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
            
                private static File newFolder(File root, String... subDirs) throws IOException {
                    String subFolder = String.join("/", subDirs);
                    File result = new File(root, subFolder);
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
            import org.junit.Test;
            import org.junit.Rule;
            import org.junit.rules.TemporaryFolder;
            
            import java.io.File;
            import java.io.IOException;
            
            class A {
            
                @Rule
                TemporaryFolder tempDir1 = new TemporaryFolder();
                
                @Test
                void someTest() {
                    File subDir = tempDir1.newFolder("sub");
                    File subDirs = tempDir1.newFolder("foo", "bar", "baz");
                    
                    String last = "z";
                    File subDirs2 = tempDir1.newFolder("v", "w", getSubFolderName(), "y", last);
                }
                
                String getSubFolderName() {
                    return "x";
                }
            }
        """,
        after = """
            import org.junit.Test;
            import org.junit.jupiter.api.io.TempDir;

            import java.io.File;
            import java.io.IOException;
            
            class A {
            
                @TempDir
                File tempDir1;
            
                @Test
                void someTest() {
                    File subDir = newFolder(tempDir1, "sub");
                    File subDirs = newFolder(tempDir1, "foo", "bar", "baz");
            
                    String last = "z";
                    File subDirs2 = newFolder(tempDir1, "v", "w", getSubFolderName(), "y", last);
                }
            
                String getSubFolderName() {
                    return "x";
                }
            
                private static File newFolder(File root, String... subDirs) throws IOException {
                    String subFolder = String.join("/", subDirs);
                    File result = new File(root, subFolder);
                    if (!result.mkdirs()) {
                        throw new IOException("Couldn't create folders " + root);
                    }
                    return result;
                }
            }
        """
    )

    @Test
    fun newFolderWithParams() = assertChanged(
        before = """
            import org.junit.Rule;
            import org.junit.Test;
            import org.junit.rules.TemporaryFolder;
            
            import java.io.File;
            import java.io.IOException;
            
            class A {
            
                @Rule
                TemporaryFolder tempDir1 = new TemporaryFolder();
                String s1 = "foo";
                String s2 = "bar";
                String s3 = "baz";
                
                @Test
                void someTest() {
                    File subDir = tempDir1.newFolder("sub");
                    File subDirs = tempDir1.newFolder(s1, s2, s3);
                }
            }
        """,
        after = """
            import org.junit.Test;
            import org.junit.jupiter.api.io.TempDir;
            
            import java.io.File;
            import java.io.IOException;
            
            class A {
            
                @TempDir
                File tempDir1;
                String s1 = "foo";
                String s2 = "bar";
                String s3 = "baz";
            
                @Test
                void someTest() {
                    File subDir = newFolder(tempDir1, "sub");
                    File subDirs = newFolder(tempDir1, s1, s2, s3);
                }
            
                private static File newFolder(File root, String... subDirs) throws IOException {
                    String subFolder = String.join("/", subDirs);
                    File result = new File(root, subFolder);
                    if (!result.mkdirs()) {
                        throw new IOException("Couldn't create folders " + root);
                    }
                    return result;
                }
            }
        """
    )

    @Test
    fun usingMethodRule() = assertChanged(
        before = """
            import org.junit.Rule;import org.junit.Test;
            import org.junit.rules.TemporaryFolder;
            
            import java.io.File;
            import java.io.IOException;
            
            public class T {
                @Rule
                TemporaryFolder tempFolder = new TemporaryFolder();
                
                @Test
                public void newNamedFileIsCreatedUnderRootFolder() throws IOException {
                    final String fileName = "SampleFile.txt";
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
                @TempDir
                File tempFolder;
            
                @Test
                public void newNamedFileIsCreatedUnderRootFolder() throws IOException {
                    final String fileName = "SampleFile.txt";
                    File f = File.createTempFile(fileName, null, tempFolder);
                }
            }
        """
    )

    @Test
    fun multipleTemporaryFoldersInMethodBody() = assertChanged(
        before = """
            import org.junit.ClassRule;
            import org.junit.Rule;
            import org.junit.Test;
            import org.junit.rules.TemporaryFolder;
            
            import java.io.File;
            import java.io.IOException;
            public class T {
                @ClassRule
                static TemporaryFolder tempFolder = new TemporaryFolder();
                @Rule
                TemporaryFolder tempFolder2 = new TemporaryFolder();
                
                @Test
                public void newNamedFileIsCreatedUnderRootFolder() throws IOException {
                    final String fileName = "SampleFile.txt";
                    final String otherFileName = "otherText.txt";
                    tempFolder.create();
                    tempFolder2.create();
                    File f = tempFolder.newFile(fileName);
                    File f2 = tempFolder2.newFile(otherFileName);
                }
            }
        """,
        after = """
            import org.junit.Test;
            import org.junit.jupiter.api.io.TempDir;
            
            import java.io.File;
            import java.io.IOException;
            
            public class T {
                @TempDir
                static File tempFolder;
                @TempDir
                File tempFolder2;
            
                @Test
                public void newNamedFileIsCreatedUnderRootFolder() throws IOException {
                    final String fileName = "SampleFile.txt";
                    final String otherFileName = "otherText.txt";
                    File f = File.createTempFile(fileName, null, tempFolder);
                    File f2 = File.createTempFile(otherFileName, null, tempFolder2);
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

    @Test
    fun newTemporaryFolderInstanceAsArgumentNotSupported() = assertUnchanged(
        before = """
            import org.junit.rules.TemporaryFolder;
            public class Z {
                void why() {
                    doSomething(new TemporaryFolder());
                }
                void doSomething(TemporaryFolder tempFolder) {
                
                }
            }
        """
    )

    @Test
    fun notSupported() = assertUnchanged(
        before = """
            import org.junit.rules.TemporaryFolder;
            public class Z {
                void why() {
                    TemporaryFolder t = new TemporaryFolder();
                    doSomething(t);
                }
                void doSomething(TemporaryFolder tempFolder) {
                
                }
            }
        """
    )
}
