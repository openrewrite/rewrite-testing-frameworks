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
import org.openrewrite.Parser
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest
import org.openrewrite.java.tree.J

/**
 * This is not functional yet. Make modifications wherever you see fit. TODO.
 */
class UpdateTestWatcherTest : JavaRecipeTest {
    override val parser: Parser<J.CompilationUnit> = JavaParser.fromJavaVersion()
        .classpath("junit")
        .build()

    override val recipe: Recipe
        get() = UpdateTestWatcher()

    @Test
    @Disabled
    fun assertJUnit4TestWatcherMethodsWithCorrespondingJUnit5TestWatcherMethodsUpdate() = assertChanged(
        before = """
            import org.junit.AssumptionViolatedException;
            import org.junit.Rule;
            import org.junit.rules.TestWatcher;
            import org.junit.runner.Description;

            public class ExampleTest {
                @Rule
                public TestWatcher watchman = new TestWatcher() {
                    @Override
                    protected void failed(Throwable e, Description description) { 
                        System.out.println("failed"); 
                    }
                    
                    @Override
                    protected void skipped(AssumptionViolatedException e, Description description) {
                        System.out.println("skipped");
                    }

                    @Override
                    protected void succeeded(Description description) {
                        System.out.println("succeeded");
                    }
                };

                public void test() {
                }
            }

            class SomeOtherClass {}
        """,
        after = """
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.junit.jupiter.api.extension.ExtensionContext;
            import org.junit.jupiter.api.extension.TestWatcher;
            import java.util.Optional;

            @ExtendWith(ExampleTest.WatchmanTestExtension.class)
            public class ExampleTest {
                public void test() {
                }

                class WatchmanTestExtension implements TestWatcher {
                    @Override
                    public void testFailed(ExtensionContext extensionContext, Throwable throwable) {
                        System.out.println("failed");
                    }
                    
                    @Override
                    public void testDisabled(ExtensionContext context, Optional<String> reason) {
                        System.out.println("skipped");
                    }
    
                    @Override
                    public void testSuccessful(ExtensionContext extensionContext) {
                        System.out.println("succeeded");
                    }
                }
            }

            class SomeOtherClass {}
        """
    )

    @Test
    @Disabled
    fun assertJUnit4TestWatcherMethodsWithoutCorrespondingJUnit5TestWatcherMethodsUpdate() = assertChanged(
        before = """
            import org.junit.Rule;
            import org.junit.rules.TestWatcher;
            import org.junit.runner.Description;

            public class ExampleTest {
                @Rule
                public TestWatcher watchman = new TestWatcher() {
                    protected void starting(Description description) { 
                        System.out.println("starting");
                    }
                    
                    protected void finished(Description description) { 
                        System.out.println("finished");
                    }
                };

                public void test() {
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.extension.ExtensionContext;
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
            import org.junit.jupiter.api.extension.AfterTestExecutionCallback;

            @ExtendWith(ExampleTest.WatchmanTestExtension.class)
            public class ExampleTest {
                public void test() {
                }

                class WatchmanTestExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {
                    @Override
                    public void beforeTestExecution(ExtensionContext extensionContext) {
                        System.out.println("starting");
                    }
                    
                    @Override
                    public void afterTestExecution(ExtensionContext context) {
                        System.out.println("finished");
                    }
                }
            }
        """
    )

    @Test
    @Disabled
    fun assertJUnit4TestWatcherMethodsMixCorrespondingJUnit5TestWatcherMethodsUpdate() = assertChanged(
        before = """
            import org.junit.Rule;
            import org.junit.rules.TestWatcher;
            import org.junit.runner.Description;

            public class ExampleTest {
                @Rule
                public TestWatcher watchman = new TestWatcher() {
                    protected void starting(Description description) { 
                        System.out.println("starting");
                    }
                    
                    protected void finished(Description description) { 
                        System.out.println("finished");
                    }
                    
                    protected void failed(Throwable e, Description description) { 
                        System.out.println("failed"); 
                    }
                };

                public void test() {
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.extension.ExtensionContext;
            import org.junit.jupiter.api.extension.TestWatcher;
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
            import org.junit.jupiter.api.extension.AfterTestExecutionCallback;

            @ExtendWith(ExampleTest.WatchmanTestExtension.class)
            public class ExampleTest {
                public void test() {
                }

                class WatchmanTestExtension implements TestWatcher, BeforeTestExecutionCallback, AfterTestExecutionCallback {
                    @Override
                    public void beforeTestExecution() {
                        System.out.println("starting");
                    }
                    
                    @Override
                    public void afterTestExecution() {
                        System.out.println("finished");
                    }
    
                    @Override
                    public void testFailed(ExtensionContext extensionContext, Throwable throwable) {
                        System.out.println("failed");
                    }
                }
            }
        """
    )

    @Test
    @Disabled
    fun assertConvertsMultipleJUnit4TestWatchers() = assertChanged(
        before = """
            import org.junit.Rule;
            import org.junit.rules.TestWatcher;
            import org.junit.runner.Description;

            public class ExampleTest {
                @Rule
                public TestWatcher myWatcher = new TestWatcher() {
                    protected void starting(Description description) {
                        System.out.println("myWatcher is starting");
                    }
                };
                
                @Rule
                public TestWatcher yourWatcher = new TestWatcher() {
                    protected void starting(Description description) {
                        System.out.println("yourWatcher is starting, too");
                    }
                };

                public void test() {
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;

            @ExtendWith(ExampleTest.MyWatcherTestExtension.class)
            @ExtendWith(ExampleTest.YourWatcherTestExtension.class)
            public class ExampleTest {
                public void test() {
                }

                class MyWatcherTestExtension implements BeforeTestExecutionCallback {
                    @Override
                    public void beforeTestExecution() {
                        System.out.println("myWatcher is starting");
                    }
                }
                
                class YourWatcherTestExtension implements BeforeTestExecutionCallback {
                    @Override
                    public void beforeTestExecution() {
                        System.out.println("yourWatcher is starting, too");
                    }
                }
            }
        """
    )

