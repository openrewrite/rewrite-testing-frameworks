package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"ResultOfMethodCallIgnored", "RedundantThrows"})
class TemporaryFolderToTempDirTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit"))
          .recipe(new TemporaryFolderToTempDir());
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/159")
    @Test
    void changesReferencesToFolder() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.junit.Rule;
              import org.junit.rules.TemporaryFolder;

              public class MyTest {
                  @Rule
                  public TemporaryFolder tmpFolder = new TemporaryFolder();

                  @After
                  public void tearDown() {
                      tmpFolder.delete();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.io.TempDir;
              
              import java.io.File;

              public class MyTest {
                  @TempDir
                  public File tmpFolder;

                  @After
                  public void tearDown() {
                      tmpFolder.delete();
                  }
              }
              """
          )
        );
    }

    @Test
    void temporaryFolderInstantiatedWithParentFolder() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.rules.TemporaryFolder;
              
              import java.io.File;
              
              class MyTest {
                  File parentDir = new File();
                  @Rule
                  TemporaryFolder tempDir = new TemporaryFolder(parentDir);
              }
              """,
            """
              import org.junit.jupiter.api.io.TempDir;
              
              import java.io.File;
              
              class MyTest {
                  File parentDir = new File();
                  @TempDir
                  File tempDir;
              }
              """
          )
        );
    }

    @Test
    void basicReplace() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.ClassRule;
              import org.junit.rules.TemporaryFolder;
              
              class MyTest {
              
                  @ClassRule
                  public static TemporaryFolder tempDir = new TemporaryFolder();
              }
              """,
            """
              import org.junit.jupiter.api.io.TempDir;
              
              import java.io.File;
              
              class MyTest {
              
                  @TempDir
                  public static File tempDir;
              }
              """
          )
        );
    }

    @Test
    void multiVarReplace() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.ClassRule;
              import org.junit.rules.TemporaryFolder;
              
              class MyTest {
              
                  @ClassRule
                  static TemporaryFolder tempDir1 = new TemporaryFolder(), tempDir2 = new TemporaryFolder();
              }
              """,
            """
              import org.junit.jupiter.api.io.TempDir;
              
              import java.io.File;
              
              class MyTest {
              
                  @TempDir
                  static File tempDir1, tempDir2;
              }
              """
          )
        );
    }

    @Test
    void getRoot() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.rules.TemporaryFolder;
              
              import java.io.File;

              class MyTest {

                  @Rule
                  TemporaryFolder tempDir = new TemporaryFolder();

                  void foo() {
                      File root = tempDir.getRoot();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.io.TempDir;
              
              import java.io.File;

              class MyTest {

                  @TempDir
                  File tempDir;

                  void foo() {
                      File root = tempDir;
                  }
              }
              """
          )
        );
    }

    @Test
    void newFolder() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.rules.TemporaryFolder;
              
              import java.io.File;
              import java.io.IOException;
              
              class MyTest {
              
                  @Rule
                  TemporaryFolder tempDir1 = new TemporaryFolder();

                  void foo() throws IOException {
                      File file1 = tempDir1.newFolder();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.io.TempDir;

              import java.io.File;
              import java.io.IOException;
              
              class MyTest {
              
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
        );
    }

    @Test
    void newFolderWithArgs() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              import org.junit.rules.TemporaryFolder;
              import org.junit.Rule;
              
              import java.io.File;
              import java.io.IOException;

              class MyTest {

                  @Rule
                  TemporaryFolder tempDir1 = new TemporaryFolder();

                  @Test
                  public void someTest() {
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
            """
              import org.junit.Test;
              import org.junit.jupiter.api.io.TempDir;

              import java.io.File;
              import java.io.IOException;

              class MyTest {

                  @TempDir
                  File tempDir1;

                  @Test
                  public void someTest() {
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
        );
    }

    @Test
    void newFolderWithParams() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;
              import org.junit.Test;
              import org.junit.rules.TemporaryFolder;
              
              import java.io.File;
              import java.io.IOException;
              
              class MyTest {
              
                  @Rule
                  TemporaryFolder tempDir1 = new TemporaryFolder();
                  String s1 = "foo";
                  String s2 = "bar";
                  String s3 = "baz";
              
                  @Test
                  public void someTest() {
                      File subDir = tempDir1.newFolder("sub");
                      File subDirs = tempDir1.newFolder(s1, s2, s3);
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.jupiter.api.io.TempDir;
              
              import java.io.File;
              import java.io.IOException;
              
              class MyTest {
              
                  @TempDir
                  File tempDir1;
                  String s1 = "foo";
                  String s2 = "bar";
                  String s3 = "baz";
              
                  @Test
                  public void someTest() {
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
        );
    }

    @Test
    void usingMethodRule() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Rule;import org.junit.Test;
              import org.junit.rules.TemporaryFolder;
              
              import java.io.File;
              import java.io.IOException;
              
              public class MyTest {
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
            """
              import org.junit.Test;
              import org.junit.jupiter.api.io.TempDir;
              
              import java.io.File;
              import java.io.IOException;
              
              public class MyTest {
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
        );
    }

    @Test
    void multipleTemporaryFoldersInMethodBody() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.ClassRule;
              import org.junit.Rule;
              import org.junit.Test;
              import org.junit.rules.TemporaryFolder;
              
              import java.io.File;
              import java.io.IOException;
              public class MyTest {
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
            """
              import org.junit.Test;
              import org.junit.jupiter.api.io.TempDir;
              
              import java.io.File;
              import java.io.IOException;

              public class MyTest {
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
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/143")
    @Test
    void fieldRetainsModifiers() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.junit.ClassRule;
              import org.junit.rules.TemporaryFolder;

              public class MyTest {
                  @ClassRule
                  public static final TemporaryFolder temporaryFolder = new TemporaryFolder();

                  public static void init() {
                      File aDir = temporaryFolder.getRoot();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.io.TempDir;
              
              import java.io.File;

              public class MyTest {
                  @TempDir
                  public static final File temporaryFolder;

                  public static void init() {
                      File aDir = temporaryFolder;
                  }
              }
              """
          )
        );
    }

    @Test
    void newTemporaryFolderInstanceAsArgumentNotSupported() {
        //language=java
        rewriteRun(
          java(
            """
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
        );
    }

    @Test
    void notSupported() {
        //language=java
        rewriteRun(
          java(
            """
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
        );
    }
}
