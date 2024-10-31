package org.openrewrite.java.testing.dbrider;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ExecutionListenerToDbRiderAnnotationUnitTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-test-6.1", "rider-spring-1.18", "rider-junit5-1.44"))
            .recipe(new ExecutionListenerToDbRiderAnnotation());
    }

    @Test
    @DocumentExample
    void replaceAnnotationIfOnlyDbRiderListenerMergedWithDefaults() {
        rewriteRun(
                //language=java
                java("""
                        package sample;

                        import org.springframework.test.context.TestExecutionListeners;
                        import com.github.database.rider.spring.DBRiderTestExecutionListener;
                        
                        @TestExecutionListeners(mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS, listeners = {DBRiderTestExecutionListener.class})
                        public class Sample {}
                        """, """
                        package sample;

                        import com.github.database.rider.junit5.api.DBRider;
                        
                        @DBRider
                        public class Sample {}
                        """));
    }

    @Test
    @DocumentExample
    void addAnnotationIfOnlyDbRiderListenerReplacedDefaults() {
        rewriteRun(
                //language=java
                java("""
                        package sample;

                        import org.springframework.test.context.TestExecutionListeners;
                        import com.github.database.rider.spring.DBRiderTestExecutionListener;
                        
                        @TestExecutionListeners(mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS, listeners = {DBRiderTestExecutionListener.class})
                        public class Sample {}
                        """, """
                        package sample;

                        import com.github.database.rider.junit5.api.DBRider;
                        import org.springframework.test.context.TestExecutionListeners;

                        @DBRider
                        @TestExecutionListeners
                        public class Sample {}
                        """));
    }

    @Test
    @DocumentExample
    void addAnnotationIfOnlyDbRiderListenerAndNoMergeModeSpecified() {
        rewriteRun(
                //language=java
                java("""
                        package sample;

                        import org.springframework.test.context.TestExecutionListeners;
                        import com.github.database.rider.spring.DBRiderTestExecutionListener;
                        
                        @TestExecutionListeners(listeners = {DBRiderTestExecutionListener.class})
                        public class Sample {}
                        """, """
                        package sample;

                        import com.github.database.rider.junit5.api.DBRider;
                        import org.springframework.test.context.TestExecutionListeners;

                        @DBRider
                        @TestExecutionListeners
                        public class Sample {}
                        """));
    }

    @Test
    void addAnnotationIfOnlyDbRiderListenerThroughValue() {
        rewriteRun(
                //language=java
                java("""
                        package sample;

                        import org.springframework.test.context.TestExecutionListeners;
                        import com.github.database.rider.spring.DBRiderTestExecutionListener;
                        
                        @TestExecutionListeners(DBRiderTestExecutionListener.class)
                        public class Sample {}
                        """, """
                        package sample;

                        import com.github.database.rider.junit5.api.DBRider;
                        import org.springframework.test.context.TestExecutionListeners;

                        @DBRider
                        @TestExecutionListeners
                        public class Sample {}
                        """));
    }

    @Test
    void addAnnotationIfOnlyDbRiderListenerThroughValueArray() {
        rewriteRun(
                //language=java
                java("""
                        package sample;

                        import org.springframework.test.context.TestExecutionListeners;
                        import com.github.database.rider.spring.DBRiderTestExecutionListener;
                        
                        @TestExecutionListeners({DBRiderTestExecutionListener.class})
                        public class Sample {}
                        """, """
                        package sample;

                        import com.github.database.rider.junit5.api.DBRider;
                        import org.springframework.test.context.TestExecutionListeners;

                        @DBRider
                        @TestExecutionListeners
                        public class Sample {}
                        """));
    }

    @Test
    void keepAnnotationIfOnlyDbRiderListenerSetAndNonDefaultSetting() {
        rewriteRun(
                //TODO: how to get rid of the blank before the listeners attribute?
                //language=java
                java("""
                        package sample;

                        import org.springframework.test.context.TestExecutionListeners;
                        import com.github.database.rider.spring.DBRiderTestExecutionListener;
                        
                        @TestExecutionListeners(value = {DBRiderTestExecutionListener.class}, inheritListeners = false)
                        public class Sample {}
                        """, """
                        package sample;

                        import com.github.database.rider.junit5.api.DBRider;
                        import org.springframework.test.context.TestExecutionListeners;

                        @DBRider
                        @TestExecutionListeners( inheritListeners = false)
                        public class Sample {}
                        """));
    }

    @Test
    void removeListenerFromOtherListeners() {
        rewriteRun(
                //language=java
                java("""
                        package sample;

                        import org.springframework.test.context.TestExecutionListeners;
                        import org.springframework.test.context.support.DirtiesContextTestExecutionListener;

                        @TestExecutionListeners(mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS, listeners = {DBRiderTestExecutionListener.class, DirtiesContextTestExecutionListener.class})
                        public class Sample {}
                        """,
                     """
                        package sample;

                        import com.github.database.rider.junit5.api.DBRider;
                        import org.springframework.test.context.TestExecutionListeners;
                        import org.springframework.test.context.support.DirtiesContextTestExecutionListener;

                        @DBRider
                        @TestExecutionListeners(mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS, listeners = {DBRiderTestExecutionListener.class, DirtiesContextTestExecutionListener.class})
                        public class Sample {}
                        """));
    }

    @Test
    void doNotTouchIfNoListenerPresent() {
        rewriteRun(
                //language=java
                java("""
                        package sample;

                        @Deprecated
                        public class Sample {}
                        """));
    }

    @Test
    void doNotTouchIfDbRiderAlreadyPresent() {
        rewriteRun(
                //language=java
                java("""
                        package sample;

                        import com.github.database.rider.junit5.api.DBRider;
                        import org.springframework.test.context.TestExecutionListeners;
                        import com.github.database.rider.spring.DBRiderTestExecutionListener;

                        @DBRider
                        @TestExecutionListeners(listeners = {DBRiderTestExecutionListener.class}, inheritListeners = false)
                        public class Sample {}
                        """));
    }
}
