package org.openrewrite.java.testing.junit5

import org.junit.jupiter.api.Test
import org.openrewrite.RefactorVisitorTestForParser
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J

class TemporaryFolderToTempDirTest : RefactorVisitorTestForParser<J.CompilationUnit> {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
            .classpath("junit")
            .build()

    override val visitors = listOf(TemporaryFolderToTempDir())

    @Test
    fun basicReplace() = assertRefactored(
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
    fun replacesNewFileNoArgs() = assertRefactored(
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
                
                    private File newFile(File dir, String fileName) throws IOException {
                        File file = new File(getRoot(), fileName);
                        file.createNewFile();
                        return file;
                    }
                }
            """
    )
}
