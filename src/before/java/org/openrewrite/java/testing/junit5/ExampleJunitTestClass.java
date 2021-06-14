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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.mockito.Mockito.*;

public class ExampleJunitTestClass {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void beforeClass() {
        MockitoAnnotations.initMocks(this);
    }

    @AfterClass
    public static void afterClass() { }

    @Mock
    List<String> mockedList;

    public void initMocks() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void usingAnnotationBasedMock() {
        mockedList.add("one");
        mockedList.clear();

        verify(mockedList).add("one");
        verify(mockedList).clear();
    }

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

    @Test(expected = IndexOutOfBoundsException.class)
    public void foo2() {
        int arr = new int[]{}[0];
    }

    @Rule
    public ExpectedException throwz = ExpectedException.none();

    @Test
    public void foo3() {
        throwz.expect(RuntimeException.class);
        throw new RuntimeException();
    }

    @Test
    public void assertsStuff() {
        Assert.assertEquals("One is one", 1, 1);
        Assert.assertArrayEquals("Empty is empty", new int[]{}, new int[]{});
        Assert.assertNotEquals("one is not two", 1, 2);
        Assert.assertFalse("false is false", false);
        Assert.assertTrue("true is true", true);
        Assert.assertEquals("foo is foo", "foo", "foo");
        Assert.assertNull("null is null", null);
        Assert.fail("fail");
    }

    @Test(timeout = 500)
    public void bar() { }

    @Test
    public void aTest() {
        String foo = mock(String.class);
        when(foo.concat(any())).then(invocation -> invocation.getArgumentAt(0, String.class));
    }
}