    @Test
    @Disabled
    fun assertTestWatcherConversionHandlesPublicAbstractTestClass() = assertChanged(
        before = """
            import org.junit.Test;
            import org.junit.Rule;
            import org.junit.rules.TestWatcher;
            import lombok.extern.slf4j.Slf4j;
            import org.junit.runner.Description;

            @Slf4j
            public abstract class ExampleTest {
                @Rule
                public TestRule watcher = new TestWatcher() {
                    protected void starting(Description description) { 
                        log.info("=================================================");
                        log.info("STARTING TEST: {}" , description.getMethodName());
                        log.info("=================================================");
                    }
                    
                    protected void succeeded(Description description) {
                        log.info("=================================================");
                        log.info("SUCCEEDED TEST: {}" , description.getMethodName());
                        log.info("=================================================");
                    }
                    
                    protected void failed(Throwable e, Description description) {
                        log.info("=================================================");
                        log.info("FAILED TEST: {}" , description.getMethodName(), e);
                        log.info("=================================================");
                    }
                };
            }
        """,
        after = """
            import lombok.extern.slf4j.Slf4j;
            import org.junit.jupiter.api.extension.ExtensionContext;
            import org.junit.jupiter.api.extension.TestWatcher;
            import org.junit.jupiter.api.extension.ExtendWith;
            
            @ExtendWith(ExampleTest.WatcherTestExtension.class)
            public abstract class ExampleTest {
                @Slf4j
                class WatcherTestExtension implements TestWatcher, BeforeTestExecutionCallback {
                    @Override
                    public void beforeTestExecution(ExtensionContext extensionContext) {
                        log.info("=================================================");
                        log.info("STARTING TEST: {}" , extensionContext.getMethodName());
                        log.info("=================================================");
                    }
    
                    @Override
                    public void testFailed(ExtensionContext extensionContext, Throwable throwable) { // todo throwable e to throwable name
                        log.info("=================================================");
                        log.info("FAILED TEST: {}" , extensionContext.getMethodName(), e);
                        log.info("=================================================");
                    }
    
                    @Override
                    public void testSuccessful(ExtensionContext extensionContext) {
                        log.info("=================================================");
                        log.info("SUCCEEDED TEST: {}" , extensionContext.getMethodName());
                        log.info("=================================================");
                    }
                }
            }
            
        """
    )

    @Test
    @Disabled
    fun assertTestWatcherConversionHandlesDescriptionMethodParameter() = assertChanged(
        before = """
            import org.junit.Rule;
            import org.junit.rules.TestWatcher;
            import org.junit.runner.Description;

            public class WatchmanTest {
                @Rule
                public TestWatcher watchman = new TestWatcher() {
                    @Override
                    protected void failed(Throwable e, Description description) { 
                        System.out.println(description + " failed"); 
                    }

                    @Override
                    protected void succeeded(Description description) {
                        System.out.println(description + " success");
                    }
                };

                public void test() {
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.extension.ExtensionContext;
            import org.junit.jupiter.api.extension.TestWatcher;
            import org.junit.jupiter.api.extension.ExtendWith;

            @ExtendWith(WatchmanTest.WatchmanTestExtension.class)
            public class WatchmanTest {
                public void test() {
                }

                class WatchmanTestExtension implements TestWatcher {
                    @Override
                    public void testFailed(ExtensionContext extensionContext, Throwable throwable) {
                        System.out.println(extensionContext.getDisplayName() + " failed");
                    }
    
                    @Override
                    public void testSuccessful(ExtensionContext extensionContext) {
                        System.out.println(extensionContext.getDisplayName() + " success");
                    }
                }
            }

        """
    )


    @Test
    @Disabled // what should this look like? // todo
    fun assertTestWatcherConversionHandlesStaticContexts() = assertChanged(
        before = """
            import org.junit.Rule;
            import org.junit.rules.TestWatcher;
            import org.junit.runner.Description;

            public static class WatchmanTest {
                private static String watchedLog;

                @Rule
                public TestWatcher watchman = new TestWatcher() {
                    @Override
                    protected void failed(Throwable e, Description description) { 
                        watchedLog += "failed"; 
                    }

                    @Override
                    protected void succeeded(Description description) {
                        watchedLog += "succeeded";
                    }
                };

                public void test() {
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.extension.ExtensionContext;
            import org.junit.jupiter.api.extension.TestWatcher;
            import org.junit.jupiter.api.extension.ExtendWith;

            @ExtendWith(WatchmanTest.WatchmanTestExtension.class)
            public static class WatchmanTest {
                private static String watchedLog;

                public void test() {
                }

                class WatchmanTestExtension implements TestWatcher {
                    @Override
                    public void testFailed(ExtensionContext extensionContext, Throwable throwable) {
                        watchedLog += "failed";
                    }
    
                    @Override
                    public void testSuccessful(ExtensionContext extensionContext) {
                        watchedLog += "succeeded";
                    }
                }
            }
        """
    )


}
