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
package org.openrewrite.java.testing.junit5;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

public class ExampleJunitTestClass {

//    @Rule
//    public Timeout globalTimeout = new Timeout(500);

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @BeforeClass
    public static void beforeClass() { }

    @AfterClass
    public static void afterClass() {}

    @Test(expected = RuntimeException.class)
    public void foo() throws IOException {
        File tempFile = folder.newFile();
        File tempFile2 = folder.newFile("filename");
        File tempDir = folder.getRoot();
        File tempDir2 = folder.newFolder("parent", "child");
        File tempDir3 = folder.newFolder("subdir");
        File tempDir4 = folder.newFolder();
        String foo = "foo";
        throw new RuntimeException(foo);
    }

    @Test(timeout = 500)
    public void bar() { }

private File newFolder(File root, String ... folders) throws IOException {
    File result = new File(root, String.join("/", folders));
    if(!result.mkdirs()) {
        throw new IOException("Couldn't create folders " + root);
    }
    return result;
}
}
